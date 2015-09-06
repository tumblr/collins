package collins.util

import scala.collection.immutable.SortedSet

import collins.util.power.PowerComponent
import collins.util.power.PowerUnit

package object power {
  type PowerComponents = Set[PowerComponent]
  type PowerUnits = SortedSet[PowerUnit]
}
