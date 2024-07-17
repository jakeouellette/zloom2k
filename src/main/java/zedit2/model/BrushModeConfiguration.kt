package zedit2.model

enum class BrushModeConfiguration(val description : String, val short : String, val inUi: Boolean = true ) {
    EDIT("Edit", "Edit"),
    DRAW("Draw", "Draw");
    companion object {
        val DEFAULT = EDIT
    }
}