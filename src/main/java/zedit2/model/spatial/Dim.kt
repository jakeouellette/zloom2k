package zedit2.model.spatial

import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.Dimension

data class Dim(val w : Int, val h: Int) {
    init {
        if ((w == 0 && h != 0) || (w != 0 && h == 0)) {
            Logger.w(TAG) {"Warn: Should not have one negative value"}
        }
    }

    fun tile(zoomx : Double, zoomy: Double) : Dim = Dim(
        SpatialUtil.tileX(w, zoomx),
        SpatialUtil.tileY(h, zoomy))

    fun tile(zoom : Double) : Dim = tile(zoom, zoom)

    operator fun times(dim: Dim): Dim {
        return Dim(w * dim.w, h * dim.h)
    }

    operator fun times(pos: Pos): Pos {
        return Pos(w * pos.x, h * pos.y)
    }

    operator fun minus(dim: Dim): Dim {
        return Dim(w - dim.w, h - dim.h)
    }

    operator fun plus(dim: Dim): Dim {
        return Dim(w + dim.w, h + dim.h)
    }

    operator fun minus(scalar: Int): Dim {
        return Dim(w - scalar, h - scalar)
    }

    operator fun plus(scalar: Int): Dim {
        return Dim(w + scalar, h + scalar)
    }

    /**
     * Convert an array to a positional offset in a table.
     */
    fun fromArray(col: Int): Pos {
        return Pos(col % w, col / w)
    }

    val arrSize: Int = w * h
    val asDimension: Dimension = Dimension(w, h)
    val asPos: Pos = Pos(w, h)

    companion object {
        val EMPTY: Dim = Dim(0,0)
        val ONE_BY_ONE: Dim = Dim(1,1)
    }
}