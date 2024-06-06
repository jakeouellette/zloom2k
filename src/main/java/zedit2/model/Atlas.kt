package zedit2.model

import zedit2.components.Util.pair
import java.util.ArrayList

class Atlas(@JvmField val w: Int, @JvmField val h: Int, @JvmField val grid: Array<IntArray>) {
    fun search(idx: Int): ArrayList<Int>? {
        for (y in 0 until this.h) {
            for (x in 0 until this.w) {
                if (this.grid[y][x] == idx) {
                    return pair(x, y)
                }
            }
        }
        return null
    }
}
