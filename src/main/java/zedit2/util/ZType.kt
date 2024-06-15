package zedit2.util

import zedit2.model.BufferBoard
import zedit2.model.Stat
import zedit2.model.Tile
import zedit2.model.spatial.Pos

open class ZType {
    companion object {
        private var zztTypes: HashMap<String, Int>? = null
        private var szztTypes: HashMap<String, Int>? = null

        fun getName(szzt: Boolean, id: Int): String {
            var name: String?
            if (id == -1) return "Unknown"
            name = if (!szzt) {
                ZZTType.getName(id)
            } else {
                SZZTType.getName(id)
            }
            if (name == null) {
                name = String.format("Unknown (%d)", id)
            }
            return name
        }

        fun getId(szzt: Boolean, name: String): Int {
            val types: HashMap<String, Int>?
            if (!szzt) {
                if (zztTypes == null) {
                    zztTypes = buildTypesMap(szzt)
                }
                types = zztTypes
            } else {
                if (szztTypes == null) {
                    szztTypes = buildTypesMap(szzt)
                }
                types = szztTypes
            }
            return types!!.getOrDefault(name, -1)
        }

        private fun buildTypesMap(szzt: Boolean): HashMap<String, Int> {
            val types = HashMap<String, Int>()
            for (i in 0..255) {
                types[getName(szzt, i)] = i
            }
            return types
        }

        fun getChar(szzt: Boolean, tile: Tile): Int {
            if (tile.id == -1) return throw UnsupportedOperationException("Id cannot be -1")
            val board = BufferBoard(szzt, 1, 1)
            board.setTile(Pos.ZERO, tile)
            return if (!szzt) {
                ZZTType.getChar(board, Pos.ZERO)
            } else {
                SZZTType.getChar(board, Pos.ZERO)
            }
        }

        fun getColour(szzt: Boolean, tile: Tile): Int {
            if (tile.id == -1) return 15
            val board = BufferBoard(szzt, 1, 1)
            board.setTile(Pos.ZERO, tile)
            return if (!szzt) {
                ZZTType.getColour(board, Pos.ZERO)
            } else {
                SZZTType.getColour(board, Pos.ZERO)
            }
        }

        fun isFloor(szzt: Boolean, tile: Tile?): Boolean {
            return if (!szzt) {
                ZZTType.isFloor(tile!!)
            } else {
                SZZTType.isFloor(tile!!)
            }
        }

        fun getTextColour(szzt: Boolean, id: Int): Int {
            return if (!szzt) {
                ZZTType.getTextColour(id)
            } else {
                SZZTType.getTextColour(id)
            }
        }

        fun isText(szzt: Boolean, id: Int): Boolean {
            return getTextColour(szzt, id) != -1
        }

        fun convert(input: Tile, szzt: Boolean): Tile {
            var stats: List<Stat>? = input.stats
            val newPadding = ByteArray(if (szzt) 0 else 8)
            for (stat in stats!!) {
                stat.padding = newPadding
            }
            var tileId = input.id
            if (szzt) { // ZZT to SZZT
                when (tileId) {
                    ZZTType.TORCH, ZZTType.SHARK, ZZTType.WATER -> {
                        tileId = EMPTY
                        stats = null
                    }

                    ZZTType.STAR -> tileId = SZZTType.STAR
                    ZZTType.BULLET -> tileId = SZZTType.BULLET
                    ZZTType.HBLINKRAY -> tileId = SZZTType.HBLINKRAY
                    ZZTType.VBLINKRAY -> tileId = SZZTType.VBLINKRAY
                    else -> if (tileId >= ZZTType.CUSTOMTEXT && tileId <= ZZTType.BLACKTEXT) tileId =
                        tileId - ZZTType.CUSTOMTEXT + SZZTType.CUSTOMTEXT
                }
            } else {
                when (tileId) {
                    SZZTType.LAVA, SZZTType.FLOOR, SZZTType.WATERN, SZZTType.WATERE, SZZTType.WATERW, SZZTType.WATERS, SZZTType.ROTON, SZZTType.DRAGONPUP, SZZTType.PAIRER, SZZTType.SPIDER, SZZTType.WEB, SZZTType.STONE -> {
                        tileId = EMPTY
                        stats = null
                    }

                    SZZTType.STAR -> tileId = ZZTType.STAR
                    SZZTType.BULLET -> tileId = ZZTType.BULLET
                    SZZTType.HBLINKRAY -> tileId = ZZTType.HBLINKRAY
                    SZZTType.VBLINKRAY -> tileId = ZZTType.VBLINKRAY
                    else -> if (tileId >= SZZTType.CUSTOMTEXT && tileId <= SZZTType.BLACKTEXT) tileId =
                        tileId - SZZTType.CUSTOMTEXT + ZZTType.CUSTOMTEXT
                }
            }

            return Tile(tileId, input.col, stats!!)
        }

        fun convert(input: Array<Tile>, szzt: Boolean): Array<Tile> {
            val outputTiles = arrayOfNulls<Tile>(input.size)
            for (i in input.indices) {
                outputTiles[i] = convert(input[i], szzt)
            }
            // TODO(jakeouellette): Better handle tile nullability
            return outputTiles.map { tile : Tile? -> tile!!}.toTypedArray()
        }

        const val EMPTY: Int = 0
        const val BOARDEDGE: Int = 1
        const val MESSENGER: Int = 2
        const val MONITOR: Int = 3
        const val PLAYER: Int = 4
        const val AMMO: Int = 5

        const val GEM: Int = 7
        const val KEY: Int = 8
        const val DOOR: Int = 9
        const val SCROLL: Int = 10
        const val PASSAGE: Int = 11
        const val DUPLICATOR: Int = 12
        const val BOMB: Int = 13
        const val ENERGIZER: Int = 14

        const val CLOCKWISE: Int = 16
        const val COUNTER: Int = 17

        const val FOREST: Int = 20
        const val SOLID: Int = 21
        const val NORMAL: Int = 22
        const val BREAKABLE: Int = 23
        const val BOULDER: Int = 24
        const val SLIDERNS: Int = 25
        const val SLIDEREW: Int = 26
        const val FAKE: Int = 27
        const val INVISIBLE: Int = 28
        const val BLINKWALL: Int = 29
        const val TRANSPORTER: Int = 30
        const val LINE: Int = 31
        const val RICOCHET: Int = 32

        const val BEAR: Int = 34
        const val RUFFIAN: Int = 35
        const val OBJECT: Int = 36
        const val SLIME: Int = 37

        const val SPINNINGGUN: Int = 39
        const val PUSHER: Int = 40
        const val LION: Int = 41
        const val TIGER: Int = 42

        const val HEAD: Int = 44
        const val SEGMENT: Int = 45
    }
}
