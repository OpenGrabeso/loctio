package com.github.opengrabeso.loctio

import java.awt.Image

import javax.imageio.ImageIO
import javax.swing.{Icon, ImageIcon}

import scala.swing._

class SimpleMenuItem (title: String, callback: => Unit) extends MenuItem(
  new Action(title) {
    override def apply() = callback
  }
) {
  def this(title: String, callback: => Unit, icon: String, iconSize: Dimension) = {
    this(title, callback)

    val is = getClass.getResourceAsStream(icon)
    val image = ImageIO.read(is)
    is.close()
    val imageSized = image.getScaledInstance(iconSize.width, iconSize.height, Image.SCALE_SMOOTH)
    this.icon = new ImageIcon(imageSized)
  }

}


