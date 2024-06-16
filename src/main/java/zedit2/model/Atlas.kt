package zedit2.model

import zedit2.components.Util.pair
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import java.util.ArrayList

class Atlas(@JvmField val dim : Dim, @JvmField val grid: Array<IntArray>) {
    fun search(idx: Int): Pos? {
        for (y in 0 until this.dim.h) {
            for (x in 0 until this.dim.w) {
                if (this.grid[y][x] == idx) {
                    return Pos(x, y)
                }
            }
        }
        return null
    }
}
