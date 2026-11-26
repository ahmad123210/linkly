package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.model.DrawPoint
import com.example.model.DrawStroke
import com.example.model.PointerShape
import com.example.model.PointerState
import com.example.model.ToolType
import kotlin.math.abs
import kotlin.math.min

@Composable
fun WhiteboardCanvas(
    strokes: List<DrawStroke>,
    currentStroke: DrawStroke?,
    pointerState: PointerState,
    backgroundImage: Bitmap?,
    isInteractive: Boolean = true,
    onDrawStart: (Float, Float) -> Unit = { _, _ -> },
    onDrawMove: (Float, Float) -> Unit = { _, _ -> },
    onDrawEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        val gestureModifier = if (isInteractive) {
            Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val normX = (offset.x / size.width).coerceIn(0f, 1f)
                        val normY = (offset.y / size.height).coerceIn(0f, 1f)
                        onDrawStart(normX, normY)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val normX = (change.position.x / size.width).coerceIn(0f, 1f)
                        val normY = (change.position.y / size.height).coerceIn(0f, 1f)
                        onDrawMove(normX, normY)
                    },
                    onDragEnd = {
                        onDrawEnd()
                    },
                    onDragCancel = {
                        onDrawEnd()
                    }
                )
            }
        } else {
            Modifier
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Draw background image if set
            backgroundImage?.let { bmp ->
                try {
                    val imgBitmap = bmp.asImageBitmap()
                    drawImage(
                        image = imgBitmap,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(canvasW.toInt(), canvasH.toInt())
                    )
                } catch (_: Exception) {}
            }

            // 2. Draw all historical strokes
            strokes.forEach { stroke ->
                renderStroke(stroke, canvasW, canvasH)
            }

            // 3. Draw currently active in-progress stroke
            currentStroke?.let { stroke ->
                renderStroke(stroke, canvasW, canvasH)
            }

            // 4. Draw pointer / laser if active
            if (pointerState.isVisible) {
                val ptrX = pointerState.x * canvasW
                val ptrY = pointerState.y * canvasH
                val ptrColor = try {
                    Color(android.graphics.Color.parseColor(pointerState.colorHex))
                } catch (_: Exception) {
                    Color.Red
                }

                when (pointerState.shape) {
                    PointerShape.LASER, PointerShape.DOT -> {
                        // Outer glowing halo
                        drawCircle(
                            color = ptrColor.copy(alpha = 0.35f),
                            radius = 24f,
                            center = Offset(ptrX, ptrY)
                        )
                        // Core dot
                        drawCircle(
                            color = ptrColor,
                            radius = 10f,
                            center = Offset(ptrX, ptrY)
                        )
                        // Hot center
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = Offset(ptrX, ptrY)
                        )
                    }
                    PointerShape.ARROW -> {
                        val path = Path().apply {
                            moveTo(ptrX, ptrY)
                            lineTo(ptrX + 28f, ptrY + 12f)
                            lineTo(ptrX + 16f, ptrY + 18f)
                            lineTo(ptrX + 22f, ptrY + 32f)
                            lineTo(ptrX + 16f, ptrY + 34f)
                            lineTo(ptrX + 10f, ptrY + 20f)
                            lineTo(ptrX, ptrY + 28f)
                            close()
                        }
                        drawPath(path, color = ptrColor)
                        drawPath(path, color = Color.White, style = Stroke(width = 2f))
                    }
                    PointerShape.STAR -> {
                        drawCircle(
                            color = ptrColor.copy(alpha = 0.4f),
                            radius = 28f,
                            center = Offset(ptrX, ptrY)
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(ptrX - 18f, ptrY),
                            end = Offset(ptrX + 18f, ptrY),
                            strokeWidth = 3f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(ptrX, ptrY - 18f),
                            end = Offset(ptrX, ptrY + 18f),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.renderStroke(stroke: DrawStroke, canvasW: Float, canvasH: Float) {
    val strokeColor = if (stroke.toolType == ToolType.ERASER) {
        Color(0xFF0F172A) // eraser matches canvas background
    } else {
        try {
            Color(android.graphics.Color.parseColor(stroke.colorHex))
        } catch (_: Exception) {
            Color.White
        }
    }

    when (stroke.toolType) {
        ToolType.PEN, ToolType.ERASER -> {
            if (stroke.points.size >= 2) {
                val path = Path()
                val first = stroke.points.first()
                path.moveTo(first.x * canvasW, first.y * canvasH)
                for (i in 1 until stroke.points.size) {
                    val p = stroke.points[i]
                    path.lineTo(p.x * canvasW, p.y * canvasH)
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = stroke.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            } else if (stroke.points.size == 1) {
                val pt = stroke.points.first()
                drawCircle(
                    color = strokeColor,
                    radius = stroke.strokeWidth / 2f,
                    center = Offset(pt.x * canvasW, pt.y * canvasH)
                )
            }
        }
        ToolType.RECTANGLE -> {
            val left = min(stroke.startX, stroke.endX) * canvasW
            val top = min(stroke.startY, stroke.endY) * canvasH
            val width = abs(stroke.endX - stroke.startX) * canvasW
            val height = abs(stroke.endY - stroke.startY) * canvasH

            drawRect(
                color = strokeColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(
                    width = stroke.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Miter
                )
            )
        }
        ToolType.CIRCLE -> {
            val left = min(stroke.startX, stroke.endX) * canvasW
            val top = min(stroke.startY, stroke.endY) * canvasH
            val width = abs(stroke.endX - stroke.startX) * canvasW
            val height = abs(stroke.endY - stroke.startY) * canvasH

            drawOval(
                color = strokeColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = stroke.strokeWidth)
            )
        }
        ToolType.POINTER -> {}
    }
}
