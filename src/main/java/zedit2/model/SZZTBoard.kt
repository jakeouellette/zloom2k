package zedit2.model

import zedit2.util.SZZTType
import zedit2.util.ZType
import zedit2.util.CP437.toBytes
import zedit2.components.Util

class SZZTBoard : Board {

    constructor(worldData: ByteArray, boardOffset: Int) : super() {
        val boardName = Util.readPascalString(
            worldData[boardOffset + 2], worldData,
            boardOffset + 3, boardOffset + 63
        )

        setName(boardName)
        val boardPropertiesOffset = decodeRLE(worldData, boardOffset + 63)

        setShots(Util.getInt8(worldData, boardPropertiesOffset + 0))
        setExit(0, Util.getInt8(worldData, boardPropertiesOffset + 1))
        setExit(1, Util.getInt8(worldData, boardPropertiesOffset + 2))
        setExit(2, Util.getInt8(worldData, boardPropertiesOffset + 3))
        setExit(3, Util.getInt8(worldData, boardPropertiesOffset + 4))
        setRestartOnZap(worldData[boardPropertiesOffset + 5].toInt() == 1)

        setPlayerX(Util.getInt8(worldData, boardPropertiesOffset + 6))
        setPlayerY(Util.getInt8(worldData, boardPropertiesOffset + 7))
        cameraX = Util.getInt16(worldData, boardPropertiesOffset + 8)
        cameraY = Util.getInt16(worldData, boardPropertiesOffset + 10)
        setTimeLimit(Util.getInt16(worldData, boardPropertiesOffset + 12).toShort().toInt())
        setStats(worldData, boardPropertiesOffset + 28, 0)

        clearDirty()
    }

    constructor()

    constructor(name: String?) {
        initialise()
        setName(toBytes(name!!))
        setShots(255)
        val playerStat = Stat(true)
        playerStat.cycle = 1
        playerStat.isPlayer = true
        val player = Tile(ZType.PLAYER, 0x1F, playerStat)
        setTile(48, 40, player)
    }

    override fun write(warning: CompatWarning?, worldData: ByteArray, boardOffset: Int) {
        val boardSize = currentSize
        Util.setInt16(worldData, boardOffset, boardSize - 2)
        if (getName().size > 60) {
            warning!!.warn(1, "has a name that is >60 characters and will be truncated.")
        }
        Util.writePascalString(getName(), worldData, boardOffset + 2, boardOffset + 3, boardOffset + 63)
        val boardPropertiesOffset = encodeRLE(worldData, boardOffset + 63)
        Util.setInt8(worldData, boardPropertiesOffset + 0, getShots())
        Util.setInt8(worldData, boardPropertiesOffset + 1, getExit(0))
        Util.setInt8(worldData, boardPropertiesOffset + 2, getExit(1))
        Util.setInt8(worldData, boardPropertiesOffset + 3, getExit(2))
        Util.setInt8(worldData, boardPropertiesOffset + 4, getExit(3))
        Util.setInt8(worldData, boardPropertiesOffset + 5, if (isRestartOnZap()) 1 else 0)
        Util.setInt8(worldData, boardPropertiesOffset + 6, getPlayerX())
        Util.setInt8(worldData, boardPropertiesOffset + 7, getPlayerY())
        Util.setInt16(worldData, boardPropertiesOffset + 8, cameraX)
        Util.setInt16(worldData, boardPropertiesOffset + 10, cameraY)
        Util.setInt16(worldData, boardPropertiesOffset + 12, getTimeLimit())
        writeStats(warning!!, worldData, boardPropertiesOffset + 28)
    }

    override fun clone(): Board {
        val other = SZZTBoard()
        cloneInto(other)
        other.cameraX = cameraX
        other.cameraY = cameraY
        return other
    }

    override val isSuperZZT: Boolean
        get() = true

    override var isDark: Boolean
        get() = false
        set(dark) {}
    override var message: ByteArray
        get() = ByteArray(0)
        set(message) {}

    override var cameraX: Int = 0
        get() = field
        set(value) {
            field = value
            setDirty()
        }

    override var cameraY:Int = 0
        get() = field
        set(value) {
            field = value
            setDirty()
        }

    // TODO(jakeouellette): I don't set Dirty on these.
    override var width: Int = 0
        get() = field
        set(value) {
            field = value
        }

    override var height:Int = 0
        get() = field
        set(value) {
            field = value
        }

    override val currentSize: Int
        get() {
            var size = 63 // header
            size += rLESize // rle data
            size += 30 // board properties
            size += statsSize
            return size
        }

    override fun drawCharacter(cols: ByteArray?, chars: ByteArray?, pos: Int, x: Int, y: Int) {
        cols!![pos] = SZZTType.getColour(this, x, y).toByte()
        chars!![pos] = SZZTType.getChar(this, x, y).toByte()
    }

    override fun isEqualTo(other: Board): Boolean {
        if (other !is SZZTBoard) return false
        if (!super.isEqualTo(other)) return false

        val szztOther = other
        if (cameraX != szztOther.cameraX) return false
        return cameraY == szztOther.cameraY
    }

    companion object {
        val width: Int = 96
        val height: Int = 80
    }
}
