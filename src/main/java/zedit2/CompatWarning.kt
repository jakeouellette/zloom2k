package zedit2

import kotlin.math.max

class CompatWarning(val isSuperZZT: Boolean) {
    var warningLevel: Int = 0
        private set
    private var prefix = ""
    var messages: HashMap<Int, ArrayList<String>> = HashMap()

    fun warn(level: Int, message: String) {
        warningLevel = max(warningLevel.toDouble(), level.toDouble()).toInt()
        if (!messages.containsKey(level)) {
            messages[level] = ArrayList()
        }
        messages[level]!!.add(prefix + message)
    }

    fun getMessages(level: Int): String {
        if (!messages.containsKey(level)) {
            return ""
        } else {
            var msgCount = 0
            val output = StringBuilder()
            val msgs = messages[level]!!
            var firstLine = true
            for (msg in msgs) {
                if (firstLine) {
                    firstLine = false
                } else {
                    output.append('\n')
                }
                if (msgs.size > 1) output.append("‧ ")
                output.append(msg)
                msgCount++
                if (msgCount > 10) {
                    output.append(String.format("\n...%d other warnings hidden...", msgs.size - msgCount))
                    break
                }
            }
            return output.toString()
        }
    }

    fun setPrefix(s: String) {
        prefix = s
    }
}
