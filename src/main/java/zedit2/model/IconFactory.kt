package zedit2.model

import zedit2.components.WorldEditor
import zedit2.util.ZType
import java.awt.image.BufferedImage

object IconFactory {
    fun getIcon(szzt: Boolean, tile: Tile, editor : WorldEditor) : BufferedImage {
        val chr = ZType.getChar(szzt, tile)
        val col = ZType.getColour(szzt, tile)
        return editor.canvas.extractCharImage(chr, col, 4, 4, false, "$")
    }
}