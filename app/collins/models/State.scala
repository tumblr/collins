package collins.models

import scala.math.Ordering

import org.squeryl.PrimitiveTypeMode.__thisDsl
import org.squeryl.PrimitiveTypeMode.from
import org.squeryl.PrimitiveTypeMode.int2ScalarInt
import org.squeryl.PrimitiveTypeMode.select
import org.squeryl.PrimitiveTypeMode.string2ScalarString
import org.squeryl.Schema

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.models.shared.AnormAdapter
import collins.models.shared.ValidatedEntity
import collins.validation.Pattern.isAlphaNumericString
import collins.validation.Pattern.isNonEmptyString

import Status.StatusFormat

object State extends Schema with AnormAdapter[State] {

  /** Status value that can apply to any asset, regardless of status **/
  val ANY_STATUS = 0
  val ANY_NAME = "Any"

  def New = State.findByName("NEW")
  def Running = State.findByName("RUNNING")
  def Starting = State.findByName("STARTING")
  def Terminated = State.findByName("TERMINATED")

  object StateOrdering extends Ordering[State] {
    override def compare(a: State, b: State) = a.getDisplayLabel compare b.getDisplayLabel
  }

  implicit object StateFormat extends Format[State] {
    import Status.StatusFormat
    override def reads(json: JsValue) = JsSuccess(State(
      (json \ "ID").asOpt[Int].getOrElse(0),
      (json \ "STATUS").asOpt[Int].getOrElse(ANY_STATUS),
      (json \ "NAME").as[String],
      (json \ "LABEL").as[String],
      (json \ "DESCRIPTION").as[String]
    ))
    override def writes(state: State) = JsObject(Seq(
      "ID" -> Json.toJson(state.id),
      "STATUS" -> Json.toJson(Status.findById(state.status)),
      "NAME" -> Json.toJson(state.name),
      "LABEL" -> Json.toJson(state.label),
      "DESCRIPTION" -> Json.toJson(state.description)
    ))
  }
  override val tableDef = table[State]("state")
  on(tableDef)(s => declare(
    s.id is (autoIncremented,primaryKey),
    s.name is(unique),
    s.status is(indexed)
  ))

  override def cacheKeys(s: State) = Seq(
    "State.find",
    "State.findById(%d)".format(s.id),
    "State.findByName(%s)".format(s.name.toLowerCase),
    "State.findByAnyStatus",
    "State.findByStatus(%d)".format(s.status)
  )

  override def delete(state: State): Int = inTransaction {
    afterDeleteCallback(state) {
      tableDef.deleteWhere(s => s.id === state.id)
    }
  }

  def empty = new State(0, ANY_STATUS, "INVALID", "Invalid Label", "Invalid Description")

  def find(): List[State] = getOrElseUpdate("State.find") {
    from(tableDef)(s => select(s)).toList
  }
  def findById(id: Int): Option[State] = getOrElseUpdate("State.findById(%d)".format(id)) {
    tableDef.lookup(id)
  }
  def findByName(name: String): Option[State] =
    getOrElseUpdate("State.findByName(%s)".format(name.toLowerCase)) {
      tableDef.where(s =>
        s.name.toLowerCase === name.toLowerCase
      ).headOption
    }
  def findByAnyStatus(): List[State] =
    getOrElseUpdate("State.findByAnyStatus") {
      tableDef.where(s =>
        s.status === ANY_STATUS
      ).toList
    }
  def findByStatus(status: Status): List[State] =
    getOrElseUpdate("State.findByStatus(%d)".format(status.id)) {
      tableDef.where(s =>
        s.status === status.id
      ).toList
    }
  override def get(state: State): State = findById(state.id).get
  def isSystemState(state: State): Boolean = state.id < 7
}

case class State(
  id: Int,            // unique PK
  status: Int = 0,    // FK to status, or 0 to apply to any status
  name: String,       // Name, should be tag like (alpha numeric and _-)
  label: String,      // A visual (short) label to accompany the state
  description: String // A longer description of the state
) extends ValidatedEntity[Int] {
  def getId(): Int = id
  def getDisplayLabel(): String = "%s - %s".format(getStatusName, label)
  def getStatusName(): String = status match {
    case 0 => State.ANY_NAME
    case n => Status.findById(n).map(_.name).getOrElse("Unknown")
  }
  override def validate() {
    require(status >= 0, "status must be >= 0")
    require(isAlphaNumericString(name), "State name must be alphanumeric")
    require(name.length > 1 && name.length <=32, "length of name must between 1 and 32")
    require(name == name.toUpperCase, "name must be uppercase")
    require(isNonEmptyString(label), "label must be specified")
    require(label.length > 1 && label.length <= 32, "length of label must be between 1 and 32 characters")
    require(isNonEmptyString(description), "Description must be specified")
    require(description.length > 1 && description.length <= 255, "length of description must be between 1 and 255 characters")
  }
  override def asJson: String = Json.toJson(this).toString
}
