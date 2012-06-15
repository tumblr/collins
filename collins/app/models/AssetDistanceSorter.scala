package models

import scala.math
import util.Config

trait AssetDistanceSorter{

  /*
   * the name used in querystring parameters for "sortType"
   */
  val name: String

  /**
   * calculates the distance between two assets, returning a positive integer.
   * Higher value corresponds with further distance
   *
   * The ordering induced by distance should be a preorder
   *
   */
  def distance(a: Asset, b: Asset): Int

  
  
}

object SortDirection extends Enumeration {
  type SortDirection  = Value
  val Asc = Value("ASC") //closest asset first
  val Desc = Value("DESC") //furthest asset first

  def op(dir: SortDirection): (Int, Int) => Boolean = if (dir == Asc) _ < _ else _ > _ 
}
import SortDirection._


object AssetDistanceSorter {

  def sort(
    target: Asset, 
    similarAssets: Seq[Asset], 
    sorter: AssetDistanceSorter, 
    direction: SortDirection
  ): Seq[Asset] = similarAssets
    .map{asset => (asset, sorter.distance(target, asset))}
    .sortWith{(a,b) => SortDirection.op(direction)(a._2,b._2)}
    .map{_._1}

}

/**
 * Expects asset names to be parsable integers!
 */
class MockAssetNameSorter extends AssetDistanceSorter {
  val name = "name"
  def distance(a: Asset, b: Asset) = try {
    math.abs(Integer.parseInt(a.tag) - Integer.parseInt(b.tag))
  } catch {
    case n: NumberFormatException => 
      throw new NumberFormatException("MockAssetNameSorter requires asset tags to be parse-able integers (%s)".format(n.getMessage))
  }
    
}

class AbstractPhysicalDistanceSorter(sortkeys: String) extends AssetDistanceSorter{
  val name = "distance"

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
      .split(",")
      .zipWithIndex
      .map{ key => if (a.getMetaAttribute(key._1) == b.getMetaAttribute(key._1)) math.pow(2, key._2).toInt else 0}
      .sum
  }
}


