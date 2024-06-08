package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.PUT_DEFAULT
import zedit2.model.Stat
import zedit2.model.Tile


internal fun WorldEditor.operationDelete() {
    val tile = getTileAt(cursorX, cursorY, false)
    val underTile = Tile(0, 0)
    checkNotNull(tile)
    val tileStats: List<Stat> = tile.stats
    if (tileStats.size > 0) {
        val uid = tileStats[0].uid
        val uco = tileStats[0].uco
        underTile.id = uid
        underTile.col = uco
    }
    putTileAt(cursorX, cursorY, underTile, PUT_DEFAULT)
    afterModification()
}