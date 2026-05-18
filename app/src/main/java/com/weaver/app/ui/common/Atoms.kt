package com.weaver.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weaver.app.ui.theme.Bg
import com.weaver.app.ui.theme.Block
import com.weaver.app.ui.theme.Line
import com.weaver.app.ui.theme.LineStrong
import com.weaver.app.ui.theme.Surface1
import com.weaver.app.ui.theme.Surface2
import com.weaver.app.ui.theme.TextPrimary
import com.weaver.app.ui.theme.Voltage
import com.weaver.app.ui.theme.WeaverType

/**
 * Dotted-grid canvas background; sits beneath every Weaver screen.
 */
@Composable
fun DottedCanvas(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(Bg)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 16.dp.toPx()
            val r = 1.dp.toPx()
            val dot = Color.White.copy(alpha = 0.06f)
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(dot, radius = r, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        content()
    }
}

data class PillItem(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val onClick: () -> Unit = {},
)

@Composable
fun PillBar(items: List<PillItem>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { Pill(it) }
    }
}

@Composable
fun Pill(item: PillItem) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (item.active) LineStrong else Line, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (item.active) Voltage else TextPrimary,
            modifier = Modifier.size(16.dp),
        )
        Text(item.label, style = WeaverType.Pill.copy(color = TextPrimary))
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * Artboard card with a header strip and body slot. The active flag highlights
 * the current selection; peek dims the card so it reads as off-stack.
 */
@Composable
fun Artboard(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    width: Dp,
    height: Dp,
    active: Boolean = false,
    peek: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(width).height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, if (active) LineStrong else Line, RoundedCornerShape(14.dp))
            .alpha(if (peek) 0.85f else 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface2)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (active) Box(Modifier.size(6.dp).clip(CircleShape).background(Voltage))
                Text(title, style = WeaverType.CardTitle, maxLines = 1)
            }
            Text(meta, style = WeaverType.MonoSmall)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun WireBlock(
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    radius: Dp = 10.dp,
    tint: Color = Block,
) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(tint),
    )
}
