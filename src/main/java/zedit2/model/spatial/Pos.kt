package zedit2.model.spatial

import zedit2.components.DosCanvas.Companion.CHAR_H
import zedit2.components.DosCanvas.Companion.CHAR_W
import zedit2.components.Util
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.Point
import kotlin.math.abs
import kotlin.math.max

data class Pos(val x: Int, val y: Int) {
    constructor(pt: Point) : this(pt.x, pt.y)

    init {
        if ((x > 0 && y < 0) || (x < 0 && y > 0)) {
            // This is known to happen currently due to the scroll selection logic.
//            Logger.w(TAG) {"Warn: Should not have one negative value, $x, $y"}
//            RuntimeException("out").printStackTrace()
        }
    }


    val dim : Dim
        get() = Dim(x, y)

    val isPositive : Boolean
        get() {
            if (x > -1 && y > -1) {
                return true
            }
            if ((x > 0 && y < 0)|| (y > 0 && x < 0)) {
                Logger.w(TAG) {"isPositive Warn: Should not have one negative value, $x, $y"}
            }
            return false
        }

    fun with(dim: Dim): Rec = Rec(x, y, dim.w, dim.h)

    fun tile(zoom : Double) : Pos = tile(zoom, zoom)

    fun tile(zoomx : Double, zoomy : Double) : Pos = Pos(
        Math.round(x * CHAR_W * zoomx).toInt(),
        Math.round(y * CHAR_H * zoomy).toInt())

    fun min(cursorPos: Pos): Pos {
        val mX = kotlin.math.min(cursorPos.x, this.x)
        val mY = kotlin.math.min(cursorPos.y, this.y)
        return Pos(mX, mY)
    }
    fun max(cursorPos: Pos): Pos {
        val mX = max(cursorPos.x, this.x)
        val mY = max(cursorPos.y, this.y)
        return Pos(mX, mY)
    }

    operator fun rem(boardDim: Dim): Pos = Pos(this.x % boardDim.w, this.y % boardDim.h)
    fun outside(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        return x < x1 || y < y1 || x > x2 || y > y2
    }

    fun outside(dim : Dim): Boolean {
        return x < 0 || y < 0 || x >= dim.w || y >= dim.h
    }
    fun inside(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        return x >= x1 && y >= y1 && x <= x2 && y <= y2
    }

    // Note for dim this is not inclusive
    fun inside(dim : Dim): Boolean {
        return x >= 0 && y >= 0 && x < dim.w && y < dim.h
    }

    operator fun times(boardDim: Dim): Pos {
        return Pos(x * boardDim.w, y * boardDim.h)
    }

    operator fun times(scalar: Int): Pos {
        return Pos(x * scalar, y * scalar)
    }

    operator fun plus(pos : Pos) : Pos {
        return Pos(x + pos.x, y + pos.y)
    }

    operator fun plus(dim : Dim) : Pos {
        return Pos(x + dim.w, y + dim.h)
    }

    operator fun plus(scalar : Int) : Pos {
        return Pos(x + scalar, y + scalar)
    }

    operator fun minus(pos: Pos): Pos {
        return Pos(x - pos.x, y - pos.y)
    }

    operator fun minus(dim: Dim): Pos {
        return Pos(x - dim.w, y - dim.h)
    }

    operator fun minus(scalar: Int): Pos {
        return Pos(x - scalar, y - scalar)
    }

    operator fun div(boardDim: Dim): Pos {
        return Pos(x / boardDim.w, y / boardDim.h)
    }

    operator fun div(scalar: Int): Pos {
        return Pos(x / scalar, y / scalar)
    }

    fun arrayIdx(width: Int): Int {
        return y * width + x
    }

    fun lt(dim: Dim): Boolean {
        return x < dim.w && y < dim.h
    }

    fun distInt(): Int {
        return max(abs(x.toDouble()), abs(y.toDouble())).toInt()
    }

    fun clamp(i: Int, pos: Pos): Pos {
        return Pos(Util.clamp(x, i, pos.x), Util.clamp(y, i, pos.y))
    }

    fun clamp(min: Pos, max: Pos): Pos {
        return Pos(Util.clamp(x, min.x, max.x), Util.clamp(y, min.y, max.y))
    }

    operator fun unaryMinus(): Pos {
        return Pos(-x, -y)

    }

    fun toDelta(): Pos {
        return Pos(if (x == 0) 0 else (x / abs(x.toDouble())).toInt(),
             if (y == 0) 0 else (y / abs(y.toDouble())).toInt())

    }

    companion object {
        val ZERO: Pos = Pos(0,0)
        val NEG_ONE: Pos = Pos(-1,-1)
        val END: Pos = Pos(999999999, 999999999)
        val HOME: Pos = Pos(-999999999, -999999999)
        val UP: Pos = Pos(0, -1)
        val DOWN: Pos = Pos(0, 1)
        val RIGHT: Pos = Pos(1, 0)
        val LEFT: Pos = Pos(-1, 0)
        val ALT_UP: Pos = UP * 10
        val ALT_DOWN : Pos = DOWN * 10
        val ALT_RIGHT : Pos = RIGHT * 10
        val ALT_LEFT : Pos = LEFT * 10
    }


}