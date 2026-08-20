package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

@Composable
fun DeveloperIntroOverlay(
    onFinished: () -> Unit = {}
) {
    var isVisible by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }

    // RGB animated color cycle for the developer name
    val infiniteTransition = rememberInfiniteTransition(label = "RgbColorTransition")
    val hueShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HueShift"
    )

    // Helper to calculate smooth RGB color from hue
    val rgbColor = remember(hueShift) {
        val h = hueShift / 60f
        val x = (1f - kotlin.math.abs((h % 2f) - 1f))
        when (h.toInt()) {
            0 -> Color(1f, x, 0f)
            1 -> Color(x, 1f, 0f)
            2 -> Color(0f, 1f, x)
            3 -> Color(0f, x, 1f)
            4 -> Color(x, 0f, 1f)
            else -> Color(1f, 0f, x)
        }
    }

    val gradientColors = remember(hueShift) {
        listOf(
            rgbColor,
            Color(0xFF00E5FF), // Electric Neon Cyan
            Color(0xFFB388FF), // Neon Purple
            rgbColor
        )
    }

    // Exact 5-second timing lifecycle
    LaunchedEffect(Unit) {
        // 0s: Start entrance from Top-Left
        isVisible = true

        // 4s: Start exit animation (lasts 1s)
        delay(4000)
        isVisible = false

        // 5s: Complete removal
        delay(1000)
        isDismissed = true
        onFinished()
    }

    if (!isDismissed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(999f)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                ) + slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 600)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
                ) + slideOutVertically(
                    targetOffsetY = { -it / 2 },
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 700))
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE090D16)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    modifier = Modifier
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = Color(0xFF00E5FF).copy(alpha = 0.35f),
                            spotColor = Color(0xFF7C4DFF).copy(alpha = 0.5f)
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(gradientColors),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF131B2E).copy(alpha = 0.95f),
                                        Color(0xFF06090F).copy(alpha = 0.98f)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Futuristic Hexagon / Glowing Icon Avatar
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF).copy(alpha = 0.25f),
                                            Color(0xFF7C4DFF).copy(alpha = 0.35f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = rgbColor.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "تم إنشاء هذا التطبيق بواسطة المطور:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = rgbColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Developer Name with glowing animated RGB effect
                            Text(
                                text = "مُحمد مفرح",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = rgbColor
                            )
                        }
                    }
                }
            }
        }
    }
}
