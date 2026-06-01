package com.example.readerapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeSwatch(
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    color: Color = Color.Transparent,
    iconRes: Int? = null,
    isAuto: Boolean = false,
    isCustom: Boolean = false,
    label: String,
    modifier: Modifier = Modifier.padding(horizontal = 4.dp)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .padding(2.dp)
                .let { m ->
                    if (isSelected) {
                        m.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                    } else m
                }
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (isAuto || isCustom || iconRes != null) Color.Transparent else color)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isAuto) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = Color.White,
                        startAngle = 90f,
                        sweepAngle = 180f,
                        useCenter = true
                    )
                    drawArc(
                        color = Color.Black,
                        startAngle = 270f,
                        sweepAngle = 180f,
                        useCenter = true
                    )
                }
            } else if (iconRes != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else if (isCustom) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Add, 
                    contentDescription = "Add Theme", 
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center
        )
    }
}
