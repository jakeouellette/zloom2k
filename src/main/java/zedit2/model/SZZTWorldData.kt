package zedit2.model

import zedit2.components.Util

class SZZTWorldData(data: ByteArray) : WorldData(data) {
    override val isSuperZZT : Boolean = true

    override var name : ByteArray = Util.readPascalString(data[27], data, 28, 48)
        set(value) {
            Util.writePascalString(value, data, 27, 28, 48)
            isDirty = true
        }

    override var torches: Int
        get() {
            return throw UnsupportedOperationException("Cannot be used on Super ZZT worlds")
        }
        set(value) {
            return throw UnsupportedOperationException("Cannot be used on Super ZZT worlds")
        }

    override var torchTimer : Int
        get() {
            return throw UnsupportedOperationException("Cannot be used on Super ZZT worlds")
        }
        set(value) {
            return throw UnsupportedOperationException("Cannot be used on Super ZZT worlds")
        }


    override var energiser: Int
        get() { return Util.getInt16(data, 21)
        }
        set(value) {
                Util.setInt16(data, 21, value)
                isDirty = true
        }
    override var score: Int
        get() {
            return Util.getInt16(data, 25)
        }
        set(value) {
            Util.setInt16(data, 25, value)
            isDirty = true
        }

    override var timeSeconds: Int
        get() {
            return Util.getInt16(data, 384)
        }
        set(value) {
            Util.setInt16(data, 384, value)
            isDirty = true
        }

    override var timeTicks: Int
        get() {
            return Util.getInt16(data, 386)
        }
        set(value) {
            Util.setInt16(data, 386, value)
            isDirty = true
        }

    override var locked: Boolean
        get() {

                return data[388].toInt() != 0

        }
        set(value) {
            data[388] = if (locked) 1.toByte() else 0.toByte()
            isDirty = true
        }

    override var z: Int
        get() {
            return Util.getInt16(data, 389)
        }
        set(value) {
        Util.setInt16(data, 389, value)
        isDirty = true
    }

    override val numFlags: Int = 16

    override fun getFlag(flag: Int): ByteArray {
        if (flag < 0 || flag >= numFlags) throw IndexOutOfBoundsException("Invalid flag index")
        val flagOffset = 48 + (21 * flag)
        return Util.readPascalString(data[flagOffset], data, flagOffset + 1, flagOffset + 21)
    }

    override fun setFlag(flag: Int, str: ByteArray) {
        if (flag < 0 || flag >= numFlags) throw IndexOutOfBoundsException("Invalid flag index")
        val flagOffset = 48 + (21 * flag)
        Util.writePascalString(str, data, flagOffset, flagOffset + 1, flagOffset + 21)
        isDirty = true
    }

    override fun boardListOffset(): Int {
        return WORLD_HEADER_SIZE
    }

    @Throws(WorldCorruptedException::class)
    override fun getBoard(boardIdx: Int): Board {
        return SZZTBoard(data, findBoardOffset(boardIdx))
    }

    companion object {
        private const val WORLD_HEADER_SIZE = 1024

        @JvmStatic
        fun createWorld(): SZZTWorldData {
            val bytes = ByteArray(WORLD_HEADER_SIZE)
            Util.setInt16(bytes, 0, -2)

            val world = SZZTWorldData(bytes)
            world.numBoards = -1
            world.health = 100
            world.z = -1
            val w = CompatWarning(true)
            world.setBoard(w, 0, SZZTBoard("Title screen"))
            world.setBoard(w, 1, SZZTBoard("First board"))
            world.currentBoard = 1
            world.isDirty = false
            return world
        }
    }
}
