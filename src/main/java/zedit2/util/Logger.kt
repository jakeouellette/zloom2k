package zedit2.util

object Logger {
    public fun i(tag: String, line:() -> String) {
        System.out.println("[$tag] ${line()}")
    }
    val Any.TAG: String
        get() {
            val tag = javaClass.simpleName
            return if (tag.length <= 23) tag else tag.substring(0, 23)
        }
}