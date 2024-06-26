package zedit2.model

import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import zedit2.util.SZZTType
import zedit2.util.ZZTType

class BufferBoard(override val isSuperZZT: Boolean, override var dim : Dim) : Board() {
    init {
        initialise()
    }

    override var isDark: Boolean
        get() = false
        set(dark) {}

    override var message: ByteArray
        get() = ByteArray(0)
        set(message) {}

    override var cameraPos: Pos
        get() = Pos(0, 0)
        set(v) {}

    override val currentSize: Int
        get() {
            throw UnsupportedOperationException("Invalid for buffer boards")
        }

    public override fun drawCharacter(cols: ByteArray?, chars: ByteArray?, posI: Int, xy: Pos) {
        if (!isSuperZZT) {
            cols!![posI] = ZZTType.getColour(this, xy).toByte()
            chars!![posI] = ZZTType.getChar(this, xy).toByte()
        } else {
            cols!![posI] = SZZTType.getColour(this, xy).toByte()
            chars!![posI] = SZZTType.getChar(this, xy).toByte()
        }
    }

    override fun write(warning: CompatWarning?, worldData: ByteArray, currentOffset: Int) {
        throw UnsupportedOperationException("Invalid for buffer boards")
    }

    override fun clone(): Board {
        val other: Board = BufferBoard(isSuperZZT, dim)
        cloneInto(other)
        return other
    }

    override fun isEqualTo(other: Board): Boolean {
        if (other !is BufferBoard) return false
        if (!super.isEqualTo(other)) return false

        val bufOther = other
        if (dim != bufOther.dim) return false
        return isSuperZZT == bufOther.isSuperZZT
    }
}
