package zedit2

import java.util.*
import kotlin.math.max
import kotlin.math.min

class MusicNote {
    var delay: Int = 0
    var note: Int = -1
    var drum: Int = -1
    var octave: Int = 0
    var indicate_pos: Int = 0
    var indicate_len: Int = 0
    var rest: Boolean = false
    var original: String? = null
    var desired_octave: Int = 0

    val isTransposable: Boolean
        get() = note >= 0

    fun transpose(by: Int): Boolean {
        require(!(by != 1 && by != -1)) { "Can only be transposed by 1 semitone" }
        if (note == -1) return true

        var new_note = note + by
        var new_octave = octave
        if (new_note < 0) {
            new_octave--
            new_note += 12
        } else if (new_note > 11) {
            new_octave++
            new_note -= 12
        }
        if (new_octave < 2 || new_octave > 7) return false
        note = new_note
        // Was the original note string in uppercase? If so, keep it in uppercase
        val upper = Character.isUpperCase(original!![0])
        original = getString(upper)

        desired_octave = new_octave
        return true
    }

    private fun getString(upper: Boolean): String {
        var noteString = "?"

        when (note) {
            0 -> noteString = "c"
            1 -> noteString = "c#"
            2 -> noteString = "d"
            3 -> noteString = "d#"
            4 -> noteString = "e"
            5 -> noteString = "f"
            6 -> noteString = "f#"
            7 -> noteString = "g"
            8 -> noteString = "g#"
            9 -> noteString = "a"
            10 -> noteString = "a#"
            11 -> noteString = "b"
        }
        if (upper) noteString = noteString.uppercase(Locale.getDefault())
        return noteString
    }

    companion object {
        fun fixOctavesFor(cursor: Int, musicNotes: ArrayList<MusicNote>): Int {
            // See if we don't need to fix octaves
            var cursor = cursor
            if (!musicNotes[cursor].isTransposable) return cursor
            if (musicNotes[cursor].desired_octave == musicNotes[cursor].octave) return cursor

            // Loop backwards, removing all octave changes between this note and the last playable note
            var erasing: Boolean
            var prevPlayableOctave = 4
            do {
                erasing = false
                for (i in cursor - 1 downTo 0) {
                    if (musicNotes[i].isTransposable) {
                        prevPlayableOctave = musicNotes[i].octave
                        break
                    }
                    if (musicNotes[i].original == "+" || musicNotes[i].original == "-") {
                        musicNotes.removeAt(i)
                        cursor--
                        erasing = true
                        break
                    }
                }
            } while (erasing)

            // Now add more +s and -s to balance everything out
            while (prevPlayableOctave != musicNotes[cursor].desired_octave) {
                var changeBy = if (prevPlayableOctave < musicNotes[cursor].desired_octave) {
                    1
                } else {
                    -1
                }
                prevPlayableOctave += changeBy
                val octaveChange = MusicNote()
                octaveChange.indicate_pos = musicNotes[cursor].indicate_pos
                octaveChange.indicate_len = 1
                octaveChange.octave = prevPlayableOctave
                octaveChange.desired_octave = octaveChange.octave
                octaveChange.original = if (changeBy == 1) "+" else "-"
                musicNotes[cursor].indicate_pos++
                musicNotes.add(cursor, octaveChange)
                cursor++
            }

            // Now loop across the entire #play sequence and fix the octave #s up
            var octave = 4
            for (musicNote in musicNotes) {
                when (musicNote.original) {
                    "+" -> octave = min((octave + 1).toDouble(), 7.0).toInt()
                    "-" -> octave = max((octave - 1).toDouble(), 2.0).toInt()
                    else -> {}
                }
                musicNote.octave = octave
            }

            return cursor
        }

        fun fromPlay(code: String): ArrayList<MusicNote>? {
            var start = code.uppercase(Locale.getDefault()).indexOf("#PLAY")
            if (start == -1) return null
            start += 5

            val music = ArrayList<MusicNote>()
            var delay: Short = 1
            var octave = 4

            var pos = start
            while (pos < code.length) {
                val indicate_pos = pos
                var indicate_len = 1
                var note = -1
                var drum = -1
                var rest = false

                when (code[pos].uppercaseChar()) {
                    'T' -> delay = 1
                    'S' -> delay = 2
                    'I' -> delay = 4
                    'Q' -> delay = 8
                    'H' -> delay = 16
                    'W' -> delay = 32
                    '3' -> delay = (delay / 3).toShort()
                    '.' -> delay = (delay + delay / 2).toShort()
                    '+' -> octave = min((octave + 1).toDouble(), 7.0).toInt()
                    '-' -> octave = max((octave - 1).toDouble(), 2.0).toInt()
                    'X' -> rest = true
                    'C' -> note = 0
                    'D' -> note = 2
                    'E' -> note = 4
                    'F' -> note = 5
                    'G' -> note = 7
                    'A' -> note = 9
                    'B' -> note = 11
                    '0' -> drum = 0
                    '1' -> drum = 1
                    '2' -> drum = 2
                    '4' -> drum = 4
                    '5' -> drum = 5
                    '6' -> drum = 6
                    '7' -> drum = 7
                    '8' -> drum = 8
                    '9' -> drum = 9
                    else -> {}
                }
                if (pos + 1 < code.length && note >= 0) {
                    val suffix = code[pos + 1]
                    if (suffix == '#') {
                        note++
                        indicate_len = 2
                    } else if (suffix == '!') {
                        note--
                        indicate_len = 2
                    }
                }

                if ((note < 0) || (note > 11)) note = -1

                if (delay.toInt() == 0) delay = 256 // In ZZT, a delay of 0 plays for this long


                val mus = MusicNote()
                mus.delay = delay.toInt()
                mus.rest = rest
                mus.note = note
                mus.drum = drum
                mus.octave = octave
                mus.desired_octave = octave
                mus.indicate_pos = indicate_pos
                mus.indicate_len = indicate_len
                mus.original = code.substring(pos, pos + indicate_len)
                music.add(mus)

                pos += indicate_len - 1
                pos++
            }
            return music
        }
    }
}
