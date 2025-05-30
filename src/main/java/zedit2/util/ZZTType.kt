package zedit2.util

import zedit2.components.GlobalEditor.getBoolean
import zedit2.model.Board
import zedit2.model.Stat
import zedit2.model.Tile
import zedit2.model.spatial.Pos

object ZZTType : ZType() {
    private val charcodes = intArrayOf(
        32, 32, 32, 32, 2, 132, 157,
        4, 12, 10, 232, 240, 250, 11, 127, 179, 47,
        92, 248, 176, 176, 219, 178, 177, 254, 18, 29,
        178, 32, 206, 0, 249, 42, 205, 153, 5,
        32, 42, 94, 24, 31, 234, 227, 186, 233, 79
    )
    private val textcols = intArrayOf(8, 31, 47, 63, 79, 95, 111, 15)
    private val kindnames = arrayOf(
        "Empty", "BoardEdge", "Messenger", "Monitor", "Player", "Ammo", "Torch",
        "Gem", "Key", "Door", "Scroll", "Passage", "Duplicator", "Bomb", "Energizer", "Star", "Clockwise",
        "Counter", "Bullet", "Water", "Forest", "Solid", "Normal", "Breakable", "Boulder", "SliderNS", "SliderEW",
        "Fake", "Invisible", "BlinkWall", "Transporter", "Line", "Ricochet", "HBlinkRay", "Bear", "Ruffian",
        "Object", "Slime", "Shark", "SpinningGun", "Pusher", "Lion", "Tiger", "VBlinkRay", "Head", "Segment",
        "CustomText", "BlueText", "GreenText", "CyanText", "RedText", "PurpleText", "BrownText", "BlackText"
    )
    private val linechars = intArrayOf(206, 204, 185, 186, 202, 200, 188, 208, 203, 201, 187, 210, 205, 198, 181, 249)
    private val duplicatorFrames = intArrayOf(250, 250, 249, 248, 111, 79)

    private fun isLineOrEdge(id: Int): Boolean {
        return (id == LINE) || (id == BOARDEDGE)
    }

    private fun getFirstStat(board: Board, xy : Pos): Stat? {
        val stats = board.getStatsAt(xy)
        if (stats.isEmpty()) return null
        return stats.getOrNull(0)
    }

    @JvmStatic
    fun getName(id: Int): String? {
        if (id >= kindnames.size) return null
        return kindnames[id]
    }

    @JvmStatic
    fun getChar(board: Board, xy: Pos): Int {
        when (val id = board.getTileId(xy)) {
            DUPLICATOR -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharCodes(board, xy, id)
                return duplicatorFrames[lastStat.p1]
            }

            BOMB -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharCodes(board, xy, id)
                var bombChar = (48 + lastStat.p1) and 0xFF
                if (bombChar == 48 || bombChar == 49) bombChar = 11
                return bombChar
            }

            TRANSPORTER -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharCodes(board, xy, id)
                val xs = lastStat.stepX
                val ys = lastStat.stepY
                if (xs < 0 && ys == 0) return 40 // '('

                if (xs > 0 && ys == 0) return 41 // ')'

                if (xs == 0 && ys > 0) return 118 // 'v'

                return 94 // '^'
            }

            OBJECT -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharCodes(board, xy, id)
                return lastStat.p1
            }

            PUSHER -> {
                val lastStat = getFirstStat(board, xy) ?: return checkCharCodes(board, xy, id)
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
                if ((xy.y == 0) || isLineOrEdge(board.getTileId(xy + Pos.UP ))) lch -= 8
                if ((xy.y == board.dim.h - 1) || isLineOrEdge(board.getTileId(xy + Pos.DOWN))) lch -= 4
                if ((xy.x == board.dim.w - 1) || isLineOrEdge(board.getTileId(xy + Pos.RIGHT))) lch -= 2
                if ((xy.x == 0) || isLineOrEdge(board.getTileId(xy + Pos.LEFT))) lch -= 1
                return linechars[lch]
            }

            INVISIBLE -> {
                return if (getBoolean("SHOW_INVISIBLES", false)) {
                    176
                } else {
                    32
                }
            }
            else -> return checkCharCodes(board, xy, id)
        }
    }

    fun checkCharCodes(board : Board, xy: Pos, id: Int) : Int {
        // Otherwise, check in the charcodes list
        if (id < charcodes.size) return charcodes[id]
        // Otherwise, return the char as color
        return board.getTileCol(xy)
    }
    @JvmStatic
    fun getColour(board: Board, xy: Pos): Int {
        val id = board.getTileId(xy)

        if (id >= 128) {
            return id - 128
        }
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

            CUSTOMTEXT, BLUETEXT, GREENTEXT, CYANTEXT, REDTEXT, PURPLETEXT, BROWNTEXT, BLACKTEXT -> {
                // For text kinds, the colour is based on the kind
                return getTextColour(id)
            }

            else -> {}
        }
        // Otherwise, use the given colour
        return board.getTileCol(xy)
    }

    @JvmStatic
    fun isFloor(tile: Tile): Boolean {
        val id = tile.id
        return id == EMPTY || id == FAKE || id == WATER
    }

    @JvmStatic
    fun getTextColour(id: Int): Int {
        if (id < CUSTOMTEXT) return -1
        if (id <= BLACKTEXT) return textcols[id - CUSTOMTEXT]
        if (id < 128) return -1
        return (id - 128)
    }

    fun getTextId(colour: Int): Int {
        var id = (colour / 16) - 1 + BLUETEXT
        if (id == (BLUETEXT - 1)) id = BLACKTEXT
        return id
    }

    const val TORCH: Int = 6

    const val STAR: Int = 15

    const val BULLET: Int = 18
    const val WATER: Int = 19

    const val HBLINKRAY: Int = 33

    const val SHARK: Int = 38

    const val VBLINKRAY: Int = 43

    const val CUSTOMTEXT: Int = 46
    const val BLUETEXT: Int = 47
    const val GREENTEXT: Int = 48
    const val CYANTEXT: Int = 49
    const val REDTEXT: Int = 50
    const val PURPLETEXT: Int = 51
    const val BROWNTEXT: Int = 52
    const val BLACKTEXT: Int = 53
}
