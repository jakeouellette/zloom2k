package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.model.Stat
import zedit2.model.Tile


internal fun WorldEditor.operationDelete() {
    val tile = getTileAt(caretPos, false)
    val underTile = Tile(0, 0)
    checkNotNull(tile)
    val tileStats: List<Stat> = tile.stats
    val firstStat = tileStats.getOrNull(0)
    if (firstStat != null) {
        val uid = firstStat.uid
        val uco = firstStat.uco
        underTile.id = uid
        underTile.col = uco
    }
    putTileAt(caretPos, underTile, WorldEditor.Companion.PutTypes.PUT_DEFAULT)
    afterModification()
}