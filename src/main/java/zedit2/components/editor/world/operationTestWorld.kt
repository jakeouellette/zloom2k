package zedit2.components.editor.world

import zedit2.components.GlobalEditor
import zedit2.components.Util.evalConfigDir
import zedit2.components.WorldEditor
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.swing.JOptionPane
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension


internal fun WorldEditor.operationTestWorld() {


    val changeBoardTo = if (GlobalEditor.getBoolean("TEST_SWITCH_BOARD", false)) {
        currentBoardIdx
    } else {
        worldData.currentBoard
    }
    if (changeBoardTo == -1) return

    try {
        val unlinkList = ArrayList<File?>()

        val zzt = if (worldData.isSuperZZT) "SZZT" else "ZZT"
        val ext = if (worldData.isSuperZZT) ".SZT" else ".ZZT"
        val hiext = if (worldData.isSuperZZT) ".HGS" else ".HI"
        val testPath = evalConfigDir(GlobalEditor.getString(zzt + "_TEST_PATH", ""))
        if (testPath.isBlank()) {
            val errmsg = String.format(
                "You need to configure a %s test directory in the settings before you can test worlds.",
                if (worldData.isSuperZZT) "Super ZZT" else "ZZT"
            )
            JOptionPane.showMessageDialog(frame, errmsg, "Error testing world", JOptionPane.ERROR_MESSAGE)
            return
        }
        val dir = File(testPath)
        val zeta = Paths.get(dir.path, GlobalEditor.getString(zzt + "_TEST_COMMAND")).toFile()
        var basename: String? = ""
        var testFile: File? = null
        var testFileHi: File? = null
        var testWeaveCfgFile: File? = null
        var testWeaveIniFile: File? = null
        var testCopiedFiles : MutableMap<File,File> = mutableMapOf()
        for (nameSuffix in 0..98) {
            basename = GlobalEditor.getString(zzt + "_TEST_FILENAME")
            if (nameSuffix > 1) {
                val suffixString = nameSuffix.toString()
                val maxLen = 8 - suffixString.length
                if (basename!!.length > maxLen) {
                    basename = basename.substring(0, maxLen)
                }
                basename += suffixString
            }
            if (basename!!.length > 8) basename = basename.substring(0, 8)
            testFile = Paths.get(dir.path, basename + ext).toFile()
            testFileHi = Paths.get(dir.path, basename + hiext).toFile()
            for (copiedFileExtension in WorldEditor.copiedFileExtensions) {
                val newPath = Paths.get(dir.path, "$basename.$copiedFileExtension")
                val oldPath = File(path?.parent, "${path?.nameWithoutExtension}.$copiedFileExtension")
                Logger.i(TAG, {"Checking: $oldPath, ${newPath.exists()} ${oldPath.exists()}"})
                if (oldPath.exists()) {
                    testCopiedFiles.put(oldPath, newPath.toFile())
                }
            }
            if (!testFile.exists()) {
                break
            }
            testFile = null
            testFileHi = null
            testCopiedFiles = mutableMapOf()

        }
        if (testFile == null) {
            throw IOException("Error creating test file")
        }
        unlinkList.add(testFile)
        unlinkList.add(testFileHi)
        unlinkList.addAll(testCopiedFiles.values)
        Logger.i(TAG, { "Weave paths for config: $testCopiedFiles" })

        val argList = ArrayList<String?>()
        argList.add(zeta.path)

        if (GlobalEditor.getBoolean(zzt + "_TEST_USE_CHARPAL", false)) {
            testCharsetPalette(dir, basename, unlinkList, argList)
        }
        if (GlobalEditor.getBoolean(zzt + "_TEST_USE_BLINK", false)) {
            if (!GlobalEditor.getBoolean("BLINKING", true)) {
                argList.add("-b")
            }
        }
        val params =
            GlobalEditor.getString(zzt + "_TEST_PARAMS")!!.split(" ".toRegex())
                .toTypedArray()
        argList.addAll(Arrays.asList(*params))

        argList.add(basename + ext)

        val inject_P = GlobalEditor.getBoolean(zzt + "_TEST_INJECT_P", false)
        val delay_P = GlobalEditor.getInt(zzt + "_TEST_INJECT_P_DELAY", 0)
        var inject_Enter = false
        var delay_Enter = 0
        if (worldData.isSuperZZT) {
            inject_Enter = GlobalEditor.getBoolean(zzt + "_TEST_INJECT_ENTER", false)
            delay_Enter = GlobalEditor.getInt(zzt + "_TEST_INJECT_ENTER_DELAY", 0)
        }
        launchTest(argList, dir, testFile, testCopiedFiles, unlinkList, changeBoardTo, inject_P, delay_P, inject_Enter, delay_Enter)
    } catch (e: IOException) {
        JOptionPane.showMessageDialog(frame, e, "Error testing world", JOptionPane.ERROR_MESSAGE)
    }
}