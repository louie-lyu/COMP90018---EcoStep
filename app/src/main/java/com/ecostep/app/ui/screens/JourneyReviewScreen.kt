package com.ecostep.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecostep.app.data.model.TransportMode

private val BackgroundColour = Color(0xFFF7F5EE)
private val DarkGreen = Color(0xFF3F7F6B)
private val LightGreen = Color(0xFFDDEBDD)
private val MapGreen = Color(0xFFE3ECDE)
private val SecondaryText = Color(0xFF68716C)
private val BorderColour = Color(0xFFDDDCD3)
private val GoldColour = Color(0xFFA66E00)

@Composable
fun JourneyReviewScreen() {
    var selectedMode by remember {
        mutableStateOf(TransportMode.PUBLIC_TRANSPORT)
    }

    var showConfirmation by remember {
        mutableStateOf(false)
    }

    val ecoPoints = ecoPointsFor(selectedMode)
    val carbonSavedKg = carbonSavedFor(selectedMode)

    Scaffold(
        containerColor = BackgroundColour,
        bottomBar = {
            EcoStepBottomBar()
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                JourneyMapPreview()
            }

            item {
                JourneyReviewContent(
                    selectedMode = selectedMode,
                    ecoPoints = ecoPoints,
                    carbonSavedKg = carbonSavedKg,
                    onModeSelected = {
                        selectedMode = it
                    },
                )
            }

            item {
                Button(
                    onClick = {
                        showConfirmation = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 20.dp,
                        )
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Confirm Journey",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Text(
                            text = String.format(
                                "Save %.2f kg CO₂ · Earn %d EcoPoints",
                                carbonSavedKg,
                                ecoPoints,
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }

    if (showConfirmation) {
        JourneyConfirmedDialog(
            ecoPoints = ecoPoints,
            carbonSavedKg = carbonSavedKg,
            onDismiss = {
                showConfirmation = false
            },
        )
    }
}

@Composable
private fun JourneyMapPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MapGreen),
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val roadColour = Color.White.copy(alpha = 0.9f)
            val routeColour = DarkGreen

            drawRect(
                color = roadColour,
                topLeft = Offset(size.width * 0.22f, 0f),
                size = Size(size.width * 0.035f, size.height),
            )

            drawRect(
                color = roadColour,
                topLeft = Offset(size.width * 0.68f, 0f),
                size = Size(size.width * 0.035f, size.height),
            )

            drawRect(
                color = roadColour,
                topLeft = Offset(0f, size.height * 0.36f),
                size = Size(size.width, size.height * 0.035f),
            )

            drawRect(
                color = roadColour,
                topLeft = Offset(0f, size.height * 0.77f),
                size = Size(size.width, size.height * 0.035f),
            )

            val start = Offset(
                x = size.width * 0.27f,
                y = size.height * 0.72f,
            )

            val end = Offset(
                x = size.width * 0.72f,
                y = size.height * 0.18f,
            )

            drawLine(
                color = routeColour,
                start = start,
                end = end,
                strokeWidth = 7f,
                cap = StrokeCap.Round,
            )

            drawCircle(
                color = routeColour,
                radius = 10f,
                center = start,
            )

            drawCircle(
                color = Color.White,
                radius = 13f,
                center = end,
            )

            drawCircle(
                color = Color(0xFF26332F),
                radius = 13f,
                center = end,
                style = Stroke(width = 7f),
            )
        }

        Surface(
            modifier = Modifier
                .padding(start = 18.dp, top = 18.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 3.dp,
        ) {
            Text(
                text = "Journey completed",
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 10.dp,
                ),
                color = DarkGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun JourneyReviewContent(
    selectedMode: TransportMode,
    ecoPoints: Int,
    carbonSavedKg: Double,
    onModeSelected: (TransportMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = BackgroundColour,
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                ),
            )
            .padding(
                horizontal = 20.dp,
                vertical = 18.dp,
            ),
    ) {
        Text(
            text = "Journey complete",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF202522),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Review your journey before saving it.",
            fontSize = 13.sp,
            color = SecondaryText,
        )

        Spacer(modifier = Modifier.height(18.dp))

        JourneySummaryCard()

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "How did you travel?",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF202522),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "We detected Public transport.",
            fontSize = 12.sp,
            color = SecondaryText,
        )

        Spacer(modifier = Modifier.height(14.dp))

        TransportModeSelector(
            selectedMode = selectedMode,
            onModeSelected = onModeSelected,
        )

        Spacer(modifier = Modifier.height(18.dp))

        EnvironmentalImpactCard(
            ecoPoints = ecoPoints,
            carbonSavedKg = carbonSavedKg,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "The displayed values are mock data for the first prototype.",
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryText,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun JourneySummaryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = "University of Melbourne",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF202522),
            )

            Text(
                text = "Starting point",
                fontSize = 11.sp,
                color = SecondaryText,
            )

            Text(
                text = "↓",
                modifier = Modifier.padding(vertical = 7.dp),
                color = DarkGreen,
                fontSize = 18.sp,
            )

            Text(
                text = "Home · Carlton North",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF202522),
            )

            Text(
                text = "Destination",
                fontSize = 11.sp,
                color = SecondaryText,
            )

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                color = BorderColour,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                JourneyDetail(
                    value = "18 min",
                    label = "Duration",
                )

                JourneyDetail(
                    value = "3.2 km",
                    label = "Distance",
                )

                JourneyDetail(
                    value = "18 Sep",
                    label = "Date",
                )
            }
        }
    }
}

@Composable
private fun JourneyDetail(
    value: String,
    label: String,
) {
    Column {
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF202522),
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            color = SecondaryText,
        )
    }
}

@Composable
private fun TransportModeSelector(
    selectedMode: TransportMode,
    onModeSelected: (TransportMode) -> Unit,
) {
    val modes = listOf(
        TransportMode.WALKING,
        TransportMode.CYCLING,
        TransportMode.PUBLIC_TRANSPORT,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TransportModeButton(
            mode = modes[0],
            selected = selectedMode == modes[0],
            onClick = {
                onModeSelected(modes[0])
            },
            modifier = Modifier.weight(1f),
        )

        TransportModeButton(
            mode = modes[1],
            selected = selectedMode == modes[1],
            onClick = {
                onModeSelected(modes[1])
            },
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    TransportModeButton(
        mode = modes[2],
        selected = selectedMode == modes[2],
        onClick = {
            onModeSelected(modes[2])
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TransportModeButton(
    mode: TransportMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColour = if (selected) {
        LightGreen
    } else {
        BackgroundColour
    }

    val borderColour = if (selected) {
        DarkGreen
    } else {
        BorderColour
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                color = backgroundColour,
                shape = RoundedCornerShape(15.dp),
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColour,
                shape = RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = transportModeName(mode),
            fontSize = 13.sp,
            fontWeight = if (selected) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            },
            color = if (selected) {
                DarkGreen
            } else {
                Color(0xFF202522)
            },
        )
    }
}

@Composable
private fun EnvironmentalImpactCard(
    ecoPoints: Int,
    carbonSavedKg: Double,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = LightGreen,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "+$ecoPoints",
                    color = GoldColour,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "EcoPoints",
                    color = SecondaryText,
                    fontSize = 11.sp,
                )
            }

            Box(
                modifier = Modifier
                    .size(
                        width = 1.dp,
                        height = 42.dp,
                    )
                    .background(Color(0xFFB9CDBF)),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp),
            ) {
                Text(
                    text = String.format("%.2f kg", carbonSavedKg),
                    color = DarkGreen,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "CO₂ saved",
                    color = SecondaryText,
                    fontSize = 11.sp,
                )
            }
        }
    }
}


@Composable
private fun EcoStepBottomBar() {
    NavigationBar(
        containerColor = Color.White,
    ) {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = {
                Text(
                    text = "⌖",
                    fontSize = 20.sp,
                )
            },
            label = {
                Text("Map")
            },
        )

        NavigationBarItem(
            selected = false,
            onClick = {},
            icon = {
                Text(
                    text = "⚑",
                    fontSize = 20.sp,
                )
            },
            label = {
                Text("Missions")
            },
        )

        NavigationBarItem(
            selected = false,
            onClick = {},
            icon = {
                Text(
                    text = "★",
                    fontSize = 18.sp,
                )
            },
            label = {
                Text("Rewards")
            },
        )

        NavigationBarItem(
            selected = false,
            onClick = {},
            icon = {
                Text(
                    text = "▥",
                    fontSize = 19.sp,
                )
            },
            label = {
                Text("Ranking")
            },
        )
    }
}

@Composable
private fun JourneyConfirmedDialog(
    ecoPoints: Int,
    carbonSavedKg: Double,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "Journey saved!",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF202522),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            color = LightGreen,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = DarkGreen,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "You earned",
                    color = SecondaryText,
                    fontSize = 13.sp,
                )

                Text(
                    text = "+$ecoPoints EcoPoints",
                    color = GoldColour,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = String.format(
                        "You saved %.2f kg of CO₂",
                        carbonSavedKg,
                    ),
                    color = DarkGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(
                    text = "Back to Map",
                    color = DarkGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

private fun ecoPointsFor(mode: TransportMode): Int {
    return when (mode) {
        TransportMode.WALKING -> 160
        TransportMode.CYCLING -> 140
        TransportMode.PUBLIC_TRANSPORT -> 120

        // CAR 不顯示為選項，但保留處理以符合共用 enum。
        TransportMode.CAR -> 0
        TransportMode.UNKNOWN -> 0
    }
}

private fun carbonSavedFor(mode: TransportMode): Double {
    return when (mode) {
        TransportMode.WALKING -> 0.64
        TransportMode.CYCLING -> 0.58
        TransportMode.PUBLIC_TRANSPORT -> 0.31

        // CAR 不顯示為選項，但保留處理以符合共用 enum。
        TransportMode.CAR -> 0.0
        TransportMode.UNKNOWN -> 0.0
    }
}

private fun transportModeName(mode: TransportMode): String {
    return when (mode) {
        TransportMode.WALKING -> "Walk"
        TransportMode.CYCLING -> "Cycle"
        TransportMode.PUBLIC_TRANSPORT -> "Public Transport"
        TransportMode.CAR -> "Car"
        TransportMode.UNKNOWN -> "Unknown"
    }
}