package models

trait CommonHelper[T] {
  type Reconstruction = Tuple2[T, Seq[MetaWrapper]]
  type FilteredSeq[T1] = Tuple2[Seq[T1], Map[Int, Seq[MetaWrapper]]]


  val managedTags: Set[AssetMeta.Enum]

  /**
   * Construct an appropriate AssetMetaValue sequence from the representation
   */
  def construct(asset: Asset, rep: T): Seq[AssetMetaValue]

  /**
   * Given an asset and sequence of meta wrappers, reconstruct its representation
   */
  def reconstruct(asset: Asset, assetMeta: Seq[MetaWrapper]): Reconstruction

  /**
   * Given some representation, update an asset with appropriate values
   */
  def updateAsset(asset: Asset, rep: T): Boolean = {
    val mvs = construct(asset, rep)
    if (!managedTags.isEmpty) {
      AssetMetaValue.deleteByAssetAndMetaId(asset, managedTags.map(_.id.toLong))
    }

    mvs.size == AssetMetaValue.create(mvs)
  }

  /**
   * Given some asset, reconstruct its representation from meta values
   */
  def reconstruct(asset: Asset): Reconstruction = {
    val assetMeta = AssetMetaValue.findByAsset(asset)
    reconstruct(asset, assetMeta)
  }
  protected def filterNot(m: Seq[MetaWrapper], s: Set[Long]): Seq[MetaWrapper] = {
    m.filterNot { mw => s.contains(mw.getMetaId) }
  }
  protected def finder[T](m: Seq[MetaWrapper], e: AssetMeta.Enum, c: (String => T), d: T): T = {
    m.find { _.getMetaId == e.id }.map { i => c(i.getValue) }.getOrElse(d)
  }
  protected def seqFinder[T](m: Seq[MetaWrapper], e: AssetMeta.Enum, c: (String => T)): Seq[T] = {
    m.filter(_.getMetaId == e.id).map(i => c(i.getValue))
  }
}
