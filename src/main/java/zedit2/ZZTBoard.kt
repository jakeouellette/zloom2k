package zedit2

import zedit2.CP437.toBytes
import zedit2.Util.getInt16
import zedit2.Util.getInt8
import zedit2.Util.readPascalString
import zedit2.Util.setInt16
import zedit2.Util.setInt8
import zedit2.Util.writePascalString
import zedit2.ZZTType.getChar
import zedit2.ZZTType.getColour

class ZZTBoard : Board {
    private var dark = false
    override var message: ByteArray = ByteArray(0)
        get() {
            return field
        }
        set(value) {
                field = value
                setDirty()
        }

    constructor(worldData: ByteArray, boardOffset: Int) : super() {
        val boardName = readPascalString(worldData[boardOffset + 2], worldData, boardOffset + 3, boardOffset + 53)

        setName(boardName)
        val boardPropertiesOffset = decodeRLE(worldData, boardOffset + 53)

        setShots(getInt8(worldData, boardPropertiesOffset + 0))
        isDark = worldData[boardPropertiesOffset + 1].toInt() != 0
        setExit(0, getInt8(worldData, boardPropertiesOffset + 2))
        setExit(1, getInt8(worldData, boardPropertiesOffset + 3))
        setExit(2, getInt8(worldData, boardPropertiesOffset + 4))
        setExit(3, getInt8(worldData, boardPropertiesOffset + 5))
        setRestartOnZap(worldData[boardPropertiesOffset + 6].toInt() == 1)

        val boardMsg = readPascalString(
            worldData[boardPropertiesOffset + 7], worldData,
            boardPropertiesOffset + 8, boardPropertiesOffset + 66
        )
        message = boardMsg

        setPlayerX(getInt8(worldData, boardPropertiesOffset + 66))
        setPlayerY(getInt8(worldData, boardPropertiesOffset + 67))
        setTimeLimit(getInt16(worldData, boardPropertiesOffset + 68).toShort().toInt())
        setStats(worldData, boardPropertiesOffset + 86, 8)

        clearDirty()
    }

    constructor()

    constructor(name: String?) {
        initialise()
        setName(toBytes(name!!))
        setShots(255)
        val playerStat = Stat(false)
        playerStat.cycle = 1
        playerStat.isPlayer = true
        val player = Tile(ZType.PLAYER, 0x1F, playerStat)
        setTile(29, 11, player)
    }

    override fun write(warning: CompatWarning?, worldData: ByteArray, boardOffset: Int) {
        val boardSize = currentSize
        setInt16(worldData, boardOffset, boardSize - 2)
        if (getName().size > 50) {
            warning!!.warn(1, "has a name that is >50 characters and will be truncated.")
        }
        writePascalString(getName(), worldData, boardOffset + 2, boardOffset + 3, boardOffset + 53)
        val boardPropertiesOffset = encodeRLE(worldData, boardOffset + 53)
        setInt8(worldData, boardPropertiesOffset + 0, getShots())
        setInt8(worldData, boardPropertiesOffset + 1, if (isDark) 1 else 0)
        setInt8(worldData, boardPropertiesOffset + 2, getExit(0))
        setInt8(worldData, boardPropertiesOffset + 3, getExit(1))
        setInt8(worldData, boardPropertiesOffset + 4, getExit(2))
        setInt8(worldData, boardPropertiesOffset + 5, getExit(3))
        setInt8(worldData, boardPropertiesOffset + 6, if (isRestartOnZap()) 1 else 0)
        writePascalString(
            message,
            worldData,
            boardPropertiesOffset + 7,
            boardPropertiesOffset + 8,
            boardPropertiesOffset + 66
        )
        setInt8(worldData, boardPropertiesOffset + 66, getPlayerX())
        setInt8(worldData, boardPropertiesOffset + 67, getPlayerY())
        setInt16(worldData, boardPropertiesOffset + 68, getTimeLimit())
        writeStats(warning!!, worldData, boardPropertiesOffset + 86)
    }

    override fun clone(): Board {
        val other = ZZTBoard()
        cloneInto(other)
        other.dark = dark
        other.message = message.clone()
        return other
    }

    override val isSuperZZT: Boolean
        get() = false

    override var isDark: Boolean
        get() = dark
        set(dark) {
            this.dark = dark
            setDirty()
        }


    override var cameraX: Int
        get() = 0
        set(x) {}
    override var cameraY: Int
        get() = 0
        set(y) {}

    override val currentSize: Int
        /**
         * Guess the board's current size (including length short)
         */
        get() {
            var size = 53 // header
            size += rLESize // rle data
            size += 88 // board properties
            size += statsSize
            return size
        }

    override fun drawCharacter(cols: ByteArray?, chars: ByteArray?, pos: Int, x: Int, y: Int) {
        cols!![pos] = getColour(this, x, y).toByte()
        chars!![pos] = getChar(this, x, y).toByte()
    }

    override fun isEqualTo(other: Board): Boolean {
        if (other !is ZZTBoard) return false
        if (!super.isEqualTo(other)) return false

        val zztOther = other
        if (dark != zztOther.dark) return false
        return message.contentEquals(zztOther.message)
    }

    override val width: Int = 60
        get() = field
    override val height: Int = 25
        get() = field
}
