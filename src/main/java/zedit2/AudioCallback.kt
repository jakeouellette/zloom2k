package zedit2

interface AudioCallback {
    fun upTo(line: Int, pos: Int, len: Int)
}
