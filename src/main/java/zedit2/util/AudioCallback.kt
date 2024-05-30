package zedit2.util

interface AudioCallback {
    fun upTo(line: Int, pos: Int, len: Int)
}
