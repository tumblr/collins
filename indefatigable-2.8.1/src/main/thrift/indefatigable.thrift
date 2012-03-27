namespace java com.tumblr.indefatigable.thrift
namespace rb Indefatigable
namespace php Indefatigable

include "twitter/twitter.thrift"

exception IndefatigableException {
  1: string message
}

struct Tick {
  1: string serv,

  /* if omitted, and an op is specified, this is a rate tick */
  2: optional double seconds,

  /* if specified, this is an aggregate tick */
  3: optional string op,

  /* if specified, this is a normal tick */
  4: optional map<string,string> tags,
}

service IndefatigableService extends twitter.ThriftService {
  void sendStats(1: list<Tick> ticks) throws (1: IndefatigableException ex)
}