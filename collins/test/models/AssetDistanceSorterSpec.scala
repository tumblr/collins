package models

import test.ApplicationSpecification
import org.specs2._
import specification._

import java.sql.Timestamp

class AssetDistanceSorterSpec extends ApplicationSpecification {

  "AssetDistanceSorter" should {

    "create assets" in new mocksorter {
      val assets = assetValues.map{ case(assetTag, metaList) => {
        val asset = Asset.create(Asset(assetTag.toString, Status.Enum.Unallocated, AssetType.Enum.ServerNode))
        metaList.foreach{ case (value, assetMetaTag) =>
          AssetMetaValue.create(AssetMetaValue(asset.id, AssetMeta.findOrCreateFromName(assetMetaTag).id, 0, value.toString))
        }
        asset
      }}
    }

    "sparse" in new mocksorter{
      val expected = List("e","c","d","b","a")
      val sortedAssets = AssetDistanceSorter.distributionSort(
        targetAsset, 
        similarAssets, 
        SortDirection.Asc,
        sortConfig) 
      sortedAssets.map{_.tag} must_== expected
    }

    "dense" in new mocksorter {
      val expected = List("a","b","c","d","e")
      val sortedAssets = AssetDistanceSorter.distributionSort(
        targetAsset, 
        similarAssets, 
        SortDirection.Desc,
        sortConfig) 
      sortedAssets.map{_.tag} must_== expected
    }
  }

  trait mocksorter extends Scope {
    val sortParams = List("A", "B", "C")
    val sortValues = List(
      ("t",List(0,0,0)),
      ("a",List(0,0,1)),
      ("b",List(0,1,0)),
      ("c",List(0,1,1)),
      ("d",List(1,0,0)),
      ("e",List(1,0,1))
    )
    val sortConfig = sortParams.reverse.mkString(",")
    val assetValues = sortValues.map{case (assetTag, values) => (assetTag, values.zip(sortParams))}
    def targetAsset = Asset.findByTag(sortValues.head._1).get
    def similarAssets = sortValues.tail.map{t => Asset.findByTag(t._1).get}
  }

    "MockAssetNameEval" should {

     "return correct distance" in
        {
            val a1 = new Asset("1", 0, 0, new Timestamp(System.currentTimeMillis), None, None)
            val a2 = new Asset("2", 0, 0, new Timestamp(System.currentTimeMillis), None, None)
            val nameeval = new MockAssetNameEval
            nameeval.distance(a1, a2) must_== 1  
        }

    }

    "AssetDistanceSorter" should {

        "sort named assets in ascending order" in
        {
            val assets = 
            (1 to 20).map{ i => 
                new Asset(i.toString, 0, 0, new Timestamp(System.currentTimeMillis), None, None)}
            assets must_== AssetDistanceSorter.sort(
                               new Asset("0", 0, 0, new Timestamp(System.currentTimeMillis), None, None),
                               assets,
                               "name",
                               SortDirection.Asc)
        }

        "sort permuted named assets in ascending order" in
        {
            val assets1 =
            (11 to 20).map{ i => 
                new Asset(i.toString, 0, 0, new Timestamp(System.currentTimeMillis), None, None)}
            val assets2 = 
            (1 to 10).map{ i => 
                new Asset(i.toString, 0, 0, new Timestamp(System.currentTimeMillis), None, None)}
            (assets2 ++ assets1) must_== AssetDistanceSorter.sort(
                               new Asset("0", 0, 0, new Timestamp(System.currentTimeMillis), None, None),
                               (assets1 ++ assets2),
                               "name",
                               SortDirection.Asc)
        }


    

        "sort named assets in descending order" in
        {
            val assets = 
            (1 to 20).map{ i => 
                new Asset(i.toString, 0, 0, new Timestamp(System.currentTimeMillis), None, None)}
            assets.reverse must_== AssetDistanceSorter.sort(
                               new Asset("0", 0, 0, new Timestamp(System.currentTimeMillis), None, None),
                               assets,
                               "name",
                               SortDirection.Desc)
        }
        
    }
  
}
