package com.ecostep.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Shared stub used by each screen below until Yu-Han (UI module owner) builds the real one.
 * Keeping one shared implementation means every stub looks/behaves identically in the
 * meantime, rather than six slightly different placeholders.
 */
@Composable
internal fun PlaceholderScreen(title: String) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("$title — TODO: Yu-Han")
        }
    }
}
