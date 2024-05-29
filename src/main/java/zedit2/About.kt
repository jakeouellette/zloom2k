package zedit2

import java.awt.Color
import java.awt.Dialog
import java.awt.event.*
import java.util.*
import javax.swing.JDialog
import javax.swing.JTextArea
import javax.swing.Timer

class About(editor: WorldEditor) {
    private val aboutBuilder: StringBuilder
    private val sx = DoubleArray(STARS)
    private val vx = DoubleArray(STARS)
    private val sy = DoubleArray(STARS)
    private val vy = DoubleArray(STARS)
    private val sc = CharArray(STARS)
    private val rng = Random()
    private val starChars = charArrayOf('.', 'z', '*', '+', 'z', 't', ',', '☼', '.', '.', '☺', '☻')
    private fun initStars() {
        for (i in 0 until STARS) {
            val x = rng.nextDouble() * 40.0
            val y = rng.nextDouble() * 13.0
            sx[i] = x
            sy[i] = y
            vx[i] = rng.nextDouble() * 0.56 + 0.14
            vy[i] = rng.nextDouble() * 0.32 + 0.08
            sc[i] = starChars[rng.nextInt(starChars.size)]
        }
    }

    init {
        val dialog = JDialog()
        dialog.isResizable = false
        dialog.modalityType = Dialog.ModalityType.APPLICATION_MODAL
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.isUndecorated = true
        val ta = JTextArea(13, 40)
        ta.isFocusable = false

        aboutBuilder = StringBuilder()
        initStars()
        val timer = getTimer(ta)
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                timer.stop()
            }
        })

        //ta.setText(about);
        dialog.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                dialog.dispose()
            }
        })
        ta.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dialog.dispose()
            }
        })

        ta.font = CP437.font
        ta.background = Color.BLACK
        ta.foreground = Color.GREEN
        //ta.setCaretColor(Color.WHITE);
        ta.isEditable = false
        dialog.add(ta)
        dialog.setIconImage(null)
        dialog.title = "About ZEdit2"

        dialog.pack()
        dialog.setLocationRelativeTo(editor.frameForRelativePositioning)
        dialog.isVisible = true
    }

    private fun getTimer(ta: JTextArea): Timer {
        val timer = Timer(10, object : ActionListener {
            private var t = 0.0f
            override fun actionPerformed(e: ActionEvent) {
                t = (t + 0.01f) % 3
                val r: Float
                val g: Float
                val b: Float
                if (t < 1.0f) {
                    val c = t
                    g = 1.0f - c
                    b = 1.0f
                    r = c
                } else if (t < 2.0f) {
                    val c = t - 1
                    r = 1.0f
                    b = 1.0f - c
                    g = c
                } else {
                    val c = t - 2
                    g = 1.0f
                    r = 1.0f - c
                    b = c
                }
                ta.foreground = Color(r, g, b)

                aboutBuilder.replace(0, aboutBuilder.length, about)
                val ver = "v" + Main.VERSION
                val verPos = 8 * 41 + 4
                aboutBuilder.replace(verPos, verPos + ver.length, ver)
                for (i in 0 until STARS) {
                    sx[i] = (sx[i] + vx[i])
                    sy[i] = (sy[i] + vy[i])
                    if (sx[i] >= 40 || sy[i] >= 13) {
                        sx[i] %= 40.0
                        sy[i] %= 13.0
                        vx[i] = rng.nextDouble() * 0.56 + 0.14
                        vy[i] = rng.nextDouble() * 0.32 + 0.08
                        sc[i] = starChars[rng.nextInt(starChars.size)]
                    }
                    val pos = sx[i].toInt() + sy[i].toInt() * 41
                    if (aboutBuilder[pos] == ' ') {
                        aboutBuilder.setCharAt(pos, sc[i])
                    }
                }

                ta.text = aboutBuilder.toString()
            }
        })
        timer.isRepeats = true
        timer.start()
        return timer
    }

    companion object {
        private const val STARS = 30
        private const val about = "                                        \n" +
                "                                        \n" +
                "         █                       ▄▀▀▄   \n" +
                "    █▀▀█ █     ▄▀▀▄ ▄▀▀▄ ▄▀▀▄▀▀▄ ▀  █   \n" +
                "      ▄▀ █     █  █ █  █ █  █  █    █   \n" +
                "     ▄▀  █     █  █ █  █ █  █  █ ▄▀▀    \n" +
                "    ▄▀   █     █  █ █  █ █  █  █ █      \n" +
                "    █▄▄█ █▄▄▄▄ ▀▄▄▀ ▀▄▄▀ █  █  █ █▄▄█   \n" +
                "                                        \n" +
                "          Hacked from 'Zedit2' by:      \n" +
                "   (c) Mahou Shoujo ☼ Magical Moestar   \n" +
                "                                        \n" +
                "                                        "
    }
}
