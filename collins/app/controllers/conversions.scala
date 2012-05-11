package controllers

package object conversions {

  implicit def help2int(h: Help) = h.id
}
