package zedit2

interface ConverterCallback {
    fun converted(checkVal: Int, x: Int, y: Int, id: Int, col: Int, chr: Int, vcol: Int)
    fun finished(checkVal: Int)
}
