package zedit2.components

import zedit2.util.CP437.registerFont
import zedit2.model.Clip.Companion.decode
import zedit2.model.Clip.Companion.encode
import zedit2.model.Tile
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import zedit2.util.Writer
import zedit2.util.ZType
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.math.sqrt

object GlobalEditor {
    @JvmField
    var f3char: Int = 0
    private var editorsOpened = 0
    private var bufferTile = Tile(0, 15)
    private var bufferTileSzzt = false
    private var properties: Properties? = null
    var blockBufferW: Int = 0
        private set
    var blockBufferH: Int = 0
        private set
    var blockBuffer: Array<Tile>? = null
    private var blockBufferSzzt = false
    var blockBufferRepeated: Boolean = false
        private set

    @JvmField
    var defaultDirectory: File = Util.jarPath
    var currentBufferNum: Int = -1
        private set
    private var currentBufferManager: BufferManager? = null

    private var configFile: File? = null

    init {
        registerFont()
        initProperties()
        if (isKey("DEFAULT_DIRECTORY")) {
            defaultDirectory = File(Util.evalConfigDir(getString("DEFAULT_DIRECTORY")!!))
        }
    }

    fun editorClosed() {
        editorsOpened--
        if (editorsOpened == 0) {
            writeOutProperties()
            System.exit(0)
        }
    }

    fun getBufferTile(szzt: Boolean): Tile {
        return if (szzt == bufferTileSzzt) bufferTile
        else {
            ZType.convert(bufferTile, szzt)
        }
    }

    fun setBufferTile(tile: Tile?, szzt: Boolean) {
        if (tile == null) return
        bufferTile = tile
        bufferTileSzzt = szzt
        clearBufferSelected()
    }

    fun setBlockBuffer(w: Int, h: Int, data: Array<Tile>?, repeated: Boolean, szzt: Boolean) {
        blockBufferW = w
        blockBufferH = h
        blockBuffer = data
        blockBufferRepeated = repeated
        blockBufferSzzt = szzt
        clearBufferSelected()
    }

    fun encodeBuffer(): String {
        val result: String
        if (isBlockBuffer()) {
            // TODO(jakeouellette): Avoid BlockBuffer being null going forward.
            result = encode(blockBufferW, blockBufferH, blockBuffer!!, blockBufferSzzt)
        } else {
            val tiles = arrayOf(bufferTile)
            result = encode(1, 1, tiles, bufferTileSzzt)
        }
        return result
    }

    fun decodeBuffer(encodedBuffer: String?) {
        val clip = decode(encodedBuffer)
        if (clip.w == 1 && clip.h == 1) {
            bufferTile = clip.tiles[0]
            bufferTileSzzt = clip.isSzzt
        } else {
            blockBufferW = clip.w
            blockBufferH = clip.h
            blockBuffer = clip.tiles
            blockBufferSzzt = clip.isSzzt
            blockBufferRepeated = false
        }
        clearBufferSelected()
    }

    fun clearBufferSelected() {
        currentBufferNum = -1
        if (currentBufferManager != null) {
            currentBufferManager!!.updateSelected(currentBufferNum)
            currentBufferManager = null
        }
    }

    fun getBlockBuffer(szzt: Boolean): Array<Tile> {
        // TODO(jakeouellette): Avoid null issues with blockbuffer.
        return if (szzt == blockBufferSzzt) blockBuffer!!
        else ZType.convert(blockBuffer!!, szzt)
    }

    fun isBlockBuffer(): Boolean {
        return blockBufferW != 0
    }

    private fun writeOutProperties() {
        // configFile
        if (writeOutProperties(configFile)) {
            return
        }

        val tryPaths = paths
        for (path in tryPaths) {
            if (writeOutProperties(path)) {
                return
            }
        }
    }

    private fun writeOutProperties(path: Path): Boolean {
        val file = Paths.get(path.toString(), PROPERTIES_FILE).toFile()
        return writeOutProperties(file)
    }

    private fun writeOutProperties(file: File?): Boolean {
        if (file == null) return false
        try {
            val fw = FileWriter(file)
            properties!!.store(fw, "ZEdit v" + Main.VERSION + " Configuration")
            fw.close()
            println("Wrote properties to $file")
        } catch (e: IOException) {
            println("Failed to write to $file")
            return false
        }
        return true
    }

    fun editorOpened() {
        editorsOpened++
    }


    fun initProperties() {
        Logger.i(TAG) { "Initializing properties..." }
        properties = Properties()
        var foundProperties = false

        val tryPaths = paths
        for (path in tryPaths) {
            val file = lookForProperties(path)
            if (file != null) {
                configFile = file
                foundProperties = true
                break
            }
        }

        if (!foundProperties) {
            System.err.println("Unable to locate properties file.")
        }

        newConfigOption("ZOOM", "1")
        newConfigOption("ONE_WORLD_AT_A_TIME", "true")
        newConfigOption("ZOOM_FACTOR", sqrt(2.0).toString())
        newConfigOption("MIN_ZOOM", "0.0625")
        newConfigOption("MAX_ZOOM", "8.0")
        newConfigOption("ATLAS_GRID", "true")
        newConfigOption("BLINKING", "true")

        newConfigOption("HELPBROWSER_WIDTH", "800")
        newConfigOption("HELPBROWSER_HEIGHT", "600")

        newConfigOption("SHOW_INVISIBLES", "false")

        newConfigOption("RECENT_MAX", "10")

        newConfigOption("UNDO_BUFFER_SIZE", "100")

        //newConfigOption("DEFAULT_DIRECTORY", "C:/Users/" + System.getProperty("user.name") + "/Dropbox/YHWH/zzt/zzt");
        //newConfigOption("SCREENSHOT_DIR", "C:/Users/" + System.getProperty("user.name") + "/Dropbox/YHWH/zzt/screenshots");
        newConfigOption("SCREENSHOT_NAME", "Screenshot {date} {time}.png")
        newConfigOption("DEFAULT_SZZT", "false")
        newConfigOption("TEST_SWITCH_BOARD", "true")
        //setConfigOption("LOAD_FILE", "C:/Users/" + System.getProperty("user.name") + "/Dropbox/YHWH/zzt/zzt/TOWN.ZZT");
        //newConfigOption("ZZT_TEST_PATH", "C:/Users/" + System.getProperty("user.name") + "/Dropbox/YHWH/zzt/zzt");
        //setConfigOption("ZZT_TEST_PATH", "");
        newConfigOption("ZZT_TEST_COMMAND", "zeta86.exe")
        newConfigOption("ZZT_TEST_PARAMS", "-t -e zzt.exe")
        newConfigOption("ZZT_TEST_FILENAME", "__TEST")
        newConfigOption("ZZT_TEST_INJECT_P", "true")
        newConfigOption("ZZT_TEST_INJECT_P_DELAY", "600")
        newConfigOption("ZZT_TEST_USE_CHARPAL", "true")
        newConfigOption("ZZT_TEST_USE_BLINK", "true")

        //newConfigOption("SZZT_TEST_PATH", "C:/Users/" + System.getProperty("user.name") + "/Dropbox/YHWH/zzt/zzt/superzzt");
        //setConfigOption("SZZT_TEST_PATH", "");
        newConfigOption("SZZT_TEST_COMMAND", "zeta86.exe")
        newConfigOption("SZZT_TEST_PARAMS", "-t -e superz.exe")
        newConfigOption("SZZT_TEST_FILENAME", "__TEST")
        newConfigOption("SZZT_TEST_INJECT_P", "true")
        newConfigOption("SZZT_TEST_INJECT_P_DELAY", "600")
        newConfigOption("SZZT_TEST_INJECT_ENTER", "true")
        newConfigOption("SZZT_TEST_INJECT_ENTER_DELAY", "50")
        newConfigOption("SZZT_TEST_USE_CHARPAL", "true")
        newConfigOption("SZZT_TEST_USE_BLINK", "true")

        config_fmenus()

        /*
        newConfigOption("F3_MENU", "Terrain");
        newConfigOption("F3_MENU_0", "Empty");
        newConfigOption("F3_MENU_1", "Solid");
        newConfigOption("F3_MENU_2", "Normal");
        newConfigOption("F3_MENU_3", "Breakable");
        newConfigOption("F3_MENU_4", "Water");
        newConfigOption("F3_MENU_5", "Floor");
        newConfigOption("F3_MENU_6", "Fake");
        newConfigOption("F3_MENU_7", "Invisible");
        newConfigOption("F3_MENU_8", "Line");
        newConfigOption("F3_MENU_9", "Forest");
        newConfigOption("F3_MENU_10", "Web");
        newConfigOption("F3_MENU_11", "Lava");
        newConfigOption("F3_MENU_12", "");
        newConfigOption("F4_MENU", "Items");
        newConfigOption("F4_MENU_0", "Ammo");
        newConfigOption("F4_MENU_1", "Torch");
        newConfigOption("F4_MENU_2", "Gem");
        newConfigOption("F4_MENU_3", "Key");
        newConfigOption("F4_MENU_4", "Door");
        newConfigOption("F4_MENU_5", "Energizer");
        newConfigOption("F4_MENU_6", "Stone(Cycle=1)");
        newConfigOption("F4_MENU_7", "");
        newConfigOption("F5_MENU", "Creatures");
        newConfigOption("F5_MENU_0", "Bear(Cycle=3)");
        newConfigOption("F5_MENU_1", "Ruffian(Cycle=1)");
        newConfigOption("F5_MENU_2", "Slime(Cycle=3)");
        newConfigOption("F5_MENU_3", "Shark(Cycle=3)");
        newConfigOption("F5_MENU_4", "SpinningGun(Cycle=2)");
        newConfigOption("F5_MENU_5", "Lion(Cycle=2)");
        newConfigOption("F5_MENU_6", "Tiger(Cycle=2)");
        newConfigOption("F5_MENU_7", "Head(Cycle=2)");
        newConfigOption("F5_MENU_8", "Segment(Cycle=2)");
        newConfigOption("F5_MENU_9", "Roton(Cycle=1)");
        newConfigOption("F5_MENU_10", "DragonPup(Cycle=2)");
        newConfigOption("F5_MENU_11", "Pairer(Cycle=2)");
        newConfigOption("F5_MENU_12", "Spider(Cycle=1)");
        newConfigOption("F5_MENU_13", "");
        newConfigOption("F6_MENU", "Puzzle pieces");
        newConfigOption("F6_MENU_0", "Boulder");
        newConfigOption("F6_MENU_1", "SliderNS");
        newConfigOption("F6_MENU_2", "SliderEW");
        newConfigOption("F6_MENU_3", "Ricochet");
        newConfigOption("F6_MENU_4", "Pusher(Cycle=4)");
        newConfigOption("F6_MENU_5", "Duplicator(Cycle=2)");
        newConfigOption("F6_MENU_6", "Bomb(Cycle=6)");
        newConfigOption("F6_MENU_7", "BlinkWall()");
        newConfigOption("F6_MENU_8", "");
        newConfigOption("F7_MENU", "Transport");
        newConfigOption("F7_MENU_0", "Passage(Cycle=0)");
        newConfigOption("F7_MENU_1", "Transporter(Cycle=2)");
        newConfigOption("F7_MENU_2", "Clockwise(Cycle=3)");
        newConfigOption("F7_MENU_3", "Counter(Cycle=2)");
        newConfigOption("F7_MENU_4", "WaterN");
        newConfigOption("F7_MENU_5", "WaterS");
        newConfigOption("F7_MENU_6", "WaterE");
        newConfigOption("F7_MENU_7", "WaterW");
        newConfigOption("F7_MENU_8", "BoardEdge");
        newConfigOption("F7_MENU_9", "");

        newConfigOption("F8_MENU", "Miscellaneous");
        newConfigOption("F8_MENU_0", "Messenger");
        newConfigOption("F8_MENU_1", "Monitor");
        newConfigOption("F8_MENU_2", "HBlinkRay");
        newConfigOption("F8_MENU_3", "VBlinkRay");
        newConfigOption("F8_MENU_4", "");

        newConfigOption("F9_MENU", "Objects");
        newConfigOption("F9_MENU_0", "Object(Cycle=1,P1=1)");
        newConfigOption("F9_MENU_1", "Player(Cycle=1,StatId=0,IsPlayer=true)");
        newConfigOption("F9_MENU_2", "Player(Cycle=1)Player Clone");
        newConfigOption("F9_MENU_3", "Scroll()");
        newConfigOption("F9_MENU_4", "Star()");
        newConfigOption("F9_MENU_5", "Bullet()");
        newConfigOption("F9_MENU_6", "");

        newConfigOption("F10_MENU", "");
        newConfigOption("F10_MENU_0", "");
         */

        /*
        setConfigOption("F3_MENU", "Item");
        setConfigOption("F3_MENU_0", "Player(Cycle=1,StatId=0)");
        setConfigOption("F3_MENU_1", "Ammo");
        setConfigOption("F3_MENU_2", "Torch");
        setConfigOption("F3_MENU_3", "Gem");
        setConfigOption("F3_MENU_4", "Key");
        setConfigOption("F3_MENU_5", "Door");
        setConfigOption("F3_MENU_6", "Scroll()");
        setConfigOption("F3_MENU_7", "Passage(Cycle=0)");
        setConfigOption("F3_MENU_8", "Duplicator(Cycle=2)");
        setConfigOption("F3_MENU_9", "Bomb(Cycle=6)");
        setConfigOption("F3_MENU_10", "Energizer");
        setConfigOption("F3_MENU_11", "Clockwise(Cycle=3)");
        setConfigOption("F3_MENU_12", "Counter(Cycle=2)");
        setConfigOption("F3_MENU_13", "Player(Cycle=1)Player Clone");
        setConfigOption("F3_MENU_14", "Stone(Cycle=1)Stone");
        //setConfigOption("F3_MENU_15", "Object(Cycle=1,P1=4,Autobind=true,Code=@Object\\n:l\\n/rnd\\n#l\\n|P1=6,Cycle=1,Autobind=true,Code=@Object\\n:l\\n/rnd\\n#l\\n)Lol*");

        setConfigOption("F4_MENU", "Creature");
        setConfigOption("F4_MENU_0", "Bear(Cycle=3)");
        setConfigOption("F4_MENU_1", "Ruffian(Cycle=1)");
        setConfigOption("F4_MENU_2", "Object(Cycle=1,P1=1)");
        setConfigOption("F4_MENU_3", "Slime(Cycle=3)");
        setConfigOption("F4_MENU_4", "Shark(Cycle=3)");
        setConfigOption("F4_MENU_5", "SpinningGun(Cycle=2)");
        setConfigOption("F4_MENU_6", "Pusher(Cycle=4)");
        setConfigOption("F4_MENU_7", "Lion(Cycle=2)");
        setConfigOption("F4_MENU_8", "Tiger(Cycle=2)");
        setConfigOption("F4_MENU_9", "Head(Cycle=2)");
        setConfigOption("F4_MENU_10", "Segment(Cycle=2)");
        setConfigOption("F4_MENU_11", "Roton(Cycle=1)");
        setConfigOption("F4_MENU_12", "DragonPup(Cycle=2)");
        setConfigOption("F4_MENU_13", "Pairer(Cycle=2)");
        setConfigOption("F4_MENU_14", "Spider(Cycle=1)");

        setConfigOption("F5_MENU", "Terrain");
        setConfigOption("F5_MENU_0", "Water");
        setConfigOption("F5_MENU_1", "Forest");
        setConfigOption("F5_MENU_2", "Solid");
        setConfigOption("F5_MENU_3", "Normal");
        setConfigOption("F5_MENU_4", "Breakable");
        setConfigOption("F5_MENU_5", "Boulder");
        setConfigOption("F5_MENU_6", "SliderNS");
        setConfigOption("F5_MENU_7", "SliderEW");
        setConfigOption("F5_MENU_8", "Fake");
        setConfigOption("F5_MENU_9", "Invisible");
        setConfigOption("F5_MENU_10", "BlinkWall()");
        setConfigOption("F5_MENU_11", "Transporter(Cycle=2)");
        setConfigOption("F5_MENU_12", "Ricochet");
        setConfigOption("F5_MENU_13", "Floor");
        setConfigOption("F5_MENU_14", "Lava");
        setConfigOption("F5_MENU_15", "WaterN");
        setConfigOption("F5_MENU_16", "WaterS");
        setConfigOption("F5_MENU_17", "WaterE");
        setConfigOption("F5_MENU_18", "WaterW");
        setConfigOption("F5_MENU_19", "Web");

         */
        newConfigOption("AUTO_INSERT_NEWLINE", "true")
        newConfigOption("CODEEDITOR_WIDTH", "640")
        newConfigOption("CODEEDITOR_HEIGHT", "480")
        newConfigOption("FIND_MATCH_CASE", "false")
        newConfigOption("FIND_REGEX", "false")

        newConfigOption("EDITOR_BG", "333333")
        newConfigOption("EDITOR_MUSIC_BG", "3A004A")
        newConfigOption("EDITOR_BIND_BG", "005A00")
        newConfigOption("EDITOR_SELECTION_COL", "AAAAAA")
        newConfigOption("EDITOR_SELECTED_TEXT_COL", "000000")
        newConfigOption("EDITOR_CARET_COL", "FFFFFF")

        // Syntax highlighting (even #)
        newConfigOption("SYNTAX_HIGHLIGHTING", "true")
        newConfigOption("HL_CENTEREDTEXT", "FFFFFF")
        newConfigOption("HL_COLON", "00C095")
        newConfigOption("HL_COMMAND", "AFFF00")
        newConfigOption("HL_CONDITION", "51CBFF")
        newConfigOption("HL_COUNTER", "00FFFF")
        newConfigOption("HL_DIRECTION", "EFAF00")
        newConfigOption("HL_DOLLAR", "9FFFFF")
        newConfigOption("HL_ERROR", "FF0000")
        newConfigOption("HL_EXCLAMATION", "9FFF9F")
        newConfigOption("HL_FLAG", "D47FC3")
        newConfigOption("HL_GOTOLABEL", "7CFFC6")
        newConfigOption("HL_HASH", "FFFF00")
        newConfigOption("HL_LABEL", "6CCCB6")
        newConfigOption("HL_MUSICDRUM", "87ABED")
        newConfigOption("HL_MUSICNOTE", "FF91D7")
        newConfigOption("HL_MUSICOCTAVE", "8D89D6")
        newConfigOption("HL_MUSICREST", "669AF5")
        newConfigOption("HL_MUSICSHARPFLAT", "92A0F4")
        newConfigOption("HL_MUSICTIMING", "99A1CC")
        newConfigOption("HL_MUSICTIMINGMOD", "88B2CC")
        newConfigOption("HL_NOOP", "888888")
        newConfigOption("HL_NUMBER", "00FF00")
        newConfigOption("HL_OBJECTLABELSEPARATOR", "DDDDDD")
        newConfigOption("HL_OBJECTNAME", "FF8FFF")
        newConfigOption("HL_SLASH", "FFDD55")
        newConfigOption("HL_TEXT", "FFFFAF")
        newConfigOption("HL_TEXTWARN", "FF6F00")
        newConfigOption("HL_TEXTWARNLIGHT", "FFA500")
        newConfigOption("HL_THING", "FFDD55")
        newConfigOption("HL_ZAPPED", "009FFF")
        newConfigOption("HL_ZAPPEDLABEL", "6CBBEB")
        newConfigOption("HL_BLUE", "AAAAFF")
        newConfigOption("HL_GREEN", "AAFFAA")
        newConfigOption("HL_CYAN", "88FFFF")
        newConfigOption("HL_RED", "FFAAAA")
        newConfigOption("HL_PURPLE", "FF88FF")
        newConfigOption("HL_YELLOW", "FFFF88")
        newConfigOption("HL_WHITE", "FFFFFF")


        newConfigOption("K_Escape", "ESCAPE")
        newConfigOption("K_Up", "UP")
        newConfigOption("K_Down", "DOWN")
        newConfigOption("K_Left", "LEFT")
        newConfigOption("K_Right", "RIGHT")
        newConfigOption("K_Alt-Up", "alt UP")
        newConfigOption("K_Alt-Down", "alt DOWN")
        newConfigOption("K_Alt-Left", "alt LEFT")
        newConfigOption("K_Alt-Right", "alt RIGHT")
        newConfigOption("K_Shift-Up", "shift UP")
        newConfigOption("K_Shift-Down", "shift DOWN")
        newConfigOption("K_Shift-Left", "shift LEFT")
        newConfigOption("K_Shift-Right", "shift RIGHT")
        newConfigOption("K_Ctrl-Shift-Up", "ctrl shift UP")
        newConfigOption("K_Ctrl-Shift-Down", "ctrl shift DOWN")
        newConfigOption("K_Ctrl-Shift-Left", "ctrl shift LEFT")
        newConfigOption("K_Ctrl-Shift-Right", "ctrl shift RIGHT")
        newConfigOption("K_Tab", "TAB")
        newConfigOption("K_Home", "HOME")
        newConfigOption("K_End", "END")
        newConfigOption("K_PgUp", "PAGE_UP")
        newConfigOption("K_PgDn", "PAGE_DOWN")
        newConfigOption("K_Insert", "INSERT")
        newConfigOption("K_Space", "SPACE")
        newConfigOption("K_Delete", "DELETE")
        newConfigOption("K_Enter", "ENTER")
        newConfigOption("K_Ctrl-Enter", "ctrl ENTER")
        newConfigOption("K_Ctrl-=", "ctrl EQUALS")
        newConfigOption("K_Ctrl--", "ctrl MINUS")
        newConfigOption("K_A", "A")
        newConfigOption("K_B", "B")
        newConfigOption("K_C", "C")
        newConfigOption("K_D", "D")
        newConfigOption("K_F", "F")
        newConfigOption("K_G", "G")
        newConfigOption("K_I", "I")
        newConfigOption("K_L", "L")
        newConfigOption("K_P", "P")
        newConfigOption("K_S", "S")
        newConfigOption("K_X", "X")
        newConfigOption("K_Ctrl-A", "ctrl A")
        newConfigOption("K_Ctrl-B", "ctrl B")
        newConfigOption("K_Ctrl-D", "ctrl D")
        newConfigOption("K_Ctrl-E", "ctrl E")
        newConfigOption("K_Ctrl-F", "ctrl F")
        newConfigOption("K_Ctrl-H", "ctrl H")
        newConfigOption("K_Ctrl-S", "ctrl S")
        newConfigOption("K_Ctrl-P", "ctrl P")
        newConfigOption("K_Ctrl-R", "ctrl R")
        newConfigOption("K_Ctrl-V", "ctrl V")
        newConfigOption("K_Ctrl-X", "ctrl X")
        newConfigOption("K_Ctrl-Y", "ctrl Y")
        newConfigOption("K_Ctrl-Z", "ctrl Z")
        newConfigOption("K_Ctrl--", "ctrl -")
        newConfigOption("K_Ctrl-=", "ctrl =")
        newConfigOption("K_Alt-B", "alt B")
        newConfigOption("K_Alt-F", "alt F")
        newConfigOption("K_Alt-I", "alt I")
        newConfigOption("K_Alt-M", "alt M")
        newConfigOption("K_Alt-S", "alt S")
        newConfigOption("K_Alt-T", "alt T")
        newConfigOption("K_Alt-X", "alt X")
        newConfigOption("K_Shift-B", "shift B")
        newConfigOption("K_Ctrl-Alt-M", "ctrl alt M")
        newConfigOption("K_F1", "F1")
        newConfigOption("K_F2", "F2")
        newConfigOption("K_F3", "F3")
        newConfigOption("K_F4", "F4")
        newConfigOption("K_F5", "F5")
        newConfigOption("K_F6", "F6")
        newConfigOption("K_F7", "F7")
        newConfigOption("K_F8", "F8")
        newConfigOption("K_F9", "F9")
        newConfigOption("K_F10", "F10")
        newConfigOption("K_F11", "F11")
        newConfigOption("K_F12", "F12")
        newConfigOption("K_Shift-F1", "shift F1")
        newConfigOption("K_Shift-F2", "shift F2")
        newConfigOption("K_Shift-F3", "shift F3")
        newConfigOption("K_Shift-F4", "shift F4")
        newConfigOption("K_Shift-F5", "shift F5")
        newConfigOption("K_Shift-F6", "shift F6")
        newConfigOption("K_Alt-F12", "alt F12")
        newConfigOption("K_0", "0")
        newConfigOption("K_1", "1")
        newConfigOption("K_2", "2")
        newConfigOption("K_3", "3")
        newConfigOption("K_4", "4")
        newConfigOption("K_5", "5")
        newConfigOption("K_6", "6")
        newConfigOption("K_7", "7")
        newConfigOption("K_8", "8")
        newConfigOption("K_9", "9")
        newConfigOption("K_Ctrl-0", "ctrl 0")
        newConfigOption("K_Ctrl-1", "ctrl 1")
        newConfigOption("K_Ctrl-2", "ctrl 2")
        newConfigOption("K_Ctrl-3", "ctrl 3")
        newConfigOption("K_Ctrl-4", "ctrl 4")
        newConfigOption("K_Ctrl-5", "ctrl 5")
        newConfigOption("K_Ctrl-6", "ctrl 6")
        newConfigOption("K_Ctrl-7", "ctrl 7")
        newConfigOption("K_Ctrl-8", "ctrl 8")
        newConfigOption("K_Ctrl-9", "ctrl 9")
        newConfigOption("K_COMMA", "COMMA")
        newConfigOption("K_PERIOD", "PERIOD")

        newConfigOption("ZZT_GRAD_0", "CAAAAAEAAAAAEwEAFwEAFgEAFQEAExkAFxkAFhkAFQkA")
        newConfigOption("ZZT_GRAD_1", "CAAAAAEAAAAAEwIAFwIAFgIAFQIAEyoAFyoAFioAFQoA")
        newConfigOption("ZZT_GRAD_2", "CAAAAAEAAAAAEwMAFwMAFgMAFQMAEzsAFzsAFjsAFQsA")
        newConfigOption("ZZT_GRAD_3", "CAAAAAEAAAAAEwQAFwQAFgQAFQQAE0wAF0wAFkwAFQwA")
        newConfigOption("ZZT_GRAD_4", "CAAAAAEAAAAAEwUAFwUAFgUAFQUAE10AF10AFl0AFQ0A")
        newConfigOption("ZZT_GRAD_5", "CAAAAAEAAAAAEwYAFwYAFgYAFQYAE24AF24AFm4AFQ4A")
        newConfigOption("ZZT_GRAD_6", "DAAAAAEAAAAAEwgAFwgAFggAFQgAFngAF3gAE3gAFQcAE38AF38AFn8AFQ8A")

        //setConfigOption("ZZT_GRAD_7", "PAAAAAEAAAAAFk4AF04AE04AFkQAFmQAF0YAFkYAFmYAFhYAF2EAFmEAFhEAFiEAFxIAFhIAFiIAFkIAFyQAFiQAFkQAFnQAF3QAFkcAFncAE34AF34AFn4AFQ4AFl4AF14AE14AFlUAFmUAF1YAFlYAFmYAE2sAF2sAFmsAFQsAFjsAFzsAEzsAFjMAEzgAFzgAFjgAFQgAFkgAF0gAE0gAFkQAFhQAF0EAFkEAFQEAExgAFxgAFhgAFQgA");
        newConfigOption("SZZT_GRAD_0", "CAAAAAEAAAABLwEAFwEAFgEAFQEALxkAFxkAFhkAFRkA")
        newConfigOption("SZZT_GRAD_1", "CAAAAAEAAAABLwIAFwIAFgIAFQIALyoAFyoAFioAFSoA")
        newConfigOption("SZZT_GRAD_2", "CAAAAAEAAAABLwMAFwMAFgMAFQMALzsAFzsAFjsAFTsA")
        newConfigOption("SZZT_GRAD_3", "CAAAAAEAAAABLwQAFwQAFgQAFQQAL0wAF0wAFkwAFUwA")
        newConfigOption("SZZT_GRAD_4", "CAAAAAEAAAABLwUAFwUAFgUAFQUAL10AF10AFl0AFV0A")
        newConfigOption("SZZT_GRAD_5", "CAAAAAEAAAABLwYAFwYAFgYAFQYAL24AF24AFm4AFW4A")
        newConfigOption("SZZT_GRAD_6", "DAAAAAEAAAABLwgAFwgAFggAFQgAFngAF3gAL3gAFXcAL38AF38AFn8AFX8A")

        newConfigOption("CONVERT_MACROW", "8")
        newConfigOption("CONVERT_MACROH", "14")
        newConfigOption("CONVERT_MAXSTATS", "0")
        newConfigOption("CONVERT_SZZT_TYPES", "X.................XXX...................X..............................")
        newConfigOption("CONVERT_ZZT_TYPES", "X..................X.XXX......................................")
        newConfigOption("CONVERT_LIVEPREVIEW", "true")
    }

    private fun config_fmenus() {
        for (f in 3..10) {
            val preset = Settings.preset_ZEdit[f - 3]
            val key = String.format("F%d_MENU", f)
            if (!isKey(key)) {
                newConfigOption(key, preset[0])
                for (i in 1 until preset.size) {
                    newConfigOption(String.format("F%d_MENU_%d", f, i - 1), preset[i])
                }
            }
        }
    }

    private val paths: Array<Path>
        get() {
            val dirs = arrayOf(
                System.getProperty("user.dir"),
                System.getProperty("user.home"),
                Util.jarPath.toString(),
                "."
            )
            val paths = ArrayList<Path>()
            for (dir in dirs) {
                if (dir != null) {
                    val path = Path.of(dir)
                    if (path.toFile().isDirectory) {
                        paths.add(path)
                    }
                }
            }
            return paths.toTypedArray<Path>()
        }

    fun getWriterInLocalDir(filename: String?, closeWriter: Boolean): Writer? {
        val paths = paths
        for (path in paths) {
            val file = Path.of(path.toString(), filename).toFile()
            try {
                val writer = Writer(file)
                if (closeWriter) {
                    writer.writer.close()
                }
                return writer
            } catch (ignored: IOException) {
                // continue
            }
        }
        return null
    }

    private fun newConfigOption(key: String, value: String) {
        if (!properties!!.containsKey(key)) {
            properties!!.setProperty(key, value)
        }
    }

    private fun setConfigOption(key: String, value: String) {
        properties!!.setProperty(key, value)
    }

    private fun lookForProperties(path: Path): File? {
        try {
            val file = Paths.get(path.toString(), PROPERTIES_FILE).toFile()
            val fr = FileReader(file)
            properties!!.load(fr)
            fr.close()
            println("Loaded properties from $file")
            return file
        } catch (e: IOException) {
            return null
        }
    }

    fun isKey(key: String?): Boolean {
        return properties!!.containsKey(key)
    }

    fun getString(key: String?): String? {
        return properties!!.getProperty(key)
    }

    // TODO(jakeouellette): Disallow nullability.
    fun getString(key: String?, def: String?): String {
        return properties!!.getProperty(key, def)
    }

    fun getInt(key: String?): Int {
        return properties!!.getProperty(key).toInt()
    }

    fun getInt(key: String?, def: Int): Int {
        return properties!!.getProperty(key, def.toString()).toInt()
    }

    fun getIntRadix(key: String?, def:String, radix: Int): Int {
        return properties!!.getProperty(key, def)!!.toInt(radix)
    }

    fun getDouble(key: String?): Double {
        return properties!!.getProperty(key).toDouble()
    }

    fun getDouble(key: String?, def: Double): Double {
        return properties!!.getProperty(key, def.toString()).toDouble()
    }

    fun getBoolean(key: String?): Boolean {
        return properties!!.getProperty(key).toBoolean()
    }

    fun getBoolean(key: String?, def: Boolean): Boolean {
        return properties!!.getProperty(key, def.toString()).toBoolean()
    }

    fun setBoolean(key: String, setting: Boolean) {
        setConfigOption(key, setting.toString())
    }

    fun setDouble(key: String, setting: Double) {
        setConfigOption(key, setting.toString())
    }

    fun setInt(key: String, setting: Int) {
        setConfigOption(key, setting.toString())
    }

    fun setString(key: String, setting: String) {
        setConfigOption(key, setting)
    }

    fun removeKey(key: String?) {
        properties!!.remove(key)
    }

    fun setBufferPos(bufferNum: Int, bufferManager: BufferManager?) {
        currentBufferNum = bufferNum
        currentBufferManager = bufferManager
        if (currentBufferManager != null) {
            currentBufferManager!!.updateSelected(bufferNum)
        }
    }

    fun addToRecent(path: File?) {
        if (path == null) return
        val recentMax = getInt("RECENT_MAX", 10)
        var startFrom = 0
        for (i in 0 until recentMax) {
            val key = String.format("RECENT_%d", i)
            val `val` = getString(key)
            if (`val` != null) {
                if (`val` == path.toString()) {
                    startFrom = i
                    break
                }
            } else {
                setString(key, path.toString())
                return
            }
        }
        var overWrite = recentMax - 1
        // No room, so move everything up
        // idx 0: sets RECENT_0 to RECENT_1
        // idx 1: sets RECENT_1 to RECENT_2
        // ...
        // idx 8: sets RECENT_8 to RECENT_9
        var i = startFrom
        while (i < recentMax - 1) {
            val fromKey = String.format("RECENT_%d", i + 1)
            val toKey = String.format("RECENT_%d", i)
            val fromFile = getString(fromKey)
            if (fromFile == null) {
                overWrite = i
                break
            }
            setString(toKey, fromFile)
            i++
        }
        val key = String.format("RECENT_%d", overWrite)
        setString(key, path.toString())
    }

    fun removeRecentFile(recentFileName: String) {
        val recentMax = getInt("RECENT_MAX", 10)
        for (i in 0 until recentMax) {
            val key = String.format("RECENT_%d", i)
            val `val` = getString(key)
            if (`val` != null) {
                if (`val` == recentFileName) {
                    for (j in i until recentMax) {
                        val jkey = String.format("RECENT_%d", j)
                        if (isKey(jkey)) {
                            removeKey(jkey)
                            val k = j + 1
                            if (k < recentMax) {
                                val nextVal = getString(String.format("RECENT_%d", k))
                                if (nextVal != null) {
                                    setString(jkey, nextVal)
                                }
                            }
                        } else {
                            return
                        }
                    }
                    return
                }
            }
        }
    }

    var timestamp: Long = 0
        private set
    private var globalInstance: GlobalEditor? = null

    private const val PROPERTIES_FILE = "zedit2.cfg"

    @JvmStatic
    fun updateTimestamp() {
        timestamp = System.nanoTime()
    }

}
