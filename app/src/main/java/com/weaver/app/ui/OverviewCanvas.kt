package com.weaver.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.weaver.app.assets.BitmapCache
import com.weaver.app.bridge.StitchNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * Phone-native filmstrip view of Stitch's canvas. One design per page on a phone,
 * two side-by-side when the fold is open (`pagesPerView = 2`). Snap on release —
 * no free pan/zoom, that lives on the focused view.
 *
 * The active page drives `select_node` over the bridge, and incoming
 * `selection_changed` events animate the pager to match.
 */
@Composable
fun OverviewCanvas(
    nodes: List<StitchNode>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onFocus: (String) -> Unit = {},
    bitmapCache: BitmapCache? = null,
    pagesPerView: Int = 1,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) {
        EmptyState(modifier)
        return
    }

    val selectedIndex = nodes.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { nodes.size }

    LaunchedEffect(selectedId, nodes) {
        val idx = nodes.indexOfFirst { it.id == selectedId }
        if (idx >= 0 && idx != pagerState.currentPage) {
            pagerState.animateScrollToPage(idx)
        }
    }

    LaunchedEffect(pagerState, nodes) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                nodes.getOrNull(page)?.let { node ->
                    if (node.id != selectedId) onSelect(node.id)
                }
            }
    }

    val pageSize =
        remember(pagesPerView) {
            if (pagesPerView <= 1) PageSize.Fill else FractionalPageSize(pagesPerView)
        }

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            pageSpacing = 12.dp,
            pageSize = pageSize,
            contentPadding = PaddingValues(horizontal = if (pagesPerView <= 1) 32.dp else 16.dp),
        ) { page ->
            val node = nodes[page]
            DesignTile(
                node = node,
                bitmapCache = bitmapCache,
                onTap = { onFocus(node.id) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Scrubber(
            count = nodes.size,
            current = pagerState.currentPage,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

private class FractionalPageSize(
    private val pages: Int,
) : PageSize {
    override fun Density.calculateMainAxisPageSize(
        availableSpace: Int,
        pageSpacing: Int,
    ): Int {
        val total = availableSpace - pageSpacing * (pages - 1)
        return total / pages
    }
}

@Composable
private fun DesignTile(
    node: StitchNode,
    bitmapCache: BitmapCache?,
    onTap: () -> Unit = {},
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, node.id, node.thumb) {
        val thumb = node.thumb
        value =
            if (thumb == null || bitmapCache == null) {
                null
            } else {
                withContext(Dispatchers.IO) { bitmapCache.decode(thumb)?.asImageBitmap() }
            }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .aspectRatio(9f / 19.5f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .pointerInput(node.id) { detectTapGestures(onTap = { onTap() }) },
        ) {
            val current = bitmap
            if (current != null) {
                Image(
                    painter = BitmapPainter(current),
                    contentDescription = node.id,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = node.id,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Scrubber(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 20) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            items(count) { i ->
                Box(
                    modifier =
                        Modifier
                            .size(if (i == current) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == current) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "${current + 1} / $count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No designs yet. Type a prompt below.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
