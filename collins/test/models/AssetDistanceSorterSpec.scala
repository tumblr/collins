package models

import test.ApplicationSpecification
import org.specs2._
import specification._

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
      sortedAssets.foreach{a => println(a.tag)}
      sortedAssets must_== similarAssets.sortWith{(a,b) => a.tag < b.tag}

    }
  }

  trait mocksorter extends Scope {
    val sortParams = List("A", "B", "C")
    val sortValues = List(
      (0,List(0,0,0)),
      (4,List(0,0,1)),
      (2,List(0,1,0)),
      (5,List(0,1,1)),
      (3,List(1,0,0)),
      (1,List(1,0,1))
    )
    val sortConfig = sortParams.mkString(",")
    val assetValues = sortValues.map{case (assetTag, values) => (assetTag, values.zip(sortParams))}
  }

}
