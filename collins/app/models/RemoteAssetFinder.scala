package models

/**
 * Just a combination of everything needed to do a search.  Probably should
 * combine all this in the future somehow
 */
case class AssetSearchParameters(
  params: util.AttributeResolver.ResultTuple, 
  afinder: AssetFinder, 
  operation: Option[String] = None, //"and" or "or"
  sort: String //ASC or DESC
)

/**
 * Executes the actual search request to a remote collins instance, returning a
 * sequence of result RemoteAssets on success
 */
trait RemoteAssetClient {
  def getRemoteAssets(params: AssetSearchParameters, page: PageParams): Seq[AssetView]
}

//class HttpRemoteAssetClient
//class MockRemoteAssetClient
//class LocalAssetClient  //<-- needed to merge results with remote assets


/** 
 * An offset representing the NEXT asset to be used from a RemoteAssetStream,
 * designed to be stored in the Flash scope
 */
case class RemoteAssetFinderOffset(page: Int, pageOffset: Int)


/**
 * A peek-able stream of assets from a remote location.  Assets are read from
 * the stream one at a time, but the stream will fetch remote assets in pages
 * from the remote collins instance as needed
 *
 * NOTE - this class is mutable, probably there's a way to turn this into a
 * real Scala Stream
 */
class RemoteAssetStream(client: RemoteAssetClient, params: AssetSearchParameters, initialOffset: RemoteAssetFinderOffset) {
  /**
   * returns the next asset without removing it from the stream
   */
  def peek: Option[AssetView]

  /**
   * returns the next asset and removes it from the stream
   */
  def get: Option[AssetView]
}

/**
 * Performs searches on remote collins instances, merging the results with
 * proper sorting and pagination.  The Finder uses the Play Flash scope to
 * store pagination information about each remote instance in order to avoid
 * unnecessary cycling through results with each page load.
 */
class RemoteAssetFinder(streams: Seq[RemoteAssetStream]) {

  /**
   * Returns the ordering to merge-sort assets.  currently you cannot specify a
   * sort key in the API, so for now sorting is just based on tag alphabetical
   * order
   */
  def getOrdering: Ordering[AssetView] = new Ordering[AssetView] {
    def compare(a: AssetView, b: AssetView) = a.tag compareToIgnoreCase b.tag
  }

  /**
   * returns the set of assets for the page
   */
  def get(pageParams: PageParams, searchParams: AssetSearchParameters): Seq[AssetView]
}
