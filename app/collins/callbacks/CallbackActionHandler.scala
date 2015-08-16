package collins.callbacks

import java.beans.PropertyChangeEvent

trait CallbackActionHandler {

  def apply(pce: PropertyChangeEvent)

  protected def getValue(pce: PropertyChangeEvent): CallbackDatumHolder = {
    pce.getNewValue match {
      case CallbackDatumHolder(Some(_)) => pce.getNewValue.asInstanceOf[CallbackDatumHolder]
      case CallbackDatumHolder(None)    => pce.getOldValue.asInstanceOf[CallbackDatumHolder]
    }
  }

  protected def maybeNullString(s: AnyRef): String =
    Option(s).map(_.toString).getOrElse("null")
}
