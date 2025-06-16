package com.example.ablindexaminer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A custom layout that arranges its children in a horizontal flow, wrapping to the next line
 * when they exceed the available width.
 * 
 * @param modifier The modifier to be applied to the layout
 * @param horizontalSpacing The spacing between children horizontally
 * @param verticalSpacing The spacing between rows
 * @param content The composable content to be laid out
 */
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        // Don't constrain child views further, measure them with given constraints
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints)
        }

        // Track coordinates of placed children
        var yPosition = 0
        var xPosition = 0
        var rowHeight = 0
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()
        val rowPlaceables = mutableListOf<MutableList<Int>>()
        var currentRow = mutableListOf<Int>()
        val horizontalSpacingPx = horizontalSpacing.roundToPx()

        // First pass: determine rows and their properties
        placeables.forEachIndexed { index, placeable ->
            // If this placeable doesn't fit on this row, create a new row
            if (xPosition + placeable.width > constraints.maxWidth && currentRow.isNotEmpty()) {
                // Add current row info
                rowWidths.add(xPosition)
                rowHeights.add(rowHeight)
                rowPlaceables.add(currentRow)
                
                // Reset for next row
                currentRow = mutableListOf()
                xPosition = 0
                rowHeight = 0
            }
            
            // Add to current row
            currentRow.add(index)
            xPosition += placeable.width + horizontalSpacingPx
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        
        // Add the last row if not empty
        if (currentRow.isNotEmpty()) {
            rowWidths.add(xPosition)
            rowHeights.add(rowHeight)
            rowPlaceables.add(currentRow)
        }

        // Calculate the total height
        val totalHeight = if (rowHeights.isEmpty()) 0 else {
            rowHeights.sum() + (rowHeights.size - 1) * verticalSpacing.roundToPx()
        }

        // Second pass: place the children
        layout(
            width = constraints.maxWidth,
            height = totalHeight
        ) {
            var y = 0
            
            // For each row
            rowPlaceables.forEachIndexed { rowIndex, rowPlaceableIndices ->
                val rowHeight = rowHeights[rowIndex]
                var x = 0
                
                // For each child in the row
                rowPlaceableIndices.forEach { placeableIndex ->
                    val placeable = placeables[placeableIndex]
                    
                    placeable.placeRelative(x, y)
                    x += placeable.width + horizontalSpacingPx
                }
                
                // Move to next row
                y += rowHeight + verticalSpacing.roundToPx()
            }
        }
    }
} 