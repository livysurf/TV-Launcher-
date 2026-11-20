package com.example.tvlauncher

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

// A short sound that sweeps from one pitch to another, with a soft fade in and out
fun buildSweep(fromHz: Double, toHz: Double, ms: Int, volume: Double): AudioTrack {
    val n = SAMPLE_RATE * ms / 1000
    val buffer = ShortArray(n)
    var phase = 0.0
    for (i in 0 until n) {
        val t = i.toDouble() / n
        val freq = fromHz + (toHz - fromHz) * t
        phase += 2 * PI * freq / SAMPLE_RATE
        val envelope = sin(PI * t)
        buffer[i] = (sin(phase) * envelope * volume * Short.MAX_VALUE).toInt().toShort()
    }
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(n * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    track.write(buffer, 0, n)
    return track
}