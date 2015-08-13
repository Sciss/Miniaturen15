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
import java.io.{FileOutputStream, FileInputStream, FileNotFoundException}
import javax.imageio.ImageIO
import javax.swing.KeyStroke

import com.jhlabs.image.{InvertFilter, TransformFilter, NoiseFilter, PolarFilter, ThresholdFilter}
import com.mortennobel.imagescaling.ResampleOp
import de.sciss.audiowidgets.Axis
import de.sciss.desktop.{OptionPane, FileDialog}
import de.sciss.file._
import de.sciss.guiflitz.AutoView
import de.sciss.numbers
import de.sciss.play.json.AutoFormat
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.swingplus.CloseOperation
import de.sciss.swingplus.Implicits._
import play.api.libs.json.{Format, Json}

import scala.concurrent.blocking
import scala.swing.Swing._
import scala.swing.event.{ButtonClicked, MouseDragged, MouseEntered, MouseEvent, MouseExited, MouseMoved, MousePressed, MouseReleased}
import scala.swing.{Button, Action, BorderPanel, BoxPanel, Component, FlowPanel, Frame, Graphics2D, Menu, MenuBar, MenuItem, Orientation, Point, ToggleButton}

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

  case class MovieConfig(duration: Double = 60.0, fps: Int = 25)

  object Situation {
    implicit val format : Format[Situation]               = AutoFormat[Situation]
    implicit val format2: Format[(Situation, Situation)]  = AutoFormat[(Situation, Situation)]
  }
  case class Situation(source: Source, trim: Trim, config: Config)

  object Source {
    implicit val format: Format[Source] = AutoFormat[Source]
  }
  case class Source(id: Int)

  object Trim {
    implicit val format: Format[Trim] = AutoFormat[Trim]
  }
  case class Trim(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0)

  object Config {
    implicit val format: Format[Config] = AutoFormat[Config]
  }
  case class Config(centerX: Double, centerY: Double, innerRadius: Double, angleStart: Double, angleSpan: Double,
                    flipX: Boolean, flipY: Boolean,
                    width: Int, height: Int, noise: Int, thresh: Int, invert: Boolean)

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

    val cfg0 = Config(centerX = 2120, centerY = 2680, innerRadius = 0.0,
      angleStart = 0.0, angleSpan = 360.0,
      flipX = true, flipY = false,
      width = 2160, height = 2160 /* , virtualWidth = 2160 */ /* * 8 */, noise = 20, thresh = 210,
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

    def mkSituation(): Situation =
      Situation(srcCfgView.value, trimCfgView.value, cfgView.value)

    class SituationView(name: String) {
      var situation = mkSituation()

      val ggSave = Button(s"> $name") {
        situation = mkSituation()
      }
      ggSave.tooltip = s"Store Settings $name"

      val ggLoad = Button(s"< $name") {
        srcCfgView  .cell() = situation.source
        trimCfgView .cell() = situation.trim
        cfgView     .cell() = situation.config
      }
      ggLoad.tooltip = s"Recall Settings $name"

      val component = new FlowPanel(ggSave, ggLoad)
    }

    lazy val sitA    = new SituationView("A")
    lazy val sitB    = new SituationView("B")

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
            val v1    = mouseToVirtual(mPress.point)
            val c     = cfgView.cell
            val c0    = c()
            val c1    = c0.copy(centerX = v1.getX, centerY = v1.getY)
            c()       = c1
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
          println("Done.")
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
            val sit = mkSituation()
            FileDialog.save(init = Some(userHome / s"trunk_${sit.hashCode.toHexString}.png")).show(None).foreach { f =>
              val pFull = renderImage(sit, f.replaceExt("png"))
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
            FileDialog.save(init = Some(userHome / s"trunk_$initName.png")).show(None).foreach { f =>
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
      title = "Trunks"
      contents = new BorderPanel {
        add(bp, BorderPanel.Position.Center)
        add(new BoxPanel(Orientation.Vertical) {
          contents += srcCfgView .component
          contents += trimCfgView.component
          contents += cfgView    .component
          contents += new FlowPanel(/* ggRender, */ ggPolar, sitA.component, sitB.component)
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

  def renderImage(situation: Situation, f: File): Processor[Unit] = {
    val res = new MkImage(situation, f)
    res.start()
    res
  }

  private class MkImage(situation: Situation, f: File)
    extends ProcessorImpl[Unit, Processor[Unit]] with Processor[Unit] {

    def body(): Unit = {
      val step1   = mkImageIn(situation.source)
      val imgIn   = await(step1, offset = 0.0, weight = 0.2)
      val step2   = mkImageCrop(imgIn, situation.trim)
      val imgTrim = await(step2, offset = 0.2, weight = 0.3)
      val step3   = mkImagePolar(imgTrim, situation.trim, situation.config, fast = false)
      val imgOut  = await(step3, offset = 0.3, weight = 0.9)
      ImageIO.write(imgOut, "png", f)
      progress = 1.0
    }
  }

  def renderImageSequence(sitA: Situation, sitB: Situation, numFrames: Int, f: File): Processor[Unit] = {
    val res = new RenderImageSequence(sitA = sitA, sitB = sitB, numFrames = numFrames, f = f)
    res.start()
    res
  }

  private final class RenderImageSequence(sitA: Situation, sitB: Situation, numFrames: Int, f: File)
    extends ProcessorImpl[Unit, RenderImageSequence] with Processor[Unit] {

    private def mkMix(w2: Double): Situation = {
      val w1      = 1 - w2

      val srcMix  = if (w2 < 0.5) sitA.source else sitB.source

      val trimA   = sitA.trim
      val trimB   = sitB.trim
      val left    = trimA.left    * w1 + trimB.left   * w2
      val top     = trimA.top     * w1 + trimB.top    * w2
      val right   = trimA.right   * w1 + trimB.right  * w2
      val bottom  = trimA.bottom  * w1 + trimB.bottom * w2
      val trimMix = Trim(left = (left + 0.5).toInt, top = (top + 0.5).toInt,
        right = (right + 0.5).toInt, bottom = (bottom + 0.5).toInt)

      val c1          = sitA.config
      val c2          = sitB.config
      val angleStart  = c1.angleStart  * w1 + c2.angleStart  * w2
      val angleSpan   = c1.angleSpan   * w1 + c2.angleSpan   * w2
      val centerX     = c1.centerX     * w1 + c2.centerX     * w2
      val centerY     = c1.centerY     * w1 + c2.centerY     * w2
      val innerRadius = c1.innerRadius * w1 + c2.innerRadius * w2
      val width       = c1.width       * w1 + c2.width       * w2
      val height      = c1.height      * w1 + c2.height      * w2
      val noise       = c1.noise       * w1 + c2.noise       * w2
      val thresh      = c1.thresh      * w1 + c2.thresh      * w2
      val invert      = if (w2 < 0.5) c1.invert else c2.invert
      val flipX       = if (w2 < 0.5) c1.flipX  else c2.flipX
      val flipY       = if (w2 < 0.5) c1.flipY  else c2.flipY
      val cfgMix      = Config(centerX = centerX, centerY = centerY,
        innerRadius = innerRadius, angleStart = angleStart, angleSpan = angleSpan,
        flipX = flipX, flipY = flipY, width = (width + 0.5).toInt, height = (height + 0.5).toInt,
        noise = (noise + 0.5).toInt, thresh = (thresh + 0.5).toInt, invert = invert)

      Situation(source = srcMix, trim = trimMix, config = cfgMix)
    }

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
          val sitMix  = mkMix(w)
          val fFrame  = dir / s"$name-$frame.png"
          renderImage(sitMix, fFrame)
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

  def mkImageIn(source: Source): Processor[BufferedImage] = {
    val res = new MkImageIn(source)
    startAndReportProcessor(res)
  }

  private class MkImageIn(source: Source)
    extends ProcessorImpl[BufferedImage, Processor[BufferedImage]] with Processor[BufferedImage] {

    def body(): BufferedImage = blocking {
      val fIn     = file("trunks_vid") / "image_in" / s"trunk${source.id}t.png"
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
      val imgScale    = if (fast) source else {
        val rxMin       = math.min(cx, 1.0 - cx) * iw
        val ryMin       = math.min(cy, 1.0 - cy) * ih
        val rMin        = math.min(rxMin, ryMin)
        val scale       = config.height / rMin
        val scaleW      = (iw * scale + 0.5).toInt
        val scaleH      = (ih * scale + 0.5).toInt
        val resampleOp  = new ResampleOp(scaleW, scaleH)
        resampleOp.filter(source, null)
      }
      progress = 0.25
      checkAborted()
      import config.{angleStart, angleSpan, innerRadius}
      // println(f"cx = $cx%1.2f, cy = $cy%1.2f, angleStart = $angleStart%1.1f, angleSpan = $angleSpan%1.1f")
      val polarOp     = new MyPolar(innerRadius = innerRadius,
          angleStart = angleStart, angleSpan = angleSpan, cx = cx, cy = cy,
        flipX = config.flipX, flipY = config.flipY)
      // val polarOp     = new PolarFilter
      polarOp.setType(PolarFilter.POLAR_TO_RECT)
      polarOp.setEdgeAction(TransformFilter.CLAMP)
      val imgPolar    = polarOp.filter(imgScale, null)
      progress = 0.75
      checkAborted()
      val imgPolarS = if (fast) imgPolar else {
        val resampleOp = new ResampleOp(config.width, config.height)
        resampleOp.filter(imgPolar, null)
      }
      progress = 0.85
      checkAborted()
      val imgNoise = if (config.noise <= 0) imgPolarS else {
        val noiseOp = new NoiseFilter
        noiseOp.setAmount(config.noise)
        noiseOp.setMonochrome(true)
        noiseOp.filter(imgPolarS, null)
      }
      progress = 0.90
      checkAborted()
      val imgThresh = if (config.thresh <= 0) imgNoise else {
        val threshOp = new ThresholdFilter(config.thresh)
        val res = new BufferedImage(imgNoise.getWidth, imgNoise.getHeight, BufferedImage.TYPE_BYTE_INDEXED)
        threshOp.filter(imgNoise, res)
      }
      progress = 0.95
      checkAborted()
      val imgOut = if (!config.invert) imgThresh else {
        val invertOp = new InvertFilter
        invertOp.filter(imgThresh, null)
      }
      progress = 1.0
      imgOut
    }
  }

  class MyPolar(innerRadius: Double, angleStart: Double, angleSpan: Double,
                cx: Double, cy: Double, flipX: Boolean, flipY: Boolean)
    extends PolarFilter {

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

//      val ang1  = if (flipX) angleStart + angleSpan else angleStart
//      val ang2  = if (flipX) angleStart else angleStart + angleSpan
//      val t     = (if (flipX) inWidth - x - 1 else x).toDouble / inWidth
//      val theta = ((t.linlin(0, 1, ang1, ang2) * math.Pi / 180) + Pi2) % Pi2
      val t     = x.toDouble / inWidth
      val theta0 = ((t.linlin(0, 1, angleStart, angleStart + angleSpan) * math.Pi / 180) + Pi2) % Pi2
      val theta = if (flipX) -theta0 else theta0
      val cos   = math.cos(theta)
      val sin   = math.sin(theta)
      val rx    = if (cos >= 0) 1.0 - cx else cx
      val ry    = if (sin >= 0) 1.0 - cy else cy
      val r0    = (if (flipY) inHeight - y - 1 else y).toDouble / inHeight
      val r     = innerRadius + (1.0 - innerRadius) * r0

      val px    = (cx + cos * rx * r) * inWidth
      val py    = (cy - sin * ry * r) * inHeight

      out(0)    = px.toFloat
      out(1)    = py.toFloat
    }
  }
}