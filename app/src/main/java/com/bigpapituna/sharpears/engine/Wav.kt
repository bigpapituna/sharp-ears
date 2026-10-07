package com.bigpapituna.sharpears.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 16-bit PCM WAV reader/writer for fixtures and the debug injector. */
object Wav {
    class Audio(val samples: FloatArray, val sampleRate: Int)

    /** Reads a PCM16 WAV and mixes it down to mono. */
    fun read(file: File): Audio {
        val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(b.remaining() >= 12 && String(ByteArray(4).also { b.get(it) }) == "RIFF") { "not a WAV file" }
        b.int
        require(String(ByteArray(4).also { b.get(it) }) == "WAVE") { "not a WAV file" }
        var channels = 1; var rate = 0; var bits = 0
        while (b.remaining() >= 8) {
            val id = String(ByteArray(4).also { b.get(it) })
            val len = b.int
            if (id == "fmt ") {
                val format = b.short.toInt(); channels = b.short.toInt(); rate = b.int
                b.int; b.short; bits = b.short.toInt()
                require(format == 1 || format == 0xFFFE.toShort().toInt()) { "only PCM WAV is supported" }
                b.position(b.position() + len - 16)
            } else if (id == "data") {
                require(bits == 16) { "only 16-bit WAV is supported" }
                val frames = minOf(len, b.remaining()) / (2 * channels)
                val out = FloatArray(frames)
                for (i in 0 until frames) {
                    var s = 0f
                    repeat(channels) { s += b.short / 32768f }
                    out[i] = s / channels
                }
                return Audio(out, rate)
            } else {
                b.position(minOf(b.limit(), b.position() + len + (len and 1)))
            }
        }
        error("WAV has no data chunk")
    }

    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        Writer(file, sampleRate).use { it.append(samples, samples.size) }
    }

    /** Streams mono PCM16 to a file; the header is patched with the final size on close. */
    class Writer(file: File, private val sampleRate: Int) : AutoCloseable {
        private val raf = RandomAccessFile(file, "rw").apply { setLength(0); write(ByteArray(44)) }
        private var frames = 0L
        private var bytes = ByteArray(0)

        fun append(samples: FloatArray, count: Int) {
            if (bytes.size < count * 2) bytes = ByteArray(count * 2)
            for (i in 0 until count) {
                val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
                bytes[2 * i] = v.toByte(); bytes[2 * i + 1] = (v shr 8).toByte()
            }
            raf.write(bytes, 0, count * 2)
            frames += count
        }

        override fun close() {
            val data = (frames * 2).toInt()
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
                .putShort(2).putShort(16)
            h.put("data".toByteArray()).putInt(data)
            raf.seek(0); raf.write(h.array()); raf.close()
        }
    }
}
