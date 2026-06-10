package com.example.readerapp.ui.features.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Palette
import com.example.readerapp.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColorPickerButton(
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        shapes = IconButtonDefaults.shapes(shape = CircleShape, pressedShape = CircleShape),
        colors = IconButtonDefaults.iconButtonColors(containerColor = color),
        modifier = modifier
            .size(48.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = CircleShape
            ),
        onClick = onClick
    ) {
        AnimatedContent(isSelected) { selected ->
            val iconTint = if (color.luminance() > 0.5f) Color.Black else Color.White
            when (selected) {
                true -> Icon(
                    imageVector = MaterialSymbols.Outlined.Check,
                    tint = iconTint,
                    contentDescription = null
                )
                else ->
                    if (color == Color.White) Icon(
                        imageVector = MaterialSymbols.Outlined.Palette,
                        tint = iconTint,
                        contentDescription = null
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
        Color.White
    )

    BasicAlertDialog(
        onDismissRequest = { setShowDialog(false) },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.chooseColorScheme),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(Modifier.height(16.dp))

                Column(Modifier.align(Alignment.CenterHorizontally)) {
                    (0..11 step 4).forEach {
                        Row {
                            colorSchemes.slice(it..it + 3).fastForEach { color ->
                                ColorPickerButton(
                                    color,
                                    color == currentColor,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    onColorChange(color)
                                }
                            }
                        }
                    }
                    ColorPickerButton(
                        colorSchemes.last(),
                        colorSchemes.last() == currentColor,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        onColorChange(colorSchemes.last())
                    }
                }

                Spacer(Modifier.height(24.dp))

                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { setShowDialog(false) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}
