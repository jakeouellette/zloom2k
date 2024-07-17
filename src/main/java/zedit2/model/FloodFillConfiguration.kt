package zedit2.model

enum class FloodFillConfiguration(val description : String, val short : String,val inUi: Boolean = true ) {
    FLOOD_FILL("Flood fill", "Flood"),
    GRADIENT_FILL("Gradient fill", "Gradient");

    companion object {
        val DEFAULT = FLOOD_FILL
    }
}