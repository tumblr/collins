package controllers

import play.core.QueryStringBindable

package object conversions {

  implicit def help2int(h: Help) = h.id
}
