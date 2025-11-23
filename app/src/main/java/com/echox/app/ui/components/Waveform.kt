package com.echox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.echox.app.ui.theme.XBlue

@Composable
fun Waveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = XBlue
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw lines
        // We want to draw the latest N points.
        // Let's assume amplitudes are normalized 0..1 (or close to it)
        // But AudioRecord RMS can be large. We need to normalize or clamp.
        
        val barWidth = 4.dp.toPx()
        val gap = 2.dp.toPx()
        val maxBars = (width / (barWidth + gap)).toInt()
        
        val visibleAmplitudes = amplitudes.takeLast(maxBars)
        
        visibleAmplitudes.forEachIndexed { index, amp ->
            // Normalize amp (assuming 16-bit PCM, max is ~32767)
            // But RMS is usually lower. Let's scale it up for visibility.
            val scaledAmp = (amp / 5000f).coerceIn(0f, 1f) * height
            
            val x = index * (barWidth + gap)
            val startY = centerY - (scaledAmp / 2f)
            val endY = centerY + (scaledAmp / 2f)
            
            drawLine(
                color = color,
                start = Offset(x, startY),
                end = Offset(x, endY),
                strokeWidth = barWidth
            )
        }
    }
}
