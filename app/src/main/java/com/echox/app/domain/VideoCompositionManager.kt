package com.echox.app.domain

import android.content.Context
import android.net.Uri
import android.opengl.GLES20
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@UnstableApi
class VideoCompositionManager(private val context: Context) {

    suspend fun generateVideo(
            imageUri: Uri,
            audioUri: Uri,
            outputFile: File,
            durationMs: Long
    ): Uri = suspendCancellableCoroutine { continuation ->
        try {
            // 1. Prepare Image MediaItem (Video Track)
            val imageItem =
                    MediaItem.Builder().setUri(imageUri).setMimeType(MimeTypes.IMAGE_PNG).build()

            // Dynamic Pulsing Glow Effect
            val glowEffect = PulsingGlowEffect()

            val imageEditedMediaItem =
                    EditedMediaItem.Builder(imageItem)
                            .setDurationUs(durationMs * MICROS_PER_MILLISECOND)
                            .setFrameRate(DEFAULT_FRAME_RATE_FPS)
                            .setEffects(Effects(listOf(), listOf(glowEffect)))
                            .build()

            // 2. Prepare Audio MediaItem (Audio Track)
            val audioItem = MediaItem.fromUri(audioUri)
            val audioEditedMediaItem = EditedMediaItem.Builder(audioItem).build()

            // 3. Create Composition
            val videoSequence = EditedMediaItemSequence(listOf(imageEditedMediaItem))
            val audioSequence = EditedMediaItemSequence(listOf(audioEditedMediaItem))

            val composition = Composition.Builder(listOf(videoSequence, audioSequence)).build()

            // 4. Configure Transformer
            val transformer =
                    Transformer.Builder(context)
                            .addListener(
                                    object : Transformer.Listener {
                                        override fun onCompleted(
                                                composition: Composition,
                                                exportResult: ExportResult
                                        ) {
                                            continuation.resume(outputFile.toUri())
                                        }

                                        override fun onError(
                                                composition: Composition,
                                                exportResult: ExportResult,
                                                exportException: ExportException
                                        ) {
                                            continuation.resumeWithException(exportException)
                                        }
                                    }
                            )
                            .build()

            // 5. Start Export
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.createNewFile()

            transformer.start(composition, outputFile.absolutePath)
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    // Dynamic Zoom Effect (Ken Burns style)
    // Zooms from 1.0 to 1.1 over the duration
    private class PulsingGlowEffect : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
            return PulsingGlowShaderProgram(context, useHdr)
        }
    }

    private class PulsingGlowShaderProgram(context: Context, useHdr: Boolean) :
            BaseGlShaderProgram(useHdr, 1) {

        private val glProgram: GlProgram
        private val uTimeLocation: Int

        init {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            uTimeLocation = glProgram.getUniformLocation("uTime")

            // Set up attributes
            glProgram.setBufferAttribute("aFramePosition", FRAME_COORDS, 4)
            glProgram.setBufferAttribute("aTexCoords", TEXTURE_COORDS, 4)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            android.util.Log.d("EchoX_Time", "drawFrame: time=$presentationTimeUs")
            glProgram.use()
            GlUtil.checkGlError()

            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            GlUtil.checkGlError()

            // Use Identity Matrix
            val identityMatrix = FloatArray(16)
            android.opengl.Matrix.setIdentityM(identityMatrix, 0)
            glProgram.setFloatsUniform("uMvpMatrix", identityMatrix)
            GlUtil.checkGlError()

            val timeSec = presentationTimeUs / 1_000_000f
            glProgram.setFloatsUniform("uTime", floatArrayOf(timeSec))
            GlUtil.checkGlError()

            glProgram.bindAttributesAndUniforms()
            GlUtil.checkGlError()

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        }

        override fun release() {
            super.release()
            glProgram.delete()
        }

        companion object {
            private val FRAME_COORDS =
                    floatArrayOf(
                            -1.0f,
                            -1.0f,
                            0.0f,
                            1.0f,
                            1.0f,
                            -1.0f,
                            0.0f,
                            1.0f,
                            -1.0f,
                            1.0f,
                            0.0f,
                            1.0f,
                            1.0f,
                            1.0f,
                            0.0f,
                            1.0f
                    )
            private val TEXTURE_COORDS =
                    floatArrayOf(
                            0.0f,
                            0.0f,
                            0.0f,
                            1.0f,
                            1.0f,
                            0.0f,
                            0.0f,
                            1.0f,
                            0.0f,
                            1.0f,
                            0.0f,
                            1.0f,
                            1.0f,
                            1.0f,
                            0.0f,
                            1.0f
                    )

            private const val VERTEX_SHADER =
                    """
                attribute vec4 aFramePosition;
                attribute vec4 aTexCoords;
                uniform mat4 uMvpMatrix;
                varying vec2 vTexCoords;
                void main() {
                    gl_Position = uMvpMatrix * aFramePosition;
                    vTexCoords = aTexCoords.xy;
                }
            """

            private const val FRAGMENT_SHADER =
                    """
                precision mediump float;
                uniform sampler2D uTexSampler;
                uniform float uTime;
                varying vec2 vTexCoords;
                
                void main() {
                    // 1. Sample Foreground
                    vec4 foreground = texture2D(uTexSampler, vTexCoords);
                    
                    // 2. Simple Black Background
                    vec3 background = vec3(0.0, 0.0, 0.0); // Pure black
                    
                    // 3. Waveform Pulsing Effect
                    // Detect waveform (blue-ish and opaque)
                    bool isWaveform = foreground.a > 0.8 && foreground.b > foreground.r + 0.1;
                    vec3 finalForeground = foreground.rgb;
                    
                    if (isWaveform) {
                        // Pulsing brightness based on time
                        float pulse = 0.7 + 0.3 * sin(uTime * 2.5);
                        
                        // Apply pulse to waveform
                        finalForeground *= pulse;
                        
                        // Horizontal sweep shine
                        float shinePos = fract(uTime * 0.4) * 1.5 - 0.25;
                        float shineWidth = 0.25;
                        float distToShine = abs(vTexCoords.x - shinePos);
                        float shineIntensity = 1.0 - smoothstep(0.0, shineWidth, distToShine);
                        
                        // Add bright highlight
                        finalForeground += vec3(0.5) * shineIntensity;
                    }
                    
                    // 4. Composite
                    vec3 finalColor = mix(background, finalForeground, foreground.a);
                    
                    gl_FragColor = vec4(finalColor, 1.0);
                }
            """
        }
    }

    companion object {
        private const val DEFAULT_FRAME_RATE_FPS = 30
        private const val MICROS_PER_MILLISECOND = 1_000L
    }
}
