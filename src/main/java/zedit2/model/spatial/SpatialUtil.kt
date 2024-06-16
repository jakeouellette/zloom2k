package zedit2.model.spatial

import zedit2.components.DosCanvas.Companion.CHAR_W
import zedit2.components.DosCanvas.Companion.CHAR_H

object SpatialUtil {
    fun tileX(x : Int, zoom: Double) =
        Math.round(x * CHAR_W * zoom).toInt()
    fun tileY(y : Int, zoom: Double) =
        Math.round(y * CHAR_H * zoom).toInt()
}