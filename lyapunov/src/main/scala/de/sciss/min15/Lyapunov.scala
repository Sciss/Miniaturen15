/*
 * Lyapunov.scala
 * (Miniaturen 15)
 *
 * Copyright (c) 2015 Hanns Holger Rutz. All rights reserved.
 *
 * This software and music is published under the
 * Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International License
 * (CC BY-NC-ND 4.0)
 *
 * For further information, please contact Hanns Holger Rutz at
 * contact@sciss.de
 */

package de.sciss.min15

import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import de.sciss.dsp.FastLog
import de.sciss.file._
import de.sciss.intensitypalette.IntensityPalette
import de.sciss.{processor, numbers}
import de.sciss.processor.Processor

import scala.collection.breakOut
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

object Lyapunov {
  def main(args: Array[String]): Unit = {
    import ExecutionContext.Implicits.global
    val s = Settings(aMin = 3.78, aMax = 3.82, bMin = 3.78, bMax = 3.82,
                     seq = Vector[Double](0,0,1,1.0,0.0,1) /* stringToSeq("AABAB") */,
                     width = 4096, height = 4096, N = 4096,
                     colrMin = -0.5, colrMax = 0.45, invert = true)
    val imgFut   = Processor[BufferedImage]("lya")(calc(_, s))
    val writeFut = imgFut.map { img =>
      ImageIO.write(img, "png", userHome / "Documents" / "temp" / "test.png")
    }
    import processor.ProcessorOps
    println("_" * 33)
    imgFut.monitor(printResult = false)
    writeFut.onComplete {
      case Success(_) => println("Done.")
      case Failure(ex) => ex.printStackTrace()
    }
    val t = new Thread {
      override def run(): Unit = this.synchronized(this.wait())
      start()
    }
    writeFut.onComplete {
      case _ => t.synchronized(t.notify())
    }
  }

  def stringToSeq(s: String): Vector[Double] = s.map(c => if (c == 'A') 0.0 else 1.0)(breakOut)

  case class Settings(aMin: Double, aMax: Double, bMin: Double, bMax: Double,
                      seq: Vector[Double], width: Int, height: Int, N: Int,
                      colrMin: Double, colrMax: Double, invert: Boolean)

  // https://en.wikipedia.org/wiki/Lyapunov_fractal
  def calc(self: Processor[BufferedImage] with Processor.Body, settings: Settings): BufferedImage = {
    import settings._
    val img     = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g       = img.createGraphics()
    val seqLen  = seq.length
    val numPixels = width * height
    var lMin    = Double.PositiveInfinity
    var lMax    = Double.NegativeInfinity
    val seqA    = seq.toArray
    val log     = FastLog(base = math.E, q = 8)
    var yi = 0
    while (yi < height) {
      var xi = 0
      while (xi < width) {
        import numbers.Implicits._
        val a = xi.linlin(0, width  - 1, aMin, aMax)
        val b = yi.linlin(0, height - 1, bMin, bMax)
        var n = 0
        var x = 0.5
        var sum = 0.0
        while (n < N) {
          val w = seqA(n % seqLen)
          val r = (1 - w) * a + w * b
          x = r * x * (1 - x)
          val arg = math.abs(r * (1 - 2 * x)).toFloat
          if (arg == 0) {
            sum = Float.NegativeInfinity
            n = N
          } else {
            sum += log.calc(arg)
            n += 1
          }
        }
        val lambda0 = sum / N
        if (lambda0 < lMin) lMin = lambda0
        if (lambda0 > lMax) lMax = lambda0
        val lambda1 = lambda0.linlin(colrMin, colrMax, 0, 1).clip(0, 1)
        val lambda = if (invert) 1 - lambda1 else lambda1

        val rgb = IntensityPalette.apply(lambda.toFloat)
        g.setColor(new Color(rgb))
        g.fillRect(xi, yi, 1, 1)
        xi += 1

        self.progress = (yi * width + xi).toDouble / numPixels
        self.checkAborted()
      }
      yi += 1
    }
    g.dispose()

    println(s"min = $lMin, max = $lMax")

    img
  }
}
