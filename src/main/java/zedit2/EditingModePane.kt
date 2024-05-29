package zedit2

import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.time.Duration
import java.time.LocalDateTime
import javax.swing.JPanel
import javax.swing.Timer

class EditingModePane : JPanel() {
    private var text = ""
    private var col: Color = Color.BLACK
    private var override_text: String? = null
    private var override_col: Color? = null
    private var timer: Timer? = null
    private var override_timer: Timer? = null

    private var currentHash = -1

    override fun getPreferredSize(): Dimension {
        val w = 128
        val h = 24

        return Dimension(w, h)
    }

    fun display(col: Color, text: String) {
        val hash = col.hashCode() + text.hashCode()
        if (hash == currentHash) return
        currentHash = hash

        this.col = col
        this.text = text
        repaint()

        stopTimer()
    }

    fun display(cols: Array<Color>, text: String) {
        val hash = cols.contentHashCode() + text.hashCode()
        if (hash == currentHash) return
        currentHash = hash

        this.col = cols[0]
        this.text = text
        repaint()

        stopTimer()
        timer = Timer(300, object : ActionListener {
            private var colNum = 0
            override fun actionPerformed(e: ActionEvent) {
                colNum++
                col = cols[colNum % cols.size]
                repaint()
            }
        })
        timer!!.isRepeats = true
        timer!!.start()
    }

    fun display(col: Color, duration: Int, text: String?) {
        override_text = text
        override_col = col
        val started = LocalDateTime.now()

        if (override_timer != null) {
            override_timer!!.stop()
            override_timer = null
        }

        override_timer = Timer(10) { e: ActionEvent? ->
            val current = LocalDateTime.now()
            val dur = Duration.between(started, current)
            val ms = dur.toMillis()
            val leadIn = duration / 4
            val leadOut = duration / 2
            if (ms < leadIn) {
                override_col = mix(col, Color.WHITE, 1.0 * ms / leadIn)
            } else if (ms < leadOut) {
                override_col = mix(Color.WHITE, col, 1.0 * (ms - leadIn) / leadIn)
            } else if (ms < duration) {
                override_col = mix(col, Color.BLACK, 1.0 * (ms - leadOut) / leadOut)
            } else {
                override_text = null
                override_col = null
                override_timer!!.stop()
                override_timer = null
            }
            repaint()
        }
        override_timer!!.isRepeats = true
        override_timer!!.start()
    }

    private fun stopTimer() {
        if (timer != null) {
            timer!!.stop()
            timer = null
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val col = getCol()
        val darkCol = mix(col, Color.BLACK, 0.5)
        val lightCol = mix(col, Color.WHITE, 0.5)

        g.color = darkCol
        //drawBorder(g, 0, false);
        drawBorder(g, 2, false)
        drawBorder(g, 4, true)
        g.color = lightCol
        val fonts = arrayOf(
            CP437.font,
            Font(Font.SANS_SERIF, Font.BOLD, 14),
            Font(Font.SANS_SERIF, Font.BOLD, 12),
            Font(Font.SANS_SERIF, Font.PLAIN, 12),
            Font(Font.SANS_SERIF, Font.BOLD, 10),
            Font(Font.SANS_SERIF, Font.PLAIN, 10),
            Font(Font.SANS_SERIF, Font.BOLD, 8),
            Font(Font.SANS_SERIF, Font.PLAIN, 8)
        )

        var metrics: FontMetrics
        var textw: Int
        var texth: Int
        val w = width
        val h = height
        val txt = getText()
        for (font in fonts) {
            g.font = font
            metrics = g.fontMetrics
            texth = metrics.height
            textw = metrics.stringWidth(txt)
            if (textw > w - 16) continue

            val x = (w - textw) / 2
            val y = (h - texth) / 2 + metrics.ascent

            g.drawString(txt, x, y)
            break
        }
    }

    private fun getText(): String {
        return if (override_text == null) text else override_text!!
    }

    private fun getCol(): Color {
        return if (override_col == null) col else override_col!!
    }

    private fun drawBorder(g: Graphics, border: Int, fill: Boolean) {
        val w = width
        val h = height
        if (fill) {
            g.fillRect(border, border, w - border * 2, h - border * 2)
        } else {
            g.drawRect(border, border, w - border * 2 - 1, h - border * 2 - 1)
        }
    }

    companion object {
        private fun mix(col: Color, mixWith: Color, mixFactor: Double): Color {
            val r = Math.round((col.red - (mixFactor * col.red)) + mixWith.red * mixFactor).toInt()
            val g = Math.round((col.green - (mixFactor * col.green)) + mixWith.green * mixFactor).toInt()
            val b = Math.round((col.blue - (mixFactor * col.blue)) + mixWith.blue * mixFactor).toInt()
            return Color(r, g, b)
        }
    }
}
