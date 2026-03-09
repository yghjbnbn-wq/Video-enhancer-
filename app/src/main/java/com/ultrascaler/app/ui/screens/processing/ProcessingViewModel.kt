package com.ultrascaler.app.ui.screens.processing

import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrascaler.app.ml.UpscaleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Inject

enum class UpscaleStatus {
    IDLE,
    PREPARING,
    PROCESSING,
    EXPORTING,
    COMPLETED,
    FAILED
}

data class ProcessingUiState(
    val status: UpscaleStatus = UpscaleStatus.IDLE,
    val progress: Float = 0f,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val fps: Int = 0,
    val elapsedTime: String = "00:00",
    val outputPath: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ProcessingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var upscaleJob: Job? = null
    private var isCancelled = false
    private var startTime: Long = 0

    private val executor = Executors.newSingleThreadExecutor()

    fun startUpscaling(context: Context, videoUri: Uri) {
        if (_uiState.value.status != UpscaleStatus.IDLE) return

        isCancelled = false
        startTime = System.currentTimeMillis()

        upscaleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(status = UpscaleStatus.PREPARING)

                // Initialize the upscale engine
                val engine = UpscaleEngine(context)

                // Get video info
                val (inputPath, width, height, frameRate, bitRate) = getVideoInfo(context, videoUri)

                // Output settings for 4K
                val outputWidth = 3840
                val outputHeight = 2160

                // Calculate total frames
                val duration = getVideoDuration(context, videoUri)
                val totalFrames = ((duration / 1000.0) * frameRate).toInt()

                _uiState.value = _uiState.value.copy(
                    status = UpscaleStatus.PROCESSING,
                    totalFrames = totalFrames,
                    currentFrame = 0,
                    progress = 0f
                )

                // Create output file
                val outputFile = createOutputFile(context)

                // Process video
                val outputPath = processVideo(
                    context = context,
                    inputUri = videoUri,
                    outputPath = outputFile.absolutePath,
                    engine = engine,
                    outputWidth = outputWidth,
                    outputHeight = outputHeight,
                    frameRate = frameRate.toInt(),
                    bitRate = bitRate,
                    onProgress = { frame, fps ->
                        if (!isCancelled) {
                            _uiState.value = _uiState.value.copy(
                                currentFrame = frame,
                                fps = fps,
                                progress = frame.toFloat() / totalFrames.toFloat(),
                                elapsedTime = formatElapsedTime()
                            )
                        }
                    }
                )

                if (!isCancelled && outputPath != null) {
                    _uiState.value = _uiState.value.copy(
                        status = UpscaleStatus.COMPLETED,
                        outputPath = outputPath,
                        progress = 1f
                    )
                }

                // Cleanup
                engine.close()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = UpscaleStatus.FAILED,
                    errorMessage = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    private fun getVideoInfo(context: Context, uri: Uri): Quadruple<String, Int, Int, Int, Int> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
        val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
        val bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 10_000_000

        retriever.release()

        // Copy to cache for processing
        val inputFile = File(context.cacheDir, "input_video.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            inputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return Quadruple(inputFile.absolutePath, width, height, frameRate.toInt(), bitRate)
    }

    private fun getVideoDuration(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        return duration
    }

    private fun createOutputFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "upscaled")
        outputDir.mkdirs()
        return File(outputDir, "upscaled_$timestamp.mp4")
    }

    private suspend fun processVideo(
        context: Context,
        inputUri: Uri,
        outputPath: String,
        engine: UpscaleEngine,
        outputWidth: Int,
        outputHeight: Int,
        frameRate: Int,
        bitRate: Int,
        onProgress: (Int, Int) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, inputUri)

        // Setup MediaMuxer for output
        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Configure video encoder for 4K
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, outputWidth, outputHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate * 4)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxerTrackIndex = muxer.addTrack(format)
        muxer.start()

        val bufferInfo = MediaCodec.BufferInfo()

        var frameCount = 0
        var startTimeNanos = System.nanoTime()
        var lastProgressUpdate = System.currentTimeMillis()
        var currentFps = 0

        try {
            val frameIntervalUs = 1_000_000L / frameRate

            while (!isCancelled) {
                // Get frame from video
                val frameTimeUs = frameCount * frameIntervalUs
                val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                if (bitmap == null) {
                    break
                }

                // Upscale frame using ML
                val upscaledBitmap = engine.upscaleFrame(bitmap, outputWidth, outputHeight)

                // Draw to encoder surface
                val canvas = inputSurface.lockCanvas(null)
                canvas.drawBitmap(upscaledBitmap, 0f, 0f, null)
                inputSurface.unlockCanvasAndPost(canvas)

                // Drain encoder
                drainEncoder(encoder, muxer, muxerTrackIndex, bufferInfo, false)

                upscaledBitmap.recycle()
                bitmap.recycle()

                frameCount++

                // Update FPS every second
                if (System.currentTimeMillis() - lastProgressUpdate > 1000) {
                    val elapsedSec = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0
                    currentFps = (frameCount / elapsedSec).toInt()
                    onProgress(frameCount, currentFps)
                    lastProgressUpdate = System.currentTimeMillis()
                }
            }

            // Signal end of stream
            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, muxerTrackIndex, bufferInfo, true)

        } finally {
            encoder.stop()
            encoder.release()
            muxer.stop()
            muxer.release()
            retriever.release()
            inputSurface.release()
        }

        if (isCancelled) {
            File(outputPath).delete()
            return@withContext null
        }

        outputPath
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        trackIndex: Int,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean
    ) {
        val timeoutUs = 10_000L

        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)

            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Format changed
                }
                outputBufferIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null && bufferInfo.size > 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
        }
    }

    fun cancelUpscaling() {
        isCancelled = true
        upscaleJob?.cancel()
        _uiState.value = _uiState.value.copy(status = UpscaleStatus.IDLE)
    }

    private fun formatElapsedTime(): String {
        val elapsed = System.currentTimeMillis() - startTime
        val seconds = (elapsed / 1000) % 60
        val minutes = (elapsed / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        cancelUpscaling()
        executor.shutdown()
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
