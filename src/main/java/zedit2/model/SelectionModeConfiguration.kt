package zedit2.model

enum class SelectionModeConfiguration(val description : String) {
    MOVE("Move Block"),
    COPY("Copy block"),
    COPY_REPEATED("Copy block (repeated)"),
    CLEAR("Clear block"),
    FLIP("Flip block"),
    MIRROR("Mirror block"),
    PAINT("Paint block");

    companion object {
        val DEFAULT = MOVE
    }
}