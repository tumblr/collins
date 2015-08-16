package collins.util.views

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.twirl.api.Html

import collins.models.conversions._
import collins.models.State
import collins.models.Status

object StateHelper {
  def statesAsJson(): Html = {
    val anyState = State.findByAnyStatus()
    // create a map where keys are asset names and values are state objects
    val statusMap = Status.find().map { status =>
      val states = (State.findByStatus(status) ++ anyState).toSet.toList.sortWith((e1, e2) =>
        e1.name.compareTo(e2.name) < 0
      )
      status.name -> Json.toJson(states)
    }
    Html(Json.stringify(JsObject(statusMap)))
  }
}
