package models

import scala.math
import scala.collection.mutable.Queue
import util.Config

trait AssetDistanceEval{

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

/**
 * Expects asset names to be parsable integers!
 */
class MockAssetNameEval extends AssetDistanceEval {
  val name = "name"
  def distance(a: Asset, b: Asset) = try {
    math.abs(Integer.parseInt(a.tag) - Integer.parseInt(b.tag))
  } catch {
    case n: NumberFormatException => 
      throw new NumberFormatException("MockAssetNameEval requires asset tags to be parse-able integers (%s)".format(n.getMessage))
  }
    
}

class PhysicalDistanceEval(sortkeys: String) extends AssetDistanceEval{
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
      .map{ key => 
                if ( (a.getMetaAttribute(key._1), b.getMetaAttribute(key._1)) match {
                       case (None, None) => true
                       case (None, _) => false
                       case (_, None) => false
                       case (Some(x),Some(y)) => x.valueEquals(y) } )
                    math.pow(2, key._2).toInt 
                else 0 }
      .sum
  }
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
    sorter: String, 
    direction: SortDirection
  ): Seq[Asset] =  
    sorter match {
        case "name" => genericsort(target, similarAssets, new MockAssetNameEval, direction)
        case "distance" => genericsort(target, similarAssets, new PhysicalDistanceEval(Config.getString("nodeclass.sortkeys","")), direction)
        case "sparse" => 
            {
                /** pulls out assets one at time based on physical proximity to
                    current group of assets. sparse search orders based on least
                    close assets physically. this can be pulled out to take a flag
                    and also serve as a dense search if needed */
                val pulled = new Queue[Asset]
                var remaining = new Queue[(Asset, Int)]
                similarAssets.foreach{ asset => remaining += (asset -> 0) }

                /** start by calculating distance from target */
                pulled += target
                
                val sort = new PhysicalDistanceEval(Config.getString("nodeclass,sortkeys", ""))
                while( !remaining.isEmpty )
                {
                    val max = Int.MaxValue
                    var pull : Option[(Asset, Int)] = None
                    var newQueue = new Queue[(Asset, Int)]
                    remaining.foreach
                    { x => x match { case (asset, d) => 
                        val dist = d + sort.distance(pulled.last, asset)
                        pull = if (dist > max ) pull else 
                            {
                                pull match {
                                    case None => Some(asset -> dist) 
                                    case Some(t) => { newQueue += t 
                                                      Some(asset -> dist) }
                                           }
                            }
                        /* this should never be None since there should always be a min element
                           we have a case for None so we don't get any warnings */
                        pulled += (pull match { case Some((a, d)) => a case None => target})
                        remaining = newQueue
                        newQueue = new Queue[(Asset, Int)] 
                    }}
                }
                /** remove target */
                pulled.tail
            } 
        }
 
    def genericsort(
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


