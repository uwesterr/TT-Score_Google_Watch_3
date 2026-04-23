package com.uwe.tabletennisscore

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TableTennisApp()
        }
    }
}

@Composable
fun TableTennisApp() {
    var state by rememberSaveable(stateSaver = MatchStateSaver) { mutableStateOf(MatchState()) }

    TableTennisTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.currentSet.firstServer == null -> ServePromptScreen(
                    setNumber = state.currentSetIndex + 1,
                    onChoose = { player -> state = MatchRules.chooseFirstServer(state, player) },
                )

                state.matchWinner != null -> MatchOverScreen(
                    state = state,
                    onNewMatch = { state = MatchRules.newMatch() },
                )

                state.currentSet.winner != null -> SetCompleteScreen(
                    state = state,
                    onNextSet = { state = MatchRules.startNextSet(state) },
                    onUndo = { state = MatchRules.undoLastPoint(state) },
                )

                else -> ScoreScreen(
                    state = state,
                    onPoint = { player -> state = MatchRules.addPoint(state, player) },
                    onUndo = { state = MatchRules.undoLastPoint(state) },
                    onNewMatch = { state = MatchRules.newMatch() },
                )
            }
        }
    }
}

@Composable
private fun ServePromptScreen(
    setNumber: Int,
    onChoose: (Player) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("servePrompt"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusPill(text = "Set $setNumber")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Serve first?",
            color = AppColors.text,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                label = "Me",
                modifier = Modifier
                    .weight(1f)
                    .testTag("serveUwe"),
                color = AppColors.uwe,
                onClick = { onChoose(Player.UWE) },
            )
            ActionButton(
                label = "Opponent",
                modifier = Modifier
                    .weight(1f)
                    .testTag("serveOpponent"),
                color = AppColors.opponent,
                onClick = { onChoose(Player.OPPONENT) },
            )
        }
    }
}

@Composable
private fun ScoreScreen(
    state: MatchState,
    onPoint: (Player) -> Unit,
    onUndo: () -> Unit,
    onNewMatch: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val server = MatchRules.currentServer(state.currentSet)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("scoreScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        HeaderRow(state = state, onUndo = onUndo, onNewMatch = onNewMatch)
        ServerIndicator(server = server)
        ScoreBoard(set = state.currentSet, server = server)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PointButton(
                player = Player.UWE,
                points = state.currentSet.uwePoints,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pointUwe"),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPoint(Player.UWE)
                },
            )
            PointButton(
                player = Player.OPPONENT,
                points = state.currentSet.opponentPoints,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pointOpponent"),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPoint(Player.OPPONENT)
                },
            )
        }
    }
}

@Composable
private fun SetCompleteScreen(
    state: MatchState,
    onNextSet: () -> Unit,
    onUndo: () -> Unit,
) {
    val winner = state.currentSet.winner ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setComplete"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusPill(text = "Set ${state.currentSetIndex + 1}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${winner.label} wins",
            color = AppColors.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${state.currentSet.uwePoints} - ${state.currentSet.opponentPoints}",
            color = AppColors.muted,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        ActionButton(
            label = "Next set",
            color = AppColors.action,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nextSet"),
            onClick = onNextSet,
        )
        Spacer(modifier = Modifier.height(8.dp))
        GhostButton(
            label = "Undo",
            modifier = Modifier.testTag("undoSetPoint"),
            onClick = onUndo,
        )
    }
}

@Composable
private fun MatchOverScreen(
    state: MatchState,
    onNewMatch: () -> Unit,
) {
    val winner = state.matchWinner ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("matchOver"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusPill(text = "Match")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${winner.label} wins",
            color = AppColors.text,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Sets ${state.uweSetsWon} - ${state.opponentSetsWon}",
            color = AppColors.muted,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))
        ActionButton(
            label = "New match",
            color = AppColors.action,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("newMatch"),
            onClick = onNewMatch,
        )
    }
}

@Composable
private fun HeaderRow(
    state: MatchState,
    onUndo: () -> Unit,
    onNewMatch: () -> Unit,
) {
    Row(
        modifier = Modifier.width(148.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusPill(text = "${state.uweSetsWon}-${state.opponentSetsWon}")
        GhostButton(
            label = "Undo",
            enabled = state.undoStack.isNotEmpty(),
            modifier = Modifier
                .width(50.dp)
                .testTag("undoPoint"),
            onClick = onUndo,
        )
        GhostButton(
            label = "New",
            modifier = Modifier
                .width(44.dp)
                .testTag("newMatchDuringPlay"),
            onClick = onNewMatch,
        )
    }
}

@Composable
private fun ServerIndicator(server: Player?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(CircleShape)
            .background(AppColors.surface)
            .semantics { contentDescription = "Serving: ${server?.label ?: "Not selected"}" }
            .testTag("serverIndicator"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Serving: ${server?.label ?: "-"}",
            color = AppColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScoreBoard(set: SetScore, server: Player?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScoreColumn(Player.UWE, set.uwePoints, server == Player.UWE, Modifier.weight(1f))
        ScoreColumn(Player.OPPONENT, set.opponentPoints, server == Player.OPPONENT, Modifier.weight(1f))
    }
}

@Composable
private fun ScoreColumn(
    player: Player,
    points: Int,
    isServing: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isServing) AppColors.serve else AppColors.outline
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.surface)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .semantics { contentDescription = "${player.label} score $points" }
            .testTag(if (player == Player.UWE) "scoreUwe" else "scoreOpponent")
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = player.label,
            color = AppColors.muted,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = points.toString(),
            color = AppColors.text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PointButton(
    player: Player,
    points: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (player == Player.UWE) AppColors.uwe else AppColors.opponent
    Box(
        modifier = modifier
            .height(62.dp)
            .semantics { contentDescription = "Point for ${player.label}" }
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "+ ${if (player == Player.OPPONENT) "Opp" else player.label}",
                color = AppColors.darkText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = points.toString(),
                color = AppColors.darkText.copy(alpha = 0.78f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = AppColors.darkText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun GhostButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .border(1.dp, AppColors.outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) AppColors.text else AppColors.disabled,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusPill(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(AppColors.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AppColors.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TableTennisTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

private object AppColors {
    val background = Brush.verticalGradient(listOf(Color(0xFF05070A), Color(0xFF101820)))
    val surface = Color(0xFF18212B)
    val outline = Color(0xFF334155)
    val text = Color(0xFFF8FAFC)
    val muted = Color(0xFFCBD5E1)
    val disabled = Color(0xFF64748B)
    val darkText = Color(0xFF071015)
    val uwe = Color(0xFFD9F99D)
    val opponent = Color(0xFF93C5FD)
    val action = Color(0xFFFDE68A)
    val serve = Color(0xFFFB7185)
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
private fun ScorePreview() {
    TableTennisTheme {
        ScoreScreen(
            state = MatchRules.chooseFirstServer(MatchState(), Player.UWE)
                .let { MatchRules.addPoint(it, Player.UWE) }
                .let { MatchRules.addPoint(it, Player.OPPONENT) },
            onPoint = {},
            onUndo = {},
            onNewMatch = {},
        )
    }
}
