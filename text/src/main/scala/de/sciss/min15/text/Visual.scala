/*
 * Visual.scala
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

package de.sciss.min15.text

import java.awt.image.BufferedImage
import java.awt.{Graphics, Color, Font, LayoutManager, Point, RenderingHints}
import javax.imageio.ImageIO
import javax.swing.JPanel

import com.jhlabs.image.{ThresholdFilter, NoiseFilter}
import de.sciss.file._
import de.sciss.processor.Processor
import de.sciss.{kollflitz, numbers}
import prefuse.action.assignment.ColorAction
import prefuse.action.{ActionList, RepaintAction}
import prefuse.activity.Activity
import prefuse.controls.{DragControl, PanControl, WheelZoomControl, ZoomControl}
import prefuse.data.{Graph => PGraph}
import prefuse.render.DefaultRendererFactory
import prefuse.util.ColorLib
import prefuse.util.force.{DragForce, Force, ForceSimulator, NBodyForce}
import prefuse.visual.expression.InGroupPredicate
import prefuse.visual.{VisualGraph, VisualItem}
import prefuse.{Constants, Display, Visualization}

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Promise, blocking}
import scala.swing.{Component, Dimension, Graphics2D, Rectangle, Swing}
import scala.util.Try
import scala.util.control.NonFatal

object Visual {
  val DEBUG = false

  // val VIDEO_WIDTH     = 720
  private val VIDEO_HEIGHT    = 2160 // 1920 // / 3 // 576
  private val VIDEO_WIDTH_SQR = 2160 // 1080 // / 3 // 1024 // 1024 : 576 = 16 : 9

  private lazy val _initFont: Font = {
    // val is  = new FileInputStream("dosis/Dosis-Medium.ttf")
    val is  = getClass.getClassLoader.getResourceAsStream("vesper_libre/VesperLibre-Regular.ttf")
    val res = Font.createFont(Font.TRUETYPE_FONT, is)
    is.close()
    res
  }

  private var _condensedFont: Font = _

  /** A condensed font for GUI usage. This is in 12 pt size,
    * so consumers must rescale.
    */
  def condensedFont: Font = {
    if (_condensedFont == null) _condensedFont = _initFont
    _condensedFont
  }
  def condensedFont_=(value: Font): Unit =
    _condensedFont = value

  final val GROUP_GRAPH   = "graph"
  final val COL_MUTA      = "muta"
  private final val GROUP_NODES   = "graph.nodes"
  private final val GROUP_EDGES   = "graph.edges"
  private final val ACTION_LAYOUT = "layout"
  private final val ACTION_COLOR  = "color"
  private final val LAYOUT_TIME   = 50

  def apply(): Visual = {
    val res = new Impl
    res.guiInit()
    res
  }

  class Word(val letters: Vec[VisualVertex], val word: String) {
    def dispose(): Unit = letters.foreach(_.dispose())
    lazy val width = letters.map(_.advance).sum   // yes I know this is not precise
  }

  class Line(val words: Vec[Word])

  private final class Impl // (map: TMap[S#ID, VisualVertex], val algorithm: Algorithm, cursorPos0: S#Acc)
    extends Visual /* with ComponentHolder[Component] */ {

    private[this] var _vis: Visualization       = _
    private[this] var _dsp: Display             = _
    private[this] var _g  : PGraph              = _
    private[this] var _vg : VisualGraph         = _
    private[this] var _lay: MyForceDirectedLayout = _
    private[this] var actionColor: ActionList   = _
    private[this] var actionAutoZoom: AutoZoom  = _
    private[this] var _runAnim = false

    private[this] var _text = ""
    private[this] var wordMap  = Map.empty[String, Word]
    private[this] var wordVec = Vec.empty[Word]

    private[this] var forces: Map[String, Force] = _

    private[this] var _autoZoom = true

    def autoZoom: Boolean = _autoZoom
    def autoZoom_=(value: Boolean): Unit = if (_autoZoom != value) {
      _autoZoom = value
      runAnimation = !runAnimation
      runAnimation = !runAnimation
    }

    //    private val cursorPos = Ref(cursorPos0)

//    def initChromosome(idx: Int): Unit =
//      algorithm.global.cursor.step { implicit tx =>
//        val c = algorithm.genome.chromosomes().apply(idx)
//        insertChromosome(c)
//      }

    def forceSimulator: ForceSimulator = _lay.getForceSimulator

//    private def insertChromosome(c: Chromosome)(implicit tx: S#Tx): Unit = {
//      c.vertices.iterator.foreach { v =>
//        if (v.isUGen || c.edges.iterator.filter(_.targetVertex == v).nonEmpty) {
//          checkOrInsertVertex(v)
//        }
//      }
//      c.edges.iterator.foreach { e =>
//        checkOrInsertLink(e)
//      }
//    }

    def display       : Display       = _dsp
    def visualization : Visualization = _vis
    def graph         : PGraph        = _g
    def visualGraph   : VisualGraph   = _vg

    //    private[this] val guiCode = TxnLocal(init = Vector.empty[() => Unit], afterCommit = handleGUI)
    //
    //    private[this] def handleGUI(seq: Vec[() => Unit]): Unit = {
    //      def exec(): Unit = _vis.synchronized {
    //        stopAnimation()
    //        seq.foreach { fun =>
    //          try {
    //            fun()
    //          } catch {
    //            case NonFatal(e) => e.printStackTrace()
    //          }
    //        }
    //        startAnimation()
    //      }
    //
    //      defer(exec())
    //    }

//    def deferVisTx(thunk: => Unit)(implicit tx: TxnLike): Unit =
//      guiCode.transform(_ :+ (() => thunk))(tx.peer)

    private def visDo[A](body: => A): A = _vis.synchronized {
      // stopAnimation()
      val res = body
      // startAnimation()
      res
    }

    def text: String = _text
    def text_=(value: String): Unit = if (_text != value) {
      visDo {
        stopAnimation()
        setText(value)
        startAnimation()
      }
    }

    private val noiseOp = new NoiseFilter
    noiseOp.setAmount(0)
    noiseOp.setMonochrome(true)

    private val threshOp = new ThresholdFilter(0)

    private var _lineWidth = 320

    def noise: Int = noiseOp.getAmount
    def noise_=(value: Int): Unit = noiseOp.setAmount(value)

    def threshold: Int = threshOp.getLowerThreshold
    def threshold_=(value: Int): Unit = {
      threshOp.setLowerThreshold(value)
      threshOp.setUpperThreshold(value)
    }

    def lineWidth: Int = _lineWidth
    def lineWidth_=(value: Int): Unit = if (_lineWidth != value) {
      _lineWidth = value
      setText(_text)
    }

    private def setText(value: String): Unit = {
      _text = value

      import kollflitz.Ops._
      val x0      = value.replace('\n', ' ').replace("  ", " ")
      val words   = x0.toVector.groupWith { (a, b) =>
        a.isLetterOrDigit && b.isLetterOrDigit
      } .map(_.mkString).toVector
      // val lines: Vec[Vec[String]] = words1.grouped(5).toVector

      wordMap.foreach { case (word, v) =>
        v.dispose()
      }
      wordMap   = Map.empty
      wordVec   = Vector.empty

      import kollflitz.Ops._

      val lineRef0 = new AnyRef

      val ws = words.map { word =>
        val wordRef = new AnyRef
        val vs      = word.map { c => VisualVertex(this, lineRef = lineRef0, wordRef = wordRef, character = c) }
        vs.foreachPair { (pred, succ) =>
          graph.addEdge(pred.pNode, succ.pNode)
        }
        val w  = new Word(vs, word)
        wordMap += word -> w
        wordVec :+= w
        w
      }

      @tailrec def mkLines(words: Vec[Word], rem: Vec[Word], width: Int, res: Vec[Line]): Vec[Line] = {
        def flush(): Line = {
          words.foreachPair { (pred, succ) =>
            val n1 = pred.letters.last.pNode
            val n2 = succ.letters.head.pNode
            graph.addEdge(n1, n2)
          }
          val line = new Line(words)
          words.foreach(_.letters.foreach(_.lineRef = line))
          line
        }

        // note: we allow the last word to exceed the maximum width
        if (width > _lineWidth && rem.headOption.map(_.letters.size).getOrElse(0) > 1) {
          val line = flush()
          mkLines(Vector.empty, rem, 0, res :+ line)
        } else rem match {
          case head +: tail =>
            mkLines(words :+ head, tail, width + head.width, res)
          case _ =>
            val line = flush()
            res :+ line
        }
      }

      val lines = mkLines(Vector.empty, ws, 0, Vector.empty)

      lines.foreachPair { (pred, succ) =>
        val n1 = pred.words.head.letters.head.pNode
        val n2 = succ.words.head.letters.head.pNode
        graph.addEdge(n1, n2)
//          val n3 = predL.letters.last.pNode
//          val n4 = succL.letters.last.pNode
//          graph.addEdge(n3, n4)
      }
    }

    @inline private def stopAnimation(): Unit = {
      _vis.cancel(ACTION_COLOR )
      _vis.cancel(ACTION_LAYOUT)
    }

    @inline private def startAnimation(): Unit =
      _vis.run(ACTION_COLOR)

//    private def checkOrInsertVertex(v: Vertex)(implicit tx: S#Tx): Unit =
//      map.get(v.id)(tx.peer).fold {
//        insertVertex(v)
//      } { vv =>
//        (v, vv) match {
//          case (vc: Vertex.Constant, vvc: VisualConstant) =>
//            val value = vc.f()
//            if (vvc.value != value) {
//              println(f"VALUE ${vvc.value}%1.3f >>> $value%1.3f")
//              vvc.value = value   // XXX animate -- how? THREADING
//            }
//          case _ =>
//        }
//        vv.touch()
//      }
//
//    private def insertVertex(v: Vertex)(implicit tx: S#Tx): Unit = {
//      implicit val itx = tx.peer
//      val vv = VisualVertex(this, v)
//      val res = map.put(v.id, vv)
//      require(res.isEmpty)
//      deferVisTx(insertVertexGUI(vv, locO = None))
//    }

//    private def initNodeGUI(obj: VisualVertex, vn: VisualVertex, locO: Option[Point2D]): VisualItem = {
//      val pNode = vn.pNode
//      val _vi   = _vis.getVisualItem(GROUP_GRAPH, pNode)
//      val same  = vn == obj
//      locO.fold {
//        if (!same) {
//          val _vi1 = _vis.getVisualItem(GROUP_GRAPH, obj.pNode)
//          _vi.setEndX(_vi1.getX)
//          _vi.setEndY(_vi1.getY)
//        }
//      } { loc =>
//        _vi.setEndX(loc.getX)
//        _vi.setEndY(loc.getY)
//      }
//      _vi
//    }

//    private def insertVertexGUI(v: VisualVertex, locO: Option[Point2D]): Unit = {
//      initNodeGUI(v, v, locO)
//    }
//
//    private def checkOrInsertLink(e: Edge)(implicit tx: S#Tx): Unit = {
//      implicit val itx = tx.peer
//      val tup = for {
//        sourceV <- map.get(e.sourceVertex.id)
//        targetV <- map.get(e.targetVertex.id)
//      } yield (sourceV, targetV)
//
//      if (tup.isEmpty) {
//        println(s"WARNING: link misses vertices: $e")
//      }
//
//      tup.foreach { case (sourceV, targetV) =>
//        val edge = VisualEdge(sourceV, targetV, init = false)
//        sourceV.edgesOut.get(edge.key).fold[Unit](edge.init())(_.touch())
//      }
//    }

//    def dispose()(implicit tx: S#Tx): Unit = ()

    private def mkActionColor(): Unit = {
      // colors
      val actionNodeStrokeColor  = new ColorAction(GROUP_NODES, VisualItem.STROKECOLOR, ColorLib.rgb(255, 255, 255))
      actionNodeStrokeColor.add(VisualItem.HIGHLIGHT, ColorLib.rgb(255, 255, 0))
      // actionNodeStroke.add(VisualItem.HIGHLIGHT, ColorLib.rgb(220, 220, 0))
      val actionNodeFillColor    = new ColorAction(GROUP_NODES, VisualItem.FILLCOLOR  , ColorLib.rgb(0, 0, 0))
      actionNodeFillColor.add(VisualItem.HIGHLIGHT, ColorLib.rgb(63, 63, 0))
      val actionTextColor   = new ColorAction(GROUP_NODES, VisualItem.TEXTCOLOR  , ColorLib.rgb(255, 255, 255))

      val actionEdgeStrokeColor = new ColorAction(GROUP_EDGES, VisualItem.STROKECOLOR, ColorLib.rgb(255, 255, 255))
      val actionEdgeFillColor   = new ColorAction(GROUP_EDGES, VisualItem.FILLCOLOR  , ColorLib.rgb(255, 255, 255))
      //      val actionAggrFill    = new ColorAction(AGGR_PROC  , VisualItem.FILLCOLOR  , ColorLib.rgb(80, 80, 80))
      //      val actionAggrStroke  = new ColorAction(AGGR_PROC  , VisualItem.STROKECOLOR, ColorLib.rgb(255, 255, 255))

      actionColor = new ActionList(_vis)
      actionColor.add(actionTextColor)
      actionColor.add(actionNodeStrokeColor)
      actionColor.add(actionNodeFillColor)
      actionColor.add(actionEdgeStrokeColor)
      actionColor.add(actionEdgeFillColor  )
      //      actionColor.add(actionAggrFill)
      //      actionColor.add(actionAggrStroke)
      // actionColor.add(_lay)
      _vis.putAction(ACTION_COLOR, actionColor)
    }

    def guiInit(): Unit = {
      _vis = new Visualization
      _dsp = new Display(_vis) {
        override def setRenderingHints(g: Graphics2D): Unit = {
          super.setRenderingHints(g)
          g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
            if (m_highQuality) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)
        }

        private var img2: BufferedImage = _
        private var img3: BufferedImage = _

        override def paintBufferToScreen(g: Graphics): Unit = this.synchronized {
          var img = m_offscreen
          if (noise > 0) {
            if (img2 == null || img2.getWidth != m_offscreen.getWidth || img2.getHeight != m_offscreen.getHeight) {
              img2 = new BufferedImage(m_offscreen.getWidth, m_offscreen.getHeight, BufferedImage.TYPE_INT_RGB)
            }
            img = noiseOp.filter(img, img2)
          }
          if (threshold > 0) {
            if (img3 == null || img3.getWidth != m_offscreen.getWidth || img3.getHeight != m_offscreen.getHeight) {
              img3 = new BufferedImage(m_offscreen.getWidth, m_offscreen.getHeight, BufferedImage.TYPE_BYTE_BINARY)
            }
            img = threshOp.filter(img, img3)
          }
          g.drawImage(img, 0, 0, null)
        }
      }

      _g     = new PGraph(true)
      _vg    = _vis.addGraph(GROUP_GRAPH, _g)
      _vg.addColumn(COL_MUTA, classOf[AnyRef])

      val procRenderer = new BoxRenderer(this) // new NuagesShapeRenderer(50)
      val edgeRenderer = new MyEdgeRenderer
      // edgeRenderer.setArrowHeadSize(8, 8)

      val rf = new DefaultRendererFactory(procRenderer)
      rf.add(new InGroupPredicate(GROUP_EDGES), edgeRenderer)
      // rf.add(new InGroupPredicate(AGGR_PROC  ), aggrRenderer)
      _vis.setRendererFactory(rf)

      _lay = new MyForceDirectedLayout(this)
      val sim = new ForceSimulator
      sim.addForce(new NBodyForce)
      sim.addForce(new MySpringForce)
      sim.addForce(new DragForce)
      _lay.setForceSimulator(sim)
      forces = sim.getForces.map { f => (f.getClass.getSimpleName, f) } (breakOut)

      //      val forceMap = Map(
      //        ("NBodyForce" , "GravitationalConstant") -> -2.0f,
      //        ("DragForce"  , "DragCoefficient"      ) -> 0.002f,
      //        ("SpringForce", "SpringCoefficient"    ) -> 1.0e-5f,
      //        ("SpringForce", "DefaultSpringLength"  ) -> 200.0f
      //      )

      //      val forceMap = Map(
      //        ("NBodyForce" , "GravitationalConstant") -> -10.0f,
      //        ("NBodyForce" , "Distance"             ) -> -1.0f,
      //        ("NBodyForce" , "BarnesHutTheta"       ) -> 0.57f,
      //        ("DragForce"  , "DragCoefficient"      ) -> 0.023f,
      //        ("SpringForce", "SpringCoefficient"    ) -> 1.0e-5f,
      //        ("SpringForce", "DefaultSpringLength"  ) -> 200.0f
      //      )

      //      val forceMap = Map(
      //        ("NBodyForce" , "GravitationalConstant") -> -2.0f,
      //        ("NBodyForce" , "Distance"             ) -> -1.0f,
      //        ("NBodyForce" , "BarnesHutTheta"       ) -> 0.57f,
      //        ("DragForce"  , "DragCoefficient"      ) -> 0.01f,
      //        ("SpringForce", "SpringCoefficient"    ) -> 1.0e-5f,
      //        ("SpringForce", "DefaultSpringLength"  ) -> 10.0f
      //      )

//      val forceMap = Map(
//        ("NBodyForce" , "GravitationalConstant") -> -0.1f,
//        ("NBodyForce" , "Distance"             ) -> -1.0f,
//        ("NBodyForce" , "BarnesHutTheta"       ) -> 0.4f,
//        ("DragForce"  , "DragCoefficient"      ) -> 0.02f,
//        ("MySpringForce", "SpringCoefficient"  ) -> 8.0e-5f,
//        ("MySpringForce", "DefaultSpringLength") -> 0.1f, // 150.0f,
//        ("MySpringForce", "Torque"             ) -> 2.0e-4f,
//        ("MySpringForce", "Limit"              ) -> 300.0f
//      )

      val forceMap = Map(
        ("NBodyForce" , "GravitationalConstant") -> 0f, // -0.01f,
        ("NBodyForce" , "Distance"             ) -> -1.0f,
        ("NBodyForce" , "BarnesHutTheta"       ) -> 0.4f,
        ("DragForce"  , "DragCoefficient"      ) -> 0.015f,
        ("MySpringForce", "SpringCoefficient"  ) -> 1.0e-4f,
        ("MySpringForce", "VSpringCoefficient" ) -> 1.0e-4f,
        // ("MySpringForce", "DefaultSpringLength") -> 0.1f, // 150.0f,
        ("MySpringForce", "HTorque"            ) -> 2.0e-4f,
        ("MySpringForce", "VTorque"            ) -> 2.0e-4f,
        ("MySpringForce", "Limit"              ) -> 300.0f
      )

      forceSimulator.getForces.foreach { force =>
        val fName = force.getClass.getSimpleName
        // println(s"----FORCE----$fName")
        for (i <- 0 until force.getParameterCount) {
          val pName = force.getParameterName(i)
          forceMap.get((fName, pName)).foreach { value =>
            force.setParameter(i, value)
          }
          // println(pName)

          // NBodyForce
          // - GravitationalConstant = -2.0
          // - Distance = -1.0
          // - BarnesHutTheta = 0.89
          // DragForce
          // - DragCoefficient = 0.002
          // SpringForce
          // - SpringCoefficient = 1.0e-5
          // - DefaultSpringLength = 200.0
        }
      }

      _lay.setMaxTimeStep(40L)
      _lay.setIterations(1)
      // forceSimulator.setSpeedLimit(0.025f)

      // ------------------------------------------------

      actionAutoZoom = new AutoZoom(this)

      // initialize the display
      _dsp.setSize(VIDEO_WIDTH_SQR, VIDEO_HEIGHT)
      _dsp.setMinimumSize(new Dimension(VIDEO_WIDTH_SQR, VIDEO_HEIGHT))
      _dsp.setMaximumSize(new Dimension(VIDEO_WIDTH_SQR, VIDEO_HEIGHT))
      _dsp.addControlListener(new ZoomControl     ())
      _dsp.addControlListener(new WheelZoomControl())
      _dsp.addControlListener(new PanControl        )
      _dsp.addControlListener(new DragControl       )
      _dsp.addControlListener(new ClickControl(this))
      _dsp.setHighQuality(true)

      // ------------------------------------------------

      edgeRenderer.setHorizontalAlignment1(Constants.CENTER) // LEFT )
      edgeRenderer.setHorizontalAlignment2(Constants.CENTER) // RIGHT)
      edgeRenderer.setVerticalAlignment1  (Constants.CENTER)
      edgeRenderer.setVerticalAlignment2  (Constants.CENTER)

      _dsp.setForeground(Color.WHITE)
      _dsp.setBackground(Color.BLACK)

      //      setLayout( new BorderLayout() )
      //      add( display, BorderLayout.CENTER )
      val p = new JPanel
      p.setLayout(new Layout(_dsp))
      p.add(_dsp)

      mkAnimation()
      // _vis.run(ACTION_COLOR)

      component = Component.wrap(p)
    }

    var component: Component = _

    def forceParameters: Map[String, Map[String, Float]] = forces.map { case (name, force) =>
      val values: Map[String, Float] = (0 until force.getParameterCount).map { i =>
        (force.getParameterName(i), force.getParameter(i))
      } (breakOut)
      (name, values)
    }

    def layoutCounter: Int = _lay.counter

    def runAnimation: Boolean = _runAnim
    def runAnimation_=(value: Boolean): Unit = if (_runAnim != value) {
      // requireEDT()
      _vis.synchronized {
        stopAnimation()
        _vis.removeAction(ACTION_COLOR)
        _vis.removeAction(ACTION_LAYOUT)
        _runAnim = value
        mkAnimation()
      }
    }

    private def mkAnimation(): Unit = {
      mkActionColor() // create really new instance, because `alwaysRunsAfter` installs listener on this
      val actionLayout = if (_runAnim) {
          new ActionList(Activity.INFINITY, LAYOUT_TIME)
        } else {
          new ActionList()
        }
      actionLayout.add(_lay)
      if (autoZoom) actionLayout.add(actionAutoZoom)
      // actionLayout.add(new PrefuseAggregateLayout(AGGR_PROC))
      actionLayout.add(new RepaintAction())
      actionLayout.setVisualization(_vis)
      _vis.putAction(ACTION_LAYOUT, actionLayout)
      _vis.alwaysRunAfter(ACTION_COLOR, ACTION_LAYOUT)
      startAnimation()
    }

    def animationStep(): Unit = {
      // requireEDT()
      _vis.synchronized {
        startAnimation()
      }
    }

    def saveFrameAsPNG(file: File): Unit = saveFrameAsPNG(file, width = _dsp.getWidth, height = _dsp.getHeight)

    def saveFrameAsPNG(file: File, width: Int, height: Int): Unit = {
      // requireEDT()
      val bImg  = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
      val g     = bImg.createGraphics()
      // val scale = width.toDouble / VIDEO_WIDTH_SQR
      val p0 = new Point(0, 0)
      try {
        _dsp.damageReport() // force complete redrawing
        // _dsp.zoom(p0, scale)
        // actionAutoZoom.karlHeinz = scale
        _dsp.paintDisplay(g, new Dimension(width, height))
        // _dsp.zoom(p0, 1.0/scale)
        // actionAutoZoom.karlHeinz = 1.0
        ImageIO.write(bImg, "png", file)
      } finally {
        g.dispose()
      }
    }

    private def execOnEDT[A](code: => A)(implicit exec: ExecutionContext): A = {
      val p = Promise[A]()
      Swing.onEDT {
        val res = Try {
          visDo(code)
        }
        p.complete(res)
      }
      blocking(Await.result(p.future, Duration.Inf)) //  Duration(20, TimeUnit.SECONDS)))
    }

    def saveFrameSeriesAsPNG(settings: VideoSettings): Processor[Unit] = {
      import settings.{text => _, _}

      import ExecutionContext.Implicits.global

      // def toFrames(sec: Double) = (sec * framesPerSecond + 0.5).toInt

      runAnimation    = false
      val child       = baseFile.base
      val parent      = baseFile.parent

      forceSimulator.setSpeedLimit(speedLimit.toFloat)

      var initialized = false

      val framesSkip = 0

      Processor[Unit]("saveFrameSeriesAsPNG") { p =>
        var frame = 0

        // def mkF() = parent / f"$child${frame - framesSkip}%05d.png"
        def mkF() = parent / f"$child${frame - framesSkip}%d.png"

        var startAnim = anim.head
        var stopAnim  = startAnim
        var animIdx   = 0

        while (frame < numFrames) {
          if (initialized) {

          } else {
            execOnEDT {
              display.panAbs(settings.width * 0.5, settings.height * 0.5)
              display.zoomAbs(new Point(0, 0), 0.1)
              setText(settings.text)
              animationStep()
            }
            initialized = true
          }

          import numbers.Implicits._
          val animFrac = frame.clip(startAnim.frame, stopAnim.frame)
            .linlin(startAnim.frame, math.max(startAnim.frame + 1, stopAnim.frame), 0, 1)

          execOnEDT {
            forceSimulator.getForces.foreach { force =>
              val fName = force.getClass.getSimpleName
              // println(s"----FORCE----$fName")
              for (i <- 0 until force.getParameterCount) {
                val pName = force.getParameterName(i)
                val startValOpt = startAnim.forceParameters.getOrElse(fName, Map.empty).get(pName)
                val stopValOpt  = stopAnim .forceParameters.getOrElse(fName, Map.empty).get(pName)

                val valOpt: Option[Float] = (startValOpt, stopValOpt) match {
                  case (Some(startVal), Some(stopVal)) => Some(startVal * (1 - animFrac) + stopVal * animFrac)
                  case _ => startValOpt orElse stopValOpt
                }
                valOpt.foreach { value =>
                  if (force.getParameter(i) != value) {
                    if (DEBUG) println(s"$fName - $pName - $value")
                    force.setParameter(i, value)
                  }
                }
              }
            }
          }

          val frameSave = frame - framesSkip
          if (frameSave >= 0) {
            val f = mkF()
            execOnEDT {
              saveFrameAsPNG(f, width = width, height = height)
              // _dsp.damageReport() // force complete redrawing
              // _dsp.paintDisplay(g, new Dimension(width, height))
              // ImageIO.write(bImg, "png", f)
            }
          }
          execOnEDT {
            animationStep()
          }

          frame += 1

          if (frame >= stopAnim.frame) {
            startAnim = stopAnim
            animIdx += 1
            if (animIdx < anim.size) stopAnim = anim(animIdx)
          }

          println(s"frame $frame")
          p.progress = frame.toDouble / numFrames
          p.checkAborted()
        }

        // now dissolve graph
        import kollflitz.RandomOps._
        implicit val rnd = new util.Random(0L)
        // val m0  = (wordMap.values.flatMap(_.letters)(breakOut): Vec[VisualVertex]).scramble()
        val m = wordVec.scramble().flatMap(_.letters)
        println(s"VERTICES AT END: ${m.size}")

        // println(s"FRAMES-PLOP $framesPlop")
        @tailrec def loopPlop(sq: Vec[VisualVertex]): Unit = sq match {
          case head +: tail =>
            val f = mkF()
            execOnEDT {
              try {
                head.dispose()
              } catch {
                case NonFatal(e) => e.printStackTrace()
              }
              saveFrameAsPNG(f, width = width, height = height)
              animationStep()
            }
            frame += 1
            loopPlop(tail)

          case _ =>
        }
        loopPlop(m)

        // execOnEDT(g.dispose())
        execOnEDT {
          wordMap = Map.empty
          wordVec = Vector.empty
        }
      }
    }
  }

  private class Layout(peer: javax.swing.JComponent) extends LayoutManager {
    def layoutContainer(parent: java.awt.Container): Unit =
      peer.setBounds(new Rectangle(0, 0, parent.getWidth, parent.getHeight))

    def minimumLayoutSize  (parent: java.awt.Container): Dimension = peer.getMinimumSize
    def preferredLayoutSize(parent: java.awt.Container): Dimension = peer.getPreferredSize

    def removeLayoutComponent(comp: java.awt.Component) = ()

    def addLayoutComponent(name: String, comp: java.awt.Component) = ()
  }
}
trait Visual {
  def display: Display

  def visualization: Visualization

  def graph: PGraph

  def visualGraph: VisualGraph

  def forceSimulator: ForceSimulator

  def component: Component

  var text: String

  def animationStep(): Unit

  var runAnimation: Boolean

  def saveFrameAsPNG(file: File): Unit

  def saveFrameAsPNG(file: File, width: Int, height: Int): Unit

  def saveFrameSeriesAsPNG(settings: VideoSettings): Processor[Unit]

  def forceParameters: Map[String, Map[String, Float]]

  def layoutCounter: Int

  var autoZoom: Boolean

  var noise     : Int
  var threshold : Int
  var lineWidth : Int
}