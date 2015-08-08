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

import java.awt.{RenderingHints, Color, Cursor}
import java.awt.geom.{AffineTransform, Point2D}
import java.awt.image.BufferedImage
import java.io.{FileNotFoundException, FileOutputStream, FileInputStream}
import javax.imageio.ImageIO
import javax.swing.KeyStroke

import de.sciss.audiowidgets.Axis
import de.sciss.desktop.{OptionPane, FileDialog}
import de.sciss.file._
import de.sciss.guiflitz.{Cell, AutoView}
import de.sciss.model.Model
import de.sciss.numbers
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.swingplus.CloseOperation
import de.sciss.swingplus.Implicits._
import play.api.libs.json.{JsObject, JsArray, Json}

import scala.concurrent.blocking
import scala.swing.{Action, MenuItem, Menu, MenuBar, Graphics2D, Point, Component, FlowPanel, BoxPanel, BorderPanel, Frame, Button, Orientation}
import scala.swing.Swing._
import scala.swing.event.{ButtonClicked, MouseReleased, MousePressed, MouseDragged, MouseMoved, MouseExited, MouseEntered, MouseEvent}
import scala.util.{Failure, Success}

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

   */

  case class Source(id: Int)

  case class Trim(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0)

  case class Config(centerX: Int, centerY: Int, angleStart: Double, angleStop: Double,
                    width: Int, height: Int, virtualWidth: Int, noise: Int, thresh: Int, invert: Boolean)

  def mkFrame(): Unit = {
    val hAxis1  = new Axis(Orientation.Horizontal)
    val vAxis1  = new Axis(Orientation.Vertical  )
    val hAxis2  = new Axis(Orientation.Horizontal)
    val vAxis2  = new Axis(Orientation.Vertical  )

    val iw      = 640
    val ih      = 640
    val img1    = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB)

    var procSource  = Option.empty[Processor[BufferedImage]]
    var procTrim    = Option.empty[Processor[BufferedImage]]
    var proc        = Option.empty[(Config, Processor[Any])]

    val avCfg   = AutoView.Config()
    avCfg.small = true

    val cfg0 = Config(centerX = 1400, centerY = 1500, angleStart = 0.0, angleStop = 360.0,
                      width = 2160, height = 2160, virtualWidth = 2160 * 8, noise = 0, thresh = 0,
                      invert = true)

    val srcCfgView  = AutoView(Source(id = 11))
    val trimCfgView = AutoView(Trim(left = 300, top = 400, right = 500, bottom = 900))
    val cfgView     = AutoView(cfg0, avCfg)

    val progIcon = new ProgressIcon()

    val ggRender = new Button("Render")
    ggRender.preferredSize = {
      val d = ggRender.preferredSize
      d.width += 48
      d
    }
    ggRender.minimumSize  = ggRender.preferredSize
    ggRender.maximumSize  = ggRender.preferredSize

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

    lazy val comp1: Component = new Component {
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
        g.drawImage(img1, 0, 0, peer)
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

    def imgInOption(): Option[BufferedImage] = procSource.flatMap(_.value).flatMap(_.toOption)

    def runTrim(): Unit = {
      procTrim.foreach(_.abort())
      imgInOption().foreach { imgIn =>
        val proc = mkImageCrop(imgIn, trimCfgView.value)
        procTrim = Some(proc)
        proc.foreach { imgTrim =>
          onEDT {
            val g = img1.createGraphics()
            val sx = img1.getWidth .toDouble / imgTrim.getWidth
            val sy = img1.getHeight.toDouble / imgTrim.getHeight
            g.drawImage(imgTrim, AffineTransform.getScaleInstance(sx,sy), null)
            comp1.repaint()
            updateAxes1()
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

    def updateColors(): Unit = {
      proc.foreach { case (cfg, p) =>
        p.value.foreach {
          case Success(data) =>
            updateColors1(cfg, data)
          case _ =>
        }
      }
    }

    def updateColors1(cfg: Config, data: Any): Unit = {
      val g       = img1.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
//      val colrCfg = colrCfgView.value
//      val img1    = mkImage(data, cfg = colrCfg)
//      val scale   = AffineTransform.getScaleInstance(iw.toDouble / 320 /* lyaCfg.width */, ih.toDouble / 320 /* lyaCfg.height */)
//      g.drawImage(img1, scale, null)
//      // g.drawImage(img1, 0, 0, null)
//      g.dispose()
//      comp.repaint()
    }

    val procL: Model.Listener[Processor.Update[Any, Any]] = {
      case Processor.Result(p1, res) => onEDT {
        proc.foreach {
          case (lyaCfg, p2) if p1 == p2 =>
            ggRender.icon = null
            res match {
              case Success(data) =>
                updateColors1(lyaCfg, data)
                // updateAxes(lyaCfg)
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
      val cfg = cfgView.value
      val p = Processor[Any]("calc") { self =>
        // calc(self, cfg, fast = true)
      }
      proc = Some(cfg -> p)
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

    // updateAxes()

//    colrCfgView.cell.addListener {
//      case _ =>
//        updateColors()
//    }

    val bp = new BorderPanel {
      add(comp1, BorderPanel.Position.Center)
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
          contents += new FlowPanel(ggRender)
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
    res.start()
    res.onFailure { case ex => ex.printStackTrace() }
    res
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
    res.start()
    res.onFailure { case ex => ex.printStackTrace() }
    res
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
}