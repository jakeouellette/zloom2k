package zedit2

import zedit2.BufferModel.Companion.getClipImage
import java.awt.Color
import java.awt.Component
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class FancyFillRenderer(private val editor: WorldEditor) : ListCellRenderer<String?> {
    private val bg: Color = editor.frame.background

    override fun getListCellRendererComponent(
        list: JList<out String?>,
        value: String?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        var img: Image = getClipImage(editor.canvas, value) ?: return JLabel("(not selected)")
        val w = img.getWidth(null)
        val h = img.getHeight(null)
        if (w > WIDTH) {
            val scale = 1.0 * w / WIDTH
            //int scaleInt = (int) Math.ceil(scale);
            val nh = Math.round(h * scale).toInt()
            val dupImg = BufferedImage(w, nh, BufferedImage.TYPE_INT_RGB)
            val g = dupImg.graphics
            val yoff = (nh % HEIGHT) / 2
            var y = 0
            while (y < nh) {
                g.drawImage(img, 0, y - yoff, null)
                y += HEIGHT
            }
            val scaledDup = dupImg.getScaledInstance(WIDTH, -1, Image.SCALE_SMOOTH)
            img = scaledDup

            //img = img.getScaledInstance(WIDTH, -1, Image.SCALE_SMOOTH).
        } else {
            val dupImg = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
            val g = dupImg.graphics
            g.color = bg
            g.fillRect(0, 0, WIDTH, HEIGHT)
            g.drawImage(img, (WIDTH - w) / 2, 0, null)
            img = dupImg
        }
        val label = JLabel(ImageIcon(img))
        label.isOpaque = true
        label.background = bg
        return label
    }

    companion object {
        private const val WIDTH = 96
        private const val HEIGHT = 14
    }
}
