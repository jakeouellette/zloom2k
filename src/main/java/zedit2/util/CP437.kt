package zedit2.util

import zedit2.components.Main
import com.formdev.flatlaf.util.UIScale
import java.awt.Font
import java.awt.FontFormatException
import java.awt.GraphicsEnvironment
import java.io.IOException

object CP437 {
    private const val unicodeString =
        "\u0000☺☻♥♦♣♠•◘○◙♂♀♪♫☼►◄↕‼¶§▬↨↑↓→←∟↔▲▼ !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~⌂ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°\u2219\u00B7√ⁿ²■\u00A0"
    var font: Font
    private var reverse: HashMap<Char, Char>? = null

    init {
        try {
            //Main.class.getClassLoader().getResource
            //new File("Px437_IBM_EGA8.ttf")
            val u = Main::class.java.classLoader.getResourceAsStream("Px437_IBM_EGA8.ttf")
            font = Font.createFont(Font.TRUETYPE_FONT, u).deriveFont(UIScale.scale(16f))
        } catch (e: FontFormatException) {
            e.printStackTrace()
            font = Font(Font.MONOSPACED, Font.PLAIN, UIScale.scale(11))
        } catch (e: IOException) {
            e.printStackTrace()
            font = Font(Font.MONOSPACED, Font.PLAIN, UIScale.scale(11))
        }
    }
    private fun buildReverseTable() {
        if (reverse == null) {
            reverse = HashMap()
            for (i in 0..255) {
                val dos = i.toChar()
                val uni = unicodeString[i]
                reverse!![uni] = dos
            }
        }
    }

    @JvmStatic
    fun registerFont() {
        val font = font
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
    }

    @JvmOverloads
    fun toUnicode(input: ByteArray, drawCr: Boolean = true): String {
        buildReverseTable()

        val output = StringBuilder()
        for (b in input) {
            val c = b.toInt() and 0xFF
            if (c == 13 && !drawCr) {
                output.append('\n')
            } else {
                output.append(unicodeString[c])
            }
        }
        return output.toString()
    }

    @JvmStatic
    @JvmOverloads
    fun toBytes(unicode: String, drawCr: Boolean = true): ByteArray {
        buildReverseTable()
        val output = ByteArray(unicode.length)
        for (i in output.indices) {
            var c = unicode[i].code
            if (c == '\n'.code && !drawCr) {
                c = '\r'.code
            } else if (reverse!!.containsKey(c.toChar())) {
                c = reverse!![c.toChar()]!!.code
            }
            if (c < 256) {
                output[i] = c.toByte()
            } else {
                output[i] = '?'.code.toByte()
            }
        }
        return output
    }
}
