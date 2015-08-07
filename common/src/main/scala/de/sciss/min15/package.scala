package de.sciss

import de.sciss.processor.Processor

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

package object min15 {
  @tailrec def gcd(a: Int, b: Int): Int = if (b == 0) a.abs else gcd(b, a % b)

  def lcm(a: Int, b: Int): Int = (math.abs(a.toLong * b) / gcd(a, b)).toInt

  ///

  type Vec[+A]  = collection.immutable.IndexedSeq[A]
  val  Vec      = collection.immutable.IndexedSeq

  ///

  def waitForProcessor(p: Processor[Any])(implicit exec: ExecutionContext): Unit = {
    val sync = new AnyRef
    val t = new Thread {
      override def run(): Unit = {
        sync.synchronized(sync.wait())
        Thread.sleep(100)
      }
    }
    t.start()

    p.onComplete {
      case _ => sync.synchronized(sync.notify())
    }
  }
}
