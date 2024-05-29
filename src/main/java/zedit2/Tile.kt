package zedit2

class Tile(@JvmField var id: Int, @JvmField var col: Int) {
    var stats: MutableList<Stat>

    constructor(id: Int, col: Int, stats: List<Stat>) : this(id, col) {
        val newStats = mutableListOf<Stat>()
        for (stat in stats) {
            newStats.add(stat.clone())
        }
        this.stats = newStats
    }

    constructor(id: Int, col: Int, stat: Stat) : this(id, col) {
        stats = arrayListOf(stat.clone())
    }

    init {
        stats = ArrayList()
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is Tile) return false
        val other = obj
        if (other.id != id || other.col != col) return false
        return other.stats == stats
    }

    fun addStat(newStat: Stat) {
        stats.add(newStat)
    }

    fun delStat(idx: Int) {
        stats.removeAt(idx)
    }

    // TODO(jakeouellette): Adjust this clone method
    fun clone(): Tile {
        return Tile(id, col, stats)
    }
}
