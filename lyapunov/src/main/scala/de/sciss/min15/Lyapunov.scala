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
import javax.swing.UIManager

import de.sciss.audiowidgets.Axis
import de.sciss.dsp.FastLog
import de.sciss.file._
import de.sciss.guiflitz.AutoView
import de.sciss.intensitypalette.IntensityPalette
import de.sciss.processor.Processor
import de.sciss.swingplus.CloseOperation
import de.sciss.{numbers, processor}
import de.sciss.swingplus.Implicits._

import scala.collection.breakOut
import scala.concurrent.ExecutionContext
import scala.swing.Swing._
import scala.swing.{BoxPanel, BorderPanel, Component, Frame, Graphics2D, Orientation}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object Lyapunov {
  def main(args: Array[String]): Unit = {
    onEDT {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)
      } catch {
        case NonFatal(_) => // ignore
      }
      mkFrame()
    }
  }

  def test(): Unit = {
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

  def mkFrame(): Unit = {
    val hAxis1  = new Axis(Orientation.Horizontal)
    val vAxis1  = new Axis(Orientation.Vertical  )
    val hAxis2  = new Axis(Orientation.Horizontal)
    val vAxis2  = new Axis(Orientation.Vertical  )

    val iw      = 640
    val ih      = 640
    val img     = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB)

    val avCfg   = AutoView.Config()
    avCfg.small = true

    val cfg0    = Config(aMin = 3.78, aMax = 3.82, bMin = 3.78, bMax = 3.82,
      seq = "AABBAAB", width = iw, height = ih, N = 4096, colrMin = -0.5,
      colrMax = 0.45, invert = true)

    val cfgView = AutoView(cfg0, avCfg)

    def updateAxes(): Unit = {
      val cfg = cfgView.cell()
      hAxis1.minimum  = cfg.aMin
      hAxis1.maximum  = cfg.aMax
      vAxis1.minimum  = cfg.bMin
      vAxis1.maximum  = cfg.bMax
      hAxis2.minimum  = hAxis1.minimum
      hAxis2.maximum  = hAxis1.maximum
      vAxis2.minimum  = vAxis1.minimum
      vAxis2.maximum  = vAxis1.maximum
    }

    updateAxes()

    cfgView.cell.addListener {
      case _ =>
        updateAxes()
    }

    val comp    = new Component {
      preferredSize = (iw, ih)

      override protected def paintComponent(g: Graphics2D): Unit = {
        super.paintComponent(g)
        g.drawImage(img, 0, 0, peer)
      }
    }
    val bp = new BorderPanel {
      add( comp , BorderPanel.Position.Center)
      add(new BoxPanel(Orientation.Horizontal) {
        contents += HStrut(16)
        contents += hAxis1
        contents += HStrut(16)
      }, BorderPanel.Position.North )
      add(vAxis1, BorderPanel.Position.West  )
      add(new BoxPanel(Orientation.Horizontal) {
        contents += HStrut(16)
        contents += hAxis2
        contents += HStrut(16)
      }, BorderPanel.Position.South )
      add(vAxis2, BorderPanel.Position.East  )
    }
    val f = new Frame { self =>
      title = "Lyapunov"
      contents = new BorderPanel {
        add(bp, BorderPanel.Position.Center)
        add(cfgView.component, BorderPanel.Position.East)
      }
      pack().centerOnScreen()
      open()
    }
    f.defaultCloseOperation = CloseOperation.Exit
  }

  def stringToSeq(s: String): Vector[Double] = s.map(c => if (c == 'A') 0.0 else 1.0)(breakOut)

  case class Config(aMin: Double, aMax: Double, bMin: Double, bMax: Double,
                    seq: String, width: Int, height: Int, N: Int,
                    colrMin: Double, colrMax: Double, invert: Boolean) {
    def toSettings: Settings =
      Settings(aMin = aMin, aMax = aMax, bMin = bMin, bMax = bMax, seq = stringToSeq(seq),
        width = width, height = height, N = N, colrMin = colrMin, colrMax = colrMax, invert = invert)
  }

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