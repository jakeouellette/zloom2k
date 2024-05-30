package zedit2.event

import zedit2.model.Tile

fun interface TileEditorCallback {
    fun callback(tile: Tile)
}
