package zedit2.util

object Logger {
    fun i(tag: String, line:() -> String) {
        val line = line()
        // FIXME(jakeouellette): Temporarily don't log focus events
        if (line.lowercase().contains("focus")) {
            return
        }
        System.out.println("[$tag] ${line}")
    }

    fun e(tag: String, e: Exception) {
        System.err.println("[$tag] An exception occurred:")
        e.printStackTrace()
    }

    fun e(tag: String, line: () -> String) {
        System.err.println("[$tag] ERR: ${line()}")
    }

    fun w(tag: String, line: () -> String) {
        System.err.println("[$tag] WARN: ${line()}")
    }

    fun v(tag: String, line: () -> String) {
//        System.out.println("[$tag] ${line}")
    }

    val Any.TAG: String
        get() {
            val tag = javaClass.simpleName
            return if (tag.length <= 23) tag else tag.substring(0, 23)
        }
}