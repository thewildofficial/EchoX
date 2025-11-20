package com.echox.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileFrameRenderer(private val context: Context) {

    private val imageLoader = ImageLoader(context)

    suspend fun renderFrame(
            avatarUrl: String?,
            durationLabel: String,
            chunkLabel: String,
            amplitudes: List<Float>,
            outputFile: File
    ): File =
            withContext(Dispatchers.IO) {
                // 16:9 Aspect Ratio (Landscape)
                val width = 1920
                val height = 1080
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                // Premium Dark Background
                canvas.drawColor(BACKGROUND_COLOR)

                // Draw Waveform (Behind Avatar)
                drawWaveform(canvas, amplitudes, width, height)

                // Draw Watermark
                drawWatermark(canvas)

                // Draw Avatar
                val avatarBitmap = loadAvatarBitmap(avatarUrl)
                drawAvatar(canvas, avatarBitmap)

                // Draw Duration/Footer
                drawFooter(canvas, durationLabel)

                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                outputFile
            }

    private suspend fun loadAvatarBitmap(url: String?): Bitmap? =
            withContext(Dispatchers.IO) {
                if (url.isNullOrBlank()) return@withContext null
                val request =
                        ImageRequest.Builder(context)
                                .data(url)
                                .allowHardware(false)
                                .size(600)
                                .build()
                runCatching { imageLoader.execute(request).drawable?.toBitmap() }.getOrNull()
            }

    private fun drawWatermark(canvas: Canvas) {
        val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x80FFFFFF.toInt() // 50% White
                    textSize = 48f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
        // Top right corner
        canvas.drawText("EchoX", canvas.width - 64f, 96f, paint)
    }

    private fun drawFooter(canvas: Canvas, durationLabel: String) {
        val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xDDFFFFFF.toInt()
                    textSize = 42f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
        // Bottom left
        canvas.drawText(durationLabel, 64f, canvas.height - 64f, paint)
    }

    private fun drawAvatar(canvas: Canvas, avatar: Bitmap?) {
        val centerX = canvas.width / 2f
        val centerY = canvas.height / 2f
        val outerRadius = 220f
        val innerRadius = 200f

        // Glow effect
        val glowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x221d9bf0 // XBlue with low alpha
                    style = Paint.Style.FILL
                    maskFilter =
                            android.graphics.BlurMaskFilter(
                                    80f,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                            )
                }
        canvas.drawCircle(centerX, centerY, outerRadius + 40f, glowPaint)

        val strokePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
        canvas.drawCircle(centerX, centerY, innerRadius + 4f, strokePaint)

        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        val path = Path().apply { addCircle(centerX, centerY, innerRadius, Path.Direction.CCW) }
        canvas.save()
        canvas.clipPath(path)

        if (avatar != null) {
            val scaled =
                    Bitmap.createScaledBitmap(
                            avatar,
                            (innerRadius * 2).toInt(),
                            (innerRadius * 2).toInt(),
                            true
                    )
            val rect =
                    RectF(
                            centerX - innerRadius,
                            centerY - innerRadius,
                            centerX + innerRadius,
                            centerY + innerRadius
                    )
            canvas.drawBitmap(scaled, null, rect, avatarPaint)
            scaled.recycle()
        } else {
            avatarPaint.color = 0xFF2A2A2A.toInt()
            canvas.drawCircle(centerX, centerY, innerRadius, avatarPaint)
        }

        canvas.restore()
    }

    private fun drawWaveform(canvas: Canvas, amplitudes: List<Float>, width: Int, height: Int) {
        if (amplitudes.isEmpty()) return

        val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF1d9bf0.toInt() // XBlue
                    strokeWidth = 6f
                    strokeCap = Paint.Cap.ROUND
                    style = Paint.Style.STROKE
                }

        val centerY = height / 2f
        // Draw across the entire width, behind avatar
        // We'll use a subset of amplitudes or stretch them
        val maxPoints = 100
        val pointsToDraw =
                if (amplitudes.size > maxPoints) {
                    amplitudes.takeLast(maxPoints) // Take latest
                } else {
                    amplitudes
                }

        val stepX = width.toFloat() / (pointsToDraw.size + 1)

        pointsToDraw.forEachIndexed { index, amp ->
            val x = (index + 1) * stepX
            // Scale amplitude. Assuming normalized 0..1 roughly, but let's clamp
            // Max height of wave = 300px
            val waveHeight = (amp * 500f).coerceAtMost(300f)

            val startY = centerY - (waveHeight / 2f)
            val endY = centerY + (waveHeight / 2f)

            canvas.drawLine(x, startY, x, endY, paint)
        }
    }

    companion object {
        private const val BACKGROUND_COLOR = android.graphics.Color.TRANSPARENT
    }
}
