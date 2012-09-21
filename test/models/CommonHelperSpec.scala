package test
package models

import _root_.models.conversions._
import _root_.models.{Asset, AssetMeta, AssetMetaValue, AssetType, MetaWrapper, Status}
import java.util.Date

trait CommonHelperSpec[REP] extends test.ResourceFinder {

  def getParser(txt: String): _root_.util.parsers.CommonParser[REP]

  def parsed(): REP

  def getParsed(filename: String): REP = {
    val data = getResource(filename)
    val parser = getParser(data)
    val parsed = parser.parse()
    parsed.right.get
  }


  def getStub(): Asset = {
    Asset("test", Status.New.get.id, AssetType.ServerNode.get.id, new Date().asTimestamp, None, None, 1)
  }

  def metaValue2metaWrapper(mvs: Seq[AssetMetaValue]): Seq[MetaWrapper] = {
    val enums = AssetMeta.Enum.values.toSeq
    mvs.map { mv =>
      val mid = mv.asset_meta_id
      val meta = enums.find { e => e.id == mid }.map { e =>
        AssetMeta(e.toString, -1, e.toString, e.toString, mv.asset_meta_id)
      }.getOrElse(throw new Exception("Found unhandled AssetMeta"))
      MetaWrapper(meta, mv)
    }
  }
}
