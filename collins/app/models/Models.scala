package models

import java.util.Date

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

import java.sql._
import org.squeryl.{Session, SessionFactory}
import org.squeryl.adapters.{H2Adapter, MySQLInnoDBAdapter}

/**
 * Wrapper on Play DB object so models don't need an implicit application
 */
object Model {
  import org.squeryl.PrimitiveTypeMode
  val name = "collins"

  lazy val driver = current.configuration.getString("db.%s.driver".format(name)).getOrElse("org.h2.Driver")
  def adapter = driver match {
    case h2 if h2.toLowerCase.contains("h2") => new H2Adapter
    case mysql if mysql.toLowerCase.contains("mysql") => new MySQLInnoDBAdapter
  }

  def withSqueryl[A](f: => A): A = {
    withConnection { con =>
      PrimitiveTypeMode.using(new Session(con, adapter))(f)
    }
  }

  def withSquerylTransaction[A](f: => A): A = {
    withTransaction { con =>
      PrimitiveTypeMode.using(new Session(con, adapter))(f)
    }
  }

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

// This exists because we need to handle conversions of timestamps and Pk/Id cols
object DaoSupport extends ExtendSupport {
  implicit val tsToStatement = new ToStatement[Timestamp] {
    def set(s: PreparedStatement, index: Int, aValue: Timestamp): Unit = {
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

  implicit val byteToStatement = new ToStatement[java.lang.Byte] {
    def set(s: PreparedStatement, index: Int, aValue: java.lang.Byte): Unit = {
      s.setByte(index, aValue)
    }
  }
  implicit def rowToByte: Column[java.lang.Byte] = {
    Column[java.lang.Byte](transformer = { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case byte: java.lang.Byte => Right(byte)
        case int: java.lang.Integer => Right(int.byteValue():java.lang.Byte)
        case _ => Left(TypeDoesNotMatch("Cannot convert " + value +":"+value.asInstanceOf[AnyRef].getClass + " to Byte for column " + qualified))
      }
    })
  }

  override def extendSetAny(original: ToStatement[Any]) = new ToStatement[Any] {
    def set(s: PreparedStatement, i: Int, value: Any): Unit = {
      value match {
        case ts: Timestamp => tsToStatement.set(s,i,value.asInstanceOf[Timestamp])
        case byte: java.lang.Byte => byteToStatement.set(s,i,value.asInstanceOf[java.lang.Byte])
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
    case m if m == Manifest.Byte => {
      Some(implicitly[ColumnTo[java.lang.Byte]]).asInstanceOf[Option[ColumnTo[C]]]
    }
    case m if false => None
  }

  def flattenSql(seq: Seq[SimpleSql[Row]], join: String = " and "): SimpleSql[Row] = {
    val filtered = seq.filter { _.sql.query.nonEmpty }
    val query = filtered.map { _.sql.query }.mkString(join)
    val params = filtered.map { _.params }.flatten
    SqlQuery(query).on(params:_*)
  }

}
