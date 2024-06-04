package zedit2.util

object Logger {
    fun i(tag: String, line:() -> String) {
        System.out.println("[$tag] ${line()}")
    }

    fun e(tag: String, e: Exception) {
        System.err.println("[$tag] An exception occurred:")
        e.printStackTrace()
    }

    fun e(tag: String, line: () -> String) {
        System.err.println("[$tag] ${line()}")
    }

    val Any.TAG: String
        get() {
            val tag = javaClass.simpleName
            return if (tag.length <= 23) tag else tag.substring(0, 23)
        }
}