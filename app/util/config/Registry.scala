package util
package config

import java.util.concurrent.ConcurrentHashMap

object Registry {

  val registered = new ConcurrentHashMap[String,Configurable]()

  def register(name: String, config: Configurable) {
    registered.put(name, config)
  }

}
