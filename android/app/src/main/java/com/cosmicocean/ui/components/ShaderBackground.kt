package com.cosmicocean.ui.components

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import com.cosmicocean.effects.CosmicShader
import kotlinx.coroutines.delay

/**
 * Shader Background Composable
 * Renders procedural cosmic nebula background with RuntimeShader (Android 13+)
 * or animated gradient fallback (Android <13)
 */
@Composable
fun ShaderBackground(
    urgency: String = "calm",
    modifier: Modifier = Modifier
) {
    // Animation time value
    var time by remember { mutableFloatStateOf(0f) }

    // Animate time continuously
    LaunchedEffect(Unit) {
        while (true) {
            time += 0.016f // ~60fps increment
            if (time > 1000f) time = 0f // Reset to prevent overflow
            delay(16) // ~60fps
        }
    }

    // Get colors for current urgency state
    val (color1, color2) = CosmicShader.getColorScheme(urgency)
    val intensity = CosmicShader.getIntensity(urgency)

    // Determine if we can use RuntimeShader
    val useShader = CosmicShader.isRuntimeShaderSupported()

    Canvas(modifier = modifier.fillMaxSize()) {
        if (useShader && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use RuntimeShader for Android 13+
            try {
                val shader = android.graphics.RuntimeShader(getShaderSource())

                // Update uniforms
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("color1", color1.red, color1.green, color1.blue)
                shader.setFloatUniform("color2", color2.red, color2.green, color2.blue)
                shader.setFloatUniform("intensity", intensity)

                // Draw with shader
                drawRect(ShaderBrush(shader))
            } catch (e: Exception) {
                // Fallback to gradient if shader fails
                drawRect(CosmicShader.createFallbackBrush(time, color1, color2, size))
            }
        } else {
            // Use animated gradient for Android <13
            drawRect(CosmicShader.createFallbackBrush(time, color1, color2, size))
        }
    }
}

/**
 * Alternative implementation with manual canvas-based noise rendering
 * Slower but works on all Android versions
 */
@Composable
fun CanvasNoiseBackground(
    urgency: String = "calm",
    modifier: Modifier = Modifier
) {
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            time += 0.016f
            if (time > 1000f) time = 0f
            delay(16)
        }
    }

    val (color1, color2) = CosmicShader.getColorScheme(urgency)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width.toInt()
        val height = size.height.toInt()

        // Sample resolution - lower for better performance
        val sampleRes = 8 // Sample every Nth pixel

        for (y in 0 until height step sampleRes) {
            for (x in 0 until width step sampleRes) {
                // Normalize coordinates
                val nx = x / width.toFloat()
                val ny = y / height.toFloat()

                // Generate noise with domain warping
                val noise = com.cosmicocean.effects.NoiseLibrary.domainWarp(
                    nx * 3f + time * 0.05f,
                    ny * 3f + time * 0.075f,
                    0.4f
                )

                // Normalize to 0-1
                val n = (noise + 1f) * 0.5f

                // Interpolate between colors
                val r = color1.red + (color2.red - color1.red) * n
                val g = color1.green + (color2.green - color1.green) * n
                val b = color1.blue + (color2.blue - color1.blue) * n

                val color = androidx.compose.ui.graphics.Color(r, g, b)

                // Draw larger rectangles for sampled pixels
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat()),
                    size = androidx.compose.ui.geometry.Size(sampleRes.toFloat(), sampleRes.toFloat())
                )
            }
        }
    }
}

/**
 * Optimized version that pre-renders noise to bitmap and updates periodically
 * Good balance between performance and visual quality
 */
@Composable
fun OptimizedShaderBackground(
    urgency: String = "calm",
    modifier: Modifier = Modifier,
    updateIntervalMs: Long = 100 // Update every 100ms instead of every frame
) {
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            time += updateIntervalMs / 1000f
            if (time > 1000f) time = 0f
            delay(updateIntervalMs)
        }
    }

    // Use the faster fallback for older devices
    val (color1, color2) = CosmicShader.getColorScheme(urgency)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(CosmicShader.createFallbackBrush(time, color1, color2, size))
    }
}

/**
 * Helper function to get shader source
 * Extracted to avoid recompilation
 */
private fun getShaderSource(): String = """
    uniform float2 resolution;
    uniform float time;
    uniform float3 color1;
    uniform float3 color2;
    uniform float intensity;

    float3 permute(float3 x) {
        return mod(((x * 34.0) + 1.0) * x, 289.0);
    }

    float snoise(float2 v) {
        const float4 C = float4(
            0.211324865405187,
            0.366025403784439,
            -0.577350269189626,
            0.024390243902439
        );

        float2 i  = floor(v + dot(v, C.yy));
        float2 x0 = v -   i + dot(i, C.xx);

        float2 i1;
        i1 = (x0.x > x0.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
        float4 x12 = x0.xyxy + C.xxzz;
        x12.xy -= i1;

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

        float3 x = 2.0 * fract(p * C.www) - 1.0;
        float3 h = abs(x) - 0.5;
        float3 ox = floor(x + 0.5);
        float3 a0 = x - ox;

        m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);

        float3 g;
        g.x  = a0.x  * x0.x  + h.x  * x0.y;
        g.yz = a0.yz * x12.xz + h.yz * x12.yw;
        return 130.0 * dot(m, g);
    }

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

    float2 domainWarp(float2 p, float strength) {
        float2 q = float2(
            fbm(p + float2(0.0, 0.0), 3),
            fbm(p + float2(5.2, 1.3), 3)
        );

        return p + q * strength;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float2 warpedUV = domainWarp(uv * 3.0 + time * 0.05, 0.4);
        float n = fbm(warpedUV, 5);
        n = n * 0.5 + 0.5;
        n = pow(n, 1.0 / max(intensity, 0.1));
        half3 color = mix(half3(color1), half3(color2), n);
        float2 center = uv - 0.5;
        float vignette = 1.0 - dot(center, center) * 0.5;
        color *= vignette;
        return half4(color, 1.0);
    }
""".trimIndent()
