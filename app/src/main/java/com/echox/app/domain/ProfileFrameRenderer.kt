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
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(BACKGROUND_COLOR)

        drawHeader(canvas, chunkLabel)
        drawFooter(canvas, durationLabel)

        val avatarBitmap = loadAvatarBitmap(avatarUrl)
        drawAvatar(canvas, avatarBitmap)

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        outputFile
    }

    private suspend fun loadAvatarBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        val request =
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(600)
                .build()
        runCatching { imageLoader.execute(request).drawable?.toBitmap() }.getOrNull()
    }

    private fun drawHeader(canvas: Canvas, chunkLabel: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(chunkLabel, 64f, 96f, paint)
    }

    private fun drawFooter(canvas: Canvas, durationLabel: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xDDFFFFFF.toInt()
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(durationLabel, 64f, 1840f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Voice", 1016f, 1840f, paint)
    }

    private fun drawAvatar(canvas: Canvas, avatar: Bitmap?) {
        val centerX = canvas.width / 2f
        val centerY = canvas.height / 2f
        val outerRadius = 300f
        val innerRadius = 260f

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, outerRadius, glowPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(centerX, centerY, innerRadius + 6f, strokePaint)

        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }

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

    companion object {
        private const val BACKGROUND_COLOR = 0xFFCF8B70.toInt()
    }
}

