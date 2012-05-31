package models

import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api._
import play.api.cache.Cache
import play.api.mvc._
import play.api.Play.current

/**
 * Just a combination of everything needed to do a search.  Probably should
 * combine all this in the future somehow
 *
 * This does not include pagination since pagination for a single remote
 * instance is different from the pagination of the overall request
 */
case class AssetSearchParameters(
  params: util.AttributeResolver.ResultTuple, 
  afinder: AssetFinder, 
  operation: Option[String] = None //"and" or "or"

) {
  def toQueryString: Map[String, String] = {
    val q1: Map[String, String] = (
      params._1.map{case (enum, value) => (enum.toString, value)} ++ 
      params._2.map{case (assetMeta,value) => ("attribute" -> "%s;%s".format(assetMeta.name, value))} ++ 
      params._3.map{i => ("ip_address" -> i)}
    ).toMap ++ afinder.toMap
    operation.map{op => q1 + ("operation" -> op)}.getOrElse(q1)
  }

  def paginationKey = toQueryString.map{case (k,v) => k + "_" + v}.mkString("&")

}

/**
 * Executes the actual search request to a remote collins instance, returning a
 * sequence of result RemoteAssets on success
 */
trait RemoteAssetClient {
  val tag: String //identifier to match up with cached paginations
  def getRemoteAssets(params: AssetSearchParameters, page: PageParams): Seq[AssetView]
}

class HttpRemoteAssetClient(val host: String, val user: String, val pass: String) extends RemoteAssetClient {
  val tag = host

  val queryUrl = host + app.routes.Api.getAssets().toString
  val authenticationTuple = (user, pass, com.ning.http.client.Realm.AuthScheme.BASIC)

  def getRemoteAssets(params: AssetSearchParameters, page: PageParams) = {
    val request = WS.url(queryUrl).copy(
      queryString = params.toQueryString ++ page.toQueryString,
      auth = Some(authenticationTuple)
    )
    val result = request.get.await.get
    val json = Json.parse(result.body)
    (json \ "data" \ "Data") match {
      case JsArray(items) => items.map{
        case obj: JsObject => Some(new RemoteAsset(host, obj))
        case _ => {
          Logger.logger.warn("Invalid asset in response data")
          None
        }
      }.flatten
      case _ => {
        Logger.logger.warn("Invalid response from %s".format(host))
        Nil
      }
    }
  }

}

//class MockRemoteAssetClient

object LocalAssetClient extends RemoteAssetClient {
  val tag = "local"

  def getRemoteAssets(params: AssetSearchParameters, page: PageParams) = Asset.find(page, params.params, params.afinder, params.operation).items
}


/** 
 * An offset representing the NEXT asset to be used from a RemoteAssetStream,
 * designed to be stored in the cache
 */
case class RemoteAssetFinderOffset(page: Int, pageOffset: Int)


/**
 * A peek-able stream of assets from a remote location.  Assets are read from
 * the stream one at a time, but the stream will fetch remote assets in pages
 * from the remote collins instance as needed
 *
 * NOTE - this class is mutable, probably there's a way to turn this into a
 * real Scala Stream
 *
 * NOTE - this class is also currently NOT thread safe
 */
class RemoteAssetStream(val client: RemoteAssetClient, val params: AssetSearchParameters, val initialOffset: Option[RemoteAssetFinderOffset] = None) {

  val PAGE_SIZE = 10
  val SORT = "ASC"

  val cachedAssets = new collection.mutable.Queue[AssetView]
  var nextRetrievedPage: Option[Int] = None
  var eof = false
  
  private[this] def retrieveHead: Option[AssetView] = cachedAssets.headOption match {
    case None if (!eof)=> {
      val page = nextRetrievedPage match {
        case Some(p) => p
        case None => initialOffset.map{_.page}.getOrElse(0)
      }
      val pageParams = PageParams(page, PAGE_SIZE, SORT)
      val results = client.getRemoteAssets(params, pageParams)
      if (results.size > 0) {
        cachedAssets ++= results
        //if we're retrieving results for the first time and there is an
        //initial offset, we have to remove the first pageOffset number of
        //items from the queue
        (nextRetrievedPage, initialOffset) match {
          case (Some(p), Some(init)) => (0 to init.pageOffset - 1).foreach{i => cachedAssets.headOption.foreach{h => cachedAssets.dequeue}}
          case _ => {}
        }
        nextRetrievedPage = Some(page + 1)
        cachedAssets.headOption
      } else {
        eof = true
        None
      }
    }
    case some => some
  }

  /**
   *
   * Returns an offset representing the next item to be dequeued 
   *
   */
  def getCurrentOffset = nextRetrievedPage match {
    case Some(page) => if (cachedAssets.size == 0) {
      RemoteAssetFinderOffset(page + 1, 0)
    } else {
      RemoteAssetFinderOffset(page, PAGE_SIZE - cachedAssets.size)
    }
    case _ => initialOffset.getOrElse(RemoteAssetFinderOffset(0,0))
  }

  /**
   * returns the next asset without removing it from the stream
   */
  def peek: Option[AssetView] = retrieveHead

  /**
   * returns the next asset and removes it from the stream
   */
  def get: Option[AssetView] = retrieveHead.map{h => cachedAssets.dequeue}
}

/**
 * Performs searches on remote collins instances, merging the results with
 * proper sorting and pagination.  The Finder uses the Play Flash scope to
 * store pagination information about each remote instance in order to avoid
 * unnecessary cycling through results with each page load.
 */
object RemoteAssetFinder {

  type PaginationMap = Map[String, RemoteAssetFinderOffset]

  /**
   * Returns the ordering to merge-sort assets.  currently you cannot specify a
   * sort key in the API, so for now sorting is just based on tag alphabetical
   * order
   */
  def getOrdering: Ordering[AssetView] = new Ordering[AssetView] {
    def compare(a: AssetView, b: AssetView) = a.tag compareToIgnoreCase b.tag
  }

  implicit val ordering = getOrdering

  /**
   * uses merge-sort to grab the next item
   */
  private[this] def getNextAsset(streams: Seq[RemoteAssetStream]): Option[AssetView] = streams
    .map{ s => s.peek.map{p => (s,p)}}
    .flatten
    .sortBy(_._2)
    .headOption
    .flatMap{_._1.get}

  /**
   * returns the set of assets for the page
   *
   * Known Issue - If a new data center (with > 0 assets) is added, all cached
   * paginations will be screwed up.
   */
  def get(clients: Seq[RemoteAssetClient], pageParams: PageParams, searchParams: AssetSearchParameters): Seq[AssetView] = {
    val key = searchParams.paginationKey
    val streams = Cache.getAs[PaginationMap](key).filter(_.size == clients.size) match {
      case Some(offsets) => clients.map{client => new RemoteAssetStream(client, searchParams, Some(offsets(client.tag)))}
      case None => {
        //we have to manually cycle through the previous pages
        val s = clients.map{client => new RemoteAssetStream(client, searchParams, None)}
        (0 to (pageParams.page * pageParams.size) - 1).foreach{i => getNextAsset(s)}
        s
      }
    }
    val results = (0 to pageParams.size - 1).map{i => getNextAsset(streams)}.flatten
    Cache.set(key, streams.map{s => (s.client.tag, s.getCurrentOffset)}.toMap, 30)
    results
  }
    
}
