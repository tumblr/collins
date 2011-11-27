package models

case class User(username: String, password: String) {
  def authenticate(): Boolean = {
    username == "blake" && password == "admin:first"
  }
}

object User {
  def authenticate(username: String, password: String) = {
    User(username, password).authenticate
  }
}
