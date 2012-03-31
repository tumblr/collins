package controllers
package actions

import models.{Asset, AssetLifecycle, AssetMeta, AssetMetaValue, Model}
import models.AssetMeta.Enum.{ChassisTag, PowerPort, RackPosition}
import util.views.Formatter.formatPowerPort

import play.api.mvc._
import play.api.data._

import scala.util.control.Exception.catching

private[controllers] class AssetIntake(stage: Int)(implicit req: Request[AnyContent]) {

  type IntakeResult = AssetIntakeForm

  def execute(asset: Asset): IntakeResult = {
    stage match {
      case 2 => processStage2(asset, Stage2Form.FORM.bindFromRequest)
      case 3 => processStage3(asset, Stage3Form.FORM.bindFromRequest)
      case n => Stage1Form()
    }
  }

  protected def processStage2(asset: Asset, form: Form[Stage2Form]): IntakeResult = {
    if (req.queryString.contains(ChassisTag.toString)) {
      form.fold(e => Stage2Form.get(e), stage2form => {
        stage2form.process(asset).map { error =>
          Stage2Form.get(form.copy(value = None, errors = Seq(error)))
        }.getOrElse(stage2form.copy(errorForm = None))
      })
    } else {
      Stage2Form.get(Stage2Form.FORM)
    }
  }

  protected def processStage3(asset: Asset, form: Form[Stage3Form]): IntakeResult = {
    if (req.queryString.contains(RackPosition.toString)) {
      form.fold(e => Stage3Form.get(e), stage3form => {
        stage3form.process(asset).map { error =>
          Stage3Form.get(form.copy(value = None, errors = Seq(error)))
        }.getOrElse(stage3form.copy(errorForm = None))
      })
    } else {
      Stage3Form.get(Stage3Form.FORM)
    }
  }

}

sealed abstract class AssetIntakeForm {
  protected[actions] def process(asset: Asset): Option[FormError]
}

case class Stage1Form() extends AssetIntakeForm {
  override protected[actions] def process(asset: Asset): Option[FormError] = None
}

case class Stage2Form(chassisTag: String, errorForm: Option[Form[Stage2Form]] = None) extends AssetIntakeForm {

  override protected[actions] def process(asset: Asset): Option[FormError] = {
    SharedStageValidators.validateChassisTag(chassisTag, asset)
  }
}
object Stage2Form {
  val FORM = Form(
    of(Stage2Form.apply _, Stage2Form.unapply _)(
      ChassisTag.toString -> text(1),
      "form" -> ignored(None)
    )
  )
  def get(form: Form[Stage2Form]) = {
    new Stage2Form("", Some(form))
  }
}

case class Stage3Form(
    chassisTag: String,
    rackPosition: String,
    powerPort1: String,
    powerPort2: String,
    errorForm: Option[Form[Stage3Form]] = None)
  extends AssetIntakeForm
{
  override protected[actions] def process(asset: Asset): Option[FormError] = {
    validate(asset).orElse {
      AssetLifecycle.updateAsset(asset, Map(
        RackPosition.toString -> rackPosition,
        formatPowerPort("A") -> powerPort1,
        formatPowerPort("B") -> powerPort2)
      ).left.map { e => Some(FormError("", e.getMessage, Nil)) }
       .right.map { success => if (success) None else Some(FormError("", "Failed to save asset")) }
       .fold(l => l, r => r)
    }
  }

  protected def validate(asset: Asset): Option[FormError] = {
    validateChassisTag(asset)
      .orElse(validateRackPosition(asset))
      .orElse(validatePowerPorts(asset))
  }
  // FIXME: We should actually verify that we have the chassis, rack, and power modules stored as
  // assets
  protected def validateChassisTag(asset: Asset): Option[FormError] = {
    SharedStageValidators.validateChassisTag(chassisTag, asset)
  }
  protected def validateRackPosition(asset: Asset): Option[FormError] = {
    None
  }
  protected def validatePowerPorts(asset: Asset): Option[FormError] = {
    Option(powerPort1 == powerPort2).filter { _ == true }.map { _ =>
      FormError("", "Power ports must not be the same", Nil)
    }
  }
}

object Stage3Form {

  val FORM = Form(
    of(Stage3Form.apply _, Stage3Form.unapply _)(
      ChassisTag.toString -> text(1),
      RackPosition.toString -> text(1),
      formatPowerPort("A") -> text(1),
      formatPowerPort("B") -> text(1),
      "form" -> ignored(None)
    )
  )
  val TRANSITION_FORM: String => Form[Stage3Form] = { s =>
    Stage3Form.FORM.bind(Map(ChassisTag.toString -> s)).copy(errors = Nil)
  }

  def get(form: Form[Stage3Form]) = new Stage3Form("","","","", Some(form))
}

private[actions] object SharedStageValidators {
  private val chassisFormError: Function2[String,Seq[String],FormError] =
    FormError(ChassisTag.toString, _: String, _: Seq[String])

  def validateChassisTag(specifiedTag: String, asset: Asset): Option[FormError] = {
    catching[Option[FormError]](classOf[IndexOutOfBoundsException])
      .opt {
        asset.getMetaAttribute(ChassisTag) match {
          case Some(attrib) =>
            val expected = attrib.getValue
            Option(specifiedTag == expected).filter { _ == false }.flatMap { error =>
              Some(chassisFormError("chassis.mismatch", Seq(asset.tag, expected, specifiedTag)))
            }
          case None =>
            Some(chassisFormError("chassis.none", Seq(asset.tag)))
        }
      }.getOrElse(Some(chassisFormError("chassis.multiple", Seq(asset.tag))))
  }
}
