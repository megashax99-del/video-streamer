package com.megashax99.videostreamer

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.generic.GenericFromFile
import com.pedro.library.view.OpenGlView

class MainActivity : AppCompatActivity(), ConnectChecker,
    VideoDecoderInterface, AudioDecoderInterface {

    private lateinit var streamer: GenericFromFile
    private lateinit var status: TextView
    private lateinit var fileName: TextView
    private lateinit var url: EditText
    private var selectedVideo: Uri? = null
    private var videoEnded = false

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedVideo = uri
            try {
                contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            fileName.text = queryDisplayName(uri) ?: "Видео выбрано"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        fileName = findViewById(R.id.fileName)
        url = findViewById(R.id.rtmpUrl)
        streamer = GenericFromFile(findViewById<OpenGlView>(R.id.preview), this, this, this)
        streamer.setLoopMode(false)

        findViewById<Button>(R.id.selectVideo).setOnClickListener {
            picker.launch(arrayOf("video/*"))
        }
        findViewById<Button>(R.id.start).setOnClickListener { startStreaming() }
        findViewById<Button>(R.id.stop).setOnClickListener { stopStreaming() }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun startStreaming() {
        val uri = selectedVideo
        val streamUrl = url.text.toString().trim()
        if (uri == null) {
            toast("Сначала выбери видео")
            return
        }
        if (!streamUrl.startsWith("rtmp://") && !streamUrl.startsWith("rtmps://")) {
            toast("Введи полный RTMP/RTMPS URL вместе с ключом")
            return
        }
        try {
            val videoReady = streamer.prepareVideo(this, uri)
            val audioReady = streamer.prepareAudio(this, uri, 160 * 1024)
            if (!videoReady) {
                toast("Телефон не смог подготовить это видео")
                return
            }
            videoEnded = false
            streamer.startStream(streamUrl)
            status.text = "CONNECTING…"
            status.setTextColor(0xFFFFCC66.toInt())
            if (!audioReady) toast("В файле не удалось подготовить аудио; будет видео без звука")
        } catch (e: Exception) {
            toast("Ошибка подготовки: ${e.message ?: "неизвестная ошибка"}")
        }
    }

    private fun stopStreaming() {
        if (::streamer.isInitialized && streamer.isStreaming) streamer.stopStream()
        status.text = "OFFLINE"
        status.setTextColor(0xFFFF7777.toInt())
    }

    override fun onConnectionStarted(url: String) { runOnUiThread { status.text = "CONNECTING…" } }
    override fun onConnectionSuccess() {
        runOnUiThread {
            status.text = "LIVE"
            status.setTextColor(0xFF35D07F.toInt())
        }
    }
    override fun onConnectionFailed(reason: String) {
        runOnUiThread { stopStreaming(); toast("Ошибка трансляции: $reason") }
    }
    override fun onDisconnect() { runOnUiThread { status.text = "OFFLINE"; status.setTextColor(0xFFFF7777.toInt()) } }
    override fun onAuthError() { runOnUiThread { stopStreaming(); toast("Ошибка авторизации RTMP") } }
    override fun onAuthSuccess() { }
    override fun onNewBitrate(bitrate: Long) { }

    override fun onVideoDecoderFinished() {
        runOnUiThread {
            if (!videoEnded) {
                videoEnded = true
                stopStreaming()
                toast("Видео закончилось. Трансляция остановлена.")
            }
        }
    }
    override fun onAudioDecoderFinished() { }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        if (::streamer.isInitialized && streamer.isStreaming) streamer.stopStream()
        super.onDestroy()
    }
}
