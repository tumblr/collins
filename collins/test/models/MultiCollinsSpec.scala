package models

import test.ApplicationSpecification
import org.specs2._ /** check up on this */
import specification._

class MultiCollinsSpec extends mutable.Specification {

  //TODO, same thing exists in AssetSearchParameterSpec, unify
  val EMPTY_RESULT_TUPLE = (Nil, Nil, Nil)
  val EMPTY_FINDER = AssetFinder(None, None, None, None, None, None, None)

  val params = new AssetSearchParameters(EMPTY_RESULT_TUPLE, EMPTY_FINDER)

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

    // note to self - since the previous three tests are syntactically similar,
    // maybe i should make them one test?


    "peek correctly - small" in {
        val mock = new MockRemoteAssetClient(AssetGenerator(10))
        val queue = new RemoteAssetQueue(mock, params)
        (0 to 10).map{i => queue.peek}.flatten must_== (0 to 10).map{i => AssetGenerator(0)}.flatten 
    }

    "peek correctly - more than full page" in {
        val mock = new MockRemoteAssetClient(AssetGenerator(100))
        val queue = new RemoteAssetQueue(mock, params)
        (0 to 100).map{i => queue.peek}.flatten must_== (0 to 100).map{i => AssetGenerator(0)}.flatten 
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
    "return 50 merged assets" in {
        val mock_start = new MockRemoteAssetClient(AssetGenerator(20))
        val mock_mid = new MockRemoteAssetClient(AssetGenerator(40).slice(20,40))
        val mock_end = new MockRemoteAssetClient(AssetGenerator(60).slice(40,60))
        val asset_seq = List(mock_start, mock_mid, mock_end)
        val stream = new RemoteAssetStream(asset_seq, params)
        0 must_== 1
    }
  }


} 
