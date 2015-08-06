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

import java.awt.{RenderingHints, Cursor, Color}
import java.awt.geom.{Point2D, AffineTransform}
import java.awt.image.BufferedImage
import javax.swing.UIManager

import de.sciss.audiowidgets.Axis
import de.sciss.dsp.FastLog
import de.sciss.guiflitz.AutoView
import de.sciss.intensitypalette.IntensityPalette
import de.sciss.model.Model
import de.sciss.numbers
import de.sciss.processor.Processor
import de.sciss.swingplus.CloseOperation
import de.sciss.swingplus.Implicits._

import scala.collection.breakOut
import scala.concurrent.ExecutionContext
import scala.swing.Swing._
import scala.swing.event.{MouseDragged, MouseReleased, MousePressed, MouseEvent, MouseMoved, MouseExited, MouseEntered, ButtonClicked}
import scala.swing.{Point, BorderPanel, BoxPanel, Button, Component, Frame, Graphics2D, Orientation}
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

//  def test(): Unit = {
//    import ExecutionContext.Implicits.global
//    val s = Settings(aMin = 3.78, aMax = 3.82, bMin = 3.78, bMax = 3.82,
//                     seq = Vector[Double](0,0,1,1.0,0.0,1) /* stringToSeq("AABAB") */,
//                     width = 4096, height = 4096, N = 4096,
//                     colrMin = -0.5, colrMax = 0.45, invert = true)
//    val imgFut   = Processor[BufferedImage]("lya")(calc(_, s))
//    val writeFut = imgFut.map { img =>
//      ImageIO.write(img, "png", userHome / "Documents" / "temp" / "test.png")
//    }
//    import processor.ProcessorOps
//    println("_" * 33)
//    imgFut.monitor(printResult = false)
//    writeFut.onComplete {
//      case Success(_) => println("Done.")
//      case Failure(ex) => ex.printStackTrace()
//    }
//    val t = new Thread {
//      override def run(): Unit = this.synchronized(this.wait())
//      start()
//    }
//    writeFut.onComplete {
//      case _ => t.synchronized(t.notify())
//    }
//  }

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

    val lyaCfg0 = LyaConfig(aMin = 3.78 * 100, aMax = 3.82 * 100, bMin = 3.78 * 100, bMax = 3.82 * 100,
      seq = "AABBAAB", width = iw, height = ih, N = 4096)
    val colrCfg0 = ColorConfig(min = -0.5, max = 0.45, invert = true)

    val lyaCfgView  = AutoView(lyaCfg0 , avCfg)
    val colrCfgView = AutoView(colrCfg0, avCfg)

    def updateAxes(cfg: LyaConfig): Unit = {
      hAxis1.minimum  = cfg.aMin
      hAxis1.maximum  = cfg.aMax
      vAxis1.minimum  = cfg.bMin
      vAxis1.maximum  = cfg.bMax
      hAxis2.minimum  = hAxis1.minimum
      hAxis2.maximum  = hAxis1.maximum
      vAxis2.minimum  = vAxis1.minimum
      vAxis2.maximum  = vAxis1.maximum
    }

    var proc = Option.empty[(LyaConfig, Processor[Result])]

    val progIcon = new ProgressIcon()
    val ggRender = new Button("Render")

    val comp: Component = new Component {
      preferredSize = (iw, ih)

      listenTo(mouse.moves)
      listenTo(mouse.clicks)

      private var crossHair   = false
      private var crossHairPt: Point = _
      private val ttPt        = new Point2D.Double

      def mouseToVirtual(p: Point, out: Point2D = new Point2D.Double): Point2D = {
        import numbers.Implicits._
        val x = p.x.linlin(0, iw, hAxis1.minimum, hAxis1.maximum)
        val y = p.y.linlin(0, ih, vAxis1.maximum, vAxis1.minimum)
        out.setLocation(x, y)
        out
      }

      def setMouse(m: MouseEvent): Unit = {
        crossHairPt = m.point
        mouseToVirtual(m.point, ttPt)
        tooltip = f"(${ttPt.getX}%1.3f,${ttPt.getY}%1.3f)"
      }

      cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

      private var isDragging = false
      private var isPressed  = false
      private var mPress: MouseEvent = _
      private var mDrag : MouseEvent = _

      reactions += {
        case m: MouseEntered =>
          crossHair = true
          setMouse(m)
          repaint()
        case m: MouseExited  =>
          crossHair = false
          tooltip = null
          repaint()
        case m: MouseMoved =>
          if (crossHair) {
            setMouse(m)
            repaint()
          }
        case m: MouseDragged =>
          setMouse(m)
          if (isPressed) {
            mDrag = m
            if (!isDragging) {
              isDragging = mDrag.point.distance(mPress.point) > 4
            }
          }
          repaint()
        case m: MousePressed =>
          mPress = m
          isPressed = true
        case m: MouseReleased =>
          if (isPressed) {
            if (isDragging) {
              val v1  = mouseToVirtual(mPress.point)
              val v2  = mouseToVirtual(mDrag .point)
              val c   = lyaCfgView.cell
              val c0  = c()
              val c1  = c0.copy(aMin = v1.getX, aMax = v2.getX, bMin = v1.getY, bMax = v2.getY)
              c()     = c1
              isDragging = false
              repaint()
            }
            isPressed = false
          }
      }

      override protected def paintComponent(g: Graphics2D): Unit = {
        super.paintComponent(g)
        g.drawImage(img, 0, 0, peer)
        if (crossHair) {
          g.setXORMode(Color.white)
          g.drawLine(crossHairPt.x, 0, crossHairPt.x, peer.getHeight)
          g.drawLine(0, crossHairPt.y, peer.getWidth, crossHairPt.y)
        }
        if (isDragging) {
          g.setXORMode(Color.white)
          val x = mPress.point.x
          val y = mPress.point.y
          g.drawRect(x, y, mDrag.point.x - x, mDrag.point.y - y)
        }
      }
    }

    def updateColors(): Unit = {
      proc.foreach { case (lyaCfg, p) =>
        p.value.foreach {
          case Success(data) =>
            updateColors1(lyaCfg, data)
          case _ =>
        }
      }
    }

    def updateColors1(lyaCfg: LyaConfig, data: Result): Unit = {
      val g       = img.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      val colrCfg = colrCfgView.cell()
      val img1    = mkImage(data, colrMin = colrCfg.min, colrMax = colrCfg.max, invert = colrCfg.invert)
      val scale   = AffineTransform.getScaleInstance(iw.toDouble / 320 /* lyaCfg.width */, ih.toDouble / 320 /* lyaCfg.height */)
      g.drawImage(img1, scale, null)
      // g.drawImage(img1, 0, 0, null)
      g.dispose()
      comp.repaint()
    }

    val procL: Model.Listener[Processor.Update[Result, Any]] = {
      case Processor.Result(p1, res) => onEDT {
        proc.foreach {
          case (lyaCfg, p2) if p1 == p2 =>
            ggRender.icon = null
            res match {
              case Success(data) =>
                updateColors1(lyaCfg, data)
                updateAxes(lyaCfg)
              case Failure(ex) =>
                ex.printStackTrace()
            }

          case _ =>
        }
      }

      case prog @ Processor.Progress(p1, _) => onEDT {
        if (proc.exists(_._2 == p1)) {
          progIcon.value = prog.toInt
          ggRender.repaint()
        }
      }
    }

    def startRendering(): Unit = {
      import ExecutionContext.Implicits.global
      stopRendering()
      val lyaCfg = lyaCfgView.cell()
      val p = Processor[Result]("calc") { self =>
        calc(self, lyaCfg.toLyaConfig1(fast = true))
      }
      proc = Some(lyaCfg -> p)
      p.addListener(procL)
      progIcon.value = (p.progress * 100).toInt
      ggRender.icon = progIcon
    }

    def stopRendering(): Unit = {
      proc.foreach { case (_, p) =>
        p.abort()
        p.removeListener(procL)
        ggRender.icon = null
        proc = None
      }
    }

    ggRender.listenTo(ggRender )
    ggRender.reactions += {
      case ButtonClicked(_) =>
        /* if (ggRender .selected) */ startRendering() // else stopRendering()
    }

    updateAxes(lyaCfg0)

    colrCfgView.cell.addListener {
      case _ =>
        updateColors()
    }

    val bp = new BorderPanel {
      add(comp, BorderPanel.Position.Center)
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
        add(new BoxPanel(Orientation.Vertical) {
          contents += lyaCfgView .component
          contents += colrCfgView.component
          contents += ggRender
        }, BorderPanel.Position.East)
      }
      resizable = false
      pack().centerOnScreen()
      open()
    }
    f.defaultCloseOperation = CloseOperation.Exit
  }

  def stringToSeq(s: String): Vector[Double] = s.map(c => if (c == 'A') 0.0 else 1.0)(breakOut)

  case class LyaConfig(aMin: Double, aMax: Double, bMin: Double, bMax: Double,
                       seq: String, width: Int, height: Int, N: Int) {
    def toLyaConfig1(fast: Boolean): LyaConfig1 =
      LyaConfig1(aMin = aMin * 0.01, aMax = aMax * 0.01, bMin = bMin * 0.01, bMax = bMax * 0.01,
        seq = stringToSeq(seq), width = if (fast) 320 else width, height = if (fast) 320 else height, N = N)
  }

  case class ColorConfig(min: Double, max: Double, invert: Boolean)

  case class LyaConfig1(aMin: Double, aMax: Double, bMin: Double, bMax: Double,
                        seq: Vector[Double], width: Int, height: Int, N: Int)

  case class Result(data: Array[Array[Double]], min: Double, max: Double)

  def mkImage(res: Result, colrMin: Double, colrMax: Double, invert: Boolean): BufferedImage = {
    import res.data
    val height = data.length
    val width  = data(0).length
    val img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    var yi = 0
    while (yi < height) {
      var xi = 0
      while (xi < width) {
        import numbers.Implicits._
        val lambda0 = data(yi)(xi)
        val lambda1 = lambda0.linlin(colrMin, colrMax, 0, 1).clip(0, 1)
        val lambda  = if (invert) 1 - lambda1 else lambda1
        val rgb = IntensityPalette.apply(lambda.toFloat)
        g.setColor(new Color(rgb))
        g.fillRect(xi, height - yi - 1, 1, 1)
        xi += 1
      }
      yi += 1
    }
    g.dispose()
    img
  }

  // https://en.wikipedia.org/wiki/Lyapunov_fractal
  def calc(self: Processor[Result] with Processor.Body, settings: LyaConfig1): Result = {
    import settings._
    // val img     = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    // val g       = img.createGraphics()
    val arr     = Array.ofDim[Double](width, height)
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
        val a = xi.linlin(0, width  /* - 1 */, aMin, aMax)
        val b = yi.linlin(0, height /* - 1 */, bMin, bMax)
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
//        val lambda1 = lambda0.linlin(colrMin, colrMax, 0, 1).clip(0, 1)
//        val lambda = if (invert) 1 - lambda1 else lambda1
//
//        val rgb = IntensityPalette.apply(lambda.toFloat)
//        g.setColor(new Color(rgb))
//        g.fillRect(xi, yi, 1, 1)
        arr(yi)(xi) = lambda0
        xi += 1

        self.progress = (yi * width + xi).toDouble / numPixels
        self.checkAborted()
      }
      yi += 1
    }

    // g.dispose()

    // println(s"min = $lMin, max = $lMax")

    Result(data = arr, min = lMin, max = lMax)
  }
}