package zedit2.util

import zedit2.components.GlobalEditor
import zedit2.model.Tile
import zedit2.model.Board
import zedit2.model.Stat
import zedit2.model.spatial.Pos

object SZZTType : ZType() {
    private val charcodes = intArrayOf(
        32, 32, 32, 2, 2, 132, 32, 4, 12, 10, 232, 240,  // 0 to 11
        250, 11, 127, 32, 47, 92, 32, 111, 176, 219, 178, 177, 254, 18, 29, 178, 32,  // 12 to 28
        206, 0, 249, 42, 32, 235, 5, 32, 42, 32, 24, 31, 234, 227, 32, 233, 79,  //
        32, 0xB0, 0x1E, 0x1F, 0x11, 0x10, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
        0x94, 0xED, 0xE5, 0x0F, 0xC5, 0x5A, 0x20, 0x20, 0x20, 0x20, 0xF8, 0xCD, 0xBA, 179
    )
    private val textcols =
        intArrayOf(31, 47, 63, 79, 95, 111, 15, 143, 159, 175, 191, 207, 223, 239, 255, 0, 0, 0, 0, 0, 0, 0, 127)
    private val kindnames = arrayOf(
        "Empty",
        "BoardEdge",
        "Messenger",
        "Monitor",
        "Player",
        "Ammo",
        null,
        "Gem",
        "Key",
        "Door",
        "Scroll",
        "Passage",
        "Duplicator",
        "Bomb",
        "Energizer",
        null,
        "Clockwise",
        "Counter",
        null,
        "Lava",
        "Forest",
        "Solid",
        "Normal",
        "Breakable",
        "Boulder",
        "SliderNS",
        "SliderEW",
        "Fake",
        "Invisible",
        "BlinkWall",
        "Transporter",
        "Line",
        "Ricochet",
        null,
        "Bear",
        "Ruffian",
        "Object",
        "Slime",
        null,
        "SpinningGun",
        "Pusher",
        "Lion",
        "Tiger",
        null,
        "Head",
        "Segment",
        null,
        "Floor",
        "WaterN",
        "WaterS",
        "WaterW",
        "WaterE",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "Roton",
        "DragonPup",
        "Pairer",
        "Spider",
        "Web",
        "Stone",
        null,
        null,
        null,
        null,
        "Bullet",
        "HBlinkRay",
        "VBlinkRay",
        "Star",
        "BlueText",
        "GreenText",
        "CyanText",
        "RedText",
        "PurpleText",
        "BrownText",
        "BlackText",
        "BlackBText",
        "BlueBText",
        "GreenBText",
        "CyanBText",
        "RedBText",
        "PurpleBText",
        "BrownBText",
        "GreyBText",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "GreyText"
    )
    private val linechars = intArrayOf(206, 204, 185, 186, 202, 200, 188, 208, 203, 201, 187, 210, 205, 198, 181, 249)
    private val webchars = intArrayOf(197, 195, 180, 179, 193, 192, 217, 179, 194, 218, 191, 179, 196, 196, 196, 250)

    private val duplicatorFrames = intArrayOf(250, 250, 249, 248, 111, 79)

    private fun isLineOrEdge(id: Int): Boolean {
        return (id == LINE) || (id == BOARDEDGE)
    }

    private fun isWebOrEdge(tile: Tile): Boolean {
        if ((tile.id == WEB) || (tile.id == BOARDEDGE)) {
            return true
        }
        val stats = tile.stats
        for (stat in stats) {
            if (stat.uid == WEB) return true
        }
        return false
    }

    private fun getFirstStat(board: Board, xy : Pos): Stat? {
        val stats = board.getStatsAt(xy)
        if (stats.isEmpty()) return null
        return stats[0]
    }

    @JvmStatic
    fun getName(id: Int): String? {
        if (id >= kindnames.size) return null
        return kindnames[id]
    }

    @JvmStatic
    fun getChar(board: Board, xy: Pos): Int {
        val id = board.getTileId(xy)

        when (id) {
            DUPLICATOR -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharcodes(id)
                return duplicatorFrames[lastStat.p1]
            }

            BOMB -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharcodes(id)
                var bombChar = (48 + lastStat.p1) and 0xFF
                if (bombChar == 48 || bombChar == 49) bombChar = 11
                return bombChar
            }

            TRANSPORTER -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharcodes(id)
                val xs = lastStat.stepX
                val ys = lastStat.stepY
                if (xs < 0 && ys == 0) return 40 // '('

                if (xs > 0 && ys == 0) return 41 // ')'

                if (xs == 0 && ys > 0) return 118 // 'v'

                return 94 // '^'
            }

            OBJECT -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharcodes(id)
                return lastStat.p1
            }

            PUSHER -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharcodes(id)
                val xs = lastStat.stepX
                val ys = lastStat.stepY
                if (xs < 0 && ys == 0) return 17
                if (xs > 0 && ys == 0) return 16
                if (xs == 0 && ys < 0) return 30
                return 31
            }

            LINE -> {
                // For lines, the char depends on surrounding tiles
                var lch = 15
                if ((xy.y == 0) || isLineOrEdge(board.getTileId(xy + Pos.UP))) lch -= 8
                if ((xy.y == board.height - 1) || isLineOrEdge(board.getTileId(xy + Pos.DOWN))) lch -= 4
                if ((xy.x == board.width - 1) || isLineOrEdge(board.getTileId(xy + Pos.RIGHT))) lch -= 2
                if ((xy.x == 0) || isLineOrEdge(board.getTileId(xy + Pos.LEFT))) lch -= 1
                return linechars[lch]
            }

            WEB -> {
                // For webs, the char depends on surrounding tiles
                var lch = 15
                if ((xy.y == 0) || isWebOrEdge(board.getTile(xy + Pos.UP, false))) lch -= 8
                if ((xy.y == board.height - 1) || isWebOrEdge(board.getTile(xy + Pos.DOWN, false))) lch -= 4
                if ((xy.x == board.width - 1) || isWebOrEdge(board.getTile(xy + Pos.RIGHT, false))) lch -= 2
                if ((xy.x == 0) || isWebOrEdge(board.getTile(xy + Pos.LEFT, false))) lch -= 1
                return webchars[lch]
            }

            BLUETEXT, GREENTEXT, CYANTEXT, REDTEXT, PURPLETEXT, BROWNTEXT, BLACKTEXT, BLACKBTEXT, BLUEBTEXT, GREENBTEXT, CYANBTEXT, REDBTEXT, PURPLEBTEXT, BROWNBTEXT, GREYBTEXT, GREYTEXT -> {
                // For text kinds, the char is the colour
                return board.getTileCol(xy)
            }

            INVISIBLE -> {
                return if (GlobalEditor.getBoolean("SHOW_INVISIBLES", false)) {
                    176
                } else {
                    32
                }
            }
            // TODO(jakeouellette): Decide if this is possible / feasible and throw exception instead.
            else -> return checkCharcodes(id)
        }
    }
    private fun checkCharcodes(id:Int) : Int {
        // Otherwise, check in the charcodes list
        if (id < charcodes.size) return charcodes[id]
        // Otherwise, return '?'
        return 63
    }

    @JvmStatic
    fun getColour(board: Board, xy: Pos): Int {
        val id = board.getTileId(xy)

        when (id) {
            EMPTY -> {
                // Empty is a little special- it's always black
                return 0x00
            }

            PLAYER -> {
                // With stats, the player is c1F
                if (!board.getStatsAt(xy).isEmpty()) {
                    return 0x1F
                }
            }

            BLUETEXT, GREENTEXT, CYANTEXT, REDTEXT, PURPLETEXT, BROWNTEXT, BLACKTEXT, BLACKBTEXT, BLUEBTEXT, GREENBTEXT, CYANBTEXT, REDBTEXT, PURPLEBTEXT, BROWNBTEXT, GREYBTEXT, GREYTEXT -> {
                // For text kinds, the colour is based on the kind
                return getTextColour(id)
            }

            else -> {}
        }
        // Otherwise, use the given colour
        return board.getTileCol(xy)
    }

    @JvmStatic
    fun getTextColour(id: Int): Int {
        if (id - BLUETEXT < 0 || id - BLUETEXT >= textcols.size) return -1
        return textcols[id - BLUETEXT]
    }

    fun getTextId(colour: Int): Int {
        if (colour == 0x7F) return GREYTEXT
        var id = (colour / 16) - 1 + BLUETEXT
        if (id == (BLUETEXT - 1)) id = BLACKTEXT
        return id
    }

    @JvmStatic
    fun isFloor(tile: Tile): Boolean {
        val id = tile.id
        return id == EMPTY || id == FAKE || id == LAVA || id == FLOOR || id == WEB || id == WATERN || id == WATERS || id == WATERW || id == WATERE
    }


    const val LAVA: Int = 19

    const val FLOOR: Int = 47
    const val WATERN: Int = 48
    const val WATERS: Int = 49
    const val WATERW: Int = 50
    const val WATERE: Int = 51

    const val ROTON: Int = 59
    const val DRAGONPUP: Int = 60
    const val PAIRER: Int = 61
    const val SPIDER: Int = 62
    const val WEB: Int = 63
    const val STONE: Int = 64

    const val BULLET: Int = 69
    const val HBLINKRAY: Int = 70
    const val VBLINKRAY: Int = 71
    const val STAR: Int = 72
    const val BLUETEXT: Int = 73
    const val GREENTEXT: Int = 74
    const val CYANTEXT: Int = 75
    const val REDTEXT: Int = 76
    const val PURPLETEXT: Int = 77
    const val BROWNTEXT: Int = 78
    const val BLACKTEXT: Int = 79
    const val BLACKBTEXT: Int = 80
    const val BLUEBTEXT: Int = 81
    const val GREENBTEXT: Int = 82
    const val CYANBTEXT: Int = 83
    const val REDBTEXT: Int = 84
    const val PURPLEBTEXT: Int = 85
    const val BROWNBTEXT: Int = 86
    const val GREYBTEXT: Int = 87
    const val GREYTEXT: Int = 95
    const val CUSTOMTEXT: Int = 128
}
