package zedit2

class BufferBoard(override val isSuperZZT: Boolean, override var width: Int, override var height: Int) : Board() {
    init {
        initialise()
    }

    override var isDark: Boolean
        get() = false
        set(dark) {}

    override var message: ByteArray
        get() = ByteArray(0)
        set(message) {}

    override var cameraX: Int
        get() = 0
        set(x) {}

    override var cameraY: Int
        get() = 0
        set(y) {}

    override val currentSize: Int
        get() {
            throw UnsupportedOperationException("Invalid for buffer boards")
        }

    public override fun drawCharacter(cols: ByteArray?, chars: ByteArray?, pos: Int, x: Int, y: Int) {
        if (!isSuperZZT) {
            cols!![pos] = ZZTType.getColour(this, x, y).toByte()
            chars!![pos] = ZZTType.getChar(this, x, y).toByte()
        } else {
            cols!![pos] = SZZTType.getColour(this, x, y).toByte()
            chars!![pos] = SZZTType.getChar(this, x, y).toByte()
        }
    }

    override fun write(warning: CompatWarning?, worldData: ByteArray, currentOffset: Int) {
        throw UnsupportedOperationException("Invalid for buffer boards")
    }

    override fun clone(): Board {
        val other: Board = BufferBoard(isSuperZZT, width, height)
        cloneInto(other)
        return other
    }

    override fun isEqualTo(other: Board): Boolean {
        if (other !is BufferBoard) return false
        if (!super.isEqualTo(other)) return false

        val bufOther = other
        if (width != bufOther.width) return false
        if (height != bufOther.height) return false
        return isSuperZZT == bufOther.isSuperZZT
    }
}
