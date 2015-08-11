/*
 * VisualVertex.scala
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

import prefuse.data.{Node => PNode}
import prefuse.visual.VisualItem

import scala.swing.Graphics2D

//object VisualUGen {
//  def apply(main: Visual, v: Vertex.UGen)(implicit tx: S#Tx): VisualUGen = impl.VisualUGenImpl(main, v)
//}
//trait VisualUGen extends VisualVertex {
//  def info: UGenSpec
//}
//
//object VisualConstant {
//  def apply(main: Visual, v: Vertex.Constant)(implicit tx: S#Tx): VisualConstant = impl.VisualConstantImpl(main, v)
//}
//trait VisualConstant extends VisualVertex {
//  var value: Float
//}

object VisualVertex {
  def apply(main: Visual, lineRef: AnyRef, wordRef: AnyRef, character: Char): VisualVertex =
    new Impl(main, lineRef = lineRef, wordRef = wordRef, character = character)

  private final class Impl(val main: Visual, var lineRef: AnyRef, val wordRef: AnyRef, val character: Char)
    extends VisualVertex with VisualVertexImpl {

    protected def renderDetail(g: Graphics2D, vi: VisualItem): Unit = {
      drawLabel(g, vi, /* diam * vi.getSize.toFloat * 0.5f, */ name)
    }

    protected def boundsResized(): Unit = ()

    def name: String = character.toString

    private var _pNode: PNode = _

    def pNode: PNode = _pNode

    def init(): Unit = {
      _pNode = mkPNode()
    }

    def dispose(): Unit = {
      if (_pNode.isValid) main.graph.removeNode(_pNode)
    }

    init()
  }
}
sealed trait VisualVertex extends VisualNode {
  def character: Char

  def advance: Int

  def pNode: PNode

  def wordRef: AnyRef
  var lineRef: AnyRef
}