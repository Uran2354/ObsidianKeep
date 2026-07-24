package com.example.obsidiankeep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.data.Note
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Модель узла графа: позиция + заметка.
 */
data class GraphNode(
    val noteId: String,
    val title: String,
    val color: Int,
    val x: Float,
    val y: Float
)

/**
 * Модель ребра: исходный узел → целевой узел.
 */
data class GraphEdge(val sourceId: String, val targetId: String)

/**
 * Экран графа связей.
 *
 * Узлы = заметки, у которых есть хотя бы одна [[ссылка]] в контенте или на которые
 * ссылаются другие заметки. Рёбра = извлечённые [[Title]] ссылки.
 *
 * Управление:
 *  - drag одним пальцем — панорамирование
 *  - pinch двумя пальцами — масштаб
 *  - tap по узлу — onNoteClick(noteId)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    allNotes: List<Note>,
    onNoteClick: (String) -> Unit,
    onBack: () -> Unit
) {
    // Строим узлы и рёбра. useMemo-style через remember.
    val (nodes, edges) = remember(allNotes) { buildGraph(allNotes) }

    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.graph_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("←", color = Color.White, fontSize = 28.sp, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(innerPadding)
        ) {
            Text(
                text = stringResource(R.string.graph_nodes, nodes.size, edges.size),
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (nodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.graph_empty),
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                offset += pan
                                zoom = (zoom * gestureZoom).coerceIn(0.3f, 3f)
                            }
                        }
                        .pointerInput(nodes) {
                            detectTapGestures { tapOffset ->
                                // Переводим координаты tap в систему координат графа
                                val graphX = (tapOffset.x - offset.x) / zoom
                                val graphY = (tapOffset.y - offset.y) / zoom
                                // Ищем узел в радиусе 40px
                                val hit = nodes.find { node ->
                                    val dx = node.x - graphX
                                    val dy = node.y - graphY
                                    dx * dx + dy * dy < 40f * 40f
                                }
                                if (hit != null) onNoteClick(hit.noteId)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Рёбра
                        edges.forEach { edge ->
                            val source = nodes.find { it.noteId == edge.sourceId } ?: return@forEach
                            val target = nodes.find { it.noteId == edge.targetId } ?: return@forEach
                            val sx = source.x * zoom + offset.x
                            val sy = source.y * zoom + offset.y
                            val tx = target.x * zoom + offset.x
                            val ty = target.y * zoom + offset.y
                            drawLine(
                                color = Color(0x55BB86FC),
                                start = Offset(sx, sy),
                                end = Offset(tx, ty),
                                strokeWidth = 1.5f
                            )
                        }
                        // Узлы
                        nodes.forEach { node ->
                            val cx = node.x * zoom + offset.x
                            val cy = node.y * zoom + offset.y
                            drawCircle(
                                color = Color(node.color),
                                radius = 16f * zoom,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2f)
                            )
                            drawCircle(
                                color = Color(node.color).copy(alpha = 0.6f),
                                radius = 14f * zoom,
                                center = Offset(cx, cy)
                            )
                            // Подпись
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 12f * zoom
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawText(
                                    node.title.take(20),
                                    cx,
                                    cy + 30f * zoom,
                                    paint
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.graph_zoom_hint),
                    color = Color(0xFF555555),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

/**
 * Строит узлы (заметки в круге радиусом 400px) и рёбра ([[Title]] ссылки).
 */
private fun buildGraph(allNotes: List<Note>): Pair<List<GraphNode>, List<GraphEdge>> {
    val titleToId = allNotes.associate { it.title.trim() to it.id }
    val regex = Regex("\\[\\[(.+?)]]")

    val involvedIds = mutableSetOf<String>()
    val edges = mutableListOf<GraphEdge>()

    allNotes.forEach { note ->
        regex.findAll(note.content).forEach { match ->
            val targetTitle = match.groupValues[1].trim()
            val targetId = titleToId[targetTitle]
            if (targetId != null && targetId != note.id) {
                edges.add(GraphEdge(note.id, targetId))
                involvedIds.add(note.id)
                involvedIds.add(targetId)
            }
        }
    }

    if (involvedIds.isEmpty()) return emptyList<GraphNode>() to emptyList<GraphEdge>()

    // Раскладываем узлы по кругу
    val involvedNotes = allNotes.filter { it.id in involvedIds }
    val random = Random(42) // стабильный сид для воспроизводимости
    val nodes = involvedNotes.mapIndexed { idx, note ->
        val angle = (2.0 * Math.PI * idx / involvedNotes.size).toFloat()
        val radius = 350f + random.nextFloat() * 60f
        GraphNode(
            noteId = note.id,
            title = note.title,
            color = note.color,
            x = (radius * cos(angle)),
            y = (radius * sin(angle))
        )
    }
    return nodes to edges
}
