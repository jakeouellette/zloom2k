package zedit2

import zedit2.Util.getInt16
import zedit2.Util.getUInt16
import zedit2.Util.setInt16
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*

abstract class WorldData(@JvmField protected var data: ByteArray) {
    var isDirty: Boolean = false

    @Throws(IOException::class)
    fun write(file: File) {
        Files.write(file.toPath(), data)
    }

    val size: Int
        get() = data.size

    abstract val isSuperZZT: Boolean
    abstract var name: ByteArray
    @Throws(WorldCorruptedException::class)
    abstract fun getBoard(boardIdx: Int): Board
    abstract val numFlags: Int
    abstract fun getFlag(flag: Int): ByteArray
    abstract fun setFlag(flag: Int, str: ByteArray)
    abstract fun boardListOffset(): Int

    var numBoards: Int
        get() = getInt16(data, 2)
        set(value) {
            setInt16(data, 2, value)
            isDirty = true
        }
    var ammo: Int
        get() = getInt16(data, 4)
        set(value) {
            setInt16(data, 4, value)
            isDirty = true
        }
    var gems: Int
        get() = getInt16(data, 6)
        set(value) {
            setInt16(data, 6, value)
            isDirty = true
        }
    var health: Int
        get() = getInt16(data, 15)
        set(value) {
            setInt16(data, 15, value)
            isDirty = true
        }
    var currentBoard: Int
        get() = getInt16(data, 17)
        set(value) {
            setInt16(data, 17, value)
            isDirty = true
        }

    fun getKey(keyIdx: Int): Boolean {
        if (keyIdx < 0 || keyIdx > 6) throw IndexOutOfBoundsException("Invalid key value")
        return data[keyIdx + 8].toInt() != 0
    }

    fun setKey(keyIdx: Int, haveKey: Boolean) {
        if (keyIdx < 0 || keyIdx > 6) throw IndexOutOfBoundsException("Invalid key value")
        data[keyIdx + 8] = if (haveKey) 1.toByte() else 0.toByte()
        isDirty = true
    }

    abstract var torches: Int
    abstract var torchTimer: Int
    abstract var energiser: Int
    abstract var score: Int
    abstract var timeSeconds: Int
    abstract var timeTicks: Int
    abstract var locked: Boolean
    abstract var z: Int

    protected fun findBoardOffset(boardIdx: Int): Int {
        if (boardIdx < 0 || boardIdx > (numBoards + 1)) throw IndexOutOfBoundsException("Invalid board index of " + boardIdx + ". Must be 0 <= boardIdx <= " + (numBoards + 1))
        var currentOffset = boardListOffset()
        for (i in 0 until boardIdx) {
            val boardSize = getUInt16(data, currentOffset)
            currentOffset += boardSize + 2
        }
        return currentOffset
    }

    fun setBoard(warning: CompatWarning, boardIdx: Int, board: Board) {
        val currentOffset = findBoardOffset(boardIdx)
        val currentBoardSize = if (data.size == currentOffset) {
            0
        } else {
            getUInt16(data, currentOffset) + 2
        }
        val newBoardSize = board.currentSize
        if (newBoardSize > 65535) {
            warning.warn(2, "is over 65535 bytes and cannot be saved in the ZZT format.")
            return
        } else if (newBoardSize > 32767) {
            warning.warn(1, "is over 32767 bytes and will require a limitation-removing port to load properly.")
        } else if (newBoardSize > 20000) {
            warning.warn(1, "is over 20000 bytes and may cause memory problems in ZZT.")
        }

        val currentBoardAfter = currentBoardSize + currentOffset
        val newBoardAfter = newBoardSize + currentOffset
        // We want to copy everything from currentBoardAfter on to newBoardAfter
        val newData = ByteArray(data.size - currentBoardSize + newBoardSize)

        // Copy everything up to the board over
        System.arraycopy(data, 0, newData, 0, currentOffset)
        // Write the new board
        board.write(warning, newData, currentOffset)
        // Copy everything from after the board over
        val lengthAfterBoard = data.size - currentBoardAfter
        System.arraycopy(data, currentBoardAfter, newData, newBoardAfter, lengthAfterBoard)

        data = newData

        if (boardIdx > numBoards) {
            numBoards = boardIdx
        }
    }

    fun terminateWorld(boardIdx: Int) {
        val currentOffset = findBoardOffset(boardIdx)
        data = Arrays.copyOfRange(data, 0, currentOffset)
        numBoards = boardIdx - 1
    }

    fun clone(): WorldData {
        val newWorld = if (isSuperZZT) {
            SZZTWorldData(data.clone())
        } else {
            ZZTWorldData(data.clone())
        }
        newWorld.isDirty = isDirty
        return newWorld
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun loadWorld(file: File): WorldData {
            val data = Files.readAllBytes(file.toPath())
            val fmt = getInt16(data, 0)
            if (fmt == -1) return ZZTWorldData(data)
            if (fmt == -2) return SZZTWorldData(data)
            throw RuntimeException("Invalid or corrupted ZZT file.")
        }
    }
}
