/*
 * package.scala
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

import de.sciss.play.json.AutoFormat
import play.api.libs.json.Format

package object text {
  object Config {
    implicit val format: Format[Config] = AutoFormat[Config]
  }
  case class Config(size: Int, lineWidth: Int,
                    speedLimit: Double, noise: Int, threshold: Int)

  object Situation {
    implicit val format: Format[Situation] = AutoFormat[Situation]
  }
  case class Situation(config: Config, forceParameters: Map[String, Map[String, Float]], text: String) {
    override def toString = s"[$config, $forceParameters, $text]"
  }

  object KeyFrame {
    implicit val format: Format[KeyFrame] = AutoFormat[KeyFrame]
  }
  case class KeyFrame(frame: Int, situation: Situation) {
    override def toString = s"$frame: $situation"
  }

  // type Anim = Vec[(Int, Map[String, Map[String, Float]])]
  type Anim = Vec[KeyFrame]
}
