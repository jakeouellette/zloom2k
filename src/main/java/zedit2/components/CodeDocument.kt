package zedit2.components

import zedit2.util.Audio.Companion.getPlayDuration
import zedit2.util.CP437
import java.awt.Color
import java.util.*
import javax.swing.text.*
import kotlin.math.max

class CodeDocument(editor: WorldEditor) : DefaultStyledDocument() {
    private val highlightEnabled: Boolean
    private val basic: SimpleAttributeSet
    private val hlObjectName: SimpleAttributeSet
    private val hlNoop: SimpleAttributeSet
    private val hlDollar: SimpleAttributeSet
    private val hlExclamation: SimpleAttributeSet
    private val hlText: SimpleAttributeSet
    private val hlCenteredText: SimpleAttributeSet
    private val hlTextWarn: SimpleAttributeSet
    private val hlTextWarnLight: SimpleAttributeSet
    private val hlError: SimpleAttributeSet
    private val hlGotoLabel: SimpleAttributeSet
    private val hlColon: SimpleAttributeSet
    private val hlLabel: SimpleAttributeSet
    private val hlZapped: SimpleAttributeSet
    private val hlZappedLabel: SimpleAttributeSet
    private val hlSlash: SimpleAttributeSet
    private val hlDirection: SimpleAttributeSet
    private val hlHash: SimpleAttributeSet
    private val hlCommand: SimpleAttributeSet
    private val hlThing: SimpleAttributeSet
    private val hlNumber: SimpleAttributeSet
    private val hlCounter: SimpleAttributeSet
    private val hlFlag: SimpleAttributeSet
    private val hlCondition: SimpleAttributeSet
    private val hlMusicOctave: SimpleAttributeSet
    private val hlMusicNote: SimpleAttributeSet
    private val hlMusicTiming: SimpleAttributeSet
    private val hlMusicTimingMod: SimpleAttributeSet
    private val hlMusicDrum: SimpleAttributeSet
    private val hlMusicRest: SimpleAttributeSet
    private val hlMusicSharpFlat: SimpleAttributeSet
    private val hlObjectLabelSeparator: SimpleAttributeSet
    private val hlBlue: SimpleAttributeSet
    private val hlGreen: SimpleAttributeSet
    private val hlCyan: SimpleAttributeSet
    private val hlRed: SimpleAttributeSet
    private val hlPurple: SimpleAttributeSet
    private val hlYellow: SimpleAttributeSet
    private val hlWhite: SimpleAttributeSet

    private var warnings: Array<String?>

    private val directions = HashMap<String, Boolean>()
    private val commands = HashSet<String>()
    private val globalEditor: GlobalEditor
    private val szzt: Boolean

    private fun colAttrib(propertyName: String): SimpleAttributeSet {
        val attrib = SimpleAttributeSet()
        val col = Color(GlobalEditor.getIntRadix(propertyName, "FFFFFF", 16))

        StyleConstants.setForeground(attrib, col)
        return attrib
    }

    init {
        val worldData = editor.worldData
        globalEditor = editor.globalEditor
        szzt = worldData.isSuperZZT
        warnings = arrayOf()
        basic = SimpleAttributeSet()
        val font = CP437.font
        StyleConstants.setForeground(basic, Color.WHITE)
        StyleConstants.setFontSize(basic, font.size)
        StyleConstants.setFontFamily(basic, font.family)

        highlightEnabled = GlobalEditor.getBoolean("SYNTAX_HIGHLIGHTING", true)

        hlCenteredText = colAttrib("HL_CENTEREDTEXT")
        hlColon = colAttrib("HL_COLON")
        hlCommand = colAttrib("HL_COMMAND")
        hlCondition = colAttrib("HL_CONDITION")
        hlCounter = colAttrib("HL_COUNTER")
        hlDirection = colAttrib("HL_DIRECTION")
        hlDollar = colAttrib("HL_DOLLAR")
        hlError = colAttrib("HL_ERROR")
        hlExclamation = colAttrib("HL_EXCLAMATION")
        hlFlag = colAttrib("HL_FLAG")
        hlGotoLabel = colAttrib("HL_GOTOLABEL")
        hlHash = colAttrib("HL_HASH")
        hlLabel = colAttrib("HL_LABEL")
        hlMusicDrum = colAttrib("HL_MUSICDRUM")
        hlMusicNote = colAttrib("HL_MUSICNOTE")
        hlMusicOctave = colAttrib("HL_MUSICOCTAVE")
        hlMusicRest = colAttrib("HL_MUSICREST")
        hlMusicSharpFlat = colAttrib("HL_MUSICSHARPFLAT")
        hlMusicTiming = colAttrib("HL_MUSICTIMING")
        hlMusicTimingMod = colAttrib("HL_MUSICTIMINGMOD")
        hlNoop = colAttrib("HL_NOOP")
        hlNumber = colAttrib("HL_NUMBER")
        hlObjectLabelSeparator = colAttrib("HL_OBJECTLABELSEPARATOR")
        hlObjectName = colAttrib("HL_OBJECTNAME")
        hlSlash = colAttrib("HL_SLASH")
        hlText = colAttrib("HL_TEXT")
        hlTextWarn = colAttrib("HL_TEXTWARN")
        hlTextWarnLight = colAttrib("HL_TEXTWARNLIGHT")
        hlThing = colAttrib("HL_THING")
        hlZapped = colAttrib("HL_ZAPPED")
        hlZappedLabel = colAttrib("HL_ZAPPEDLABEL")
        hlBlue = colAttrib("HL_BLUE")
        hlGreen = colAttrib("HL_GREEN")
        hlCyan = colAttrib("HL_CYAN")
        hlRed = colAttrib("HL_RED")
        hlPurple = colAttrib("HL_PURPLE")
        hlYellow = colAttrib("HL_YELLOW")
        hlWhite = colAttrib("HL_WHITE")

        StyleConstants.setItalic(hlNoop, true)
        StyleConstants.setItalic(hlTextWarn, true)
        StyleConstants.setItalic(hlError, true)

        for (dir in PURE_DIRS) directions[dir] = true
        for (dir in MOD_DIRS) directions[dir] = false
        commands.addAll(Arrays.asList(*COMMANDS))
    }

    fun reHighlight(insert: Boolean, len: Int, offset: Int) {
        val newLen = warnings.size + (if (insert) len else -len)
        if (newLen != length) {
            // This can easily happen if multiple things change at once
            // Discard and rehighlight everything
            reHighlight()
            return
        }

        val warningsCopy = arrayOfNulls<String>(newLen)
        val offsetStart: Int
        val offsetEnd: Int
        if (insert) {
            System.arraycopy(warnings, 0, warningsCopy, 0, offset)
            System.arraycopy(warnings, offset, warningsCopy, offset + len, warnings.size - offset)
            offsetStart = offset
            offsetEnd = offset + len
        } else {
            System.arraycopy(warnings, 0, warningsCopy, 0, offset)
            System.arraycopy(warnings, offset + len, warningsCopy, offset, warningsCopy.size - offset)
            offsetStart = offset
            offsetEnd = offset + 1
        }
        warnings = warningsCopy
        continueHighlight(offsetStart, offsetEnd)
    }

    fun reHighlight() {
        val len = length
        warnings = arrayOfNulls(len)
        continueHighlight(0, len - 1)
    }

    fun continueHighlight(offsetStart: Int, offsetEnd: Int) {
        val len = length
        val text: String
        try {
            text = getText(0, len)
        } catch (e: BadLocationException) {
            return
        }
        var start = findLineBeginning(text, offsetStart)
        val end = findLineEnding(text, offsetEnd)
        setCharacterAttributes(start, end - start, basic, true)
        for (pos in start until end) {
            if (text[pos] == '\n') {
                highlight(text, start, pos, false)
                start = pos + 1
            }
        }
        highlight(text, start, end, false)
    }

    fun findLineBeginning(text: String, pos: Int): Int {
        var pos = pos
        while (true) {
            if (pos <= 0) return 0
            if (text[pos - 1] == '\n') return pos
            pos--
        }
    }

    fun findLineEnding(text: String, pos: Int): Int {
        var pos = pos
        if (pos < 0) pos = 0
        while (true) {
            if (pos >= text.length) return text.length
            if (text[pos] == '\n') return pos
            pos++
        }
    }

    private fun highlight(text: String, start: Int, end: Int, assumeCommand: Boolean) {
        var start = start
        if (start == -1) return
        if (!highlightEnabled) return
        val prevStart = start
        while (true) {
            if (start == end) return
            if (text[start] == ' ') start++
            else break
        }
        val firstChar = text[start].uppercaseChar()
        if (firstChar == '@') {
            if (start == 0) {
                setCharacterAttributes(start, end - start, hlObjectName, false)
            } else {
                setCharacterAttributes(start, end - start, hlNoop, false)
                setWarning(start, end - start, NAME_NOT_FIRST)
            }
            return
        }
        if (firstChar == '\'') {
            highlightLabel(text, start, end, true)
            return
        }
        if (firstChar == ':') {
            highlightLabel(text, start, end, false)
            return
        }
        if (firstChar == '#') {
            highlightCommand(text, start, end, !assumeCommand)
            return
        }
        if (firstChar == '/' || firstChar == '?') {
            handleDirection(text, start, start, end, true)
            return
        }
        if (assumeCommand) {
            val p = scanIn(text, start, end)
            if (p.isEmpty()) {
                highlightText(text, start, end)
            } else {
                highlightCommand(text, start, end, false)
            }
            return
        }
        if (!assumeCommand) start = prevStart
        highlightText(text, start, end)
    }

    private fun highlightText(text: String, start: Int, end: Int) {
        // Max length of a message bar line: 50 characters
        // Max sensible length of object name: 45 characters
        // Max length of a textbox line: 43 characters
        // Max length of a $ textbox line: 45 characters
        // Max length of a ! textbox line: 38

        val firstChar = text[start]
        if (firstChar == '$') {
            setCharacterAttributes(start, 1, hlDollar, false)
            highlightTextLine(start + 1, end, 42, 49, hlCenteredText, hlTextWarnLight, hlTextWarn)
            return
        }
        if (firstChar == '!') {
            var semicolon_pos = text.indexOf(';', start + 1)
            if (semicolon_pos == -1 || semicolon_pos >= end) {
                setCharacterAttributes(start, 1, hlExclamation, false)
                highlightTextLine(start + 1, end, 38, 49, hlText, hlTextWarnLight, hlTextWarn)
            } else {
                semicolon_pos -= start + 1
                val el = getCharacterElement(start)
                setCharacterAttributes(start, 1, hlExclamation, false)
                setCharacterAttributes(start + 1, semicolon_pos, hlGotoLabel, false)
                setCharacterAttributes(start + 1 + semicolon_pos, 1, hlExclamation, false)
                highlightTextLine(
                    start + 1 + semicolon_pos + 1,
                    end,
                    38,
                    48 - semicolon_pos,
                    hlText,
                    hlTextWarnLight,
                    hlTextWarn
                )
            }
            return
        }
        highlightTextLine(start, end, 42, 50, hlText, hlTextWarnLight, hlTextWarn)
    }

    private fun highlightTextLine(
        start: Int,
        end: Int,
        warn1: Int,
        warn2: Int,
        hlNormal: SimpleAttributeSet,
        hlWarn1: SimpleAttributeSet,
        hlWarn2: SimpleAttributeSet
    ) {
        var warn1 = warn1
        var warn2 = warn2
        if (szzt) {
            // Super ZZT actually has the message box length longer than the message bar length
            // So modify then swap these
            warn1 -= (42 - 29)
            warn2 -= (50 - 26)

            val t = warn1
            warn1 = warn2
            warn2 = t
        }

        var len = end - start
        var lenWarn1 = max(0.0, (len - warn1).toDouble()).toInt()
        val lenWarn2 = max(0.0, (len - warn2).toDouble()).toInt()
        len -= lenWarn1
        lenWarn1 -= lenWarn2

        setCharacterAttributes(start, len, hlNormal, false)
        setCharacterAttributes(start + len, lenWarn1, hlWarn1, false)
        setCharacterAttributes(start + len + lenWarn1, lenWarn2, hlWarn2, false)

        if (!szzt) {
            setWarning(start + len, lenWarn1, TEXT_WARNING_1)
            setWarning(start + len + lenWarn1, lenWarn2, TEXT_WARNING_2)
        } else {
            setWarning(start + len, lenWarn1, TEXT_WARNING_1_SZZT)
            setWarning(start + len + lenWarn1, lenWarn2, TEXT_WARNING_2_SZZT)
        }
    }

    private fun setWarning(start: Int, len: Int, warning: String) {
        for (i in 0 until len) {
            warnings[i + start] = warning
        }
    }

    private fun highlightLabel(text: String, start: Int, end: Int, zapped: Boolean) {
        if (start == 0 && !zapped) {
            setCharacterAttributes(start, end, hlTextWarn, false)
            setWarning(start, end, LABEL_IS_FIRST)
        } else {
            var warn = false
            if (!zapped) {
                for (i in start until end) {
                    val c = text[i]
                    if (c >= '0' && c <= '9') {
                        warn = true
                        break
                    }
                }
            }
            setCharacterAttributes(start, 1, if (zapped) hlZapped else hlColon, false)
            setCharacterAttributes(start + 1, end - start - 1, if (zapped) hlZappedLabel else hlLabel, false)
            if (warn) {
                setCharacterAttributes(start, end - start, hlTextWarn, false)
                setWarning(start, end - start, LABEL_CONTAINS_NUMBER)
            }
        }
    }

    private fun handleDirection(text: String, lineStart: Int, start: Int, end: Int, moveCharacter: Boolean): Int {
        var pos = start
        if (moveCharacter) {
            setCharacterAttributes(start, 1, hlSlash, false)
            pos++
        }
        // Scan in movement command. Bring in a-z 0-9 : and _
        pos = skipSpaces(text, pos, end)
        val prevPos = pos
        var mov = scanIn(text, pos, end)
        var isPureDir = directions[mov]
        pos += mov.length
        if (isPureDir == null) {
            // Not a direction
            setCharacterAttributes(lineStart, pos - lineStart, hlError, false)
            setWarning(lineStart, pos - lineStart, INVALID_DIR)
        } else {
            setCharacterAttributes(prevPos, pos - prevPos, hlDirection, false)

            while (!isPureDir!!) {
                pos = skipSpaces(text, pos, end)
                mov = scanIn(text, pos, end)
                val endPos = pos + mov.length
                isPureDir = directions[mov]
                if (isPureDir == null) {
                    setCharacterAttributes(start, endPos - start, hlError, false)
                    setWarning(start, endPos - start, INVALID_DIR)
                    pos = endPos
                    break
                } else {
                    setCharacterAttributes(pos, endPos - pos, hlDirection, false)
                    pos = endPos
                }
            }
        }

        pos = skipSpaces(text, pos, end)

        if (moveCharacter) {
            // Single-character movement optionally takes a number in Super ZZT.
            if (szzt) {
                val nextp = scanIn(text, pos, end)
                val value = parseNumber(nextp, 0, 255)
                if (value != null) {
                    setCharacterAttributes(pos, nextp.length, hlNumber, false)
                    pos += nextp.length
                    pos = skipSpaces(text, pos, end)
                }
                if (text[start] == '?') {
                    setCharacterAttributes(start, pos - start, hlTextWarn, false)
                    setWarning(start, pos - start, "Warning: ? has unusual behaviour in Super ZZT")
                }
            }

            highlight(text, pos, end, false)
            return -1
        }
        return pos
    }

    private fun scanIn(text: String, start: Int, end: Int): String {
        var start = start
        val scan = StringBuilder()
        var consumeSpaces = true
        while (true) {
            if (start >= end) return scan.toString()
            val c = text[start].uppercaseChar()
            if (c == ' ' && consumeSpaces) {
                start++
                continue
            }

            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (c == ':') || (c == '_')) {
                consumeSpaces = false
                scan.append(c)
            } else {
                return scan.toString()
            }
            start++
        }
    }

    private fun parseColour(text: String): SimpleAttributeSet? {
        return when (text) {
            "BLUE" -> hlBlue
            "GREEN" -> hlGreen
            "CYAN" -> hlCyan
            "RED" -> hlRed
            "PURPLE" -> hlPurple
            "YELLOW" -> hlYellow
            "WHITE" -> hlWhite
            else -> null
        }
    }

    private fun isType(text: String): Boolean {
        return when (text) {
            "AMMO", "BEAR", "BLINKWALL", "BOMB", "BOULDER", "BULLET", "BREAKABLE", "CLOCKWISE", "COUNTER", "DOOR", "DUPLICATOR", "EMPTY", "ENERGIZER", "FAKE", "FOREST", "GEM", "HEAD", "INVISIBLE", "KEY", "LINE", "LION", "MONITOR", "NORMAL", "OBJECT", "PASSAGE", "PLAYER", "PUSHER", "RICOCHET", "RUFFIAN", "SCROLL", "SEGMENT", "SLIDEREW", "SLIDERNS", "SLIME", "SOLID", "SPINNINGGUN", "STAR", "TIGER", "TRANSPORTER" -> true
            "SHARK", "TORCH", "WATER" -> !szzt
            "LAVA", "FLOOR", "WATERN", "WATERS", "WATERE", "WATERW", "ROTON", "DRAGONPUP", "PAIRER", "SPIDER", "WEB", "STONE" -> szzt
            else -> false
        }
    }

    private fun highlightCommand(text: String, start: Int, end: Int, leadingHash: Boolean) {
        var pos = start
        if (leadingHash) {
            setCharacterAttributes(start, 1, hlHash, false)
            pos++
            while (pos < end) {
                if (text[pos] == ' ') {
                    pos++
                } else {
                    break
                }
            }
        }
        while (pos < end) {
            if (text[pos] == '#') {
                setCharacterAttributes(pos, 1, hlNoop, false)
                setWarning(pos, 1, "Unnecessary #")
                pos++
            } else {
                break
            }
        }
        val cmd = scanIn(text, pos, end)

        if (commands.contains(cmd)) {
            // This is a valid command. Let's see what params it takes
            setCharacterAttributes(pos, cmd.length, hlCommand, false)
            val beforePos = pos
            pos += cmd.length

            when (cmd) {
                "BECOME" -> {
                    pos = handleColourThing(text, start, pos, end, false)
                    pos = ignoreExtra(text, pos, end)
                }

                "BIND" -> {
                    pos = skipSpaces(text, pos, end)

                    val p = scanIn(text, pos, end)
                    if (p.isEmpty()) {
                        setCharacterAttributes(start, end - start, hlNoop, false)
                        setWarning(start, end - start, "Missing bind target")
                    } else {
                        setCharacterAttributes(pos, p.length, hlObjectName, false)
                    }
                    pos += p.length
                    pos = ignoreExtra(text, pos, end)
                }

                "CHANGE" -> {
                    pos = handleColourThing(text, start, pos, end, true)
                    pos = handleColourThing(text, start, pos, end, true)
                    pos = ignoreExtra(text, pos, end)
                }

                "CHAR" -> {
                    pos = skipSpaces(text, pos, end)
                    val p = scanIn(text, pos, end)
                    val charVal = parseNumber(p, 1, 255)

                    if (charVal == null) {
                        setCharacterAttributes(start, end - start, hlNoop, false)
                        setWarning(start, end - start, "Missing/invalid char value")
                    } else {
                        setCharacterAttributes(pos, p.length, hlNumber, false)
                        val charUni = CP437.toUnicode(byteArrayOf(charVal.toByte()))[0]
                        val tooltip = String.format(
                            "<html><font size=\"5\" face=\"%s\">&#%d;</body></html>",
                            CP437.font.family,
                            charUni.code
                        )
                        setWarning(pos, p.length, tooltip)
                    }
                    pos += p.length
                    pos = ignoreExtra(text, pos, end)
                }

                "SET", "CLEAR" -> {
                    pos = handleFlagName(text, start, pos, end)
                }

                "CYCLE" -> {
                    pos = skipSpaces(text, pos, end)
                    val p = scanIn(text, pos, end)
                    val cycleVal = parseNumber(p, 1, 255)
                    if (cycleVal == null) {
                        setCharacterAttributes(start, end - start, hlNoop, false)
                        setWarning(start, end - start, "Missing/invalid cycle value")
                    } else {
                        setCharacterAttributes(pos, p.length, hlNumber, false)
                    }
                    pos += p.length
                    pos = ignoreExtra(text, pos, end)
                }

                "DIE", "END", "ENDGAME", "IDLE", "LOCK", "UNLOCK" -> pos = ignoreExtra(text, pos, end)
                "TAKE", "GIVE" -> {
                    pos = handleCounter(text, start, pos, end)
                    pos = handleNumber(text, start, pos, end, 0, 32767, true)
                    highlight(text, pos, end, true)
                }

                "WALK", "GO", "THROWSTAR", "SHOOT" -> {
                    pos = handleDirection(text, start, pos, end, false)
                    pos = ignoreExtra(text, pos, end)
                }

                "IF" -> {
                    pos = handleCondition(text, start, pos, end)
                    highlight(text, pos, end, true)
                }

                "PLAY" -> {
                    val playMus = text.substring(pos, end)
                    val playDur = getPlayDuration(playMus)
                    val cycles = if (playDur % 2 == 1) {
                        String.format("%.1f", playDur * 0.5)
                    } else {
                        (playDur / 2).toString()
                    }
                    setWarning(
                        start,
                        end - start,
                        String.format(
                            "Play duration length: %s cycle%s at default speed",
                            cycles,
                            if (playDur == 2) "" else "s"
                        )
                    )
                    var i = pos
                    while (i < end) {
                        val c = text[i].uppercaseChar()
                        when (c) {
                            'T', 'S', 'I', 'Q', 'H', 'W' -> setCharacterAttributes(i, 1, hlMusicTiming, false)
                            '0', '1', '2', '4', '5', '6', '7', '8', '9' -> setCharacterAttributes(
                                i,
                                1,
                                hlMusicDrum,
                                false
                            )

                            '+', '-' -> setCharacterAttributes(i, 1, hlMusicOctave, false)
                            'A', 'B', 'C', 'D', 'E', 'F', 'G' -> setCharacterAttributes(i, 1, hlMusicNote, false)
                            'X' -> setCharacterAttributes(i, 1, hlMusicRest, false)
                            '#', '!' -> setCharacterAttributes(i, 1, hlMusicSharpFlat, false)
                            '3', '.' -> setCharacterAttributes(i, 1, hlMusicTimingMod, false)
                            ' ', '[', ']' -> {}
                            else -> {
                                setCharacterAttributes(i, 1, hlError, false)
                                setWarning(start, end - start, "Invalid command in #PLAY sequence")
                            }
                        }
                        i++
                    }
                }

                "PUT" -> {
                    pos = handleDirection(text, start, pos, end, false)
                    pos = handleColourThing(text, start, pos, end, false)
                    pos = ignoreExtra(text, pos, end)
                }

                "ZAP", "RESTORE", "SEND" -> {
                    pos = handleLabel(text, start, pos, end, false)
                    pos = ignoreExtra(text, pos, end)
                }

                "THEN" -> {
                    setCharacterAttributes(beforePos, pos - beforePos, hlNoop, false)
                    setWarning(beforePos, pos - beforePos, "This command does nothing and can be removed")
                    pos = skipSpaces(text, pos, end)
                    highlight(text, pos, end, true)
                }

                "TRY" -> {
                    pos = handleDirection(text, start, pos, end, false)
                    highlight(text, pos, end, true)
                }

                else -> {}
            }
        } else {
            // This is an abbreviated SEND instead
            pos = handleLabel(text, start, pos, end, false)
            pos = ignoreExtra(text, pos, end)
            /*
            setCharacterAttributes(pos, cmd.length(), hlGotoLabel, false);
            pos += cmd.length();
            setCharacterAttributes(pos, end - pos, hlNoop, false);
            setWarning(pos, end - pos, IGNORED_EXTRA);
            */
        }
    }

    private fun handleLabel(text: String, start: Int, pos: Int, end: Int, optional: Boolean): Int {
        var pos = pos
        if (pos == -1) return pos
        pos = skipSpaces(text, pos, end)
        val p = scanIn(text, pos, end)
        if (p.isEmpty()) {
            if (optional) return pos

            setWarning(start, end - start, "Missing/invalid label name")
            setCharacterAttributes(start, end - start, hlError, false)
            return -1
        }
        setCharacterAttributes(pos, p.length, hlGotoLabel, false)
        val colon = p.indexOf(':')
        if (colon != -1) {
            setCharacterAttributes(pos, colon, hlObjectName, false)
            setCharacterAttributes(pos + colon, 1, hlObjectLabelSeparator, false)
        }
        pos += p.length
        return pos
    }

    private fun handleCondition(text: String, start: Int, pos: Int, end: Int): Int {
        var pos = pos
        if (pos == -1) return pos
        pos = skipSpaces(text, pos, end)
        val beforePos = pos
        val p = scanIn(text, pos, end)
        setCharacterAttributes(pos, p.length, hlCondition, false)
        when (p) {
            "ALLIGNED", "CONTACT", "ENERGIZED" -> pos += p.length
            "ALIGNED" -> {
                setWarning(pos, p.length, "Did you mean ALLIGNED?")
                setCharacterAttributes(pos, p.length, hlTextWarn, false)
                pos += p.length
            }

            "ANY" -> {
                pos += p.length
                pos = handleColourThing(text, start, pos, end, true)
            }

            "BLOCKED" -> {
                pos += p.length
                pos = handleDirection(text, pos - p.length, pos, end, false)
            }

            "NOT" -> {
                pos += p.length
                pos = handleCondition(text, start, pos, end)
            }

            "" -> {
                setWarning(start, end - start, "Missing conditional expression")
                setCharacterAttributes(start, end - start, hlError, false)
                return -1
            }

            else -> pos = handleFlagName(text, start, pos, end)
        }
        return pos
    }

    private fun handleCounter(text: String, start: Int, pos: Int, end: Int): Int {
        var pos = pos
        if (pos == -1) return pos
        pos = skipSpaces(text, pos, end)
        val p = scanIn(text, pos, end)
        var valid = false
        when (p) {
            "AMMO", "GEMS", "HEALTH", "SCORE", "TIME" -> valid = true
            "TORCHES" -> valid = !szzt
            "Z" -> valid = szzt
        }
        if (!valid) {
            setWarning(start, end - start, "Missing/invalid counter name")
            setCharacterAttributes(start, end - start, hlError, false)
            return -1
        }
        setCharacterAttributes(pos, p.length, hlCounter, false)
        pos += p.length
        return pos
    }

    private fun handleNumber(text: String, start: Int, pos: Int, end: Int, min: Int, max: Int, error: Boolean): Int {
        var pos = pos
        if (pos == -1) return pos
        pos = skipSpaces(text, pos, end)
        val p = scanIn(text, pos, end)
        val num = parseNumber(p, min, max)
        if (num == null) {
            setWarning(start, end - start, "Missing/invalid value")
            if (error) {
                setCharacterAttributes(start, end - start, hlError, false)
            } else {
                setCharacterAttributes(start, end - start, hlNoop, false)
            }
            return -1
        } else {
            setCharacterAttributes(pos, p.length, hlNumber, false)
        }
        pos += p.length

        return pos
    }

    private fun parseNumber(text: String, min: Int, max: Int): Int? {
        if (!text.isEmpty()) {
            if (text.matches(".*\\d.*".toRegex())) {
                try {
                    val value = text.toInt()
                    if (value >= min && value <= max) {
                        return value
                    }
                } catch (ignored: NumberFormatException) {
                    return null
                }
            }
        }
        return null
    }

    private fun handleFlagName(text: String, start: Int, pos: Int, end: Int): Int {
        var pos = pos
        if (pos == -1) return pos
        pos = skipSpaces(text, pos, end)
        val p = scanIn(text, pos, end)
        if (p.isEmpty()) {
            setCharacterAttributes(start, end - start, hlError, false)
            setWarning(start, end - start, "Invalid flag name")
            return -1
        }
        setCharacterAttributes(pos, p.length, hlFlag, false)
        pos += p.length
        return pos
    }

    private fun ignoreExtra(text: String, pos: Int, end: Int): Int {
        var pos = pos
        if (pos == -1) return pos
        pos = skipSpaces(text, pos, end)
        setCharacterAttributes(pos, end - pos, hlNoop, false)
        setWarning(pos, end - pos, IGNORED_EXTRA)
        return pos
    }

    private fun handleColourThing(text: String, start: Int, pos: Int, end: Int, missingError: Boolean): Int {
        var pos = pos
        if (pos == -1) return pos
        pos = skipSpaces(text, pos, end)
        val p1 = scanIn(text, pos, end)
        var finished = false
        var error = false
        val col: AttributeSet?
        if (!p1.isEmpty()) {
            col = parseColour(p1)
            if (col != null) {
                setCharacterAttributes(pos, p1.length, col, false)
                pos += p1.length
            }
            pos = skipSpaces(text, pos, end)
            val p2 = scanIn(text, pos, end)
            if (!p2.isEmpty()) {
                if (isType(p2)) {
                    setCharacterAttributes(pos, p2.length, hlThing, false)
                    finished = true
                }
                pos += p2.length
            }
        }
        //if (!finished && (missingError || col == null)) {
        if (!finished && missingError) {
            error = true
        }
        if (error) {
            setCharacterAttributes(start, end - start, hlError, false)
            setWarning(start, end - start, "Invalid colour/type")
            return -1
        }
        return pos
    }

    private fun skipSpaces(text: String, start: Int, end: Int): Int {
        var start = start
        while (true) {
            if (start == end) return start
            if (text[start] != ' ') return start
            start++
        }
    }

    fun getWarning(pos: Int): String? {
        if (pos < 0 || pos >= warnings.size) return null
        return warnings[pos]
    }

    companion object {
        private const val TEXT_WARNING_1 = "This text will fit in a message bar but not a text box"
        private const val TEXT_WARNING_2 = "This text is too long to show on a message bar"
        private const val TEXT_WARNING_1_SZZT = "This text will fit in a text box but not a message bar "
        private const val TEXT_WARNING_2_SZZT = "This text is too long to show in a text box"

        private const val NAME_NOT_FIRST = "@Name has no effect except at the very start of a program"
        private const val IGNORED_EXTRA = "This has no effect"
        private const val LABEL_IS_FIRST = "Labels do not function properly at the very start of a program"
        private const val LABEL_CONTAINS_NUMBER =
            "Labels with numbers in them function strangely. Only use them if you know what you're doing"
        private const val INVALID_DIR = "Invalid direction"
        private val PURE_DIRS = arrayOf(
            "N", "S", "E", "W", "I", "NORTH", "SOUTH", "EAST", "WEST", "IDLE",
            "SEEK", "FLOW", "RND", "RNDNE", "RNDNS", "RND"
        )
        private val MOD_DIRS = arrayOf("OPP", "CW", "CCW", "RNDP")
        private val COMMANDS = arrayOf(
            "BECOME", "BIND", "CHANGE", "CHAR", "CLEAR", "CYCLE", "DIE", "END", "ENDGAME", "GIVE", "GO",
            "IDLE", "IF", "LOCK", "PLAY", "PUT", "RESTORE", "SEND", "SET", "SHOOT", "TAKE", "THEN",
            "THROWSTAR", "TRY", "UNLOCK", "WALK", "ZAP"
        )
    }
}
