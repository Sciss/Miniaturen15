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

import java.awt.geom.{AffineTransform, Point2D}
import java.awt.image.BufferedImage
import java.awt.{Color, Cursor, RenderingHints}
import java.io.{FileInputStream, FileOutputStream}
import javax.imageio.ImageIO
import javax.swing.KeyStroke

import de.sciss.audiowidgets.Axis
import de.sciss.desktop.{FileDialog, OptionPane}
import de.sciss.dsp.FastLog
import de.sciss.file._
import de.sciss.guiflitz.AutoView
import de.sciss.intensitypalette.IntensityPalette
import de.sciss.model.Model
import de.sciss.numbers
import de.sciss.play.json.AutoFormat
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.swingplus.CloseOperation
import de.sciss.swingplus.Implicits._
import play.api.libs.json.{Format, JsArray, JsObject, Json}

import scala.collection.breakOut
import scala.concurrent.blocking
import scala.swing.Swing._
import scala.swing.event.{ButtonClicked, MouseDragged, MouseEntered, MouseEvent, MouseExited, MouseMoved, MousePressed, MouseReleased, ValueChanged}
import scala.swing.{Action, BorderPanel, BoxPanel, Button, Component, FlowPanel, Frame, Graphics2D, Menu, MenuBar, MenuItem, Orientation, Point, Slider}
import scala.util.{Failure, Success}

object Lyapunov {
  def main(args: Array[String]): Unit = runGUI(mkFrame())

  case class MovieConfig(duration: Double = 60.0, fps: Int = 25)

  def renderImage(lya: LyaConfig1, colr: ColorConfig, f: File): Processor[Unit] = {
    val res = new RenderImage(lya, colr, f)
    res.start()
    res
  }

  private final class RenderImage(lya: LyaConfig1, colr: ColorConfig, f: File)
    extends ProcessorImpl[Unit, Processor[Unit]] with Processor[Unit] {

    protected def body(): Unit = blocking {
      val fOut  = f.replaceExt("png")
      if (!fOut.exists()) {
        val res = calc(this, lya)
        val img = mkImage(res, colr)
        ImageIO.write(img, "png", fOut)
      }
    }
  }

  def renderImageSequence(sitA: Situation, sitB: Situation, numFrames: Int, f: File): Processor[Unit] = {
    val res = new RenderImageSequence(sitA = sitA, sitB = sitB, numFrames = numFrames, f = f)
    res.start()
    res
  }

  def mixLya(sitA: Situation, sitB: Situation, w2: Double, fast: Boolean = false): LyaConfig1 = {
    val w1      = 1 - w2
    val l1      = sitA.lya
    val l2      = sitB.lya
    val aMin    = l1.aMin   * w1 + l2.aMin   * w2
    val aMax    = l1.aMax   * w1 + l2.aMax   * w2
    val bMin    = l1.bMin   * w1 + l2.bMin   * w2
    val bMax    = l1.bMax   * w1 + l2.bMax   * w2
    val width   = l1.width  * w1 + l2.width  * w2
    val height  = l1.height * w1 + l2.height * w2
    val N       = l1.N      * w1 + l2.N      * w2

    val seqLen = lcm(sitA.lya.seq.length, sitB.lya.seq.length)
    val seqA   = {
      val xs = stringToSeq(sitA.lya.seq)
      Vector.tabulate(seqLen)(i => xs(i % xs.length))
    }
    val seqB    = {
      val xs = stringToSeq(sitB.lya.seq)
      Vector.tabulate(seqLen)(i => xs(i % xs.length))
    }
    val seqAB = seqA zip seqB

    val seq     = seqAB.map { case (x1, x2) =>
      x1 * w1 + x2 * w2
    }

    // beware that LyaConfig1 has different scaling
    val res0 = LyaConfig(aMin = aMin, aMax = aMax, bMin = bMin, bMax = bMax,
      seq = l1.seq, width = (width + 0.5).toInt, height = (height + 0.5).toInt, N = (N + 0.5).toInt)
    val res1 = res0.toLyaConfig1(fast = fast)
    val res2 = res1.copy(seq = seq)
    res2
  }

  def mixColor(sitA: Situation, sitB: Situation, w2: Double): ColorConfig = {
    val w1      = 1 - w2
    val c1      = sitA.color
    val c2      = sitB.color
    val min     = c1.min    * w1 + c2.min    * w2
    val max     = c1.max    * w1 + c2.max    * w2
    val noise   = c1.noise  * w1 + c2.noise  * w2
    val thresh  = c1.thresh * w1 + c2.thresh * w2
    val invert  = if (w2 < 0.5) c1.invert else c2.invert
    ColorConfig(min = min, max = max, invert = invert, noise = noise, thresh = thresh)
  }

  private final class RenderImageSequence(sitA: Situation, sitB: Situation, numFrames: Int, f: File)
    extends ProcessorImpl[Unit, RenderImageSequence] with Processor[Unit] {

    private def mixLya  (w2: Double): LyaConfig1  = Lyapunov.mixLya  (sitA, sitB, w2)
    private def mixColor(w2: Double): ColorConfig = Lyapunov.mixColor(sitA, sitB, w2)

    protected def body(): Unit = {
      val jsonF = f.replaceExt("json")
      if (!jsonF.exists()) blocking {
        val json    = Situation.format2.writes((sitA, sitB)).toString()
        val jsonOut = new FileOutputStream(jsonF)
        jsonOut.write(json.getBytes("UTF-8"))
        jsonOut.close()
      }

      val dir       = f.parent
      val name      = f.base
      val clumpSz   = Runtime.getRuntime.availableProcessors()
      val clump     = (1 to numFrames).grouped(clumpSz).toVector
      val numClumps = clump.size

      val fWeight   = 1.0 / numClumps

      clump.zipWithIndex.foreach { case (group, groupIdx) =>
        import numbers.Implicits._
        val pGroup: Vec[Processor[Any]] = group.map { frame =>
          val w       = frame.linlin(1, numFrames, 0, 1)
          val lya     = mixLya(w)
          val colr    = mixColor(w)
          val fFrame  = dir / s"$name-$frame.png"
          renderImage(lya, colr, fFrame)
        }
        // val futGroup = Future.sequence(pGroup)
        // XXX TODO --- we need Processor.sequence
        pGroup.zipWithIndex.foreach { case (p, i) =>
          await(p, progress, fWeight)
        }
        progress = (groupIdx + 1).toDouble / numClumps
        checkAborted()
      }
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

    val lyaCfg0 = LyaConfig(aMin = 0 * 1000, aMax = 4 * 1000, bMin = 0 * 1000, bMax = 4 * 1000,
      seq = "AABBAAB", width = 2160, height = 2160, N = 1000)
    val colrCfg0 = ColorConfig(min = -0.5, max = 0.45, invert = true, noise = 0.0, thresh = 0.0)

    val lyaCfgView  = AutoView(lyaCfg0 , avCfg)
    val colrCfgView = AutoView(colrCfg0, avCfg)
    val statsView   = AutoView(Stats(0.0, 0.0), avCfg)

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
    ggRender.preferredSize = {
      val d = ggRender.preferredSize
      d.width += 48
      d
    }
    ggRender.minimumSize  = ggRender.preferredSize
    ggRender.maximumSize  = ggRender.preferredSize

    val ggNormalize = Button("Normalize") {
      val stats = statsView.value
      val c     = colrCfgView.cell
      val c0    = c()
      val c1    = c0.copy(min = if (stats.min.isInfinity) stats.max - 10 else stats.min, max = stats.max)
      c()       = c1
    }

    def mkSituation(): Situation =
      Situation(lyaCfgView.value, colrCfgView.value)

    class SituationView(name: String) {
      var situation = mkSituation()

      val ggSave = Button(s"> $name") {
        situation = mkSituation()
      }
      ggSave.tooltip = s"Store Settings $name"

      val ggLoad = Button(s"< $name") {
        lyaCfgView .cell() = situation.lya
        colrCfgView.cell() = situation.color
      }
      ggLoad.tooltip = s"Recall Settings $name"

      val component = new FlowPanel(ggSave, ggLoad)
    }

    val sitA    = new SituationView("A")
    val sitB    = new SituationView("B")

    var procMix = Option.empty[Processor[Any]]

    lazy val ggMix: Slider = new Slider {
      min    = 0
      value  = 0
      max    = 159

      listenTo(this)
      reactions += {
        case ValueChanged(_) =>
          if (!adjusting) {
            procMix.foreach(_.abort())

            import numbers.Implicits._
            val w2    = value.linlin(min, max, 0, 1)
            val sitA1 = sitA.situation
            val sitB1 = sitB.situation
            val p = Processor[BufferedImage]("calc") { self =>
              val lya   = mixLya  (sitA1, sitB1, w2, fast = true)
              val colr  = mixColor(sitA1, sitB1, w2)
              val res   = calc(self, lya)
              val img   = mkImage(res, colr)
              img
            }
            p.foreach { in =>
              onEDT(updateImage(in))
            }
            procMix = Some(p)
          }
      }
    }

    lazy val comp: Component = new Component {
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

      private def setCell(v1: Point2D, v2: Point2D): Unit = {
        val c     = lyaCfgView.cell
        val c0    = c()
        val xMin  = math.min(v1.getX, v2.getX)
        val xMax  = math.max(v1.getX, v2.getX)
        val yMin  = math.min(v1.getY, v2.getY)
        val yMax  = math.max(v1.getY, v2.getY)
        val c1    = c0.copy(aMin = xMin, aMax = xMax, bMin = yMin, bMax = yMax)
        c()       = c1
      }

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
          if (m.peer.getButton == 3) {
            val hSpan = hAxis1.maximum - hAxis1.minimum
            val vSpan = vAxis1.maximum - vAxis1.minimum
            val vc = mouseToVirtual(mPress.point)
            import numbers.Implicits._
            val v1 = new Point2D.Double((vc.getX - hSpan).clip(0, 4000), (vc.getY - vSpan).clip(0, 4000))
            val v2 = new Point2D.Double((vc.getX + hSpan).clip(0, 4000), (vc.getY + vSpan).clip(0, 4000))
            setCell(v1, v2)
          }

        case m: MouseReleased =>
          if (isPressed) {
            if (isDragging) {
              val v1  = mouseToVirtual(mPress.point)
              val v2  = mouseToVirtual(mDrag .point)
              setCell(v1, v2)
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
      val colrCfg = colrCfgView.value
      val img1    = mkImage(data, cfg = colrCfg)
      updateImage(img1)
    }

    def updateImage(in: BufferedImage): Unit = {
      val g       = img.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      val scale   = AffineTransform.getScaleInstance(iw.toDouble / 320 /* lyaCfg.width */, ih.toDouble / 320 /* lyaCfg.height */)
      g.drawImage(in, scale, null)
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
                statsView.cell() = data.stats
                updateColors1(lyaCfg, data)
                updateAxes(lyaCfg)
              case Failure(Processor.Aborted()) =>
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
      stopRendering()
      val lyaCfg = lyaCfgView.value
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

    val mb = new MenuBar {
      contents += new Menu("File") {
        contents += new MenuItem(new Action("Load Settings...") {
          accelerator = Some(KeyStroke.getKeyStroke("ctrl O"))
          def apply(): Unit = {
            val dlg = FileDialog.open()
            dlg.setFilter(_.ext.toLowerCase == "json")
            dlg.show(None).foreach { f =>
              val fin = new FileInputStream(f)
              val arr = new Array[Byte](fin.available())
              fin.read(arr)
              fin.close()
              val jsn = Json.parse(new String(arr, "UTF-8"))
              jsn match {
                case _: JsArray => // video setting tuple
                  import Situation.format2
                  val (sitAv, sitBv) = Json.fromJson[(Situation, Situation)](jsn).get
                  sitA.situation = sitAv
                  sitB.situation = sitBv

                case _: JsObject => // individual setting
                  val res = Json.fromJson[Situation](jsn).get
                  lyaCfgView .cell() = res.lya
                  colrCfgView.cell() = res.color

                case _ =>
                  sys.error(s"Not an array or object: $jsn")
              }
            }
          }
        })

        contents += new MenuItem(new Action("Export Image...") {
          accelerator = Some(KeyStroke.getKeyStroke("ctrl S"))
          def apply(): Unit = {
            val sit = mkSituation()
            FileDialog.save(init = Some(userHome / s"lya_${sit.hashCode.toHexString}.png")).show(None).foreach { f =>
              val pFull = renderImage(sit.lya.toLyaConfig1(fast = false), sit.color, f.replaceExt("png"))
              val futTail = pFull.map { _ =>
                val json = Situation.format.writes(sit).toString()
                blocking {
                  val jsonOut = new FileOutputStream(f.replaceExt("json"))
                  jsonOut.write(json.getBytes("UTF-8"))
                  jsonOut.close()
                }
              }
              mkProgressDialog("Exporting...", pFull, futTail)
            }
          }
        })

        contents += new MenuItem(new Action("Export Image Sequence...") {
          accelerator = Some(KeyStroke.getKeyStroke("ctrl shift S"))

          def apply(): Unit = {
            val initName = (sitA.situation, sitB.situation).hashCode.toHexString
            FileDialog.save(init = Some(userHome / s"lya_$initName.png")).show(None).foreach { f =>
              val pMovie    = AutoView(MovieConfig())
              val optMovie  = OptionPane.confirmation(message = pMovie.component,
                optionType = OptionPane.Options.OkCancel)
              if (optMovie.show(title = "Image Sequence Settings") == OptionPane.Result.Ok) {
                val MovieConfig(duration, fps) = pMovie.value
                val numFrames = (duration * fps + 0.5).toInt
                val pFull = renderImageSequence(sitA.situation, sitB.situation, numFrames, f)
                mkProgressDialog("Exporting...", pFull, pFull)
              }
            }
          }
        })
      }
    }

    val fr = new Frame { self =>
      title = "Lyapunov"
      contents = new BorderPanel {
        add(bp, BorderPanel.Position.Center)
        add(new BoxPanel(Orientation.Vertical) {
          contents += lyaCfgView .component
          contents += colrCfgView.component
          contents += statsView  .component
          contents += new FlowPanel(ggRender, ggNormalize, sitA.component, sitB.component)
          contents += ggMix
        }, BorderPanel.Position.East)
      }
      resizable = false
      menuBar = mb
      pack().centerOnScreen()
      open()
    }
    fr.defaultCloseOperation = CloseOperation.Exit

    ggRender.doClick()
  }

  def stringToSeq(s: String): Vector[Double] = s.map(c => if (c == 'A') 0.0 else 1.0)(breakOut)

  object LyaConfig {
    implicit val format = AutoFormat[LyaConfig]
  }
  case class LyaConfig(aMin: Double, aMax: Double, bMin: Double, bMax: Double,
                       seq: String, width: Int, height: Int, N: Int) {
    def toLyaConfig1(fast: Boolean): LyaConfig1 =
      LyaConfig1(aMin = aMin * 0.001, aMax = aMax * 0.001, bMin = bMin * 0.001, bMax = bMax * 0.001,
        seq = stringToSeq(seq), width = if (fast) 320 else width, height = if (fast) 320 else height, N = N)
  }

  object ColorConfig {
    implicit val format = AutoFormat[ColorConfig]
  }
  case class ColorConfig(min: Double, max: Double, invert: Boolean, noise: Double, thresh: Double)

  object Situation {
    implicit val format : Format[Situation]               = AutoFormat[Situation]
    implicit val format2: Format[(Situation, Situation)]  = AutoFormat[(Situation, Situation)]
  }
  case class Situation(lya: LyaConfig, color: ColorConfig)

  case class LyaConfig1(aMin: Double, aMax: Double, bMin: Double, bMax: Double,
                        seq: Vector[Double], width: Int, height: Int, N: Int)

  case class Stats(min: Double, max: Double)

  case class Result(data: Array[Array[Double]], stats: Stats)

  def mkImage(res: Result, cfg: ColorConfig): BufferedImage = {
    import cfg._
    import res.data
    val height    = data.length
    val width     = data(0).length
    val img       = new BufferedImage(width, height, if (thresh != 0) BufferedImage.TYPE_BYTE_BINARY else BufferedImage.TYPE_INT_ARGB)
    val g         = img.createGraphics()
    val noiseAmt  = if (noise <= 0) 0.0 else noise * 0.01

    var threshN = 0.0

    import numbers.Implicits._

    if (thresh < 0) {
      val ds  = data.flatten.sorted
      val i   = math.max(0, math.min(ds.length - 1, (-thresh * 0.01 * ds.length + 0.5).toInt))
      threshN = ds(i).linlin(min, max, 0, 1)
    }

    var yi = 0
    while (yi < height) {
      var xi = 0
      while (xi < width) {
        val lambda0 = data(yi)(xi)
        val lambda  = if (thresh >= 0) {
          val lambda1 = lambda0.linlin(min, max, 0, 1).clip(0, 1)
          val lambda2 = if (invert) 1 - lambda1 else lambda1
          val lambda3 = if (noiseAmt != 0) (math.random * 2 - 1) * noiseAmt + lambda2 else lambda2
          if (thresh > 0) {
            if (lambda3 > thresh * 0.01) 1.0 else 0.0
          } else lambda3
        } else {
          val lambda1 = lambda0.linlin(min, max, 0, 1) // .clip(0, 1)
          val lambda2 = if (noiseAmt != 0) (math.random * 2 - 1) * noiseAmt + lambda1 else lambda1
          val lambda3 = if (lambda2 > threshN) 1.0 else 0.0
          if (invert) 1 - lambda3 else lambda3
        }
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

  private final val log = FastLog(base = math.E, q = 8)

  // https://en.wikipedia.org/wiki/Lyapunov_fractal
  def calc(self: Processor[Any] with Processor.Body, settings: LyaConfig1): Result = {
    import settings._
    // val img     = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    // val g       = img.createGraphics()
    val arr     = Array.ofDim[Double](width, height)
    val seqLen  = seq.length
    val numPixels = width * height
    var lMin    = Double.PositiveInfinity
    var lMax    = Double.NegativeInfinity
    val seqA    = seq.toArray
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

    Result(data = arr, stats = Stats(min = lMin, max = lMax))
  }
}