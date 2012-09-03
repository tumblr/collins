package models

import scala.math
import util.config.NodeclassifierConfig
import play.api.Logger

trait AssetDistanceEval {

  /**
   * calculates the distance between two assets, returning a positive integer.
   * Higher value corresponds with further distance
   *
   * The ordering induced by distance should be a preorder
   */
  def distance(a: Asset, b: Asset): Int
}

/**
 * Expects asset names to be parsable integers!
 */
class MockAssetNameEval extends AssetDistanceEval {
  def distance(a: Asset, b: Asset) = try {
    math.abs(Integer.parseInt(a.tag) - Integer.parseInt(b.tag))
  } catch {
    case n: NumberFormatException => 
      throw new NumberFormatException("MockAssetNameEval requires asset tags to be parse-able integers (%s)".format(n.getMessage))
  }
    
}

class PhysicalDistanceEval(sortkeys: Set[String]) extends AssetDistanceEval {
  /**
   * Calculates physical distance using the configured set of nodeclass.sortKeys
   *
   * The formula is as follows:
   * - sort keys are ordered from least to most significant, let Sn be the nth sort key (starting at 0)
   * - let a.Sn be the tag value of Sn for asset a
   * - let f(n) = if (a.Sn == b.Sn) 0 else 1
   * - let distance(a,b) = SUM(i: 0 to n-1) (2 * f(i))^i
   */
  def distance(a: Asset, b: Asset): Int = {
    sortkeys
      .zipWithIndex
      .map{ key => 
        if ( (a.getMetaAttribute(key._1), b.getMetaAttribute(key._1)) match {
            case (None, None) => true
            case (None, _) => false
            case (_, None) => false
            case (Some(x),Some(y)) => x.valueEquals(y) } )
          math.pow(2, key._2).toInt 
        else 0 
      }
      .sum
  }

}

import SortDirection._

object AssetSortType extends Enumeration {
  type AssetSortType = Value
  val Name = Value("name")
  val Distance = Value("distance")
  val Distribution = Value("distribution")
  val Arbitrary = Value("arbitrary")
}
import AssetSortType._

object AssetDistanceSorter {
  
  def sortKeys = NodeclassifierConfig.sortKeys

  def sort(
    target: Asset, 
    similarAssets: Seq[Asset], 
    sortType: AssetSortType, 
    direction: SortDirection
  ): Seq[Asset] = sortType match {
    case Name => genericSort(target, similarAssets, new MockAssetNameEval, direction)
    case Distance => genericSort(target, similarAssets, new PhysicalDistanceEval(sortKeys), direction)
    /** Asc means sparse search, Desc means dense search */
    case Distribution => distributionSort(target, similarAssets, direction, sortKeys)
    case Arbitrary => similarAssets
  }

  def distributionSort(target: Asset, similar: Seq[Asset], direction: SortDirection, sortKeys: Set[String]) = {
    val sort = new PhysicalDistanceEval(sortKeys)

    /** pulls out assets one at time based on physical proximity to
        current group of assets. sparse search orders based on least
        close assets physically. this can be pulled out to take a flag
        and also serve as a dense search if needed */
    def sortLoop(build: Seq[Asset], remain: Seq[(Asset, Int)]): Seq[Asset] = if (remain == Nil) build else {
      val s = remain
        .map{case (assetA, sum) => (assetA, sum + sort.distance(assetA, build.headOption.getOrElse(target)))}
        .sortWith{(a,b) => op(direction)(a._2,b._2) || (a._2 == b._2 && a._1.tag < b._1.tag)}
      sortLoop(s.head._1 +: build, s.tail)
    }

    sortLoop(Nil, similar.map{x => (x,0)}).reverse
  }

  def genericSort(
    target: Asset,
    similarAssets: Seq[Asset],
    sorter: AssetDistanceEval,
    direction : SortDirection
  ): Seq[Asset] = {
    similarAssets
      .map{asset => (asset, sorter.distance(target, asset))}
      .sortWith{(a,b) => SortDirection.op(direction)(a._2,b._2)}
      .map{_._1}
  }

}


