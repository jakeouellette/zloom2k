package zedit2.model.spatial

data class Rec(val x : Int, val y: Int, val w: Int, val h : Int) {

    val toPos : Pair<Pos, Pos> = Pair(Pos(x, y), Pos(x + w, y + h))

    // Function to check if this rectangle overlaps with another rectangle
    fun overlapping(r: Rec): Boolean {
        if (x + w <= r.x || r.x + r.w <= x) return false
        if (y + h <= r.y || r.y + r.h <= y) return false
        return true
    }

    // Function to check if this rectangle contains a point
    fun overlapping(p: Pos): Boolean {
        return p.x in x until x + w && p.y in y until y + h
    }

    /** Clamp inside the rect */
    fun clampInside(r : Rec) : Rec {
        val nX = x.coerceIn(r.x, r.x + r.w)
        val nY = y.coerceIn(r.y, r.y + r.h)
        val nW = w.coerceAtMost(r.x + r.w - nX)
        val nH = h.coerceAtMost(r.y + r.h - nY)
        return Rec(nX, nY, nW, nH)
    }

        object companion {
            fun from(a : Pos, b : Pos) : Rec {
                    val minPosX = kotlin.math.min(a.x, b.x)
                    val minPosY = kotlin.math.min(a.y, b.y)
                    val maxPosX = kotlin.math.max(a.x, b.x)
                    val maxPosY = kotlin.math.max(a.y, b.y)
                    return Rec(minPosX, minPosY, maxPosX - minPosX, maxPosY-minPosY)
            }
        }
}