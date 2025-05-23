package zedit2.model

import zedit2.*
import zedit2.components.DosCanvas
import zedit2.components.GlobalEditor
import zedit2.components.Util
import zedit2.components.WorldEditor
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import zedit2.util.CP437
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import zedit2.util.ZType
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*

abstract class Board {
    private var isCorrupted = false
    // TODO(jakeouellette): Must these be lateinit?
    private lateinit var bid: IntArray
    private lateinit var bco: IntArray
    private lateinit var name: ByteArray
    private var shots = 0
    private var exits = IntArray(4)
    private var restartOnZap = false
    private var playerPos = Pos(0, 0)
    private var timeLimit = 0
    private var stats = ArrayList<Stat>()
    private var rleSizeSaved = 0
    private var statsSizeSaved = 0
    var isDirty: Boolean = false
        private set
    private var needsFinalisation = false
    var dirtyTimestamp: Long = 0
        private set

    abstract val isSuperZZT: Boolean
    abstract val dim : Dim
    abstract var isDark: Boolean
    abstract var message: ByteArray
    abstract var cameraPos : Pos

    protected fun initialise() {
        val d = dim
        bid = IntArray(d.arrSize)
        bco = IntArray(d.arrSize)
    }

    @Throws(WorldCorruptedException::class)
    protected fun decodeRLE(worldData: ByteArray, dataOffset: Int): Int {
        var dataOffset = dataOffset
        initialise()

        val d = dim

        var pos = 0
        try {
            while (pos < d.arrSize) {
                var len = Util.getInt8(worldData, dataOffset)
                if (len == 0) len = 256
                for (i in 0 until len) {
                    bid[pos] = Util.getInt8(worldData, dataOffset + 1)
                    bco[pos] = Util.getInt8(worldData, dataOffset + 2)
                    pos++
                }
                dataOffset += 3
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw WorldCorruptedException("RLE decoding error")
        }
        dirtyRLE()
        return dataOffset
    }

    protected fun encodeRLE(worldData: ByteArray, dataOffset: Int): Int {
        val d = dim
        var runPos = dataOffset
        // Write our initial run (0, 0, 0)
        Util.setInt8(worldData, runPos + 0, 0)
        Util.setInt8(worldData, runPos + 1, 0)
        Util.setInt8(worldData, runPos + 2, 0)

        for (i in 0 until d.arrSize) {
            val currBid = bid[i] and 0xFF
            val currBco = bco[i] and 0xFF

            val runLen = Util.getInt8(worldData, runPos + 0)
            val runBid = Util.getInt8(worldData, runPos + 1)
            val runBco = Util.getInt8(worldData, runPos + 2)
            if (currBid == runBid && currBco == runBco && runLen < 255) {
                Util.setInt8(worldData, runPos + 0, Util.getInt8(worldData, runPos + 0) + 1)
            } else {
                if (runLen != 0) runPos += 3
                Util.setInt8(worldData, runPos + 0, 1)
                Util.setInt8(worldData, runPos + 1, currBid)
                Util.setInt8(worldData, runPos + 2, currBco)
            }
        }
        runPos += 3
        return runPos
    }

    private fun dirtyRLE() {
        rleSizeSaved = 0
        setDirty()
    }

    fun dirtyStats() {
        statsSizeSaved = 0
        setDirty()
    }

    protected val rLESize: Int
        get() {
            if (rleSizeSaved == 0) {
                val d = dim
                var runs = 0
                var runBid = -1
                var runBco = -1
                var runLen = 0

                for (i in 0 until d.arrSize) {
                    val currBid = bid[i] and 0xFF
                    val currBco = bco[i] and 0xFF
                    if (currBid == runBid && currBco == runBco && runLen < 255) {
                        runLen++
                    } else {
                        runs++
                        runBid = currBid
                        runBco = currBco
                        runLen = 1
                    }
                }
                rleSizeSaved = runs * 3
            }
            return rleSizeSaved
        }

    protected val statsSize: Int
        get() {
            finalisationCheck()
            if (statsSizeSaved == 0) {
                for (stat in stats) {
                    statsSizeSaved += stat.statSize
                }
            }
            return statsSizeSaved
        }

    abstract val currentSize: Int

    fun getName(): ByteArray {
        return name
    }

    fun setName(name: ByteArray) {
        this.name = name
        setDirty()
    }

    fun getShots(): Int {
        return shots
    }

    fun setShots(shots: Int) {
        this.shots = shots
        setDirty()
    }

    fun getExit(exit: Int): Int {
        return exits[exit]
    }

    fun setExit(exit: Int, `val`: Int) {
        exits[exit] = `val`
        setDirty()
    }

    fun isRestartOnZap(): Boolean {
        return restartOnZap
    }

    fun setRestartOnZap(restartOnZap: Boolean) {
        this.restartOnZap = restartOnZap
        setDirty()
    }

    fun getPlayerX(): Int {
        return playerPos.x
    }

    fun setPlayerX(x: Int) {
        this.playerPos = Pos(x, playerPos.y)
        setDirty()
    }

    fun getPlayerY(): Int {
        return playerPos.y
    }

    fun setPlayerY(y: Int) {
        this.playerPos = Pos(playerPos.x, y)
        setDirty()
    }

    fun getTimeLimit(): Int {
        return timeLimit
    }

    fun setTimeLimit(timeLimit: Int) {
        this.timeLimit = timeLimit
        setDirty()
    }

    val statCount: Int
        get() = stats.size

    fun setStats(worldData: ByteArray, offset: Int, padding: Int) {
        var offset = offset
        val statCount = Util.getInt16(worldData, offset)
        offset += 2
        stats.clear()

        for (i in 0..statCount) {
            val stat = Stat(worldData, offset, padding, i)
            stat.isPlayer = i == 0
            offset += stat.statSize
            stats.add(stat)
        }
    }

    protected fun writeStats(warning: CompatWarning, worldData: ByteArray, offset: Int) {
        var offset = offset
        finalisationCheck()

        if (warning.isSuperZZT) {
            if (stats.size - 1 > 128) {
                warning.warn(1, "has over 128 stats, which may cause problems in Super ZZT.")
            }
        } else {
            if (stats.size > 254) {
                warning.warn(1, "has over 254 stats, and Weave 3 can't handle that. Be careful!")
            } else if (stats.size - 1 > 150) {
                warning.warn(
                    1,
                    "has over 150 stats- make sure to set 'other.maxstats = " + (stats.size - 1) + "' (or higher) in your CFG."
                )
            }
        }
        Util.setInt16(worldData, offset, stats.size - 1)
        offset += 2

        for (stat in stats) {
            offset = stat.write(worldData, offset)
        }
    }

    protected abstract fun drawCharacter(cols: ByteArray?, chars: ByteArray?, pos: Int, xy: Pos)

    fun getTileCol(pos : Pos): Int {
        return bco[pos.arrayIdx(dim.w)] and 0xFF
    }

    fun getTileId(pos : Pos): Int {
        return bid[pos.arrayIdx(dim.w)] and 0xFF
    }

    fun getStatsAt(pos : Pos): List<Stat> {
        val tileStats = ArrayList<Stat>()
        for (stat in stats) {
            // TODO(jakeouellette): Why is this pos + 1? Can we make this simpler?
            if (stat.pos == (pos + 1)) {
                tileStats.add(stat.clone())
            }
        }
        return tileStats
    }

    fun getTile(pos : Pos, copy: Boolean): Tile {
        val tile = Tile(getTileId(pos), getTileCol(pos), getStatsAt(pos))
        if (copy) {
            for (stat in tile.stats) {
                val visited = HashSet<Stat?>()
                var followStat = stat
                while (true) {
                    if (visited.contains(followStat)) {
                        // Failed to follow, remove the bind
                        stat.codeLength = 0
                        break
                    }
                    visited.add(followStat)
                    if (followStat.codeLength >= 0) {
                        // Now we are at the parent. We will steal its code, then set autobind on
                        if (followStat !== stat) {
                            stat.code = followStat.code
                            stat.isAutobind = true
                        }
                        break
                    } else {
                        val newId = followStat.codeLength * -1
                        if (newId < stats.size) {
                            followStat = stats[newId]
                        } else {
                            // Failed to follow, remove the bind
                            stat.codeLength = 0
                        }
                    }
                }
            }
        }
        return tile
    }

    fun setTile(pos : Pos, tile: Tile) {
        setTileDirect(pos, tile)
        finaliseStats()
    }

    fun setTileDirect(xy : Pos, tile: Tile) {
        val idx = xy.arrayIdx(dim.w)
        bid[idx] = tile.id.toByte().toInt()
        bco[idx] = tile.col.toByte().toInt()
        dirtyRLE()

        val statsInTile = HashMap<Int, Stat>()
        val statsToPut = HashSet<Stat?>()
        if (tile.stats != null) {
            for (stat in tile.stats) {
                statsToPut.add(stat)
                if (stat.statId != -1) {
                    statsInTile[stat.statId] = stat
                }
            }
        }

        val statsToDelete = ArrayList<Int>()
        for (i in stats.indices) {
            val stat = stats[i]
            if (stat.pos == xy + 1) {
                val newStat = statsInTile[stat.statId]
                if (newStat != null) {
                    statsInTile.remove(stat.statId)
                    statsToPut.remove(newStat)
                    stats[i] = newStat
                } else {
                    statsToDelete.add(i)
                }
            }
        }

        directDeleteStats(statsToDelete)

        for (stat in statsToPut) {
            val newStat = stat!!.clone()
            newStat.pos = xy + 1
            newStat.statId = -1
            stats.add(newStat)
        }

        needsFinalisation = true
    }

    fun directAddStat(stat: Stat) {
        stats.add(stat.clone())
        needsFinalisation = true
    }

    fun directReplaceStat(statId: Int, replacementStat: Stat): Boolean {
        if (stats[statId].isIdenticalTo(replacementStat)) return false
        stats[statId] = replacementStat.clone()
        needsFinalisation = true
        return true
    }

    fun directDeleteStats(statsToDelete: List<Int>): Boolean {
        if (statsToDelete.isEmpty()) return false
        val lastValue = -1
        for ((offset, statIdx) in statsToDelete.withIndex()) {
            if (statIdx <= lastValue) {
                throw RuntimeException("Stat list must be unique and ordered")
            }
            statDeleted(statIdx - offset)
            stats.removeAt(statIdx - offset)
        }
        needsFinalisation = true
        return true
    }

    fun moveStatTo(src: Int, destination: Int) {
        val stat = stats[src]
        if (src < destination) {
            for (i in src until destination) {
                stats[i] = stats[i + 1]
            }
            stats[destination] = stat
        } else {
            for (i in src downTo destination + 1) {
                stats[i] = stats[i - 1]
            }
            stats[destination] = stat
        }
        finaliseStats()
    }

    fun finaliseStats() {
        postProcessStats()
        needsFinalisation = false
    }

    private fun finalisationCheck() {
        if (needsFinalisation) {
            throw RuntimeException("finaliseStats not called after direct modifications")
        }
    }

    /**
     * Sets a tile without affecting stats
     */
    fun setTileRaw(tilePos : Pos, id: Int, col: Int) {
        val idx = tilePos.arrayIdx(dim.w)
        if (bid[idx] != id.toByte().toInt() || bco[idx] != col.toByte().toInt()) {
            bid[idx] = id.toByte().toInt()
            bco[idx] = col.toByte().toInt()
            dirtyRLE()
        }
    }

    private fun postProcessStats() {
        if (stats.isEmpty()) return

        val newStatList = arrayOfNulls<Stat>(stats.size)
        val placed = BooleanArray(stats.size)
        // stat 0 always goes in the same place
        newStatList[0] = stats[0]

        placed[0] = true

        // Go through the stats with specific IDs
        for (i in 1 until stats.size) {
            val stat = stats[i]
            if (stat.isSpecifyId) {
                val preferredPos = stat.order
                if (preferredPos >= 1 && preferredPos < newStatList.size) {
                    if (newStatList[preferredPos] == null) {
                        newStatList[preferredPos] = stat
                        placed[i] = true
                    }
                }
            }
        }

        for (i in 1 until stats.size) {
            if (placed[i]) continue
            val stat = stats[i]
            if (stat.isSpecifyId) {
                var preferredPos = Util.clamp(stat.order, 1, newStatList.size - 1)
                var dir = 1
                while (true) {
                    if (newStatList[preferredPos] == null) {
                        newStatList[preferredPos] = stat
                        placed[i] = true
                        break
                    }
                    preferredPos += dir
                    if (preferredPos >= newStatList.size) {
                        dir *= -1
                        preferredPos += dir * 2
                    }
                }
            }
        }

        val notPlaced = ArrayList<Stat?>()

        for (i in 1 until stats.size) {
            if (placed[i]) continue
            val stat = stats[i]
            notPlaced.add(stat)
        }
        notPlaced.sortWith(Comparator.comparingInt { obj: Stat? -> obj!!.order })
        var notPlacedIdx = 0
        for (preferredPos in 1 until newStatList.size) {
            if (newStatList[preferredPos] != null) continue
            val stat = notPlaced[notPlacedIdx]
            newStatList[preferredPos] = stat
            notPlacedIdx++
        }

        val oldToNew = HashMap<Int, Int>()
        for (i in stats.indices) {
            val s = newStatList[i]
            val oldIdx = s!!.statId
            if (oldIdx != -1) {
                oldToNew[oldIdx] = i
            }

            stats[i] = s
            stats[i].statId = i
        }

        // Remapping stage
        for (stat in stats) {
            if (stat.follower != -1) {
                val newFollower = oldToNew[stat.follower]
                stat.follower = Objects.requireNonNullElse(newFollower, -1)!!
            }
            if (stat.leader != -1) {
                val newLeader = oldToNew[stat.leader]
                stat.leader = Objects.requireNonNullElse(newLeader, -1)!!
            }
            if (stat.codeLength < 0) {
                val newBind = oldToNew[-stat.codeLength]
                if (newBind == null) {
                    stat.codeLength = 0
                } else {
                    stat.codeLength = -newBind
                }
            }
        }
        // Rebinding stage
        val bindMap = HashMap<Int, ArrayList<Int>>()
        val followedSet = HashSet<Stat?>()
        for (i in stats.indices) {
            val stat = stats[i]
            var followStat = stat
            var codeOwnerIdx = i
            while (true) {
                if (followedSet.contains(followStat)) {
                    codeOwnerIdx = -1
                    break
                }
                followedSet.add(followStat)
                if (followStat.codeLength < 0) {
                    val bind = -stat.codeLength
                    if (bind < stats.size) {
                        codeOwnerIdx = bind
                        followStat = stats[codeOwnerIdx]
                    } else break
                } else break
            }
            if (codeOwnerIdx != -1 && codeOwnerIdx != i) {
                if (!bindMap.containsKey(codeOwnerIdx)) {
                    val list = ArrayList<Int>()
                    list.add(codeOwnerIdx)
                    bindMap[codeOwnerIdx] = list
                }
                val list = bindMap[codeOwnerIdx]!!
                list.add(i)
            }
        }
        for (codeOwnerIdx in bindMap.keys) {
            val list = bindMap[codeOwnerIdx]!!
            // TODO(jakeout): This was sorted by null?
//            list.sortWith(null)
            val code = stats[codeOwnerIdx].code
            val newOwner = list[0]
            for (i in list) {
                stats[i].codeLength = -newOwner
            }
            stats[newOwner].code = code
        }
        // Autobinding stage
        val autoBind = HashMap<String, Int>()
        for (i in 1 until stats.size) {
            if (stats[i].codeLength > 0) {
                val code = CP437.toUnicode(stats[i].code)
                if (autoBind.containsKey(code)) {
                    if (stats[i].isAutobind) {
                        // Bind to the earlier object
                        stats[i].codeLength = autoBind[code]!! * -1
                        //System.out.printf("stat #%d autobound with #%d\n", i, autoBind.get(code));
                    }
                } else {
                    autoBind[code] = i
                }
            }
        }

        dirtyStats()
    }

    fun drawToCanvas(canvas: DosCanvas, offset : Pos, pos : Pos, pos2 : Pos, showing: Int) {
        Logger.i(TAG) { "Drawing to canvas, $offset, $pos, $pos2, $showing" }
        finalisationCheck()

        val wh = (pos2 - pos + 1).dim
        val arrSize = wh.arrSize
        //System.out.printf("Draw %d x %d\n", width, height);
        val cols = ByteArray(arrSize)
        val chars = ByteArray(arrSize)
        var show: ByteArray? = null
        if (showing != WorldEditor.SHOW_NOTHING) {
            show = ByteArray(arrSize)

            if (showing == WorldEditor.SHOW_STATS) {
                for (stat in stats) {
                    val xy = stat.pos - 1 - pos
                    if (xy.inside(wh)) {
                        show[xy.arrayIdx(wh.w)] = showing.toByte()
                    }
                }
            }
        }
        for (dy in 0 until wh.h) {
            for (dx in 0 until wh.w) {
                val dxy = Pos(dx, dy)

                val idx = dxy.arrayIdx(wh.w)
                drawCharacter(cols, chars, idx, dxy + pos)
                if (showing != WorldEditor.SHOW_NOTHING) {
                    val bIdx = (dxy + pos).arrayIdx(this.dim.w)
                    val id = bid[bIdx]
                    when (showing) {
                        WorldEditor.SHOW_EMPTIES, WorldEditor.SHOW_EMPTEXTS -> if (id == ZType.EMPTY) {
                            show!![idx] = showing.toByte()
                            cols[idx] = bco[bIdx].toByte()
                        }

                        WorldEditor.SHOW_FAKES -> if (id == ZType.FAKE) show!![idx] = showing.toByte()
                        WorldEditor.SHOW_INVISIBLES -> if (id == ZType.INVISIBLE) show!![idx] = showing.toByte()
                        WorldEditor.SHOW_OBJECTS -> if (id == ZType.OBJECT) show!![idx] = showing.toByte()
                    }
                }
            }
        }
        // TODO: changed "showing" to true, it wasn't used inside?
        canvas.setData(wh, cols, chars, offset + pos, true, show)
    }

    fun setDirty() {
        isDirty = true
        dirtyTimestamp = GlobalEditor.timestamp
    }

    fun clearDirty() {
        isDirty = false
    }

    abstract fun write(warning: CompatWarning?, worldData: ByteArray, currentOffset: Int)

    fun getStat(idx: Int): Stat {
        return stats[idx]
    }

    abstract fun clone(): Board

    fun cloneInto(other: Board) {
        other.isCorrupted = isCorrupted
        other.bid = bid.clone()
        other.bco = bco.clone()
        other.name = name.clone()
        other.shots = shots
        Logger.i(TAG) { "exits: ${exits.joinToString()}"}
        other.exits = exits.clone()
        other.restartOnZap = restartOnZap
        other.playerPos = playerPos
        other.timeLimit = timeLimit

        other.isDark = isDark
        other.message = message
        other.cameraPos = cameraPos

        other.stats = ArrayList()
        for (stat in stats) other.stats.add(stat.clone())
        other.rleSizeSaved = rleSizeSaved
        other.statsSizeSaved = statsSizeSaved
        other.isDirty = isDirty
        other.needsFinalisation = needsFinalisation
        other.dirtyTimestamp = dirtyTimestamp
    }

    private fun statDeleted(statIdx: Int) {
        //System.out.println("Deleted stat " + statIdx);
        val stat = stats[statIdx]
        val statId = stat.statId

        val statPos = HashMap<Int, Int>()
        for (i in stats.indices) {
            statPos[stats[i].statId] = i
        }

        // First of all, does this stat have its own code or is it just another in the chain?
        var codeOwnerIdx = statIdx
        var codeOwnerId = statId
        while (true) {
            val cl = stats[codeOwnerIdx].codeLength
            if (cl >= 0) break // Reached the parent

            if (-cl >= stats.size) {
                // This is as far as we can go
                break
            } else {
                codeOwnerIdx = statPos[-cl]!!
                codeOwnerId = stats[codeOwnerIdx].statId
            }
        }

        // Fix binds and erase centipede links
        for (i in stats.indices) {
            if (stats[i].codeLength == -statId) {
                // Stat i is bound to statIdx.
                if (codeOwnerIdx == statIdx) {
                    // As statIdx is going away, stat i will become the new owner
                    val targetCodeLen = stats[statIdx].codeLength
                    val targetCode = stats[statIdx].code
                    stats[i].code = targetCode
                    if (targetCodeLen < 0) stats[i].codeLength = targetCodeLen
                    codeOwnerIdx = i
                    codeOwnerId = stats[i].statId
                } else {
                    // Otherwise, point to the owner
                    stats[i].codeLength = -codeOwnerId
                }
            }
            if (stats[i].follower == statId) {
                // Break link
                stats[i].follower = -1
            }
            if (stats[i].leader == statId) {
                // Break link
                stats[i].leader = -1
            }
        }
        dirtyStats()
    }

    @Throws(IOException::class)
    fun saveTo(file: File) {
        val w = CompatWarning(isSuperZZT)
        val data = ByteArray(currentSize)
        write(w, data, 0)
        Files.write(file.toPath(), data)
    }

    @Throws(IOException::class, WorldCorruptedException::class)
    fun loadFrom(file: File) {
        val loadedBoard: Board
        val data = Files.readAllBytes(file.toPath())

        loadedBoard = if (isSuperZZT) {
            SZZTBoard(data, 0)
        } else {
            ZZTBoard(data, 0)
        }
        loadedBoard.cloneInto(this)
        setDirty()
    }

    /**
     * Compare for equality, ignoring 'dirty' flag
     * @param other
     * @return
     */
    open fun isEqualTo(other: Board): Boolean {
        if (isCorrupted != other.isCorrupted) return false
        if (!bid.contentEquals(other.bid)) return false
        if (!bco.contentEquals(other.bco)) return false
        if (!name.contentEquals(other.name)) return false
        if (!exits.contentEquals(other.exits)) return false
        if (restartOnZap != other.restartOnZap) return false
        if (playerPos != other.playerPos) return false
        if (timeLimit != other.timeLimit) return false
        if (stats.size != other.stats.size) return false
        for (i in stats.indices) {
            if (!stats[i].isIdenticalTo(other.stats[i])) return false
        }
        return true
    }

    fun timestampEquals(other: Board): Boolean {
        return dirtyTimestamp == other.dirtyTimestamp
    }
}
