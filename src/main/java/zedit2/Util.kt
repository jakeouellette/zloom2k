package zedit2

import zedit2.CP437.toUnicode
import zedit2.ColourSelector.Companion.createColourSelector
import java.awt.Component
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.net.URISyntaxException
import java.nio.file.Path
import java.util.*
import java.util.function.BooleanSupplier
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.text.BadLocationException
import javax.swing.text.JTextComponent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object Util {
    // Unused
    // int charset[] = new int[] {0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006, 0x0007, 0x0008, 0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x000E, 0x000F, 0x0010, 0x0011, 0x0012, 0x0013, 0x0014, 0x0015, 0x0016, 0x0017, 0x0018, 0x0019, 0x001A, 0x001B, 0x001C, 0x001D, 0x001E, 0x001F, 0x0020, 0x0021, 0x0022, 0x0023, 0x0024, 0x0025, 0x0026, 0x0027, 0x0028, 0x0029, 0x002A, 0x002B, 0x002C, 0x002D, 0x002E, 0x002F, 0x0030, 0x0031, 0x0032, 0x0033, 0x0034, 0x0035, 0x0036, 0x0037, 0x0038, 0x0039, 0x003A, 0x003B, 0x003C, 0x003D, 0x003E, 0x003F, 0x0040, 0x0041, 0x0042, 0x0043, 0x0044, 0x0045, 0x0046, 0x0047, 0x0048, 0x0049, 0x004A, 0x004B, 0x004C, 0x004D, 0x004E, 0x004F, 0x0050, 0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057, 0x0058, 0x0059, 0x005A, 0x005B, 0x005C, 0x005D, 0x005E, 0x005F, 0x0060, 0x0061, 0x0062, 0x0063, 0x0064, 0x0065, 0x0066, 0x0067, 0x0068, 0x0069, 0x006A, 0x006B, 0x006C, 0x006D, 0x006E, 0x006F, 0x0070, 0x0071, 0x0072, 0x0073, 0x0074, 0x0075, 0x0076, 0x0077, 0x0078, 0x0079, 0x007A, 0x007B, 0x007C, 0x007D, 0x007E, 0x007F, 0x00C7, 0x00FC, 0x00E9, 0x00E2, 0x00E4, 0x00E0, 0x00E5, 0x00E7, 0x00EA, 0x00EB, 0x00E8, 0x00EF, 0x00EE, 0x00EC, 0x00C4, 0x00C5, 0x00C9, 0x00E6, 0x00C6, 0x00F4, 0x00F6, 0x00F2, 0x00FB, 0x00F9, 0x00FF, 0x00D6, 0x00DC, 0x00A2, 0x00A3, 0x00A5, 0x20A7, 0x0192, 0x00E1, 0x00ED, 0x00F3, 0x00FA, 0x00F1, 0x00D1, 0x00AA, 0x00BA, 0x00BF, 0x2310, 0x00AC, 0x00BD, 0x00BC, 0x00A1, 0x00AB, 0x00BB, 0x2591, 0x2592, 0x2593, 0x2502, 0x2524, 0x2561, 0x2562, 0x2556, 0x2555, 0x2563, 0x2551, 0x2557, 0x255D, 0x255C, 0x255B, 0x2510, 0x2514, 0x2534, 0x252C, 0x251C, 0x2500, 0x253C, 0x255E, 0x255F, 0x255A, 0x2554, 0x2569, 0x2566, 0x2560, 0x2550, 0x256C, 0x2567, 0x2568, 0x2564, 0x2565, 0x2559, 0x2558, 0x2552, 0x2553, 0x256B, 0x256A, 0x2518, 0x250C, 0x2588, 0x2584, 0x258C, 0x2590, 0x2580, 0x03B1, 0x00DF, 0x0393, 0x03C0, 0x03A3, 0x03C3, 0x00B5, 0x03C4, 0x03A6, 0x0398, 0x03A9, 0x03B4, 0x221E, 0x03C6, 0x03B5, 0x2229, 0x2261, 0x00B1, 0x2265, 0x2264, 0x2320, 0x2321, 0x00F7, 0x2248, 0x00B0, 0x2219, 0x00B7, 0x221A, 0x207F, 0x00B2, 0x25A0, 0x00A0};
    /*
 ☺☻♥♦♣♠•◘○◙♂♀♪♫☼►◄↕‼¶§▬↨↑↓→←∟↔▲▼
 !"#$%&'()*+,-./0123456789:;<=>?
@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_
`abcdefghijklmnopqrstuvwxyz{|}~⌂
ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒ
áíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐
└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀
αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙⋅√ⁿ²■ 
    */
    @JvmStatic
    fun readPascalString(inputLen: Byte, bytes: ByteArray?, from: Int, to: Int): ByteArray {
        var len = toUInt8(inputLen)
        if (to < from) throw RuntimeException("Invalid range")

        val stringLength = to - from
        if (len > stringLength) len = stringLength

        return Arrays.copyOfRange(bytes, from, from + len)
    }

    @JvmStatic
    fun writePascalString(str: ByteArray, bytes: ByteArray, lenPos: Int, from: Int, to: Int) {
        if (to < from) throw RuntimeException("Invalid range")

        val bufferLength = to - from

        val len = min(bufferLength.toDouble(), str.size.toDouble()).toInt()
        if (len > 255) throw RuntimeException("Range too large for Pascal string")

        System.arraycopy(str, 0, bytes, from, len)
        if (bufferLength > len) {
            Arrays.fill(bytes, from + len, to, 0.toByte())
        }
        bytes[lenPos] = len.toByte()
    }

    fun toUInt8(input: Byte): Int {
        return input.toInt() and 0xFF
    }

    @JvmStatic
    fun getInt16(bytes: ByteArray, offset: Int): Int {
        var value = toUInt8(bytes[offset])
        value = value or (toUInt8(bytes[offset + 1]) shl 8)
        if (value > 32767) {
            value = (-65536 + value).toShort().toInt()
        }
        return value
    }

    @JvmStatic
    fun setInt16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value.toShort().toInt() and 0xFF00) shr 8).toByte()
    }

    @JvmStatic
    fun getInt8(bytes: ByteArray, offset: Int): Int {
        return toUInt8(bytes[offset])
    }

    @JvmStatic
    fun setInt8(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
    }

    @JvmStatic
    fun clamp(`val`: Int, min: Int, max: Int): Int {
        return max(min(`val`.toDouble(), max.toDouble()), min.toDouble()).toInt()
    }

    @JvmStatic
    fun clamp(`val`: Double, min: Double, max: Double): Double {
        return max(min(`val`, max), min)
    }

    fun setInt32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset + 0] = ((value and 0x000000FF) shr 0).toByte()
        bytes[offset + 1] = ((value and 0x0000FF00) shr 8).toByte()
        bytes[offset + 2] = ((value and 0x00FF0000) shr 16).toByte()
        bytes[offset + 3] = ((value and -0x1000000) shr 24).toByte()
    }

    fun getInt32(bytes: ByteArray, offset: Int): Int {
        var `val` = toUInt8(bytes[offset])
        `val` = `val` or (toUInt8(bytes[offset + 1]) shl 8)
        `val` = `val` or (toUInt8(bytes[offset + 2]) shl 16)
        `val` = `val` or (toUInt8(bytes[offset + 3]) shl 24)
        return `val`
    }

    @JvmStatic
    fun getKeyStroke(ge: GlobalEditor, actionName: String): KeyStroke {
        return KeyStroke.getKeyStroke(ge.getString("K_$actionName"))
    }

    @JvmStatic
    fun addKeybind(ge: GlobalEditor, receiver: KeyActionReceiver, component: JComponent, actionName: String) {
        if (actionName.isBlank()) return
        val keyStroke = getKeyStroke(ge, actionName) ?: return
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, actionName)
        if ((keyStroke.modifiers or InputEvent.ALT_DOWN_MASK) != 0) {
            val altgrMod = keyStroke.modifiers or InputEvent.ALT_GRAPH_DOWN_MASK
            val altgrKeyStroke = KeyStroke.getKeyStroke(keyStroke.keyCode, altgrMod)
            component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(altgrKeyStroke, actionName)
        }
        component.actionMap.put(actionName, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                receiver.keyAction(actionName, e)
            }
        })
    }

    @JvmStatic
    fun getUInt16(bytes: ByteArray, offset: Int): Int {
        return getInt16(bytes, offset) and 0xFFFF
    }

    val jarPath: File
        get() = try {
            File(Util::class.java.protectionDomain.codeSource.location.toURI()).parentFile
        } catch (e: URISyntaxException) {
            File(".")
        }


    @JvmStatic
    fun getExtensionless(path: String?): String {
        val p = Path.of(path)
        val baseName = p.fileName.toString()
        val extPos = baseName.lastIndexOf('.')
        if (extPos == -1) return baseName
        return baseName.substring(0, extPos)
    }

    @JvmStatic
    fun keyStrokeString(keyStroke: KeyStroke?): String? {
        if (keyStroke == null) return null
        val mods = keyStroke.modifiers
        val shift = (mods and InputEvent.SHIFT_DOWN_MASK) != 0
        val ctrl = (mods and InputEvent.CTRL_DOWN_MASK) != 0
        val alt = (mods and InputEvent.ALT_DOWN_MASK) != 0

        val keyName = KeyStroke.getKeyStroke(keyStroke.keyCode, 0).toString().replace("pressed ", "")
        return String.format(
            "%s%s%s%s",
            if (shift) "Shift+" else "",
            if (ctrl) "Ctrl+" else "",
            if (alt) "Alt+" else "",
            keyName
        )
    }

    fun setKeyStroke(ge: GlobalEditor, actionName: String, keyStroke: KeyStroke?) {
        var keyStrokeName = ""
        if (keyStroke != null) {
            keyStrokeName = keyStroke.toString()
        }
        ge.setString("K_$actionName", keyStrokeName)
    }

    fun keyMatches(e: KeyEvent, ks: KeyStroke): Boolean {
        if (e.keyCode == ks.keyCode) {
            if ((e.modifiersEx and InputEvent.CTRL_DOWN_MASK) == (ks.modifiers and InputEvent.CTRL_DOWN_MASK)) if ((e.modifiersEx and InputEvent.ALT_DOWN_MASK) == (ks.modifiers and InputEvent.ALT_DOWN_MASK)) return (e.modifiersEx and InputEvent.SHIFT_DOWN_MASK) == (ks.modifiers and InputEvent.SHIFT_DOWN_MASK)
        }
        return false
    }

    @JvmStatic
    fun pair(x: Int, y: Int): ArrayList<Int> {
        val ar = ArrayList<Int>(2)
        ar.add(x)
        ar.add(y)
        return ar
    }

    fun addEscClose(window: Window, inputMapComponent: JComponent) {
        addKeyClose(window, inputMapComponent, KeyEvent.VK_ESCAPE, 0)
    }

    fun addPromptedEscClose(window: Window, inputMapComponent: JComponent, confirm: BooleanSupplier) {
        addKeyClose(window, inputMapComponent, KeyEvent.VK_ESCAPE, 0, confirm)
    }

    @JvmOverloads
    fun addKeyClose(
        window: Window,
        inputMapComponent: JComponent,
        keyCode: Int,
        modifiers: Int,
        confirm: BooleanSupplier = BooleanSupplier { true }
    ) {
        val keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers)
        val act = "key" + keyStroke.hashCode()
        inputMapComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, act)
        inputMapComponent.actionMap.put(act, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (confirm.asBoolean) {
                    window.dispose()
                }
            }
        })
    }

    fun charInsert(worldEditor: WorldEditor, editor: JTextComponent, relativeTo: Component?, owner: Any?) {
        val ge = worldEditor.globalEditor
        val listener = ActionListener { e: ActionEvent ->
            val pos = editor.caretPosition
            var insertNumber = false
            var insertSpace = false
            try {
                for (checkLen in 5..6) {
                    if (pos >= checkLen && editor.document.getText(pos - checkLen, checkLen)
                            .lowercase(Locale.getDefault()).trim { it <= ' ' } == "#char"
                    ) {
                        insertNumber = true
                    }
                    if (editor.document.getText(pos - 1, 1) != " ") {
                        insertSpace = true
                    }
                }
            } catch (ignored: BadLocationException) {
            }

            val c = e.actionCommand.toInt()
            ge.f3char = c
            var str = ""
            if (insertNumber) {
                if (insertSpace) str = " "
                str += c.toString()
            } else {
                if (c == 13) {
                    str = "\n"
                } else {
                    val bytes = byteArrayOf(c.toByte())
                    str = toUnicode(bytes)
                }
            }
            try {
                editor.document.insertString(pos, str, null)
            } catch (ignored: BadLocationException) {
            }
        }
        createColourSelector(worldEditor, ge.f3char, relativeTo, owner, listener, ColourSelector.CHAR)
    }

    fun rgbToLab(R: Int, G: Int, B: Int): DoubleArray {
        // https://stackoverflow.com/a/45263428
        var r: Double
        var g: Double
        var b: Double
        var xr: Double
        var yr: Double
        var zr: Double

        // D65/2°
        val Xr = 95.047
        val Yr = 100.0
        val Zr = 108.883

        // --------- RGB to XYZ ---------//
        r = R / 255.0
        g = G / 255.0
        b = B / 255.0

        if (r > 0.04045) r = ((r + 0.055) / 1.055).pow(2.4)
        else r = r / 12.92

        if (g > 0.04045) g = ((g + 0.055) / 1.055).pow(2.4)
        else g = g / 12.92

        if (b > 0.04045) b = ((b + 0.055) / 1.055).pow(2.4)
        else b = b / 12.92

        r *= 100.0
        g *= 100.0
        b *= 100.0

        val X = 0.4124 * r + 0.3576 * g + 0.1805 * b
        val Y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val Z = 0.0193 * r + 0.1192 * g + 0.9505 * b


        // --------- XYZ to Lab --------- //
        xr = X / Xr
        yr = Y / Yr
        zr = Z / Zr

        xr = if (xr > 0.008856) (xr.pow(1 / 3.0).toFloat()).toDouble()
        else ((7.787 * xr) + 16 / 116.0).toFloat().toDouble()

        yr = if (yr > 0.008856) (yr.pow(1 / 3.0).toFloat()).toDouble()
        else ((7.787 * yr) + 16 / 116.0).toFloat().toDouble()

        zr = if (zr > 0.008856) (zr.pow(1 / 3.0).toFloat()).toDouble()
        else ((7.787 * zr) + 16 / 116.0).toFloat().toDouble()


        val lab = DoubleArray(3)

        lab[0] = (116 * yr) - 16
        lab[1] = 500 * (xr - yr)
        lab[2] = 200 * (yr - zr)

        return lab
    }

    @JvmStatic
    fun evalConfigDir(string: String): String {
        var string = string
        while (string.contains("<<") && string.contains(">>")) {
            val paramStart = string.indexOf("<<")
            val paramEnd = string.indexOf(">>")
            if (paramStart == -1 || paramEnd == -1 || paramEnd < paramStart) break
            val param = string.substring(paramStart + 2, paramEnd)
            val before = string.substring(0, paramStart)
            val after = string.substring(paramEnd + 2)
            val replaceWith = System.getProperty(param)
            if (replaceWith != null) {
                string = before + replaceWith + after
            }
        }
        return string
    }
}
