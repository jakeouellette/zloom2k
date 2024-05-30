package zedit2.util

import zedit2.model.AudioTiming
import zedit2.model.MusicLine
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.sound.sampled.*
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

class Audio(
    private val toPlay: ArrayList<MusicLine>,
    private val line: SourceDataLine,
    private val callback: AudioCallback
) : Runnable {
    private var currentPlaying = 0

    private val audioThread = Thread(this)
    private val progressThread: Thread
    private var stopped = false

    private val timingQueue = ConcurrentLinkedQueue<AudioTiming>()

    init {
        audioThread.start()
        progressThread = Thread { this.progressRun() }
        progressThread.start()
    }

    fun stop() {
        val stopThread = Thread {
            stopped = true
            line.stop()
            line.close()
            try {
                audioThread.join()
            } catch (ignored: InterruptedException) {
            }
            try {
                progressThread.join()
            } catch (ignored: InterruptedException) {
            }
        }
        stopThread.start()
    }

    private fun progressRun() {
        while (!line.isRunning) {
            delay()
        }
        var timing: AudioTiming? = null
        while (!stopped) {
            delay()
            val frame = line.framePosition
            if (timing == null) {
                timing = timingQueue.poll()
                if (timing == null) continue
            }
            if (frame >= timing.bytes) {
                val seq = timing.seq
                val pos = timing.pos
                val len = timing.len
                SwingUtilities.invokeLater { callback.upTo(seq, pos, len) }

                timing = null
            }
        }
        SwingUtilities.invokeLater { callback.upTo(-1, -1, -1) }
    }

    private fun delay() {
        try {
            Thread.sleep(10)
        } catch (ignored: InterruptedException) {
        }
    }

    override fun run() {
        line.start()
        var wavelen = 0
        while (currentPlaying < toPlay.size) {
            if (stopped) break
            wavelen = play(wavelen, toPlay[currentPlaying])
            currentPlaying++
        }
        if (!stopped) {
            stopped = true
            line.drain()
            line.stop()
            line.close()
        }
    }

    private fun addTiming(pos: Int, len: Int, bytes: Int) {
        timingQueue.add(AudioTiming(currentPlaying, pos, len, bytes))
    }

    private fun isPlayLine(code: String): Boolean {
        var code = code
        code = code.replace("?i", "")
        code = code.replace("/i", "")
        code = code.trim { it <= ' ' }.uppercase(Locale.getDefault())
        return code.startsWith("#PLAY")
    }

    private fun play(wavelen: Int, musicLine: MusicLine): Int {
        var wavelen = wavelen
        val code = musicLine.seq

        if (!isPlayLine(code)) return wavelen
        val waves = ArrayList<ByteArray>()

        val music = MusicNote.fromPlay(code) ?: return wavelen

        for (mus in music) {
            if (mus.indicate_pos < musicLine.start) continue
            if (mus.indicate_pos >= musicLine.end) continue

            val duration = 65535.0 * mus.delay / CLOCK_HZ
            val totalSamples = (PLAYBACK_HZ * duration).toInt()

            val oldWavelen = wavelen

            if (mus.note >= 0) {
                val tone = 2.0.pow((mus.octave * 12 + mus.note + 48.0) / 12.0) as Int
                val divi = ZZT_CLOCK_HZ / tone

                val period = divi * 44100.0 / CLOCK_HZ

                //System.out.printf("Playing %d (octave %d) for %f seconds\n", divi, octave, duration);
                wavelen = addNote(waves, wavelen, period, totalSamples)
            } else if (mus.drum >= 0) {
                wavelen = addDrum(waves, wavelen, mus.drum, totalSamples)
            } else if (mus.rest) {
                wavelen = addRest(waves, wavelen, totalSamples)
            }
            if (wavelen > oldWavelen) {
                addTiming(mus.indicate_pos, mus.indicate_len, oldWavelen)
            }
        }
        makeNoise(waves)
        return wavelen
    }

    private fun addNote(waves: ArrayList<ByteArray>, wavelen: Int, period: Double, totalSamples: Int): Int {
        var wavelen = wavelen
        val wave = ByteArray(totalSamples)
        var writeFrom = 0

        while (writeFrom < totalSamples) {
            val m = (writeFrom + wavelen) % period
            var a: Byte
            var rem: Int
            if (m < period / 2) {
                a = AMPLITUDE
                rem = ceil(period / 2 - m).toInt()
            } else {
                a = (-AMPLITUDE).toByte()
                rem = ceil(period - m).toInt()
            }

            //System.out.printf("period: %f  m: %f  writeFrom: %d   rem: %d   totalSamples: %d\n", period, m, writeFrom, rem, totalSamples);
            var writeTo = writeFrom + rem
            writeTo = min(writeTo.toDouble(), totalSamples.toDouble()).toInt()
            Arrays.fill(wave, writeFrom, writeTo, a)
            writeFrom = writeTo
        }
        waves.add(wave)
        wavelen += totalSamples
        return wavelen
    }

    private fun addRest(waves: ArrayList<ByteArray>, wavelen: Int, totalSamples: Int): Int {
        var wavelen = wavelen
        val wave = ByteArray(totalSamples)
        waves.add(wave)
        wavelen += totalSamples
        return wavelen
    }

    private fun addDrum(waves: ArrayList<ByteArray>, wavelen: Int, drum: Int, totalSamples: Int): Int {
        var wavelen = wavelen
        var totalSamples = totalSamples
        val drums = arrayOf(
            intArrayOf(372),
            intArrayOf(1084, 994, 917, 852, 795, 745, 701, 662, 627, 596, 568, 542, 518, 497),
            intArrayOf(248, 248, 149, 745, 248, 248, 149, 745, 248, 248, 149, 745, 248, 248),
            intArrayOf(),
            intArrayOf(2386, 466, 618, 315, 352, 264, 861, 1081, 243, 351, 1365, 738, 232, 1968),
            intArrayOf(745, 788, 745, 1453, 745, 695, 745, 1309, 745, 606, 745, 800, 745, 692),
            intArrayOf(542, 677, 677, 903, 451, 1355, 542, 677, 677, 903, 451, 1355, 542, 677),
            intArrayOf(1734, 1765, 1796, 1830, 1864, 1899, 1936, 1975, 2015, 2057, 2100, 2146, 2193, 2242),
            intArrayOf(988, 974, 1025, 1058, 1029, 965, 940, 908, 1058, 974, 903, 895, 949, 899),
            intArrayOf(3156, 3604, 3775, 5187, 5326, 3107, 2485, 3728, 3332, 2896, 3173, 1921, 2153, 2800)
        )
        val drumPattern = drums[drum]

        for (divi in drumPattern) {
            val period = divi * 44100.0 / CLOCK_HZ
            val len = 50
            wavelen = addNote(waves, wavelen, period, len)
            totalSamples -= len
        }
        wavelen = addRest(waves, wavelen, totalSamples)
        return wavelen
    }

    private fun makeNoise(waves: ArrayList<ByteArray>) {
        for (wave in waves) {
            //System.out.printf("Before write: %d\n", line.available());
            //System.out.printf("%s\n", line);
            if (!stopped) {
                line.write(wave, 0, wave.size)
            }

            //System.out.printf("After write: %d\n", line.available());
        }
    }

    companion object {
        private const val ZZT_CLOCK_HZ = 1193182
        private const val CLOCK_HZ = 1193182.0
        private const val PLAYBACK_HZ = 44100.0
        private const val AMPLITUDE: Byte = 4
        private const val BUFFER_SIZE = 4410

        @JvmStatic
        fun playSequence(toPlay: ArrayList<MusicLine>, callback: AudioCallback): Audio? {
            val fmt = AudioFormat(PLAYBACK_HZ.toFloat(), 8, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, fmt)
            var audio: Audio? = null
            try {
                val line = AudioSystem.getLine(info) as SourceDataLine
                line.open(fmt, BUFFER_SIZE)
                audio = Audio(toPlay, line, callback)
            } catch (e: LineUnavailableException) {
                e.printStackTrace()
            }
            return audio
        }

        @JvmStatic
        fun getPlayDuration(playMus: String): Int {
            var delay: Short = 1
            var playDur = 0
            for (i in 0 until playMus.length) {
                val c = playMus[i].uppercaseChar()
                when (c) {
                    'T' -> delay = 1
                    'S' -> delay = 2
                    'I' -> delay = 4
                    'Q' -> delay = 8
                    'H' -> delay = 16
                    'W' -> delay = 32
                    '3' -> delay = (delay / 3).toShort()
                    '.' -> delay = (delay + delay / 2).toShort()
                    'C', 'D', 'E', 'F', 'G', 'A', 'B', '0', '1', '2', '4', '5', '6', '7', '8', '9', 'X' -> playDur += (if (delay.toInt() == 0) 256 else delay).toInt()
                    else -> {}
                }
            }
            return playDur
        }
    }
}
