package com.ultrascaler.app.ui.screens.processing

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ProcessingScreen(
    videoUri: String,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(videoUri) {
        viewModel.startUpscaling(context, Uri.parse(videoUri))
    }

    LaunchedEffect(uiState.status) {
        when (uiState.status) {
            UpscaleStatus.COMPLETED -> {
                uiState.outputPath?.let { onComplete(it) }
            }
            UpscaleStatus.FAILED -> {
                // Handle error
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Icon
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size((120 * scale).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (uiState.status) {
                        UpscaleStatus.PROCESSING -> Icons.Default.AutoAwesome
                        UpscaleStatus.EXPORTING -> Icons.Default.SaveAlt
                        UpscaleStatus.COMPLETED -> Icons.Default.CheckCircle
                        UpscaleStatus.FAILED -> Icons.Default.Error
                        else -> Icons.Default.HourglassEmpty
                    },
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status Text
            Text(
                text = when (uiState.status) {
                    UpscaleStatus.PREPARING -> "Preparing Video..."
                    UpscaleStatus.PROCESSING -> "Upscaling Video"
                    UpscaleStatus.EXPORTING -> "Saving Video"
                    UpscaleStatus.COMPLETED -> "Complete!"
                    UpscaleStatus.FAILED -> "Failed"
                    else -> "Starting..."
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (uiState.status) {
                    UpscaleStatus.PREPARING -> "Analyzing video frames"
                    UpscaleStatus.PROCESSING -> "Enhancing with AI: ${uiState.currentFrame}/${uiState.totalFrames} frames"
                    UpscaleStatus.EXPORTING -> "Encoding to 4K"
                    UpscaleStatus.COMPLETED -> "Your video is ready!"
                    UpscaleStatus.FAILED -> uiState.errorMessage ?: "Something went wrong"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Progress Bar
            if (uiState.status == UpscaleStatus.PROCESSING || uiState.status == UpscaleStatus.EXPORTING) {
                val progress = if (uiState.totalFrames > 0) {
                    uiState.currentFrame.toFloat() / uiState.totalFrames.toFloat()
                } else {
                    uiState.progress
                }

                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Stats Row
            if (uiState.status == UpscaleStatus.PROCESSING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        icon = Icons.Default.Memory,
                        label = "GPU",
                        value = "Active"
                    )
                    StatItem(
                        icon = Icons.Default.Timer,
                        label = "Elapsed",
                        value = uiState.elapsedTime
                    )
                    StatItem(
                        icon = Icons.Default.Speed,
                        label = "Speed",
                        value = "${uiState.fps} fps"
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Cancel Button
            if (uiState.status == UpscaleStatus.PROCESSING || uiState.status == UpscaleStatus.EXPORTING) {
                OutlinedButton(
                    onClick = {
                        viewModel.cancelUpscaling()
                        onCancel()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
