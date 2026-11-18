package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.CanvasItem
import kotlin.math.roundToInt

// Map view (fase 2) — second presentation over the same Board data (K1):
// one node card per image of the current board. The card shows the image
// thumbnail, the name used by the layer list ("Image #N"), the IMAGE label,
// and a port on each side (anchor for edge creation in fase 3). Edges are
// NOT drawn here yet (fase 3); the + button for notes is fase 4 (T8), so it
// is deliberately absent — no dead buttons.
//
// Node positions use mapX/mapY (O1, own layout — not canvas posX/posY). An
// item that was never positioned (mapX == 0f && mapY == 0f) is placed on a
// deterministic grid slot by its index in the board's item list (zIndex
// order), so nodes never pile up on one point. (0, 0) is the "never
// positioned" sentinel — a node moved to exactly (0, 0) returns to its grid
// slot. Dragging nodes is NOT part of fase 2 (no to-do covers it).
@Composable
fun MapView(
    items: List<CanvasItem>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardWidth = 180.dp
    // Thumbnail 120dp + name/label rows (~52dp). Grid cell height carries a
    // little slack so rows never overlap if the text measures taller.
    val cardHeight = 188.dp
    val cellGap = 16.dp
    val startPadding = 16.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("map_view")
    ) {
        val columns = ((maxWidth - startPadding * 2 + cellGap) / (cardWidth + cellGap))
            .toInt().coerceAtLeast(1)
        val cellWidthPx = with(density) { (cardWidth + cellGap).toPx() }
        val cellHeightPx = with(density) { (cardHeight + cellGap).toPx() }
        val startXPx = with(density) { startPadding.toPx() }
        val startYPx = with(density) { startPadding.toPx() }
        val rows = (items.size + columns - 1) / columns
        val contentHeight = with(density) { (startYPx + rows * cellHeightPx).toDp() }

        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // The inner box must be tall enough to contain the lowest grid
            // row, otherwise absolute offsets cannot be scrolled to.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight)
            ) {
                items.forEachIndexed { index, item ->
                    val position = effectiveMapPosition(
                        item = item,
                        index = index,
                        columns = columns,
                        cellWidthPx = cellWidthPx,
                        cellHeightPx = cellHeightPx,
                        startXPx = startXPx,
                        startYPx = startYPx
                    )
                    MapNodeCard(
                        item = item,
                        name = "Image #${index + 1}",
                        modifier = Modifier.offset {
                            IntOffset(position.x.roundToInt(), position.y.roundToInt())
                        }
                    )
                }
            }
        }
    }
}

// Stored mapX/mapY when the node was positioned, otherwise a deterministic
// grid slot by index (see the MapView header comment).
private fun effectiveMapPosition(
    item: CanvasItem,
    index: Int,
    columns: Int,
    cellWidthPx: Float,
    cellHeightPx: Float,
    startXPx: Float,
    startYPx: Float
): Offset {
    if (item.mapX == 0f && item.mapY == 0f) {
        val column = index % columns
        val row = index / columns
        return Offset(
            x = startXPx + column * cellWidthPx,
            y = startYPx + row * cellHeightPx
        )
    }
    return Offset(item.mapX, item.mapY)
}

@Composable
fun MapNodeCard(
    item: CanvasItem,
    name: String,
    modifier: Modifier = Modifier
) {
    // The ports sit OUTSIDE the Card (half past its left/right edge) so they
    // are not clipped by the Card's rounded shape. They are centered on the
    // thumbnail (120dp tall -> center 60dp, minus half the 12dp port = 54dp).
    Box(
        modifier = modifier
            .width(180.dp)
            .testTag("map_node_${item.id}")
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.thumbPath)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        colorFilter = if (item.isGrayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    )
                }
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "IMAGE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
        // Left port — anchor for edge creation (fase 3); visual only here.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-6).dp, y = 54.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .testTag("map_port_left_${item.id}")
        )
        // Right port — anchor for edge creation (fase 3); visual only here.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = 54.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .testTag("map_port_right_${item.id}")
        )
    }
}
