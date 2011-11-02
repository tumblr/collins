package com.tumblr.referencebenchmark

import com.tumblr.specsbench._
import scala.actors.Actor._

class ScalaAgents
	extends SpecWithSpecsbenchReporting
{
  report(
    metrics("blocking_receive", Seq(min, mean, max)),
    metrics("blocking_react", Seq(min, mean, max))
  )

	"Agents" should {
		case class Message(val number: Int)
		case class Reply(val number: Int)
		
		"receive" in {
			object Receiver {
				val processor = actor {
					loop {
						receive {
							case Message(index) =>
								reply(Reply(index + 1))
						}
					}
				}
			}

      statTiming("blocking_receive", 50) {
        var index = 0
        while(index < 1000) {
          Receiver.processor !? Message(index)
          index += 1
        }
      }

		}
		
		"react" in {
      object Reactor {
        val processor = actor {
          loop {
            receive {
              case Message(index) =>
                reply(Reply(index + 2))
            }
          }
        }
      }

      statTiming("blocking_react", 50) {
        var index = 0
        while(index < 1000) {
          Reactor.processor !? Message(index)
          index += 1
        }
      }
		}
	}
}