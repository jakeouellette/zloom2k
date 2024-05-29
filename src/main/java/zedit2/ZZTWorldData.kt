package zedit2

import zedit2.Util.getInt16
import zedit2.Util.readPascalString
import zedit2.Util.setInt16
import zedit2.Util.writePascalString

class ZZTWorldData(data: ByteArray?) : WorldData(data!!) {
    override val isSuperZZT: Boolean
        get() = false

    override var name: ByteArray
        get() = readPascalString(data[29], data, 30, 50)
        set(str) {
            writePascalString(str!!, data, 29, 30, 50)
            isDirty = true
        }

    override var torches: Int
        get() = getInt16(data, 19)
        set(value) {
            setInt16(data, 19, value)
            isDirty = true
        }
    override var torchTimer: Int
        get() = getInt16(data, 21)
        set(value) {
            setInt16(data, 21, value)
            isDirty = true
        }
    override var energiser: Int
        get() = getInt16(data, 23)
        set(value) {
            setInt16(data, 23, value)
            isDirty = true
        }
    override var score: Int
        get() = getInt16(data, 27)
        set(value) {
            setInt16(data, 27, value)
            isDirty = true
        }
    override var timeSeconds: Int
        get() = getInt16(data, 260)
        set(value) {
            setInt16(data, 260, value)
            isDirty = true
        }
    override var timeTicks: Int
        get() = getInt16(data, 262)
        set(value) {
            setInt16(data, 262, value)
            isDirty = true
        }
    override var locked: Boolean
        get() = data[264].toInt() != 0
        set(isLocked) {
            data[264] = if (isLocked) 1.toByte() else 0.toByte()
            isDirty = true
        }
    override var z: Int
        get() {
            throw UnsupportedOperationException("Cannot be used on ZZT worlds")
        }
        set(value) {
            throw UnsupportedOperationException("Cannot be used on ZZT worlds")
        }
    override val numFlags: Int
        get() = 10

    override fun getFlag(flag: Int): ByteArray {
        if (flag < 0 || flag >= numFlags) throw IndexOutOfBoundsException("Invalid flag index")
        val flagOffset = 50 + (21 * flag)
        return readPascalString(data[flagOffset], data, flagOffset + 1, flagOffset + 21)
    }

    override fun setFlag(flag: Int, str: ByteArray) {
        if (flag < 0 || flag >= numFlags) throw IndexOutOfBoundsException("Invalid flag index")
        val flagOffset = 50 + (21 * flag)
        writePascalString(str, data, flagOffset, flagOffset + 1, flagOffset + 21)
        isDirty = true
    }

    override fun boardListOffset(): Int {
        return WORLD_HEADER_SIZE
    }

    @Throws(WorldCorruptedException::class)
    override fun getBoard(boardIdx: Int): Board {
        return ZZTBoard(data, findBoardOffset(boardIdx))
    }

    companion object {
        private const val WORLD_HEADER_SIZE = 512
        fun createWorld(): ZZTWorldData {
            val bytes = ByteArray(WORLD_HEADER_SIZE)
            setInt16(bytes, 0, -1)

            val world = ZZTWorldData(bytes)
            world.numBoards = -1
            world.health = 100
            val w = CompatWarning(false)
            world.setBoard(w, 0, ZZTBoard("Title screen"))
            world.setBoard(w, 1, ZZTBoard("First board"))
            world.currentBoard = 1
            world.isDirty = false
            return world
        }
    }
}
