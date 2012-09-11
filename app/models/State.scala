package models

import collins.validation.Pattern.{isAlphaNumericString, isNonEmptyString}

import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema
import scala.math.Ordering

object State extends Schema with AnormAdapter[State] {

  /** Status value that can apply to any asset, regardless of status **/
  val ANY_STATUS = 0

  def New = State.findByName("NEW")
  def Running = State.findByName("RUNNING")
  def Terminated = State.findByName("TERMINATED")

  object StateOrdering extends Ordering[State] {
    override def compare(a: State, b: State) = a.getDisplayLabel compare b.getDisplayLabel
  }

  implicit object StateFormat extends Format[State] {
    import Status.StatusFormat
    override def reads(json: JsValue) = State(
      (json \ "ID").asOpt[Int].getOrElse(0),
      (json \ "STATUS").asOpt[Int].getOrElse(ANY_STATUS),
      (json \ "NAME").as[String],
      (json \ "LABEL").as[String],
      (json \ "DESCRIPTION").as[String]
    )
    override def writes(state: State) = JsObject(Seq(
      "ID" -> toJson(state.id),
      "STATUS" -> toJson(Status.findById(state.status)),
      "NAME" -> toJson(state.name),
      "LABEL" -> toJson(state.label),
      "DESCRIPTION" -> toJson(state.name)
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
}

case class State(
  id: Int,            // unique PK
  status: Int = 0,    // FK to status, or 0 to apply to any status
  name: String,       // Name, should be tag like (alpha numeric and _-)
  label: String,      // A visual (short) label to accompany the state
  description: String // A longer description of the state
) extends ValidatedEntity[Int] {
  private[this] val logger = Logger("State")

  def getId(): Int = id
  def getDisplayLabel(): String = "%s - %s".format(getStatusName, label)
  def getStatusName(): String = status match {
    case 0 => "Any"
    case n => Status.findById(n).map(_.name).getOrElse("Unknown")
  }
  override def validate() {
    require(status >= 0, "status must be >= 0")
    require(isAlphaNumericString(name), "State name must be alphanumeric")
    require(name.length > 1 && name.length <=32, "length of name must between 1 and 32")
    require(isNonEmptyString(label), "label must be specified")
    require(label.length > 1 && label.length <= 32, "length of label must be between 1 and 32 characters")
    require(isNonEmptyString(description), "Description must be specified")
    require(description.length > 1 && description.length <= 255, "length of description must be between 1 and 255 characters")
  }
  override def asJson: String = toJson(this).toString
}
