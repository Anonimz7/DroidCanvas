package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.CanvasItem
import com.example.data.ConnectionType
import com.example.data.Edge
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Map view (fase 3) — second presentation over the same Board data (K1):
// one node card per image of the current board, connected by typed edges.
// The Map is as free as the Board (K8): its own pan/zoom camera (separate
// from the Board camera, persisted per board in the ViewModel) and nodes
// that can be dragged anywhere. The grid from fase 2 is only the initial
// placement for nodes that were never positioned (mapX == 0f && mapY == 0f).
//
// Touch disambiguation (T7) — one awaitEachGesture loop for the whole Map
// area, like the Board background handler, so no two detectors fight over
// the same touch: a touch on a port starts an edge, on a card drags the
// node, on empty space pans/zooms. A tap on an edge line opens the delete
// confirmation. Edges are drawn UNDER the cards; their geometry is derived
// from the node positions at render time (K5), so lines follow live while
// a node is dragged. The + button for notes is fase 4 (T8), still absent.
@Composable
fun MapView(
    items: List<CanvasItem>,
    edges: List<Edge>,
    viewModel: DroidCanvasViewModel,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val cardWidth = 180.dp
    // Thumbnail 120dp + name/label rows (~52dp). Grid cell height carries a
    // little slack so rows never overlap if the text measures taller.
    val cardHeight = 188.dp
    val cellGap = 16.dp
    val startPadding = 16.dp
    // Port center from the card top: the 12dp port sits at y = 54dp, so its
    // center is at 60dp. Port centers are (x, y + 60dp) and (x + 180dp, y + 60dp).
    val portCenterYOffset = 60.dp

    val cardWidthPx = with(density) { cardWidth.toPx() }
    val cardHeightPx = with(density) { cardHeight.toPx() }
    val cellWidthPx = with(density) { (cardWidth + cellGap).toPx() }
    val cellHeightPx = with(density) { (cardHeight + cellGap).toPx() }
    val startXPx = with(density) { startPadding.toPx() }
    val startYPx = with(density) { startPadding.toPx() }
    val portYOffsetPx = with(density) { portCenterYOffset.toPx() }
    // The port visual is 12dp; the touch target is a generous 28dp radius
    // around its center. Same radius for tapping a thin edge line.
    val portTouchRadiusPx = with(density) { 28.dp.toPx() }
    val edgeTouchRadiusPx = with(density) { 28.dp.toPx() }

    // Fresh data for the gesture loop: pointerInput(Unit) never restarts,
    // so the loop reads through these instead of the captured params.
    val currentItems by rememberUpdatedState(items)
    val currentEdges by rememberUpdatedState(edges)

    // Temp edge line while creating an edge (content coords), the pending
    // (fromId, toId) pair for the type picker, and the edge awaiting delete.
    var tempEdgeStart by remember { mutableStateOf<Offset?>(null) }
    var tempEdgeEnd by remember { mutableStateOf<Offset?>(null) }
    var pendingEdge by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var edgeToDelete by remember { mutableStateOf<Edge?>(null) }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val labelPillBackground = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
    val tempLineColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("map_view")
    ) {
        val columns = ((maxWidth - startPadding * 2 + cellGap) / (cardWidth + cellGap))
            .toInt().coerceAtLeast(1)
        val currentColumns by rememberUpdatedState(columns)

        // Node top-left positions in Map content coords (stored mapX/mapY,
        // or a deterministic grid slot when never positioned).
        val positions = items.mapIndexed { index, item ->
            item.id to effectiveMapPosition(
                item, index, columns, cellWidthPx, cellHeightPx, startXPx, startYPx
            )
        }.toMap()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downScreen = down.position
                        // The camera is fixed for the whole gesture in
                        // NODE_DRAG / EDGE_CREATE (single finger); PAN
                        // updates it live through the ViewModel, so the
                        // start values are captured for hit tests.
                        val scaleAtDown = viewModel.mapScale
                        val txAtDown = viewModel.mapTranslateX
                        val tyAtDown = viewModel.mapTranslateY
                        fun toContent(screen: Offset) =
                            Offset((screen.x - txAtDown) / scaleAtDown, (screen.y - tyAtDown) / scaleAtDown)
                        fun toScreen(content: Offset) =
                            Offset(content.x * scaleAtDown + txAtDown, content.y * scaleAtDown + tyAtDown)

                        fun positionsOf(list: List<CanvasItem>): Map<Int, Offset> =
                            list.mapIndexed { index, item ->
                                item.id to effectiveMapPosition(
                                    item, index, currentColumns,
                                    cellWidthPx, cellHeightPx, startXPx, startYPx
                                )
                            }.toMap()

                        // Topmost first: items are drawn in list (zIndex)
                        // order, so the last one is on top.
                        fun portItemAt(screenPos: Offset, list: List<CanvasItem>, posById: Map<Int, Offset>): CanvasItem? {
                            for (item in list.asReversed()) {
                                val pos = posById[item.id] ?: continue
                                val (left, right) = portCenters(pos, cardWidthPx, portYOffsetPx)
                                if ((toScreen(left) - screenPos).getDistance() <= portTouchRadiusPx) return item
                                if ((toScreen(right) - screenPos).getDistance() <= portTouchRadiusPx) return item
                            }
                            return null
                        }

                        fun nodeAt(screenPos: Offset, list: List<CanvasItem>, posById: Map<Int, Offset>): CanvasItem? {
                            for (item in list.asReversed()) {
                                val pos = posById[item.id] ?: continue
                                val topLeft = toScreen(pos)
                                val w = cardWidthPx * scaleAtDown
                                val h = cardHeightPx * scaleAtDown
                                if (screenPos.x in topLeft.x..(topLeft.x + w) &&
                                    screenPos.y in topLeft.y..(topLeft.y + h)
                                ) return item
                            }
                            return null
                        }

                        fun edgeAt(screenPos: Offset, list: List<CanvasItem>, edgeList: List<Edge>, posById: Map<Int, Offset>): Edge? {
                            for (edge in edgeList) {
                                val fromPos = posById[edge.fromItemId] ?: continue
                                val toPos = posById[edge.toItemId] ?: continue
                                val curve = edgeCurve(fromPos, toPos, cardWidthPx, portYOffsetPx)
                                var minDist = Float.MAX_VALUE
                                for (i in 0..24) {
                                    val pt = cubicPoint(i / 24f, curve.p0, curve.p1, curve.p2, curve.p3)
                                    minDist = min(minDist, (toScreen(pt) - screenPos).getDistance())
                                }
                                if (minDist <= edgeTouchRadiusPx) return edge
                            }
                            return null
                        }

                        val startList = currentItems
                        val startPositions = positionsOf(startList)
                        val sourceItem = portItemAt(downScreen, startList, startPositions)
                        val targetItem = if (sourceItem == null) nodeAt(downScreen, startList, startPositions) else null
                        val mode = when {
                            sourceItem != null -> MapDragMode.EDGE_CREATE
                            targetItem != null -> MapDragMode.NODE_DRAG
                            else -> MapDragMode.PAN
                        }
                        val dragStartPos = targetItem?.let { startPositions[it.id] }
                        val sourcePort = sourceItem?.let { item ->
                            val pos = startPositions[item.id] ?: return@let null
                            val (left, right) = portCenters(pos, cardWidthPx, portYOffsetPx)
                            if ((toScreen(left) - downScreen).getDistance() <=
                                (toScreen(right) - downScreen).getDistance()
                            ) left else right
                        }
                        if (mode == MapDragMode.EDGE_CREATE) {
                            tempEdgeStart = sourcePort
                            tempEdgeEnd = toContent(downScreen)
                        }

                        var pastSlop = false
                        var snapshotTaken = false
                        var dragDeltaPx = Offset.Zero
                        var lastFingerScreen = downScreen
                        var zoom = 1f
                        var pan = Offset.Zero
                        val touchSlop = viewConfiguration.touchSlop

                        do {
                            val event = awaitPointerEvent()
                            val active = event.changes.find { it.id == down.id }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            zoom *= zoomChange
                            pan += panChange
                            val zoomMotion = abs(1 - zoom) * event.calculateCentroidSize(useCurrent = false)
                            val panMotion = pan.getDistance()
                            if (!pastSlop && (zoomMotion > touchSlop || panMotion > touchSlop)) {
                                pastSlop = true
                            }
                            if (active != null) {
                                lastFingerScreen = active.position
                                when (mode) {
                                    MapDragMode.NODE_DRAG -> {
                                        val delta = active.position - active.previousPosition
                                        dragDeltaPx += delta
                                        if (pastSlop) {
                                            // Undo snapshot once, BEFORE any
                                            // position override is applied —
                                            // snapshotting later would capture
                                            // the already-moved position.
                                            if (!snapshotTaken) {
                                                viewModel.saveCurrentStateToUndo()
                                                snapshotTaken = true
                                            }
                                            viewModel.updateMapPositionInMemory(
                                                targetItem!!,
                                                dragStartPos!!.x + dragDeltaPx.x / scaleAtDown,
                                                dragStartPos.y + dragDeltaPx.y / scaleAtDown
                                            )
                                        }
                                        if (delta != Offset.Zero) active.consume()
                                    }
                                    MapDragMode.EDGE_CREATE -> {
                                        tempEdgeEnd = toContent(active.position)
                                        if (active.position != active.previousPosition) active.consume()
                                    }
                                    MapDragMode.PAN -> {
                                        if (pastSlop) {
                                            val centroid = event.calculateCentroid(useCurrent = true)
                                            val centroidCoord = if (centroid != Offset.Unspecified) centroid else down.position
                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                val zoomRes = CanvasZoomHelper.computeZoom(
                                                    viewModel.mapScale, zoomChange, panChange.x, panChange.y,
                                                    viewModel.mapTranslateX, viewModel.mapTranslateY,
                                                    centroidCoord.x, centroidCoord.y, 0.25f, 4f
                                                )
                                                if (zoomRes != null) {
                                                    viewModel.mapScale = zoomRes.nextScale
                                                    viewModel.mapTranslateX = zoomRes.nextTranslateX
                                                    viewModel.mapTranslateY = zoomRes.nextTranslateY
                                                }
                                            }
                                            event.changes.forEach {
                                                if (it.position != it.previousPosition) it.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        when (mode) {
                            MapDragMode.NODE_DRAG -> {
                                if (pastSlop && snapshotTaken && targetItem != null) {
                                    // One DB write for the whole drag.
                                    viewModel.commitMapPosition(
                                        targetItem,
                                        dragStartPos!!.x + dragDeltaPx.x / scaleAtDown,
                                        dragStartPos.y + dragDeltaPx.y / scaleAtDown
                                    )
                                }
                                // Tap on a node (no movement): no-op.
                            }
                            MapDragMode.EDGE_CREATE -> {
                                tempEdgeStart = null
                                tempEdgeEnd = null
                                val endList = currentItems
                                val target = nodeAt(lastFingerScreen, endList, positionsOf(endList))
                                if (sourceItem != null && target != null && target.id != sourceItem.id) {
                                    pendingEdge = sourceItem.id to target.id
                                }
                                // Released on empty space or the same node:
                                // cancelled, nothing saved.
                            }
                            MapDragMode.PAN -> {
                                if (!pastSlop) {
                                    // Tap: hit-test the edge curves.
                                    val endList = currentItems
                                    val hit = edgeAt(downScreen, endList, currentEdges, positionsOf(endList))
                                    if (hit != null) edgeToDelete = hit
                                }
                            }
                        }
                    }
                }
        ) {
            // Transformed Map content: edges under the node cards.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = viewModel.mapScale
                        scaleY = viewModel.mapScale
                        translationX = viewModel.mapTranslateX
                        translationY = viewModel.mapTranslateY
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Temp line while creating an edge.
                    val start = tempEdgeStart
                    val end = tempEdgeEnd
                    if (start != null && end != null) {
                        drawLine(
                            color = tempLineColor,
                            start = start,
                            end = end,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    for (edge in edges) {
                        val fromPos = positions[edge.fromItemId] ?: continue
                        val toPos = positions[edge.toItemId] ?: continue
                        val curve = edgeCurve(fromPos, toPos, cardWidthPx, portYOffsetPx)
                        val type = ConnectionType.fromName(edge.type)
                        val path = Path().apply {
                            moveTo(curve.p0.x, curve.p0.y)
                            cubicTo(
                                curve.p1.x, curve.p1.y,
                                curve.p2.x, curve.p2.y,
                                curve.p3.x, curve.p3.y
                            )
                        }
                        drawPath(
                            path = path,
                            color = Color(type.colorArgb),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Type label pill at the curve midpoint (K3, K5).
                        val mid = cubicPoint(0.5f, curve.p0, curve.p1, curve.p2, curve.p3)
                        val layout = textMeasurer.measure(text = type.label, style = labelStyle)
                        val padX = 8.dp.toPx()
                        val padY = 4.dp.toPx()
                        val pillW = layout.size.width + padX * 2
                        val pillH = layout.size.height + padY * 2
                        drawRoundRect(
                            color = labelPillBackground,
                            topLeft = Offset(mid.x - pillW / 2, mid.y - pillH / 2),
                            size = Size(pillW, pillH),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        drawText(
                            layout,
                            topLeft = Offset(
                                mid.x - layout.size.width / 2,
                                mid.y - layout.size.height / 2
                            )
                        )
                    }
                }
                items.forEachIndexed { index, item ->
                    val position = positions[item.id] ?: return@forEachIndexed
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

        // Connection type picker (T7): shown after dragging from a port
        // onto another node. No arrows (K4); the line is undirected, so a
        // duplicate of the same type in either direction is refused.
        val pending = pendingEdge
        if (pending != null) {
            val (fromId, toId) = pending
            AlertDialog(
                onDismissRequest = { pendingEdge = null },
                title = {
                    Text(
                        text = "Connection type",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                text = {
                    Column {
                        ConnectionType.entries.forEach { type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val exists = currentEdges.any { edge ->
                                            edge.type == type.name &&
                                                ((edge.fromItemId == fromId && edge.toItemId == toId) ||
                                                    (edge.fromItemId == toId && edge.toItemId == fromId))
                                        }
                                        if (exists) {
                                            Toast.makeText(
                                                context,
                                                "Garis '${type.label}' sudah ada",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            viewModel.insertEdge(fromId, toId, type)
                                        }
                                        pendingEdge = null
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(type.colorArgb))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { pendingEdge = null }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Delete edge confirmation (T7): opened by tapping an edge line.
        val doomed = edgeToDelete
        if (doomed != null) {
            val type = ConnectionType.fromName(doomed.type)
            AlertDialog(
                onDismissRequest = { edgeToDelete = null },
                title = {
                    Text(
                        text = "Hapus garis",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                text = {
                    Text("Hapus garis '${type.label}' ini?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteEdge(doomed)
                            edgeToDelete = null
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Hapus", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { edgeToDelete = null }) {
                        Text("Batal")
                    }
                }
            )
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

// Port centers of a node card (left, right), in Map content coords.
// The ports sit half outside the card; their centers are on the card edge.
private fun portCenters(
    nodePos: Offset,
    cardWidthPx: Float,
    portYOffsetPx: Float
): Pair<Offset, Offset> =
    Offset(nodePos.x, nodePos.y + portYOffsetPx) to
        Offset(nodePos.x + cardWidthPx, nodePos.y + portYOffsetPx)

// A cubic bezier between two nodes (K5): from the facing port of the first
// node to the facing port of the second. Control points run along the axis
// of greater separation so the curve stays smooth in both orientations.
private data class EdgeCurve(val p0: Offset, val p1: Offset, val p2: Offset, val p3: Offset)

private fun edgeCurve(
    fromPos: Offset,
    toPos: Offset,
    cardWidthPx: Float,
    portYOffsetPx: Float
): EdgeCurve {
    val fromCenterX = fromPos.x + cardWidthPx / 2
    val toCenterX = toPos.x + cardWidthPx / 2
    val (p0, p3) = if (fromCenterX <= toCenterX) {
        Offset(fromPos.x + cardWidthPx, fromPos.y + portYOffsetPx) to
            Offset(toPos.x, toPos.y + portYOffsetPx)
    } else {
        Offset(fromPos.x, fromPos.y + portYOffsetPx) to
            Offset(toPos.x + cardWidthPx, toPos.y + portYOffsetPx)
    }
    val dx = p3.x - p0.x
    val dy = p3.y - p0.y
    val (p1, p2) = if (abs(dx) >= abs(dy)) {
        val c = max(64f, abs(dx) * 0.5f) * if (dx >= 0) 1f else -1f
        Offset(p0.x + c, p0.y) to Offset(p3.x - c, p3.y)
    } else {
        val c = max(64f, abs(dy) * 0.5f) * if (dy >= 0) 1f else -1f
        Offset(p0.x, p0.y + c) to Offset(p3.x, p3.y - c)
    }
    return EdgeCurve(p0, p1, p2, p3)
}

private fun cubicPoint(t: Float, p0: Offset, p1: Offset, p2: Offset, p3: Offset): Offset {
    val u = 1f - t
    return Offset(
        x = u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
        y = u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y
    )
}

private enum class MapDragMode { NODE_DRAG, EDGE_CREATE, PAN }

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
        // Left port — anchor for edge creation (fase 3); visual only here,
        // the gesture is handled by the MapView pointerInput.
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
