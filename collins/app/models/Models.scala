package models

case class User(username: String, password: String)
object User {
  def authenticate(username: String, password: String) = {
    username == "blake" && password == "admin"
  }
}
