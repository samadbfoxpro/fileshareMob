package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun QrCodeIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFD0BCFF)
) {
    Canvas(modifier = modifier) {
        val sizePx = size.width
        val stroke = sizePx * 0.12f
        val qSize = sizePx * 0.35f

        // Top-Left corner finder square
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(qSize, qSize),
            style = Stroke(width = stroke)
        )
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(stroke * 1.5f, stroke * 1.5f),
            size = androidx.compose.ui.geometry.Size(qSize - stroke * 3f, qSize - stroke * 3f),
            style = Fill
        )

        // Top-Right corner finder square
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(sizePx - qSize, 0f),
            size = androidx.compose.ui.geometry.Size(qSize, qSize),
            style = Stroke(width = stroke)
        )
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(sizePx - qSize + stroke * 1.5f, stroke * 1.5f),
            size = androidx.compose.ui.geometry.Size(qSize - stroke * 3f, qSize - stroke * 3f),
            style = Fill
        )

        // Bottom-Left corner finder square
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, sizePx - qSize),
            size = androidx.compose.ui.geometry.Size(qSize, qSize),
            style = Stroke(width = stroke)
        )
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(stroke * 1.5f, sizePx - qSize + stroke * 1.5f),
            size = androidx.compose.ui.geometry.Size(qSize - stroke * 3f, qSize - stroke * 3f),
            style = Fill
        )

        // Custom QR dots details on Bottom-Right
        val dotSize = sizePx * 0.1f
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(sizePx - qSize, sizePx - qSize),
            size = androidx.compose.ui.geometry.Size(dotSize, dotSize)
        )
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(sizePx - dotSize * 2, sizePx - dotSize * 2),
            size = androidx.compose.ui.geometry.Size(dotSize, dotSize)
        )
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(sizePx - qSize, sizePx - dotSize * 2),
            size = androidx.compose.ui.geometry.Size(dotSize, dotSize)
        )
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(sizePx - dotSize * 2, sizePx - qSize),
            size = androidx.compose.ui.geometry.Size(dotSize, dotSize)
        )
    }
}
