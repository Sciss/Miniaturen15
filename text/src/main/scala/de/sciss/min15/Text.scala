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
import de.sciss.min15.text.{Config, Situation, VideoSettings}
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import play.api.libs.json.Json
import prefuse.util.ui.JForcePanel

import scala.collection.breakOut
import scala.concurrent.blocking
import scala.swing.Swing._
import scala.swing.event.ButtonClicked
import scala.swing.{Action, BorderPanel, BoxPanel, Button, Component, Dimension, FlowPanel, Frame, Menu, MenuBar, MenuItem, Orientation, Swing, TextArea, ToggleButton}
import scala.util.{Failure, Success}

object Text {
  def main(args: Array[String]): Unit = runGUI(mkFrame())

  def mkFrame(): Unit = {
    val v = text.Visual()
    // v.display.setDoubleBuffered(true)
    v.displaySize = (640, 640)

    val avCfg   = AutoView.Config()
    avCfg.small = true
    val cfg0    = Config(size = 2160, lineWidth = 160 /* 320 */, speedLimit = 0.1, noise = 0, threshold = 0)
    val cfgView = AutoView(cfg0, avCfg)

    val ggText = new TextArea(8, 40)

    def textUpdated(): Unit = {
      val newText = ggText.text
      if (v.text != newText) v.text = newText
    }

    val clpseText = new Timer(1000, ActionListener { _ =>
      textUpdated()
    })
    clpseText.setRepeats(false)

    ggText.peer.getDocument.addDocumentListener(new DocumentListener {
      def insertUpdate (e: DocumentEvent): Unit = clpseText.restart()
      def changedUpdate(e: DocumentEvent): Unit = clpseText.restart()
      def removeUpdate (e: DocumentEvent): Unit = clpseText.restart()
    })

    def mkSituation(): Situation = {
      val config  = cfgView.value
      val text    = ggText.text
      val force   = v.forceParameters
      Situation(config = config, forceParameters = force, text = text)
    }

    val ggAutoZoom = new ToggleButton("Zoom") {
      selected = true
      listenTo(this)
      reactions += {
        case ButtonClicked(_) =>
          v.autoZoom = selected
      }
    }

    val ggRunAnim = new ToggleButton("Anim") {
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

    // val ggProgress = new ProgressBar

    val miExportSequence = new MenuItem(new Action("Export Image Sequence...") {
      accelerator = Some(KeyStroke.getKeyStroke("ctrl shift S"))

      def apply(): Unit =
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
            case prog @ Processor.Progress(_, _) => // onEDT(ggProgress.value = prog.toInt)
            case Processor.Result(_, Success(_)) => println("Done.")
            case Processor.Result(_, Failure(ex)) =>
              println("Move rendering failed.")
              ex.printStackTrace()
          }

        } { p =>
          p.abort()
          seriesProc = None
        }
    })

    val pBottom = new BoxPanel(Orientation.Vertical) {
      contents += new FlowPanel(ggAutoZoom, ggRunAnim, ggParamSnap)
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

    def configUpdated(): Unit = {
      val cfg = cfgView.value
      v.lineWidth = cfg.lineWidth
      v.noise     = cfg.noise
      v.threshold = cfg.threshold
      v.imageSize = new Dimension(cfg.size, cfg.size)
      // cfg.invert
    }

    cfgView.cell.addListener { case _ => configUpdated() }

    // v.component.preferredSize = (640, 640)

    val split = new BorderPanel {
      add(v.component, BorderPanel.Position.North )
      add(Swing.VGlue, BorderPanel.Position.Center)
      add(pBottom    , BorderPanel.Position.South )
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

        contents += miExportSequence
      }
    }

    new Frame {
      title     = "Text"
      contents  = new BorderPanel {
        add(split   , BorderPanel.Position.Center)
        // add(pBottom , BorderPanel.Position.South)
        add(pRight  , BorderPanel.Position.East)
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

    configUpdated()
    v.display.panAbs(320, 320)
    ggText.text = "But words are still the principal instruments of control"
    textUpdated()
  }

  def renderImage(v: text.Visual, sit: Situation, f: File): Processor[Unit] = {
    val res = new RenderImage(v = v, sit = sit, f = f)
    res.start()
    res
  }

  private final class RenderImage(v: text.Visual, sit: Situation, f: File)
    extends ProcessorImpl[Unit, Processor[Unit]] with Processor[Unit] {

    protected def body(): Unit = blocking {
      val fOut  = f.replaceExt("png")
      if (!fOut.exists()) {
        // val vs = sit.config.toVideoSettings
        v.saveFrameAsPNG(f, width = sit.config.size /* vs.width */, height = sit.config.size)
      }
      progress = 1.0
    }
  }
}
