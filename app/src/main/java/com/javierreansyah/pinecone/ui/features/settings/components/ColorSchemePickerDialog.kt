package com.javierreansyah.pinecone.ui.features.settings.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Palette
import com.javierreansyah.pinecone.R

@Composable
fun ColorPickerButton(
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "colorSwatchSelectionRingAlpha",
    )
    val innerScale by animateFloatAsState(
        targetValue = if (isSelected) 0.94f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "colorSwatchSelectionInnerScale",
    )
    val rimColor = Color.Black.copy(alpha = 0.2f).compositeOver(color)
    val iconTint = if (color.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = modifier
            .size(56.dp)
            .padding(1.dp)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha),
                shape = CircleShape,
            )
            .padding(4.dp)
            .graphicsLayer {
                scaleX = innerScale
                scaleY = innerScale
            }
            .clip(CircleShape)
            .background(color)
            .border(
                width = 2.dp,
                color = rimColor,
                shape = CircleShape,
            )
            .semantics { selected = isSelected }
            .clickable(
                onClick = onClick,
                role = Role.RadioButton,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (color == Color.White) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Palette,
                tint = iconTint,
                contentDescription = null,
            )
        }
    }
}

@Composable
fun ColorSchemePickerDialog(
    currentColor: Color,
    modifier: Modifier = Modifier,
    setShowDialog: (Boolean) -> Unit,
    onColorChange: (Color) -> Unit,
) {
    val colorSchemes = listOf(
        Color(0xfffeb4a7), Color(0xffffb3c0), Color(0xfffcaaff), Color(0xffb9c3ff),
        Color(0xff62d3ff), Color(0xff44d9f1), Color(0xff52dbc9), Color(0xff78dd77),
        Color(0xff9fd75c), Color(0xffc1d02d), Color(0xfffabd00), Color(0xffffb86e),
        Color.White,
    )

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        modifier = modifier,
        title = {
            Text(text = stringResource(R.string.settings_choose_color_scheme))
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    (0..11 step 4).forEach { start ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            colorSchemes.slice(start..start + 3).fastForEach { color ->
                                ColorPickerButton(
                                    color = color,
                                    isSelected = color == currentColor,
                                ) {
                                    onColorChange(color)
                                }
                            }
                        }
                    }
                    ColorPickerButton(
                        color = colorSchemes.last(),
                        isSelected = colorSchemes.last() == currentColor,
                    ) {
                        onColorChange(colorSchemes.last())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { setShowDialog(false) },
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}
