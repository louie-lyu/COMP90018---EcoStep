package com.ecostep.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ecostep.app.core.navigation.Routes

data class BottomNavigationItem(
    val label: String,
    val icon: String,
    val route: String,
)

private val bottomNavigationItems = listOf(
    BottomNavigationItem(
        label = "Map",
        icon = "⌖",
        route = Routes.HOME,
    ),
    BottomNavigationItem(
        label = "Missions",
        icon = "⚑",
        route = Routes.MISSIONS,
    ),
    BottomNavigationItem(
        label = "Rewards",
        icon = "★",
        route = Routes.HISTORY,
    ),
    BottomNavigationItem(
        label = "Profile",
        icon = "●",
        route = Routes.SETTINGS,
    ),
)

@Composable
fun EcoStepBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        bottomNavigationItems.forEach { item ->
            val isSelected = when (item.route) {
                Routes.HOME -> {
                    currentRoute == Routes.HOME ||
                            currentRoute == Routes.JOURNEY_REVIEW
                }

                else -> currentRoute == item.route
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        onNavigate(item.route)
                    }
                },
                icon = {
                    Text(
                        text = item.icon,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor =
                        MaterialTheme.colorScheme.primary,
                    selectedTextColor =
                        MaterialTheme.colorScheme.primary,
                    indicatorColor =
                        MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}