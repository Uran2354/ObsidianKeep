package com.example.obsidiankeep.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val outputFile: File, val startedAt: Long, val recordId: String) : RecordingState()
}

sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Playing(val file: File, val recordId: String) : PlaybackState()
}

data class AudioRecord(
    val id: String,
    val file: File,
    val title: String,
    val durationMs: Int,
    val createdAt: Long
)

/**
 * Диктофон — запись и воспроизведение аудио.
 *
 * Аудио хранятся в filesDir/audio/<id>.m4a. Каждая запись — отдельный файл,
 * НЕ связана с Note в БД. Это чистый диктофон.
 */
@Singleton
class AudioNoteManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioDir = File(context.filesDir, "audio").apply { mkdirs() }

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0)
    val playbackPositionMs: StateFlow<Int> = _playbackPositionMs.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(0)
    val playbackDurationMs: StateFlow<Int> = _playbackDurationMs.asStateFlow()

    private val _records = MutableStateFlow<List<AudioRecord>>(emptyList())
    val records: StateFlow<List<AudioRecord>> = _records.asStateFlow()

    private var recordingTimerJob: kotlinx.coroutines.Job? = null
    private var playbackTimerJob: kotlinx.coroutines.Job? = null

    init { refreshRecords() }

    /** Перечитать список аудио-файлов с диска. */
    fun refreshRecords() {
        val list = audioDir.listFiles { f -> f.extension == "m4a" }
            ?.map { file ->
                val id = file.nameWithoutExtension
                val duration = getDuration(file)
                val createdAt = file.lastModified()
                val title = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(createdAt))
                AudioRecord(id, file, title, duration, createdAt)
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
        _records.value = list
    }

    private fun getDuration(file: File): Int {
        return try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            val dur = mp.duration
            mp.release()
            dur
        } catch (_: Exception) { 0 }
    }

    fun fileFor(recordId: String): File = File(audioDir, "$recordId.m4a")

    fun hasAudio(recordId: String): Boolean = fileFor(recordId).exists()

    fun startRecording() {
        if (_recordingState.value is RecordingState.Recording) return
        val recordId = System.currentTimeMillis().toString()
        val file = fileFor(recordId)
        try {
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            _recordingState.value = RecordingState.Recording(file, System.currentTimeMillis(), recordId)
            _recordingElapsedMs.value = 0L
            recordingTimerJob = scope.launch {
                val startedAt = System.currentTimeMillis()
                while (_recordingState.value is RecordingState.Recording) {
                    _recordingElapsedMs.value = System.currentTimeMillis() - startedAt
                    delay(200)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioNoteManager", "startRecording failed", e)
            _recordingState.value = RecordingState.Idle
        }
    }

    fun stopRecording() {
        val state = _recordingState.value
        if (state !is RecordingState.Recording) return
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Log.e("AudioNoteManager", "stopRecording failed", e)
        }
        recorder = null
        _recordingState.value = RecordingState.Idle
        refreshRecords()
    }

    fun deleteAudio(recordId: String) {
        stopPlayback()
        fileFor(recordId).delete()
        refreshRecords()
    }

    fun startPlayback(recordId: String) {
        val file = fileFor(recordId)
        if (!file.exists()) return
        stopPlayback()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            val duration = mp.duration
            mp.setOnCompletionListener {
                _playbackState.value = PlaybackState.Idle
                _playbackPositionMs.value = 0
                _playbackDurationMs.value = 0
                playbackTimerJob?.cancel()
                playbackTimerJob = null
                it.release()
                player = null
            }
            mp.start()
            player = mp
            _playbackState.value = PlaybackState.Playing(file, recordId)
            _playbackDurationMs.value = duration
            _playbackPositionMs.value = 0
            playbackTimerJob = scope.launch {
                while (_playbackState.value is PlaybackState.Playing) {
                    try {
                        _playbackPositionMs.value = player?.currentPosition ?: 0
                    } catch (_: Exception) { }
                    delay(200)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioNoteManager", "startPlayback failed", e)
        }
    }

    fun stopPlayback() {
        playbackTimerJob?.cancel()
        playbackTimerJob = null
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) { }
        player = null
        _playbackState.value = PlaybackState.Idle
        _playbackPositionMs.value = 0
        _playbackDurationMs.value = 0
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
