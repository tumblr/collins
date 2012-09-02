package collins
package callbacks

import java.beans.PropertyChangeEvent

trait CallbackActionHandler {
  def apply(pce: PropertyChangeEvent)
}
