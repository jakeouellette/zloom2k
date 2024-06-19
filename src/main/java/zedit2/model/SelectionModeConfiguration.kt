package zedit2.model

enum class SelectionModeConfiguration(val description : String, val short : String,val inUi: Boolean = true ) {
    MOVE("Move Block", "Move"),
    COPY("Copy block", "Copy"),
    COPY_REPEATED("Copy block (repeated)", "Copy++", inUi = false),
    CLEAR("Clear block", "Clear"),
    FLIP("Flip block", "Flip"),
    MIRROR("Mirror block", "Mirror"),
    PAINT("Paint block", "Paint");

    companion object {
        val DEFAULT = MOVE
    }
}