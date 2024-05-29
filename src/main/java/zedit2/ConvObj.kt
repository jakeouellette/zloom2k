package zedit2

class ConvObj(val rmseImprovement: Double, val x: Int, val y: Int) : Comparable<ConvObj> {
    override fun compareTo(o: ConvObj): Int {
        return if (rmseImprovement < o.rmseImprovement) 1
        else if (rmseImprovement > o.rmseImprovement) -1
        else if (y < o.y) -1
        else if (y > o.y) 1
        else Integer.compare(x, o.x)
    }
}
