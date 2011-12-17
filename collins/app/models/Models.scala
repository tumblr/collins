package models

import java.util.Date

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

import java.sql._

/**
 * Wrapper on Play DB object so models don't need an implicit application
 */
object Model {
  val name = "collins"
  def withConnection[A](block: Connection => A): A = {
    DB.withConnection(name)(block)(current)
  }
  def withTransaction[A](block: Connection => A): A = {
    DB.withTransaction(name)(block)(current)
  }

  val defaults = new Convention(asIs, DaoSupport) with WithDefaults {
    override lazy val defaultConvention = asIs
  }

}

import java.sql.Timestamp
// This exists because we need to handle conversions of timestamps and Pk/Id cols
object DaoSupport extends ExtendSupport {
  implicit val tsToStatement = new ToStatement[java.sql.Timestamp] {
    def set(s: java.sql.PreparedStatement, index: Int, aValue: java.sql.Timestamp): Unit = {
      s.setTimestamp(index, aValue)
    }
  }
  implicit def rowToTimestamp: Column[Timestamp] = {
    Column[Timestamp](transformer = { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case date: Timestamp => Right(date)
        case _ => Left(TypeDoesNotMatch("Cannot convert " + value +":"+value.asInstanceOf[AnyRef].getClass + " to Timestamp for column " + qualified))
      }
    })
  }

  override def extendSetAny(original: ToStatement[Any]) = new ToStatement[Any] {
    def set(s: java.sql.PreparedStatement, i: Int, value: Any): Unit = {
      value match {
        case ts: java.sql.Timestamp => tsToStatement.set(s,i,value.asInstanceOf[java.sql.Timestamp])
        case _ => original.set(s,i,value)
      }
    }
  }

  override def extendExtractor[C](f: (Manifest[C] => Option[ColumnTo[C]])): PartialFunction[Manifest[C], Option[ColumnTo[C]]] = {
    case m if 
       // Fixed for Id
       m <:< manifest[Id[Any]] ||
       // Added support for checking Pk
       m <:< manifest[Pk[Any]] => {
      val typeParam = m.typeArguments
        .headOption
        .collect { case m: ClassManifest[_] => m }
        .getOrElse(implicitly[Manifest[Any]])
      f(typeParam.asInstanceOf[Manifest[C]]).map(mapper => ColumnTo.rowToPk(mapper)).asInstanceOf[Option[ColumnTo[C]]]
      // OR: getExtractor(typeParam).map(mapper => ColumnTo.rowToPk(mapper)).asInstanceOf[Option[ColumnTo[C]]]
    }
    case m if m == Manifest.classType(classOf[Timestamp]) => {
      Some(implicitly[ColumnTo[Timestamp]]).asInstanceOf[Option[ColumnTo[C]]]
    }
    case _ if false => None
  }

}
