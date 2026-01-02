package com.cosmicocean.effects

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import org.intellij.lang.annotations.Language

/**
 * Cosmic Shader - Procedural nebula background using RuntimeShader (Android 13+)
 * or animated gradient fallback (Android <13)
 */
object CosmicShader {

    /**
     * AGSL Shader code for Android 13+
     * Implements simplex noise, FBM, and domain warping in shader language
     */
    @Language("AGSL")
    private val COSMIC_SHADER_SOURCE = """
        uniform float2 resolution;
        uniform float time;
        uniform float3 color1;
        uniform float3 color2;
        uniform float intensity;

        // Simplex noise helpers
        float3 permute(float3 x) {
            return mod(((x * 34.0) + 1.0) * x, 289.0);
        }

        float snoise(float2 v) {
            const float4 C = float4(
                0.211324865405187,  // (3.0-sqrt(3.0))/6.0
                0.366025403784439,  // 0.5*(sqrt(3.0)-1.0)
                -0.577350269189626, // -1.0 + 2.0 * C.x
                0.024390243902439   // 1.0 / 41.0
            );

            // First corner
            float2 i  = floor(v + dot(v, C.yy));
            float2 x0 = v -   i + dot(i, C.xx);

            // Other corners
            float2 i1;
            i1 = (x0.x > x0.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
            float4 x12 = x0.xyxy + C.xxzz;
            x12.xy -= i1;

            // Permutations
            i = mod(i, 289.0);
            float3 p = permute(permute(i.y + float3(0.0, i1.y, 1.0))
                             + i.x + float3(0.0, i1.x, 1.0));

            float3 m = max(0.5 - float3(
                dot(x0, x0),
                dot(x12.xy, x12.xy),
                dot(x12.zw, x12.zw)
            ), 0.0);

            m = m * m;
            m = m * m;

            // Gradients
            float3 x = 2.0 * fract(p * C.www) - 1.0;
            float3 h = abs(x) - 0.5;
            float3 ox = floor(x + 0.5);
            float3 a0 = x - ox;

            // Normalize
            m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);

            // Compute noise
            float3 g;
            g.x  = a0.x  * x0.x  + h.x  * x0.y;
            g.yz = a0.yz * x12.xz + h.yz * x12.yw;
            return 130.0 * dot(m, g);
        }

        // Fractal Brownian Motion
        float fbm(float2 p, int octaves) {
            float value = 0.0;
            float amplitude = 0.5;
            float frequency = 1.0;
            float maxValue = 0.0;

            for (int i = 0; i < octaves; i++) {
                value += amplitude * snoise(p * frequency);
                maxValue += amplitude;
                amplitude *= 0.5;
                frequency *= 2.0;
            }

            return value / maxValue;
        }

        // Domain warping for organic patterns
        float2 domainWarp(float2 p, float strength) {
            float2 q = float2(
                fbm(p + float2(0.0, 0.0), 3),
                fbm(p + float2(5.2, 1.3), 3)
            );

            return p + q * strength;
        }

        half4 main(float2 fragCoord) {
            // Normalize coordinates
            float2 uv = fragCoord / resolution;

            // Apply domain warping with time animation
            float2 warpedUV = domainWarp(uv * 3.0 + time * 0.05, 0.4);

            // Generate fractal noise
            float n = fbm(warpedUV, 5);

            // Normalize to 0-1 range
            n = n * 0.5 + 0.5;

            // Apply intensity (for urgency states)
            n = pow(n, 1.0 / max(intensity, 0.1));

            // Mix colors based on noise
            half3 color = mix(half3(color1), half3(color2), n);

            // Add subtle vignette
            float2 center = uv - 0.5;
            float vignette = 1.0 - dot(center, center) * 0.5;
            color *= vignette;

            return half4(color, 1.0);
        }
    """.trimIndent()

    /**
     * Check if RuntimeShader is available (Android 13+)
     */
    fun isRuntimeShaderSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * Create shader brush for Android 13+
     */
    @SuppressLint("NewApi")
    @Composable
    fun createShaderBrush(
        time: Float,
        color1: Color,
        color2: Color,
        intensity: Float = 1.0f
    ): ShaderBrush? {
        if (!isRuntimeShaderSupported()) return null

        return remember(time, color1, color2, intensity) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val shader = android.graphics.RuntimeShader(COSMIC_SHADER_SOURCE)

                    ShaderBrush(shader).also {
                        // Note: Uniforms will be set in the drawing code
                        // as they depend on canvas size
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                // Fallback to gradient if shader compilation fails
                null
            }
        }
    }

    /**
     * Update shader uniforms (call before drawing)
     */
    @SuppressLint("NewApi")
    fun updateShaderUniforms(
        shader: android.graphics.RuntimeShader?,
        time: Float,
        size: Size,
        color1: Color,
        color2: Color,
        intensity: Float = 1.0f
    ) {
        shader?.apply {
            setFloatUniform("time", time)
            setFloatUniform("resolution", size.width, size.height)
            setFloatUniform("color1", color1.red, color1.green, color1.blue)
            setFloatUniform("color2", color2.red, color2.green, color2.blue)
            setFloatUniform("intensity", intensity)
        }
    }

    /**
     * Create fallback gradient brush for Android <13
     * Animated radial gradient with time-based positioning
     */
    fun createFallbackBrush(
        time: Float,
        color1: Color,
        color2: Color,
        size: Size
    ): Brush {
        // Animate gradient center based on time
        val centerX = size.width * (0.3f + kotlin.math.sin(time * 0.1f) * 0.2f)
        val centerY = size.height * (0.3f + kotlin.math.cos(time * 0.15f) * 0.2f)

        return Brush.radialGradient(
            colors = listOf(
                color1,
                color2,
                color1.copy(alpha = 0.7f)
            ),
            center = Offset(centerX, centerY),
            radius = size.height * 0.8f
        )
    }

    /**
     * Color schemes for different urgency states
     */
    object ColorSchemes {
        val CALM = Pair(
            Color(0xFF1A0A28),  // Deep purple
            Color(0xFF6B8AFF)   // Soft blue
        )

        val ATTENTION = Pair(
            Color(0xFF14102A),  // Dark purple
            Color(0xFF9B7AFF)   // Light purple
        )

        val URGENT = Pair(
            Color(0xFF1E0A32),  // Deep violet
            Color(0xFFFFB74D)   // Orange
        )

        val CRITICAL = Pair(
            Color(0xFF280A14),  // Dark red
            Color(0xFFFF5252)   // Bright red
        )

        val DONE = Pair(
            Color(0xFF0A1E14),  // Dark green
            Color(0xFF4CAF50)   // Green
        )
    }

    /**
     * Get color scheme based on urgency level
     */
    fun getColorScheme(urgency: String): Pair<Color, Color> {
        return when (urgency.lowercase()) {
            "calm" -> ColorSchemes.CALM
            "attention" -> ColorSchemes.ATTENTION
            "urgent" -> ColorSchemes.URGENT
            "critical" -> ColorSchemes.CRITICAL
            "done" -> ColorSchemes.DONE
            else -> ColorSchemes.CALM
        }
    }

    /**
     * Get intensity value based on urgency (for shader)
     */
    fun getIntensity(urgency: String): Float {
        return when (urgency.lowercase()) {
            "calm" -> 0.8f
            "attention" -> 1.0f
            "urgent" -> 1.3f
            "critical" -> 1.6f
            "done" -> 0.9f
            else -> 1.0f
        }
    }
}
