/*
 * Trunks.scala
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
import java.awt.{Color, Cursor}
import java.io.{FileInputStream, FileNotFoundException}
import javax.imageio.ImageIO
import javax.swing.KeyStroke

import com.jhlabs.image.{NoiseFilter, PolarFilter, ThresholdFilter}
import com.mortennobel.imagescaling.ResampleOp
import de.sciss.audiowidgets.Axis
import de.sciss.desktop.FileDialog
import de.sciss.file._
import de.sciss.guiflitz.AutoView
import de.sciss.numbers
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.swingplus.CloseOperation
import de.sciss.swingplus.Implicits._
import play.api.libs.json.Json

import scala.concurrent.blocking
import scala.swing.Swing._
import scala.swing.event.{ButtonClicked, MouseDragged, MouseEntered, MouseEvent, MouseExited, MouseMoved, MousePressed, MouseReleased}
import scala.swing.{Action, BorderPanel, BoxPanel, Component, FlowPanel, Frame, Graphics2D, Menu, MenuBar, MenuItem, Orientation, Point, ToggleButton}

object Trunks {
  def main(args: Array[String]): Unit = runGUI(mkFrame())

  /*
    Parameters:

    - image-source
    - crop
    - center
    - start-angle
    - stop -angle
    - virtual width
    - noise, thresh, invert

    for the polar transformation, we find the smallest
    radius (left, top, bottom, right), and scale the
    input up by target-height / smallest-radius.

   */

  case class Source(id: Int)

  case class Trim(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0)

  case class Config(centerX: Int, centerY: Int, angleStart: Double, angleSpan: Double,
                    width: Int, height: Int, virtualWidth: Int, noise: Int, thresh: Int, invert: Boolean)

  def mkFrame(): Unit = {
    val hAxis1  = new Axis(Orientation.Horizontal)
    val vAxis1  = new Axis(Orientation.Vertical  )
    val hAxis2  = new Axis(Orientation.Horizontal)
    val vAxis2  = new Axis(Orientation.Vertical  )

    val iw      = 640
    val ih      = 640
    val img1    = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB)
    val img2    = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB)

    var polarValid = false

    var procSource  = Option.empty[Processor[BufferedImage]]
    var procTrim    = Option.empty[Processor[BufferedImage]]
    var procPolar   = Option.empty[Processor[BufferedImage]]

    val avCfg   = AutoView.Config()
    avCfg.small = true

    val cfg0 = Config(centerX = 1400, centerY = 1500, angleStart = 0.0, angleSpan = 360.0,
                      width = 2160, height = 2160, virtualWidth = 2160 * 8, noise = 0, thresh = 0,
                      invert = true)

    val srcCfgView  = AutoView(Source(id = 11))
    val trimCfgView = AutoView(Trim(left = 300, top = 400, right = 500, bottom = 900))
    val cfgView     = AutoView(cfg0, avCfg)

    def updateAxes1(): Unit = {
      imgInOption().foreach { imgIn =>
        val trim = trimCfgView.value
        hAxis1.minimum  = trim.left
        hAxis1.maximum  = imgIn.getWidth - (trim.left + trim.right)
        vAxis1.minimum  = trim.top
        vAxis1.maximum  = imgIn.getHeight - (trim.top + trim.bottom)
        hAxis2.minimum  = hAxis1.minimum
        hAxis2.maximum  = hAxis1.maximum
        vAxis2.minimum  = vAxis1.minimum
        vAxis2.maximum  = vAxis1.maximum
      }
    }

    lazy val ggPolar: ToggleButton = new ToggleButton("Output") {
      listenTo(this)
      reactions += {
        case ButtonClicked(_) =>
          comp.repaint()
          if (selected && !polarValid) runPolar()
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
//        val c     = lyaCfgView.cell
//        val c0    = c()
//        val xMin  = math.min(v1.getX, v2.getX)
//        val xMax  = math.max(v1.getX, v2.getX)
//        val yMin  = math.min(v1.getY, v2.getY)
//        val yMax  = math.max(v1.getY, v2.getY)
//        val c1    = c0.copy(aMin = xMin, aMax = xMax, bMin = yMin, bMax = yMax)
//        c()       = c1
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
        val img = if (ggPolar.selected) img2 else img1
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

    def imgInOption  (): Option[BufferedImage] = procSource.flatMap(_.value).flatMap(_.toOption)
    def imgTrimOption(): Option[BufferedImage] = procTrim  .flatMap(_.value).flatMap(_.toOption)

    def runPolar(): Unit = {
      procPolar.foreach(_.abort())
      imgTrimOption().foreach { imgTrim =>
        val proc = mkImagePolar(imgTrim, trimCfgView.value, cfgView.value, fast = true)
        procPolar = Some(proc)
        proc.foreach { imgPolar =>
          onEDT {
            val g   = img2.createGraphics()
            val sx  = img2.getWidth .toDouble / imgPolar.getWidth
            val sy  = img2.getHeight.toDouble / imgPolar.getHeight
            g.drawImage(imgPolar, AffineTransform.getScaleInstance(sx ,sy), null)
            comp.repaint()
          }
        }
        polarValid = true
      }
    }

    def runTrim(): Unit = {
      procTrim.foreach(_.abort())
      imgInOption().foreach { imgIn =>
        val proc = mkImageCrop(imgIn, trimCfgView.value)
        procTrim = Some(proc)
        proc.foreach { imgTrim =>
          onEDT {
            val g   = img1.createGraphics()
            val sx  = img1.getWidth .toDouble / imgTrim.getWidth
            val sy  = img1.getHeight.toDouble / imgTrim.getHeight
            g.drawImage(imgTrim, AffineTransform.getScaleInstance(sx ,sy), null)
            comp.repaint()
            updateAxes1()
            if (ggPolar.selected) runPolar() else polarValid = false
          }
        }
      }
    }

    def runSource(): Unit = {
      procSource.foreach(_.abort())
      val proc    = mkImageIn(srcCfgView.value)
      procSource  = Some(proc)
      proc.foreach { imgIn =>
        onEDT(runTrim())
      }
    }

    srcCfgView.cell.addListener {
      case _ => runSource()
    }

    trimCfgView.cell.addListener {
      case _ => runTrim()
    }

    cfgView.cell.addListener {
      case _ =>
        if (ggPolar.selected) runPolar() else polarValid = false
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
//              jsn match {
//                case _: JsArray => // video setting tuple
//                  import Situation.format2
//                  val (sitAv, sitBv) = Json.fromJson[(Situation, Situation)](jsn).get
//                  sitA.situation = sitAv
//                  sitB.situation = sitBv
//
//                case _: JsObject => // individual setting
//                  val res = Json.fromJson[Situation](jsn).get
//                  lyaCfgView .cell() = res.lya
//                  colrCfgView.cell() = res.color
//
//                case _ =>
//                  sys.error(s"Not an array or object: $jsn")
//              }
            }
          }
        })

        contents += new MenuItem(new Action("Export Image...") {
          accelerator = Some(KeyStroke.getKeyStroke("ctrl S"))
          def apply(): Unit = {
//            FileDialog.save(init = Some(userHome / s"trunk_${sit.hashCode.toHexString}.png")).show(None).foreach { f =>
//              val pFull = renderImage(sit.lya.toLyaConfig1(fast = false), sit.color, f.replaceExt("png"))
//              val futTail = pFull.map { _ =>
//                val json = Situation.format.writes(sit).toString()
//                blocking {
//                  val jsonOut = new FileOutputStream(f.replaceExt("json"))
//                  jsonOut.write(json.getBytes("UTF-8"))
//                  jsonOut.close()
//                }
//              }
//              mkProgressDialog("Exporting...", pFull, futTail)
//            }
          }
        })

        contents += new MenuItem(new Action("Export Image Sequence...") {
          accelerator = Some(KeyStroke.getKeyStroke("ctrl shift S"))

          def apply(): Unit = {
//            val initName = (sitA.situation, sitB.situation).hashCode.toHexString
//            FileDialog.save(init = Some(userHome / s"lya_$initName.png")).show(None).foreach { f =>
//              val pMovie    = AutoView(MovieConfig())
//              val optMovie  = OptionPane.confirmation(message = pMovie.component,
//                optionType = OptionPane.Options.OkCancel)
//              if (optMovie.show(title = "Image Sequence Settings") == OptionPane.Result.Ok) {
//                val MovieConfig(duration, fps) = pMovie.value
//                val numFrames = (duration * fps + 0.5).toInt
//                val pFull = renderImageSequence(sitA.situation, sitB.situation, numFrames, f)
//                mkProgressDialog("Exporting...", pFull, pFull)
//              }
//            }
          }
        })
      }
    }

    val fr = new Frame { self =>
      title = "Trunks"
      contents = new BorderPanel {
        add(bp, BorderPanel.Position.Center)
        add(new BoxPanel(Orientation.Vertical) {
          contents += srcCfgView .component
          contents += trimCfgView.component
          contents += cfgView    .component
          contents += new FlowPanel(/* ggRender, */ ggPolar)
        }, BorderPanel.Position.East)
      }
      resizable = false
      menuBar = mb
      pack().centerOnScreen()
      open()
    }
    fr.defaultCloseOperation = CloseOperation.Exit

    runSource()
  }

  def mkImageIn(source: Source): Processor[BufferedImage] = {
    val res = new MkImageIn(source)
    startAndReportProcessor(res)
  }

  private class MkImageIn(source: Source)
    extends ProcessorImpl[BufferedImage, Processor[BufferedImage]] with Processor[BufferedImage] {

    def body(): BufferedImage = blocking {
      val fIn     = file("trunks_vid") / "image_in" / s"trunk${source.id}.png"
      if (!fIn.isFile) throw new FileNotFoundException(fIn.path)
      val imgIn   = ImageIO.read(fIn)
//      val imgOut  = cropImage(imgIn, trimLeft, trimTop,
//        imgIn.getWidth - (trimLeft + trimRight), imgIn.getHeight - (trimTop + trimBottom))
      progress = 1.0
      checkAborted()
      imgIn
    }
  }

  def mkImageCrop(source: BufferedImage, trim: Trim): Processor[BufferedImage] = {
    val res = new MkImageCrop(source, trim)
    startAndReportProcessor(res)
  }

  private class MkImageCrop(source: BufferedImage, trim: Trim)
    extends ProcessorImpl[BufferedImage, Processor[BufferedImage]] with Processor[BufferedImage] {

    def body(): BufferedImage = blocking {
      val imgOut  = cropImage(source, trim.left, trim.top,
        source.getWidth - (trim.left + trim.right), source.getHeight - (trim.top + trim.bottom))
      progress = 1.0
      checkAborted()
      imgOut
    }
  }

  /** @param source the already trimmed image */
  def mkImagePolar(source: BufferedImage, trim: Trim, config: Config, fast: Boolean): Processor[BufferedImage] = {
    val res = new MkImagePolar(source, trim, config, fast = fast)
    startAndReportProcessor(res)
  }

  private class MkImagePolar(source: BufferedImage, trim: Trim, config: Config, fast: Boolean)
    extends ProcessorImpl[BufferedImage, Processor[BufferedImage]] with Processor[BufferedImage] {

    def body(): BufferedImage = blocking {
      val cxi         = config.centerX - trim.left
      val cyi         = config.centerY - trim.top
      val iw          = source.getWidth
      val ih          = source.getHeight
      val cx          = cxi.toDouble / iw
      val cy          = cyi.toDouble / ih
      val rxMin       = math.min(cx, 1.0 - cx) * iw
      val ryMin       = math.min(cy, 1.0 - cy) * ih
      val rMin        = math.min(rxMin, ryMin)
      val scale       = config.height / rMin
      val scaleW      = (iw * scale + 0.5).toInt
      val scaleH      = (ih * scale + 0.5).toInt
      val resampleOp  = new ResampleOp(scaleW, scaleH)
      val imgScale    = resampleOp.filter(source, null)
      progress = 0.25
      checkAborted()
      val angleStart  = config.angleStart
      val angleSpan   = config.angleSpan * config.width / config.virtualWidth
      val polarOp     = new MyPolar(angleStart = angleStart, angleSpan = angleSpan, cx = cx, cy = cy)
      val imgPolar    = polarOp.filter(imgScale, null)
      progress = 0.50
      checkAborted()
      val noiseOp     = new NoiseFilter
      noiseOp.setAmount(config.noise)
      noiseOp.setMonochrome(true)
      val imgNoise    = noiseOp.filter(imgPolar, null)
      progress = 0.75
      checkAborted()
      val threshOp    = new ThresholdFilter(config.thresh)
      val imgOut      = threshOp.filter(imgNoise, null)
      progress = 1.00
      checkAborted()
      imgOut
    }
  }

  class MyPolar(angleStart: Double, angleSpan: Double, cx: Double, cy: Double) extends PolarFilter {
    private var inWidth   = 0
    private var inHeight  = 0

    override def filter(src: BufferedImage, dst: BufferedImage): BufferedImage = {
      inWidth     = src.getWidth
      inHeight    = src.getHeight
      super.filter(src, dst)
    }

    override def transformInverse(x: Int, y: Int, out: Array[Float]): Unit = {
      // import config._
      import numbers.Implicits._

      val Pi2 = math.Pi * 2

      val theta = ((x.linlin(0, inWidth, angleStart, angleStart + angleSpan) * math.Pi / 180) + Pi2) % Pi2
      val cos   = math.cos(theta)
      val sin   = math.sin(theta)
      val rx    = if (cos >= 0) 1.0 - cx else cx
      val ry    = if (sin  < 0) 1.0 - cy else cy

      val px    = (cx + cos * rx) * inWidth
      val py    = (cy - sin * ry) * inHeight

      out(0)    = px.toFloat
      out(1)    = py.toFloat
    }
  }
}