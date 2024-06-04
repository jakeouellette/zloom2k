package zedit2.util

import zedit2.util.Logger.TAG
import java.io.File
import java.io.FileWriter

class Writer(file: File) {
    val file: File
    val writer: FileWriter

    init {
        var file = file
        var num = 1
        while (file.exists() && !file.isDirectory) {
            Logger.i(TAG) {"file $file exists. Trying again" }
            num++
            val ext = getExtension(file)
            val basename = getBasename(file)
            Logger.i(TAG) {"About to construct $basename-$num$ext"}
            file = File("$basename-$num$ext")
                Logger.i(TAG) {"Constructed $file"}
            if (num >= 99) break
            Logger.i(TAG) {"Looping"}
        }

        this.file = file
        this.writer = FileWriter(file)
    }

    companion object {
        private fun getBasename(file: File): String {
            val filename = file.toString()
            val dotPos = filename.lastIndexOf('.')
            if (dotPos == -1) return ""
            return filename.substring(0, dotPos)
        }

        private fun getExtension(file: File): String {
            val filename = file.toString()
            val dotPos = filename.lastIndexOf('.')
            if (dotPos == -1) return filename
            return filename.substring(dotPos)
        }
    }
}
