package com.example.kinetixfsl.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kinetixfsl.modules.model.FslSignData
import com.example.kinetixfsl.modules.model.SignCategory
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme

/**
 * Modules tab — category grid for FSL learning modules.
 *
 * Matches the Figma "Lessons_Category" screen: a search bar on top,
 * followed by a 2-column grid of category cards in a checkerboard
 * light / dark pattern.
 *
 * @param onCategoryClick Called when the user taps a category card.
 *                        Receives the [SignCategory] that was tapped.
 *                        Currently a no-op — will navigate to the
 *                        sign-list screen once we build it.
 */
@Composable
fun ModulesScreen(
    onCategoryClick: (SignCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = remember { FslSignData.categories }
    var searchQuery by remember { mutableStateOf("") }

    val filtered by remember(searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                categories
            } else {
                val q = searchQuery.trim().lowercase()
                categories.filter { cat ->
                    cat.title.lowercase().contains(q) ||
                            cat.signs.any { it.name.lowercase().contains(q) }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        // ── Search bar ──────────────────────────────────────────
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 20.dp),
        )

        // ── Category grid ───────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = filtered,
                key = { _, cat -> cat.id },
            ) { index, category ->
                // Checkerboard: even rows start light, odd rows start dark.
                // Row = index / 2, column = index % 2.
                val row = index / 2
                val col = index % 2
                val isDark = (row + col) % 2 != 0

                CategoryCard(
                    category = category,
                    isDark = isDark,
                    onClick = { onCategoryClick(category) },
                )
            }
        }
    }
}

// ── Search bar ──────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Hint text (visible when query is empty)
        if (query.isEmpty()) {
            Text(
                text = "Search your sign",
                style = MaterialTheme.typography.bodyLarge,
                color = hintColor,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 32.dp), // leave room for icon
        )

        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = hintColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(22.dp),
        )
    }
}

// ── Category card ───────────────────────────────────────────────

@Composable
private fun CategoryCard(
    category: SignCategory,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)

    // Colours that match the Figma checkerboard:
    //   dark cards  → primary (KinetixIndigo / IndigoLight in dark theme)
    //   light cards → primaryContainer (KinetixIndigo10 / Indigo in dark theme)
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isDark) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = modifier
            .aspectRatio(0.85f) // slightly taller than square, matching the Figma
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            color = contentColor,
        )

        // Sign count at the bottom — gives the learner a sense of scope
        Text(
            text = "${category.signCount} signs",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

// ── Previews ────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Modules – Light")
@Composable
private fun ModulesScreenPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        ModulesScreen(onCategoryClick = {})
    }
}

@Preview(showBackground = true, name = "Modules – Dark")
@Composable
private fun ModulesScreenPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        ModulesScreen(onCategoryClick = {})
    }
}