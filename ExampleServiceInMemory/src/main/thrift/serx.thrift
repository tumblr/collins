namespace java com.tumblr.serx
namespace rb Serx

/**
 * It's considered good form to declare an exception type for your service.
 * Thrift will serialize and transmit them transparently.
 */
exception SerxException {
  1: string description
}

/**
 * A simple memcache-like service, which stores strings by key/value.
 * You should replace this with your actual service.
 */
service SerxService {
  string get(1: string key) throws(1: SerxException ex)

  void put(1: string key, 2: string value)

  void multiPut(1: string key, 2: string value, 3: string key2, 4: string value2)

  void clear()
}
