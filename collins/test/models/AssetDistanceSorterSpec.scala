package models

import test.ApplicationSpecification
import org.specs2._
import specification._

import java.sql.Timestamp

class AssetDistanceSorterSpec extends ApplicationSpecification {

  "AssetDistanceSorter" should {
    "test" in new mocksorter{
      val assets = assetValues.map{ case(assetTag, metaList) => {
        val asset = Asset.create(Asset(assetTag.toString, Status.Enum.Unallocated, AssetType.Enum.ServerNode))
        metaList.foreach{ case (value, assetMetaTag) =>
          AssetMetaValue.create(AssetMetaValue(asset.id, AssetMeta.findOrCreateFromName(assetMetaTag).id, 0, value.toString))
        }
        asset
      }}
      val targetAsset = assets.head
      val similarAssets = assets.tail
      val sortedAssets = AssetDistanceSorter.distributionSort(
        targetAsset, 
        similarAssets, 
        SortDirection.Asc,
        sortConfig) 
      sortedAssets must_== similarAssets.sortWith{(a,b) => a.tag < b.tag}

    }
  }

  trait mocksorter extends Scope {
    val sortParams = List("A", "B", "C")
    val sortValues = List(
      (0,List(0,0,0)),
      (5,List(0,0,1)),
      (4,List(0,1,0)),
      (2,List(0,1,1)),
      (3,List(1,0,0)),
      (1,List(1,0,1))
    )
    val sortConfig = sortParams.reverse.mkString(",")
    val assetValues = sortValues.map{case (assetTag, values) => (assetTag, values.zip(sortParams))}
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
