package zedit2.util.image

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.attributes.ViewBox
import java.awt.*
import javax.swing.JButton
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import kotlin.math.ceil

class SvgJButton(val svg : SVGDocument) : JButton("") {
    val borderWidth = 3
    val thisSize = Dimension(
        ceil(svg.size().width.toDouble()).toInt(),
        ceil(svg.size().height.toDouble()).toInt())
    val thisSizeWithBorder = Dimension(
        thisSize.width + borderWidth * 2,
        thisSize.height + borderWidth * 2)

    var picked = false

    init {
        preferredSize = thisSizeWithBorder
        maximumSize = thisSizeWithBorder
        minimumSize = thisSizeWithBorder
        this.border = EmptyBorder(borderWidth, borderWidth, borderWidth, borderWidth)
//     ]]
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        val g2d = (g as Graphics2D?)!!
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);


        g!!.color = UIManager.getLookAndFeelDefaults().get("Panel.background") as Color
        g.fillRect(0, 0, thisSizeWithBorder.width, thisSizeWithBorder.height)
        if (picked) {
            g.color = g.color.brighter()
        }
        g.fillRoundRect(0,0, thisSizeWithBorder.width, thisSizeWithBorder.height, 6, 6)


        svg.render(
            this,
            (g as Graphics2D?)!!,
            ViewBox(borderWidth.toFloat(), borderWidth.toFloat(), thisSize.width.toFloat(), thisSize.height.toFloat())
        )
    }
}