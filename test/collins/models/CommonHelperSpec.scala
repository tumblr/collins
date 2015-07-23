package collins.models

import collins.models.conversions._
import java.util.Date

trait CommonHelperSpec[REP] extends collins.ResourceFinder {

  def getParser(txt: String): collins.util.parsers.CommonParser[REP]

  def parsed(): REP

  def getParsed(filename: String): REP = {
    val data = getResource(filename)
    val parser = getParser(data)
    val parsed = parser.parse()
    parsed.right.getOrElse(throw parsed.left.get)
  }


  def getStub(): Asset = {
    Asset("test", Status.New.get.id, AssetType.ServerNode.get.id, new Date().asTimestamp, None, None, 1)
  }

  def metaValue2metaWrapper(mvs: Seq[AssetMetaValue]): Seq[MetaWrapper] = {
    val enums = AssetMeta.Enum.values.toSeq
    val dvals = AssetMeta.DynamicEnum.getValues
    mvs.map { mv =>
      val mid = mv.assetMetaId
      MetaWrapper(AssetMeta.findById(mv.assetMetaId).get, mv)
    }
  }
}
