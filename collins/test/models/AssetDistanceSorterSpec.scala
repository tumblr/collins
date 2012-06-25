package models

import test.ApplicationSpecification
import org.specs2._
import specification._

import java.sql.Timestamp

class AssetDistanceSorterSpec extends mutable.Specification {

    "MockAssetNameEval" should {

     "return correct distance" in
        {
            val a1 = new Asset("1", 0, 0, new Timestamp(System.currentTimeMillis), None, None)
            val a2 = new Asset("2", 0, 0, new Timestamp(System.currentTimeMillis), None, None)
            val nameeval = new MockAssetNameEval
            nameeval.distance(a1, a2) must_== 1  
        }

    }

    "PhysicalDistanceEval" should {
        "return assets in ascending order" in
        {
            val assets : Seq[AssetMeta] =
            (1 to 20).map{ i =>
                (1 to 20).map { j =>
                    {
                        Asset( ((i * 20)+j).toString, 
                    }
                }}
          

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
            assets must_== AssetDistanceSorter.sort(
                               new Asset("0", 0, 0, new Timestamp(System.currentTimeMillis), None, None),
                               assets,
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
