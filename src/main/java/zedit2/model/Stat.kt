package zedit2.model

import zedit2.util.CP437.toUnicode
import zedit2.components.Util
import java.util.*

class Stat(szzt: Boolean = false,
           var stepX : Int = 0,
           var stepY : Int = 0,
           var cycle : Int = 1,
           var p1 : Int = 0,
           var p2 : Int = 0,
           var p3 : Int = 0,
           var statId : Int = -1,
           var follower : Int = -1,
           var leader : Int = -1,
           var uid : Int = 0,
           var uco : Int = 0,
           var pointer : Int = 0,
           var ip : Int = 0,
           var x : Int = -1,
           var y : Int = -1,
           var cachedCodeLength : Int = 0,
           var internalTag : Int = 0) {

    constructor(worldData: ByteArray, offset: Int, paddingSize: Int, statId: Int)
            : this(
        szzt = false,
        statId = statId,
                x = Util.getInt8(worldData, offset + 0),
            y = Util.getInt8(worldData, offset + 1),
            stepX = Util.getInt16(worldData, offset + 2),
            stepY = Util.getInt16(worldData, offset + 4),
            cycle = Util.getInt16(worldData, offset + 6),
            p1 = Util.getInt8(worldData, offset + 8),
            p2 = Util.getInt8(worldData, offset + 9),
            p3 = Util.getInt8(worldData, offset + 10),
            follower = Util.getInt16(worldData, offset + 11),
            leader = Util.getInt16(worldData, offset + 13),
            uid = Util.getInt8(worldData, offset + 15),
            uco = Util.getInt8(worldData, offset + 16),
            pointer = Util.getInt32(worldData, offset + 17),
            ip = Util.getInt16(worldData, offset + 21),
            cachedCodeLength = Util.getInt16(worldData, offset + 23),
            internalTag = 0
            ) {

        padding = Arrays.copyOfRange(worldData, offset + 25, offset + 25 + paddingSize)
        if (codeLength > 0) {
            val codeOffset = offset + 25 + paddingSize
            code = Arrays.copyOfRange(worldData, codeOffset, codeOffset + codeLength)
        } else {
            code = ByteArray(0)
        }
        migrateOldZeditInfo()
    }



    var codeLength: Int = 0
        set(value) {
            if (value > 0) {
                throw RuntimeException("Can't set codeLength to >0. Use setCode() instead.")
            }
            this@Stat.code = ByteArray(0)
            field = value
        }
        get() {
            return cachedCodeLength
        }


    var padding: ByteArray = ByteArray(if (szzt) 0 else 8)
        set(value) {field = value.clone()}

    var code: ByteArray = ByteArray(0)
        set(value) {
            field = value.clone()
            cachedCodeLength = field.size
        }

    // TODO(jakeouellette): These override clone, but probably shouldn't. Refactor.
    fun clone(): Stat {
        val newStat = Stat(true) // Passing szzt=true to avoid padding
        copyTo(newStat)
        return newStat
    }

    fun copyTo(other: Stat) {
        other.statId = statId
        other.x = x
        other.y = y
        other.stepX = stepX
        other.stepY = stepY
        other.cycle = cycle
        other.p1 = p1
        other.p2 = p2
        other.p3 = p3
        other.follower = follower
        other.leader = leader
        other.uid = uid
        other.uco = uco
        other.pointer = pointer
        other.ip = ip
        other.cachedCodeLength = codeLength
        other.padding = padding.clone()
        other.code = code.clone()
        other.internalTag = internalTag
    }

    fun write(worldData: ByteArray, offset: Int): Int {
        var offset = offset
        Util.setInt8(worldData, offset + 0, x)
        Util.setInt8(worldData, offset + 1, y)
        Util.setInt16(worldData, offset + 2, stepX)
        Util.setInt16(worldData, offset + 4, stepY)
        Util.setInt16(worldData, offset + 6, cycle)
        Util.setInt8(worldData, offset + 8, p1)
        Util.setInt8(worldData, offset + 9, p2)
        Util.setInt8(worldData, offset + 10, p3)
        Util.setInt16(worldData, offset + 11, follower)
        Util.setInt16(worldData, offset + 13, leader)
        Util.setInt8(worldData, offset + 15, uid)
        Util.setInt8(worldData, offset + 16, uco)
        Util.setInt32(worldData, offset + 17, pointer)
        Util.setInt16(worldData, offset + 21, ip)
        Util.setInt16(worldData, offset + 23, codeLength)
        System.arraycopy(padding, 0, worldData, offset + 25, padding.size)
        offset = offset + 25 + padding.size
        if (codeLength > 0) {
            System.arraycopy(code, 0, worldData, offset, codeLength)
            offset += codeLength
        }

        return offset
    }

    val statSize: Int
        get() = 25 + padding.size + code.size



    /*
        original zedit padding bytes:
        0#1: zspecial (0=normal, 1=clone, 2=bindable)
        4#2: priority (now order)
        6#2: zedit tag (28159)

        zedit2 special pointer data:
        & 0xFFF00000 = (0xA6500000)
        & 0x000F0000 = flags
        & 0x0000FFFF = order

        Flags:
        1000 0x00080000 (mask=0111  0xFFF7FFFF 7) - autobind
        0100 0x00040000 (mask=1011  0xFFFBFFFF B) - specifyId
        0010 0x00020000 (mask=1101  0xFFFDFFFF D) -
        0001 0x00010000 (mask=1110  0xFFFEFFFF E) -
    */
    private fun migrateOldZeditInfo() {
        // The MZX zedit used special codes in the padding data to store priority (and other things)
        // Migrate priority and use it as 'order' here.
        if (padding.size < 8) return
        if (Util.getInt16(padding, 6) == 28159) { // We have zedit padding bytes
            val priority = Util.getInt16(padding, 4)
            val zspecial = Util.getInt8(padding, 0)
            order = priority
            isAutobind = zspecial == 2

            // Now that we have copied this over, zero the padding
            Arrays.fill(padding, 0.toByte())
        }
    }

    val isZedit2Special: Boolean
        get() = ((pointer and -0x100000) == -0x59b00000)

    fun setZedit2Special() {
        if (!isZedit2Special) {
            pointer = -0x59b00000
        }
    }

    var order: Int
        get() {
            if (isZedit2Special) {
                return (pointer and 0xFFFF).toShort().toInt()
            }
            return 0
        }
        set(order) {
            setZedit2Special()
            pointer = (pointer and -0x10000) or (order.toShort().toInt() and 0xFFFF)
        }
    var isAutobind: Boolean
        get() {
            if (isZedit2Special) return (pointer and 0x00080000) != 0
            return false
        }
        set(flag) {
            setZedit2Special()
            pointer = ((pointer and -0x80001) or (if (flag) 0x00080000 else 0))
        }
    var isSpecifyId: Boolean
        get() {
            if (isZedit2Special) return (pointer and 0x00040000) != 0
            return false
        }
        set(flag) {
            setZedit2Special()
            pointer = ((pointer and -0x40001) or (if (flag) 0x00040000 else 0))
        }
    var isPlayer: Boolean
        get() {
            if (isZedit2Special) return (pointer and 0x00020000) != 0
            return false
        }
        set(flag) {
            setZedit2Special()
            pointer = ((pointer and -0x20001) or (if (flag) 0x00020000 else 0))
        }
    var isFlag4: Boolean
        get() {
            if (isZedit2Special) return (pointer and 0x00010000) != 0
            return false
        }
        set(flag) {
            setZedit2Special()
            pointer = ((pointer and -0x10001) or (if (flag) 0x00010000 else 0))
        }

    val name: String
        get() {
            if (code.size == 0) return ""
            if (code[0] != '@'.code.toByte()) return ""
            var nameLen = 0
            for (i in 1 until code.size) {
                if (code[i] == '\r'.code.toByte()) {
                    nameLen = i - 1
                    break
                }
            }
            return toUnicode(Arrays.copyOfRange(code, 1, 1 + nameLen))
        }

    fun isIdenticalTo(replacementStat: Stat): Boolean {
        return (x == replacementStat.x) &&
                (y == replacementStat.y) &&
                equals(replacementStat)
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is Stat) return false
        val replacementStat = obj
        return (p1 == replacementStat.p1) &&
                (p2 == replacementStat.p2) &&
                (p3 == replacementStat.p3) &&
                (stepX == replacementStat.stepX) &&
                (stepY == replacementStat.stepY) &&
                (cycle == replacementStat.cycle) &&
                (follower == replacementStat.follower) &&
                (leader == replacementStat.leader) &&
                (uid == replacementStat.uid) &&
                (uco == replacementStat.uco) &&
                (ip == replacementStat.ip) &&
                (pointer == replacementStat.pointer) &&
                (codeLength == replacementStat.codeLength) &&
                (padding.contentEquals(replacementStat.padding)) &&
                (code.contentEquals(replacementStat.code))
    }
}
