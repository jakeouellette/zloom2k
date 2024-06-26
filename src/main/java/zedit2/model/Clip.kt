package zedit2.model

import zedit2.components.Util
import zedit2.model.spatial.Dim
import java.util.*

class Clip(val dim : Dim,
           val tiles: Array<Tile>,
           val isSzzt: Boolean) {

    companion object {
        @JvmStatic
        fun encode(dim: Dim, tiles: Array<Tile>, szzt: Boolean): String {
            // Calculate size
            var size = 9
            for (tile in tiles) {
                size += tileSize(tile)
            }
            val data = ByteArray(size)
            Util.setInt32(data, 0, dim.w)
            Util.setInt32(data, 4, dim.h)
            Util.setInt8(data, 8, if (szzt) 1 else 0)
            var offset = 9
            for (tile in tiles) {
                Util.setInt8(data, offset, tile.id)
                Util.setInt8(data, offset + 1, tile.col)
                val stats = tile.stats
                Util.setInt8(data, offset + 2, stats.size)
                offset += 3
                for (stat in stats) {
                    offset = stat.write(data, offset)
                }
            }
            return Base64.getEncoder().encodeToString(data)
        }

        @JvmStatic
        fun decode(encodedClip: String?): Clip {
            val data = Base64.getDecoder().decode(encodedClip)
            val w = Util.getInt32(data, 0)
            val h = Util.getInt32(data, 4)
            val isSzzt = Util.getInt8(data, 8) == 1
            val tiles = mutableListOf<Tile>()
            var offset = 9
            for (i in 0 until w * h) {
                var id = Util.getInt8(data, offset)
                var col = Util.getInt8(data, offset + 1)
                var statCount = Util.getInt8(data, offset + 2)
                offset += 3
                val stats = ArrayList<Stat>(statCount)
                for (j in 0 until statCount) {
                    val stat = Stat(data, offset, if (isSzzt) 0 else 8, -1)
                    offset += stat.statSize
                    stats.add(stat)
                }
                tiles.add(Tile(id, col, stats))
            }
            return Clip(
                dim = Dim(w, h),
                isSzzt = isSzzt,
                tiles = tiles.toTypedArray())
        }

        private fun tileSize(tile: Tile): Int {
            var size = 3 // id, col and stat
            val stats = tile.stats
            for (stat in stats) {
                size += stat.statSize
            }
            return size
        }
    }
}
