package de.sciss

import scala.annotation.tailrec

package object min15 {
  @tailrec def gcd(a: Int, b: Int): Int = if (b == 0) a.abs else gcd(b, a % b)

  def lcm(a: Int, b: Int): Int = (math.abs(a.toLong * b) / gcd(a, b)).toInt
}
