package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.model.Tile


internal fun WorldEditor.setBufferToElement(element: String, editOnPlace: Boolean) {
    val tile = getTileFromElement(element, getTileColour(bufferTile!!))
    if (editOnPlace) {
        openCurrentTileEditor(
            callback = { tile: Tile -> this.elementPlaceAtCursor(tile) },
            exempt = true,
            advanced = false,
            tile = tile,
        )
    } else {
        elementPlaceAtCursor(tile)
    }
}