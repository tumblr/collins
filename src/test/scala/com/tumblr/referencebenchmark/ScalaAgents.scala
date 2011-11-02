package com.tumblr.referencebenchmark

import com.tumblr.specsbench._
import scala.actors.Actor._

class ScalaAgents
	extends SpecWithSpecsbenchReporting
{		
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
		}
		
		"react" in {
		}
	}
}