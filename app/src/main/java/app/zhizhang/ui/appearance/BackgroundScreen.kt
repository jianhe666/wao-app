package app.zhizhang.ui.appearance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.ui.components.BackgroundArtwork
import app.zhizhang.ui.components.BackgroundPreset
import app.zhizhang.ui.components.BackgroundPresets
import app.zhizhang.ui.components.ZhCard
import app.zhizhang.ui.components.liquidGlass
import app.zhizhang.ui.theme.zh
import kotlin.math.roundToInt

@Composable
fun BackgroundScreen(onBack: () -> Unit) {
    val viewModel: BackgroundViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var opacity by remember { mutableFloatStateOf(state.opacity) }
    var glassTransparency by remember { mutableFloatStateOf(state.glassTransparency) }
    LaunchedEffect(state.opacity) { opacity = state.opacity }
    LaunchedEffect(state.glassTransparency) { glassTransparency = state.glassTransparency }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importCustom(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "个性化背景",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "预设、自己的照片和背景透明度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text("当前效果", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.zh.glassBorder, RoundedCornerShape(24.dp)),
        ) {
            BackgroundArtwork(
                presetId = state.presetId,
                opacity = opacity,
                customImagePath = state.customPath.takeIf { it.isNotBlank() },
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("预设背景", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        BackgroundPresets.all.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { preset ->
                    PresetCard(
                        preset = preset,
                        selected = state.presetId == preset.id,
                        onClick = { viewModel.selectPreset(preset.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))
        Text("自定义背景", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        ZhCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.zh.glassBorder, RoundedCornerShape(14.dp)),
                ) {
                    if (state.customPath.isBlank()) {
                        Text(
                            text = "＋",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        BackgroundArtwork(
                            presetId = "custom",
                            opacity = 1f,
                            customImagePath = state.customPath,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.customPath.isBlank()) "选择一张图片" else "使用自己的背景",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "图片会复制到 App 本地，不会上传",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.importing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(
                        onClick = { picker.launch(arrayOf("image/*")) },
                        modifier = Modifier.liquidGlass(
                            shape = RoundedCornerShape(50),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            lensHeight = 6.dp,
                            lensAmount = 14.dp,
                            blurRadius = 2.dp,
                        ),
                    ) {
                        Text("选择图片")
                    }
                }
            }
            if (state.customPath.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { viewModel.selectPreset("custom") },
                    modifier = Modifier
                        .align(Alignment.End)
                        .liquidGlass(
                            shape = RoundedCornerShape(50),
                            tint = if (state.presetId == "custom") {
                                MaterialTheme.zh.income.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            },
                            lensHeight = 6.dp,
                            lensAmount = 14.dp,
                            blurRadius = 2.dp,
                        ),
                ) {
                    Text(
                        if (state.presetId == "custom") "当前使用" else "使用这张图片",
                        color = if (state.presetId == "custom") MaterialTheme.zh.income
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            state.error?.let { error ->
                Spacer(Modifier.height(5.dp))
                Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.zh.expense)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "背景图案不透明度  ${(opacity * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(16.dp),
                    tint = MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                    lensHeight = 6.dp,
                    lensAmount = 14.dp,
                    blurRadius = 2.dp,
                )
                .padding(horizontal = 8.dp),
        ) {
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { viewModel.setOpacity(opacity) },
                valueRange = 0f..1f,
            )
        }

        Spacer(Modifier.height(22.dp))
        Text(
            text = "玻璃透明度  ${(glassTransparency * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(16.dp),
                    tint = MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                    lensHeight = 6.dp,
                    lensAmount = 14.dp,
                    blurRadius = 2.dp,
                )
                .padding(horizontal = 8.dp),
        ) {
            Slider(
                value = glassTransparency,
                onValueChange = { glassTransparency = it },
                onValueChangeFinished = { viewModel.setGlassTransparency(glassTransparency) },
                valueRange = 0f..1f,
            )
        }
        Spacer(Modifier.height(34.dp))
    }
}

@Composable
private fun PresetCard(
    preset: BackgroundPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                lensHeight = 8.dp,
                lensAmount = 18.dp,
                blurRadius = 3.dp,
            )
            .clickable(onClick = onClick)
            .padding(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            BackgroundArtwork(
                presetId = preset.id,
                opacity = 1f,
                customImagePath = null,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = preset.description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
