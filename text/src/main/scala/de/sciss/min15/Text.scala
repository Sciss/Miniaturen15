/*
 * Text.scala
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

import java.io.{FileInputStream, FileOutputStream}
import javax.swing.event.{DocumentEvent, DocumentListener}
import javax.swing.{KeyStroke, Timer}

import de.sciss.desktop.FileDialog
import de.sciss.file._
import de.sciss.guiflitz.AutoView
import de.sciss.min15.text.VideoSettings
import de.sciss.play.json.AutoFormat
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import play.api.libs.json.{Format, Json}
import prefuse.util.ui.JForcePanel

import scala.collection.breakOut
import scala.concurrent.blocking
import scala.swing.Swing._
import scala.swing.event.ButtonClicked
import scala.swing.{Action, BorderPanel, BoxPanel, Button, Component, FlowPanel, Frame, Menu, MenuBar, MenuItem, Orientation, ProgressBar, TextArea, ToggleButton}
import scala.util.{Failure, Success}

object Text {
  def main(args: Array[String]): Unit = runGUI(mkFrame())

  object Config {
    implicit val format: Format[Config] = AutoFormat[Config]
  }
  case class Config(size: Int, lineWidth: Int,
                    speedLimit: Double, noise: Int, threshold: Int, invert: Boolean) {
    def toVideoSettings: VideoSettings = {
      val b             = VideoSettings()
      b.width           = size
      b.height          = size
      b.speedLimit      = speedLimit
      b.baseFile        = ???
      b.anim            = ???
      b.framesPerSecond = ???
      b.numFrames       = ???
      b.text            = ???
      b
    }
  }

  case class Situation(config: Config, forceParameters: Map[String, Map[String, Float]], text: String)

  def mkFrame(): Unit = {
    val v = text.Visual()
    // v.display.setDoubleBuffered(true)

    def mkSituation(): Config = ???

    val avCfg   = AutoView.Config()
    avCfg.small = true
    val cfg0    = Config(size = 2160, lineWidth = 320, speedLimit = 0.1, noise = 0, threshold = 0, invert = false)
    val cfgView = AutoView(cfg0, avCfg)

    val ggText = new TextArea(12, 40)
    val clpseText = new Timer(1000, ActionListener { _ =>
      val newText = ggText.text
      if (v.text != newText) v.text = newText
    })
    clpseText.setRepeats(false)

    ggText.peer.getDocument.addDocumentListener(new DocumentListener {
      def insertUpdate (e: DocumentEvent): Unit = clpseText.restart()
      def changedUpdate(e: DocumentEvent): Unit = clpseText.restart()
      def removeUpdate (e: DocumentEvent): Unit = clpseText.restart()
    })

    val ggAutoZoom = new ToggleButton("Zoom") {
      selected = true
      listenTo(this)
      reactions += {
        case ButtonClicked(_) =>
          v.autoZoom = selected
      }
    }

    val ggRunAnim = new ToggleButton("Run Animation") {
      listenTo(this)
      reactions += {
        case ButtonClicked(_) =>
          v.runAnimation = selected
      }
    }

    val ggStepAnim = Button("Step Anim") {
      v.animationStep()
    }

    val ggParamSnap = Button("Parameter Snapshot") {
      val vec: Vec[(String, String)] = v.forceParameters.map { case (name, values) =>
        val pVec: Vec[(String, String)] = values.map { case (pName, pVal) =>
          (pName, s"${pVal}f")
        } (breakOut)
        val s = pVec.sorted.map { case (pName, pVal) => s""""$pName" -> $pVal""" } .mkString("Map(", ", ", ")")
        (name, s)
      } (breakOut)

      println(s"Layout count: ${v.layoutCounter}\n")
      val tx = vec.sorted.map { case (name, values) => s""""$name" -> $values""" } .mkString("Map(\n  ", ",\n  ", "\n)")
      println(tx)
    }

    var seriesProc = Option.empty[Processor[Unit]]

    val ggProgress = new ProgressBar

    val ggSaveFrameSeries = Button("Save Movie...") {
      seriesProc.fold[Unit] {
        val dir       = file("render")
        require(dir.isDirectory)
        val cfg       = VideoSettings()
        cfg.baseFile  = dir / "frame"
        cfg.anim      = ??? // textObj.anim
        cfg.text      = ??? // textObj.text
        cfg.numFrames = cfg.anim.last.frame + cfg.framesPerSecond * (??? : Int) /* textObj.tail */ // 120
        cfg.baseFile

        val p         = v.saveFrameSeriesAsPNG(cfg)
        seriesProc    = Some(p)
        p.addListener {
          case prog @ Processor.Progress(_, _) => onEDT(ggProgress.value = prog.toInt)
          case Processor.Result(_, Success(_)) => println("Done.")
          case Processor.Result(_, Failure(ex)) =>
            println("Move rendering failed.")
            ex.printStackTrace()
        }

      } { p =>
        p.abort()
        seriesProc = None
      }
    }

    val pBottom = new BoxPanel(Orientation.Vertical) {
      contents += new FlowPanel(ggAutoZoom, ggRunAnim, ggStepAnim)
      contents += new FlowPanel(ggSaveFrameSeries, ggParamSnap, ggProgress)
    }
    val fSim    = v.forceSimulator
    val fPanel  = new JForcePanel(fSim)
    fPanel.setBackground(null)
//    val scroll = new ScrollPane(v.component)

    val pRight = new BoxPanel(Orientation.Vertical) {
      contents += Component.wrap(fPanel)
      contents += cfgView.component
      contents += ggText
    }
    ggText.preferredSize = {
      val d = ggText.preferredSize
      d.width = math.min(320, d.width)  // WTF
      d
    }
    ggText.minimumSize  = ggText.preferredSize
    ggText.maximumSize  = ggText.preferredSize

    cfgView.cell.addListener { case _ =>
      val cfg = cfgView.value
      v.lineWidth = cfg.lineWidth
      v.noise     = cfg.noise
      v.threshold = cfg.threshold
      // cfg.invert
    }

    v.component.preferredSize = (640, 640)

    val split = new BorderPanel {
      add(v.component, BorderPanel.Position.Center)
      add(pRight, BorderPanel.Position.East  )
    }
//    split.oneTouchExpandable  = true
//    split.continuousLayout    = false
//    split.dividerLocation     = 800
//    split.resizeWeight        = 1.0

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
            }
          }
        })

        contents += new MenuItem(new Action("Export Image...") {
          accelerator = Some(KeyStroke.getKeyStroke("ctrl S"))
          def apply(): Unit = {
            val sit = mkSituation()
            FileDialog.save(init = Some(userHome / s"collat_${sit.hashCode.toHexString}.png")).show(None).foreach { f =>
              val pFull = renderImage(v, sit, f = f.replaceExt("png"))
              val futTail = pFull.map { _ =>
                val json = Config.format.writes(sit).toString()
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
      }
    }

    new Frame {
      title     = "Text"
      contents  = new BorderPanel {
        add(split   , BorderPanel.Position.Center)
        add(pBottom , BorderPanel.Position.South)
      }
      menuBar = mb
      resizable = false
      pack().centerOnScreen()
      // size      = (640, 480)

      // v.display.panTo((-136 + 20, -470 + 20))   // XXX WAT -- where the heck do these values come from?
      //      v.display.panTo((-100, 100))
      //      v.display.zoomAbs((0, 0), 1.3333)

      open()

      override def closeOperation(): Unit = {
        try {
          // v.algorithm.system.close()
        } finally {
          sys.exit(0)
        }
      }
    }
  }

  def renderImage(v: text.Visual, config: Config, f: File): Processor[Unit] = {
    val res = new RenderImage(v = v, config, f = f)
    res.start()
    res
  }

  private final class RenderImage(v: text.Visual, config: Config, f: File)
    extends ProcessorImpl[Unit, Processor[Unit]] with Processor[Unit] {

    protected def body(): Unit = blocking {
      val fOut  = f.replaceExt("png")
      if (!fOut.exists()) {
        val vs = config.toVideoSettings
        v.saveFrameAsPNG(f, width = vs.width, height = vs.height)
      }
      progress = 1.0
    }
  }
}
