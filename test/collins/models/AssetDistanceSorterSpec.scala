package collins.models

import org.specs2._
import specification._
import collins.models.shared.SortDirection._
import java.sql.Timestamp
import play.api.test.WithApplication

class AssetDistanceSorterSpec extends mutable.Specification {

  args(sequential = true)


  "Asset distance sorter" should {
    "during sorting" in {
      "using sparse" in new WithApplication with mocksorter {
        initialize()
        val expected = List("e", "b", "d", "c", "a")
        val sortedAssets = AssetDistanceSorter.distributionSort(
          targetAsset,
          similarAssets,
          SortAsc,
          sortConfig)
        sortedAssets.map { _.tag } must_== expected
      }

      "using dense" in new WithApplication with mocksorter {
        initialize()
        val expected = List("a", "b", "c", "d", "e")
        val sortedAssets = AssetDistanceSorter.distributionSort(
          targetAsset,
          similarAssets,
          SortDesc,
          sortConfig)
        sortedAssets.map { _.tag } must_== expected
      }
    }
  }

  trait mocksorter {
    val sortParams = List("G", "H", "I")
    val sortValues = List(
      ("t", List(0, 0, 0)),
      ("a", List(0, 0, 1)),
      ("b", List(0, 1, 0)),
      ("c", List(0, 1, 1)),
      ("d", List(1, 0, 0)),
      ("e", List(1, 0, 1)))
    val sortConfig = sortParams.reverse.toSet
    val assetValues = sortValues.map { case (assetTag, values) => (assetTag, values.zip(sortParams)) }

    def initialize() = assetValues.foreach {
      case (assetTag, metaList) =>
        Asset.findByTag(assetTag.toString.toLowerCase).getOrElse {
          val asset =
            Asset.create(Asset(assetTag.toString.toLowerCase, Status.Unallocated.get, AssetType.ServerNode.get))
          metaList.foreach {
            case (value, assetMetaTag) =>
              AssetMeta.findOrCreateFromName(assetMetaTag)
              val meta = AssetMeta.findByName(assetMetaTag).get
              val mv = AssetMetaValue(asset, meta.id, value.toString)
              try {
                AssetMetaValue.create(mv)
              } catch {
                case e: RuntimeException =>
                  Thread.sleep(1000)
                  AssetMetaValue.create(mv)
              }
          }
        }
    }
    def targetAsset = Asset.findByTag(sortValues.head._1).get
    def similarAssets = sortValues.tail.map { t => Asset.findByTag(t._1).get }
  }

  "MockAssetNameEval" should {
    "return correct distance" in {
      val a1 = new Asset("1", 0, 0, new Timestamp(System.currentTimeMillis), None, None)
      val a2 = new Asset("2", 0, 0, new Timestamp(System.currentTimeMillis), None, None)
      val nameeval = new MockAssetNameEval
      nameeval.distance(a1, a2) must_== 1
    }
  }

  "AssetDistanceSorter" should {

    "sort named assets in ascending order" in {
      val assets = (1 to 20).map { i =>
        new Asset(i.toString, 0, 0, new Timestamp(System.currentTimeMillis), None, None)
      }
      assets must_== AssetDistanceSorter.sort(
        new Asset("0", 0, 0, new Timestamp(System.currentTimeMillis), None, None),
        assets,
        AssetSort.Name,
        SortAsc)
    }

    "sort permuted named assets in ascending order" in {
      val assets1 = (11 to 20).map { i =>
        new Asset(i.toString, 0, 0, new Timestamp(System.currentTimeMillis), None, None)
      }
      val assets2 = (1 to 10).map { i =>
        new Asset(i.toString, 0, 0, new Timestamp(System.currentTimeMillis), None, None)
      }
      (assets2 ++ assets1) must_== AssetDistanceSorter.sort(
        new Asset("0", 0, 0, new Timestamp(System.currentTimeMillis), None, None),
        (assets1 ++ assets2),
        AssetSort.Name,
        SortAsc)
    }

    "sort named assets in descending order" in {
      val assets = (1 to 20).map { i =>
        new Asset(i.toString, 0, 0, new Timestamp(System.currentTimeMillis), None, None)
      }
      assets.reverse must_== AssetDistanceSorter.sort(
        new Asset("0", 0, 0, new Timestamp(System.currentTimeMillis), None, None),
        assets,
        AssetSort.Name,
        SortDesc)
    }

  } // AssetDistanceSorter should

}
