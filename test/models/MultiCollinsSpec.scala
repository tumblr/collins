package models

import test.ApplicationSpecification
import org.specs2._ /** check up on this */
import specification._

class MultiCollinsSpec extends mutable.Specification {

  //TODO, same thing exists in AssetSearchParameterSpec, unify
  val EMPTY_RESULT_TUPLE = (Nil, Nil, Nil)

  val params = new AssetSearchParameters(EMPTY_RESULT_TUPLE, AssetFinder.empty)

  "RemoteAssetQueue" should {

    // one page
    "return 50 assets" in {
        val mock = new MockRemoteAssetClient(AssetGenerator(50))
        val queue = new RemoteAssetQueue(mock, params)
        (0 to 50).map{i => queue.get}.flatten must_== AssetGenerator(50) 
    }

    // two pages
    "return 100 assets" in {
        val mock = new MockRemoteAssetClient(AssetGenerator(100))
        val queue = new RemoteAssetQueue(mock, params)
        (0 to 100).map{i => queue.get}.flatten must_== AssetGenerator(100) 
    }

    // a lot of pages 
    "return 20000 assets" in {
        val mock = new MockRemoteAssetClient(AssetGenerator(20000))
        val queue = new RemoteAssetQueue(mock, params)
        (0 to 20000).map{i => queue.get}.flatten must_== AssetGenerator(20000) 
    }

    "peek correctly - small" in {
        val mock = new MockRemoteAssetClient(AssetGenerator(10))
        val queue = new RemoteAssetQueue(mock, params)
        (0 to 9).map{i => queue.peek}.flatten must_== List.fill(10)((AssetGenerator(1)).apply(0)) 
    }

    "peek correctly - more than full page" in {
        val mock = new MockRemoteAssetClient(AssetGenerator(100))
        val queue = new RemoteAssetQueue(mock, params)
        (0 to 99).map{i => queue.peek}.flatten must_== List.fill(100)((AssetGenerator(1)).apply(0)) 
    }

    "not repeatedly make requests on EOF" in {
      val mock = new MockRemoteAssetClient(Nil)
      val queue = new RemoteAssetQueue(mock, params)
      queue.get
      mock.numRequests must_== 1
      (0 to 50).foreach{i => queue.get}
      mock.numRequests must_== 1
    }

  }

  "RemoteAssetStream" should {
    "return 50 merged assets in correct order" in {
      val numClients = 4
      val assets = AssetGenerator(50)
      val clients = assets
        .zipWithIndex
        .groupBy{case(a, i) => i % numClients}
        .toSeq
        .map{case(index, assets) => new MockRemoteAssetClient(assets.map{_._1})}
      val stream = new RemoteAssetStream(clients, params)
      stream.slice(0, 50) must_== assets
    }
  }


} 
