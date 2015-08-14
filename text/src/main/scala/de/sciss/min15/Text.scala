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

import de.sciss.desktop.{FileDialog, OptionPane}
import de.sciss.file._
import de.sciss.guiflitz.AutoView
import de.sciss.min15.text.{Config, KeyFrame, Situation, VideoSettings}
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.swingplus.ListView
import play.api.libs.json.Json
import prefuse.util.ui.JForcePanel

import scala.concurrent.blocking
import scala.swing.Swing._
import scala.swing.event.ButtonClicked
import scala.swing.{Action, BorderPanel, BoxPanel, Button, Component, Dimension, FlowPanel, Frame, Label, Menu, MenuBar, MenuItem, Orientation, ScrollPane, TextArea, ToggleButton}
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

//    val ggStepAnim = Button("Step Anim") {
//      v.animationStep()
//    }

    val mSnapshots    = ListView.Model.empty[KeyFrame]
    val ggSnapshots   = new ListView[KeyFrame](mSnapshots)
    ggSnapshots.visibleRowCount = 6
    val ggAddSnapshot = Button("Add") {
      val sit       = mkSituation()
      val initFrame = mSnapshots.lastOption.map(_.frame + 250).getOrElse(0)
      val opt = OptionPane.textInput("Key Frame:", initial = initFrame.toString)
      opt.show(None, title = "Add Snapshot").foreach { str =>
        val frame = str.trim.toInt
        val i0 = mSnapshots.indexWhere(_.frame >= frame)
        val i  = if (i0 < 0) mSnapshots.size else i0
        mSnapshots.insert(i, KeyFrame(frame, sit))
      }
    }
    val ggMoveSnapshot = Button("Move") {
      ggSnapshots.selection.indices.headOption.foreach { row =>
        val old @ KeyFrame(initFrame, sit) = mSnapshots(row)
        val opt = OptionPane.textInput("Key Frame:", initial = initFrame.toString)
        opt.show(None, title = "Move Snapshot").foreach { str =>
          val frame = str.trim.toInt
          assert(row == mSnapshots.indexOf(old))
          mSnapshots.remove(row)
          val i0    = mSnapshots.indexWhere(_.frame >= frame)
          val i     = if (i0 < 0) mSnapshots.size else i0
          mSnapshots.insert(i, KeyFrame(frame, sit))
        }
      }
    }
    val ggRemoveSnapshot = Button("Remove") {
      ggSnapshots.selection.indices.toList.sorted.reverse.foreach { row =>
        mSnapshots.remove(row)
      }
    }
    lazy val ggRecallSnapshot: Button = Button("Recall") {
      ggSnapshots.selection.indices.headOption.foreach { row =>
        val sit           = mSnapshots(row).situation
        ggText.text       = sit.text
        textUpdated()
        cfgView.cell()    = sit.config
        v.forceParameters = sit.forceParameters
        mkForcePanel()  // XXX TODO - not cool
      }
    }

    lazy val pSnapshots: Component = new ScrollPane(ggSnapshots)

//    val ggParamSnap = Button("Parameter Snapshot") {
//      val vec: Vec[(String, String)] = v.forceParameters.map { case (name, values) =>
//        val pVec: Vec[(String, String)] = values.map { case (pName, pVal) =>
//          (pName, s"${pVal}f")
//        } (breakOut)
//        val s = pVec.sorted.map { case (pName, pVal) => s""""$pName" -> $pVal""" } .mkString("Map(", ", ", ")")
//        (name, s)
//      } (breakOut)
//
//      println(s"Layout count: ${v.layoutCounter}\n")
//      val tx = vec.sorted.map { case (name, values) => s""""$name" -> $values""" } .mkString("Map(\n  ", ",\n  ", "\n)")
//      println(tx)
//    }

    // val ggProgress = new ProgressBar

    lazy val miExportSequence: MenuItem = new MenuItem(new Action("Export Image Sequence...") {
      accelerator = Some(KeyStroke.getKeyStroke("ctrl shift S"))

      def apply(): Unit = {
        val dir       = file("render")
        require(dir.isDirectory)
        val cfg       = VideoSettings()
        cfg.baseFile  = dir / "frame"
        cfg.anim      = mSnapshots.toVector
        cfg.numFrames = cfg.anim.last.frame + 0 /* XXX TODO -- tail */
        cfg.baseFile

        val p         = v.saveFrameSeriesAsPNG(cfg)
        // seriesProc    = Some(p)
        p.addListener {
          case prog @ Processor.Progress(_, _) => // onEDT(ggProgress.value = prog.toInt)
          case Processor.Result(_, Success(_)) => println("Done.")
          case Processor.Result(_, Failure(ex)) =>
            println("Move rendering failed.")
            ex.printStackTrace()
        }
      }
    })

    lazy val pBottom: Component = new BoxPanel(Orientation.Vertical) {
      contents += new FlowPanel(ggAutoZoom, ggRunAnim, HStrut(16),
        new Label("Key Frames:"), ggAddSnapshot, ggMoveSnapshot, ggRecallSnapshot, HStrut(16), ggRemoveSnapshot)
    }
    lazy val pRight: BoxPanel = new BoxPanel(Orientation.Vertical) {
      contents += VStrut(16)  // will be replaced
      contents += cfgView.component
      contents += ggText
    }

    // stupidly, it doesn't listen for model changes
    def mkForcePanel(): Unit = {
      val fSim    = v.forceSimulator
      val fPanel  = new JForcePanel(fSim)
      fPanel.setBackground(null)
      pRight.contents.update(0, Component.wrap(fPanel))
    }

    mkForcePanel()

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
      add(pSnapshots , BorderPanel.Position.Center)
      // add(Swing.VGlue, BorderPanel.Position.Center)
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
