package org.dra.kpeg

import com.sun.javafx.iio.ImageStorage
import java.awt.Dimension
import java.awt.Frame
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

/**
 * Created by Derek Alexander on 6/9/2017.
 */

fun main(args: Array<String>) {
    val frame = JFrame();
    val icon = ImageIcon("test_data/colorful_block_reencoded.jpg");
    val manualImage = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)

    val manualBytes = JfifParser.parseChunks(File("test_data/colorful_block.jpg"))

    manualBytes.forEachIndexed { x, y, data -> manualImage.setRGB(x, y, data) }

    val manualIcon = ImageIcon(manualImage)
    //frame.add(JLabel(icon))
    frame.add(JLabel(manualIcon))
    frame.size = Dimension(200, 200)
    frame.setVisible(true)
}