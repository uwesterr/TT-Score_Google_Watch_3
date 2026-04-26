package com.uwe.tabletennisscore

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TableTennisApp()
        }
    }
}

private enum class AppScreen {
    MATCH,
    SETTINGS,
}

private enum class SpeechTarget {
    ME,
    OPPONENT,
}

private data class SettingsDraft(
    val meName: String,
    val opponentName: String,
    val matchFormat: MatchFormat,
    val hapticsEnabled: Boolean,
    val soundsEnabled: Boolean,
    val keepScreenOn: Boolean,
) {
    companion object {
        fun from(settings: AppSettings) = SettingsDraft(
            meName = settings.meName,
            opponentName = settings.opponentName,
            matchFormat = settings.matchFormat,
            hapticsEnabled = settings.hapticsEnabled,
            soundsEnabled = settings.soundsEnabled,
            keepScreenOn = settings.keepScreenOn,
        )
    }

    fun toSettings(): AppSettings = AppSettings.sanitize(
        meName = meName,
        opponentName = opponentName,
        setsToWinMatch = matchFormat.setsToWinMatch,
        hapticsEnabled = hapticsEnabled,
        soundsEnabled = soundsEnabled,
        keepScreenOn = keepScreenOn,
    )
}

private enum class EndEffectType {
    HAPPY_SET,
    HAPPY_MATCH,
    NEUTRAL_SET,
    NEUTRAL_MATCH,
}

private data class EndEffect(
    val type: EndEffectType,
    val token: String,
)

private class EndSoundPlayer(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    suspend fun play(effect: EndEffectType) = withContext(Dispatchers.Default) {
        val toneGenerator = ToneGenerator(selectStreamType(), 100)
        try {
            when (effect) {
                EndEffectType.HAPPY_SET -> playSequence(
                    toneGenerator,
                    listOf(
                        ToneGenerator.TONE_PROP_BEEP to 110,
                        ToneGenerator.TONE_PROP_ACK to 120,
                        ToneGenerator.TONE_PROP_BEEP2 to 150,
                    ),
                )

                EndEffectType.HAPPY_MATCH -> playSequence(
                    toneGenerator,
                    listOf(
                        ToneGenerator.TONE_PROP_BEEP to 120,
                        ToneGenerator.TONE_PROP_ACK to 140,
                        ToneGenerator.TONE_PROP_BEEP2 to 160,
                        ToneGenerator.TONE_PROP_ACK to 200,
                    ),
                )

                EndEffectType.NEUTRAL_SET -> playSequence(
                    toneGenerator,
                    listOf(ToneGenerator.TONE_PROP_NACK to 170),
                )

                EndEffectType.NEUTRAL_MATCH -> playSequence(
                    toneGenerator,
                    listOf(
                        ToneGenerator.TONE_PROP_NACK to 170,
                        ToneGenerator.TONE_PROP_NACK to 210,
                    ),
                )
            }
        } finally {
            toneGenerator.release()
        }
    }

    private fun selectStreamType(): Int {
        val candidates = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
        )
        return candidates.firstOrNull { stream ->
            audioManager.getStreamVolume(stream) > 0
        } ?: AudioManager.STREAM_MUSIC
    }

    private suspend fun playSequence(
        toneGenerator: ToneGenerator,
        sequence: List<Pair<Int, Int>>,
    ) {
        sequence.forEach { (tone, duration) ->
            toneGenerator.startTone(tone, duration)
            delay((duration + 40).toLong())
        }
    }
}

@Composable
fun TableTennisApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val settingsStore = remember(context) { AppSettingsStore(context.applicationContext) }
    val settings by settingsStore.settings.collectAsState(initial = AppSettings())
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val soundPlayer = remember(context) { EndSoundPlayer(context.applicationContext) }

    var state by rememberSaveable(stateSaver = MatchStateSaver) { mutableStateOf(MatchRules.newMatch()) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.MATCH) }
    var settingsDraft by remember { mutableStateOf(SettingsDraft.from(settings)) }
    var activeCue by remember { mutableStateOf<MatchCue?>(null) }
    var settingsHydrated by rememberSaveable { mutableStateOf(false) }
    var consumedEndEffectToken by rememberSaveable { mutableStateOf("") }
    var speechTarget by remember { mutableStateOf<SpeechTarget?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (spokenText.isNotBlank()) {
            settingsDraft = when (speechTarget) {
                SpeechTarget.ME -> settingsDraft.copy(meName = spokenText)
                SpeechTarget.OPPONENT -> settingsDraft.copy(opponentName = spokenText)
                null -> settingsDraft
            }
        }
        speechTarget = null
    }

    LaunchedEffect(settings) {
        settingsDraft = SettingsDraft.from(settings)
    }

    LaunchedEffect(settings.setsToWinMatch) {
        if (!settingsHydrated && state.isPristine()) {
            state = state.copy(setsToWinMatch = settings.setsToWinMatch)
            settingsHydrated = true
        }
    }

    SideEffect {
        if (settings.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(activeCue?.kind, activeCue?.text) {
        if (activeCue != null) {
            delay(1600)
            activeCue = null
        }
    }

    fun performHaptic(type: HapticFeedbackType) {
        if (settings.hapticsEnabled) {
            haptics.performHapticFeedback(type)
        }
    }

    fun newMatch() {
        state = MatchRules.newMatch(settings)
        activeCue = null
        consumedEndEffectToken = ""
        screen = AppScreen.MATCH
    }

    fun launchSpeechInput(target: SpeechTarget) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (target == SpeechTarget.ME) "Speak your name" else "Speak opponent name",
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechTarget = target
        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            speechTarget = null
            Toast.makeText(context, "Speech unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun onPoint(player: Player) {
        val previous = state
        val next = MatchRules.addPoint(previous, player)
        if (next == previous) return

        performHaptic(HapticFeedbackType.LongPress)
        val cue = MatchRules.cueForTransition(previous, next, settings)
        state = next
        activeCue = cue
        cue?.let { performHaptic(it.hapticType()) }
    }

    TableTennisTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (screen) {
                AppScreen.SETTINGS -> SettingsScreen(
                    draft = settingsDraft,
                    onDraftChange = { settingsDraft = it },
                    onSpeechInput = ::launchSpeechInput,
                    onSave = {
                        val sanitized = settingsDraft.toSettings()
                        if (state.isPristine()) {
                            state = state.copy(setsToWinMatch = sanitized.setsToWinMatch)
                        }
                        scope.launch {
                            settingsStore.save(sanitized)
                        }
                        screen = AppScreen.MATCH
                    },
                    onCancel = {
                        settingsDraft = SettingsDraft.from(settings)
                        screen = AppScreen.MATCH
                    },
                )

                AppScreen.MATCH -> when {
                    state.currentSet.firstServer == null -> ServePromptScreen(
                        setNumber = state.currentSetIndex + 1,
                        settings = settings,
                        onChoose = { player -> state = MatchRules.chooseFirstServer(state, player) },
                        onOpenSettings = { screen = AppScreen.SETTINGS },
                    )

                    state.matchWinner != null -> MatchOverScreen(
                        state = state,
                        settings = settings,
                        effect = rememberEndEffect(state = state, isMatch = true),
                        soundsEnabled = settings.soundsEnabled,
                        consumedToken = consumedEndEffectToken,
                        onConsumeEffect = { consumedEndEffectToken = it },
                        onPlayEffect = { effect -> soundPlayer.play(effect) },
                        onNewMatch = ::newMatch,
                    )

                    state.currentSet.winner != null -> SetCompleteScreen(
                        state = state,
                        settings = settings,
                        effect = rememberEndEffect(state = state, isMatch = false),
                        soundsEnabled = settings.soundsEnabled,
                        consumedToken = consumedEndEffectToken,
                        onConsumeEffect = { consumedEndEffectToken = it },
                        onPlayEffect = { effect -> soundPlayer.play(effect) },
                        onNextSet = {
                            state = MatchRules.startNextSet(state)
                            activeCue = null
                            consumedEndEffectToken = ""
                        },
                        onUndo = {
                            state = MatchRules.undoLastPoint(state)
                            activeCue = null
                            consumedEndEffectToken = ""
                        },
                    )

                    else -> ScoreScreen(
                        state = state,
                        settings = settings,
                        activeCue = activeCue,
                        onPoint = ::onPoint,
                        onUndo = {
                            state = MatchRules.undoLastPoint(state)
                            activeCue = null
                        },
                        onNewMatch = ::newMatch,
                        onOpenSettings = { screen = AppScreen.SETTINGS },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServePromptScreen(
    setNumber: Int,
    settings: AppSettings,
    onChoose: (Player) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("servePrompt"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(text = "Set $setNumber")
            GhostButton(
                label = "Set",
                modifier = Modifier.testTag("openSettingsFromPrompt"),
                onClick = onOpenSettings,
            )
        }
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
                label = settings.meName,
                modifier = Modifier
                    .weight(1f)
                    .testTag("serveUwe"),
                color = AppColors.uwe,
                onClick = { onChoose(Player.UWE) },
            )
            ActionButton(
                label = settings.opponentName,
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
    settings: AppSettings,
    activeCue: MatchCue?,
    onPoint: (Player) -> Unit,
    onUndo: () -> Unit,
    onNewMatch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0) { 2 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("scoreScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            modifier = Modifier.fillMaxWidth(),
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = true,
        ) { page ->
            when (page) {
                0 -> ScoreboardPage(
                    state = state,
                    settings = settings,
                    activeCue = activeCue,
                    onPoint = onPoint,
                    onUndo = onUndo,
                    onNewMatch = onNewMatch,
                    onOpenSettings = onOpenSettings,
                )

                else -> HistoryChartScreen(
                    state = state,
                    settings = settings,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        PagerDots(currentPage = pagerState.currentPage)
    }
}

@Composable
private fun ScoreboardPage(
    state: MatchState,
    settings: AppSettings,
    activeCue: MatchCue?,
    onPoint: (Player) -> Unit,
    onUndo: () -> Unit,
    onNewMatch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val server = MatchRules.currentServer(state.currentSet)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        HeaderRow(
            state = state,
            onUndo = onUndo,
            onNewMatch = onNewMatch,
            onOpenSettings = onOpenSettings,
        )
        CueAndServerArea(activeCue = activeCue, server = server, settings = settings)
        ScoreBoard(set = state.currentSet, server = server, settings = settings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PointButton(
                player = Player.UWE,
                points = state.currentSet.uwePoints,
                settings = settings,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pointUwe"),
                onClick = { onPoint(Player.UWE) },
            )
            PointButton(
                player = Player.OPPONENT,
                points = state.currentSet.opponentPoints,
                settings = settings,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pointOpponent"),
                onClick = { onPoint(Player.OPPONENT) },
            )
        }
    }
}

@Composable
private fun HistoryChartScreen(
    state: MatchState,
    settings: AppSettings,
) {
    val timeline = remember(state.undoStack, state.currentSetIndex, state.currentSet) {
        currentSetTimeline(state)
    }
    SetHistoryChartContent(
        setLabel = "Set ${state.currentSetIndex + 1}",
        setScore = state.currentSet,
        timeline = timeline,
        settings = settings,
        screenTag = "historyChartScreen",
        chartTag = "historyChart",
    )
}

@Composable
private fun SetHistoryChartContent(
    setLabel: String,
    setScore: SetScore,
    timeline: List<ChartPoint>,
    settings: AppSettings,
    screenTag: String,
    chartTag: String,
) {
    val maxPoints = remember(timeline) {
        maxOf(
            MatchRules.POINTS_TO_WIN_SET,
            timeline.maxOfOrNull { maxOf(it.uwePoints, it.opponentPoints) + 1 } ?: MatchRules.POINTS_TO_WIN_SET,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(screenTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPill(text = setLabel)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Points",
            color = AppColors.text,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${settings.displayName(Player.UWE)} ${setScore.uwePoints} - ${setScore.opponentPoints} ${settings.displayName(Player.OPPONENT)}",
            color = AppColors.muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(138.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.surface)
                .border(1.dp, AppColors.outline, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .testTag(chartTag),
        ) {
            PointsHistoryChart(
                timeline = timeline,
                maxPoints = maxPoints,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LegendRow(settings = settings)
    }
}

@Composable
private fun PointsHistoryChart(
    timeline: List<ChartPoint>,
    maxPoints: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val leftPad = 12.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 8.dp.toPx()
        val bottomPad = 14.dp.toPx()
        val chartWidth = size.width - leftPad - rightPad
        val chartHeight = size.height - topPad - bottomPad
        val baselineY = topPad + chartHeight
        val axisColor = AppColors.outline.copy(alpha = 0.7f)
        val rallies = (timeline.lastOrNull()?.rallyNumber ?: 0).coerceAtLeast(1)

        repeat(4) { row ->
            val y = topPad + chartHeight * (row / 3f)
            drawLine(
                color = axisColor,
                start = androidx.compose.ui.geometry.Offset(leftPad, y),
                end = androidx.compose.ui.geometry.Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        drawLine(
            color = axisColor,
            start = androidx.compose.ui.geometry.Offset(leftPad, topPad),
            end = androidx.compose.ui.geometry.Offset(leftPad, baselineY),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawLine(
            color = axisColor,
            start = androidx.compose.ui.geometry.Offset(leftPad, baselineY),
            end = androidx.compose.ui.geometry.Offset(size.width - rightPad, baselineY),
            strokeWidth = 1.5.dp.toPx(),
        )

        fun pointOffset(point: ChartPoint, points: Int): androidx.compose.ui.geometry.Offset {
            val x = leftPad + chartWidth * (point.rallyNumber / rallies.toFloat())
            val normalized = points / maxPoints.toFloat()
            val y = baselineY - chartHeight * normalized
            return androidx.compose.ui.geometry.Offset(x, y)
        }

        fun drawSeries(
            seriesColor: Color,
            valueForPoint: (ChartPoint) -> Int,
        ) {
            timeline.zipWithNext().forEach { (startPoint, endPoint) ->
                val start = pointOffset(startPoint, valueForPoint(startPoint))
                val end = pointOffset(endPoint, valueForPoint(endPoint))
                drawLine(
                    color = seriesColor,
                    start = start,
                    end = end,
                    strokeWidth = 3.dp.toPx(),
                )
            }
            timeline.forEach { point ->
                drawCircle(
                    color = seriesColor,
                    radius = 2.6.dp.toPx(),
                    center = pointOffset(point, valueForPoint(point)),
                )
            }
        }

        drawSeries(AppColors.uwe) { it.uwePoints }
        drawSeries(AppColors.opponent) { it.opponentPoints }
    }
}

@Composable
private fun LegendRow(settings: AppSettings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(color = AppColors.uwe, label = settings.displayName(Player.UWE))
        LegendItem(color = AppColors.opponent, label = settings.displayName(Player.OPPONENT))
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 10.dp)
                .height(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            color = AppColors.muted,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PagerDots(currentPage: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) { page ->
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .widthIn(min = if (page == currentPage) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (page == currentPage) AppColors.action else AppColors.outline),
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
    onSpeechInput: (SpeechTarget) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("settingsScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPill(text = "TT Score Pro")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Settings",
            color = AppColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        NameField(
            label = "Me",
            value = draft.meName,
            tag = "settingsMeName",
            onValueChange = { onDraftChange(draft.copy(meName = it)) },
            speechTag = "settingsMeSpeech",
            onSpeechInput = { onSpeechInput(SpeechTarget.ME) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        NameField(
            label = "Opponent",
            value = draft.opponentName,
            tag = "settingsOpponentName",
            onValueChange = { onDraftChange(draft.copy(opponentName = it)) },
            speechTag = "settingsOpponentSpeech",
            onSpeechInput = { onSpeechInput(SpeechTarget.OPPONENT) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsSegmentedRow(
            label = "Match",
            first = MatchFormat.BEST_OF_THREE.label,
            second = MatchFormat.BEST_OF_FIVE.label,
            firstSelected = draft.matchFormat == MatchFormat.BEST_OF_THREE,
            firstTag = "bestOf3",
            secondTag = "bestOf5",
            onFirst = { onDraftChange(draft.copy(matchFormat = MatchFormat.BEST_OF_THREE)) },
            onSecond = { onDraftChange(draft.copy(matchFormat = MatchFormat.BEST_OF_FIVE)) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleRow(
            label = "Haptics",
            enabled = draft.hapticsEnabled,
            tag = "toggleHaptics",
            onToggle = { onDraftChange(draft.copy(hapticsEnabled = !draft.hapticsEnabled)) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleRow(
            label = "Sounds",
            enabled = draft.soundsEnabled,
            tag = "toggleSounds",
            onToggle = { onDraftChange(draft.copy(soundsEnabled = !draft.soundsEnabled)) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleRow(
            label = "Always on",
            enabled = draft.keepScreenOn,
            tag = "toggleKeepScreenOn",
            onToggle = { onDraftChange(draft.copy(keepScreenOn = !draft.keepScreenOn)) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Match format applies on the next new match.",
            color = AppColors.muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        ActionButton(
            label = "Save",
            color = AppColors.action,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("saveSettings"),
            onClick = onSave,
        )
        Spacer(modifier = Modifier.height(8.dp))
        GhostButton(
            label = "Cancel",
            modifier = Modifier.testTag("cancelSettings"),
            onClick = onCancel,
        )
    }
}

@Composable
private fun NameField(
    label: String,
    value: String,
    tag: String,
    onValueChange: (String) -> Unit,
    speechTag: String,
    onSpeechInput: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = AppColors.muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.surface)
                    .border(1.dp, AppColors.outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag(tag),
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = label,
                            color = AppColors.disabled,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                },
            )
            HeaderActionButton(
                label = "Mic",
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .testTag(speechTag),
                onClick = onSpeechInput,
            )
        }
    }
}

@Composable
private fun SettingsSegmentedRow(
    label: String,
    first: String,
    second: String,
    firstSelected: Boolean,
    firstTag: String,
    secondTag: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = AppColors.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentButton(
                label = first,
                selected = firstSelected,
                modifier = Modifier.weight(1f).testTag(firstTag),
                onClick = onFirst,
            )
            SegmentButton(
                label = second,
                selected = !firstSelected,
                modifier = Modifier.weight(1f).testTag(secondTag),
                onClick = onSecond,
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    enabled: Boolean,
    tag: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.surface)
            .border(1.dp, AppColors.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        StatusPill(text = if (enabled) "On" else "Off")
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) AppColors.action else AppColors.surface)
            .border(1.dp, if (selected) AppColors.action else AppColors.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.darkText else AppColors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun SetCompleteScreen(
    state: MatchState,
    settings: AppSettings,
    effect: EndEffect?,
    soundsEnabled: Boolean,
    consumedToken: String,
    onConsumeEffect: (String) -> Unit,
    onPlayEffect: suspend (EndEffectType) -> Unit,
    onNextSet: () -> Unit,
    onUndo: () -> Unit,
) {
    val winner = state.currentSet.winner ?: return
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val timeline = remember(state.undoStack, state.currentSetIndex, state.currentSet) {
        currentSetTimeline(state)
    }
    EndEffectPlayback(
        effect = effect,
        soundsEnabled = soundsEnabled,
        consumedToken = consumedToken,
        onConsumeEffect = onConsumeEffect,
        onPlayEffect = onPlayEffect,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setComplete"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            modifier = Modifier.fillMaxWidth(),
            state = pagerState,
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (winner == Player.UWE) {
                        CelebrationConfetti(
                            effect = effect,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("winnerConfetti"),
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        StatusPill(text = "Set ${state.currentSetIndex + 1}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${settings.displayName(winner)} wins",
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

                else -> SetHistoryChartContent(
                    setLabel = "Set ${state.currentSetIndex + 1}",
                    setScore = state.currentSet,
                    timeline = timeline,
                    settings = settings,
                    screenTag = "completedSetHistoryScreen",
                    chartTag = "completedSetHistoryChart",
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        PagerDots(currentPage = pagerState.currentPage)
    }
}

@Composable
private fun MatchOverScreen(
    state: MatchState,
    settings: AppSettings,
    effect: EndEffect?,
    soundsEnabled: Boolean,
    consumedToken: String,
    onConsumeEffect: (String) -> Unit,
    onPlayEffect: suspend (EndEffectType) -> Unit,
    onNewMatch: () -> Unit,
) {
    val winner = state.matchWinner ?: return
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val timeline = remember(state.undoStack, state.currentSetIndex, state.currentSet) {
        currentSetTimeline(state)
    }
    EndEffectPlayback(
        effect = effect,
        soundsEnabled = soundsEnabled,
        consumedToken = consumedToken,
        onConsumeEffect = onConsumeEffect,
        onPlayEffect = onPlayEffect,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("matchOver"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            modifier = Modifier.fillMaxWidth(),
            state = pagerState,
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (winner == Player.UWE) {
                        CelebrationConfetti(
                            effect = effect,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("winnerConfetti"),
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        StatusPill(text = "Match")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${settings.displayName(winner)} wins",
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

                else -> SetHistoryChartContent(
                    setLabel = "Final set",
                    setScore = state.currentSet,
                    timeline = timeline,
                    settings = settings,
                    screenTag = "finalSetHistoryScreen",
                    chartTag = "finalSetHistoryChart",
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        PagerDots(currentPage = pagerState.currentPage)
    }
}

@Composable
private fun HeaderRow(
    state: MatchState,
    onUndo: () -> Unit,
    onNewMatch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .testTag("headerRow"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        HeaderScorePill(text = "${state.uweSetsWon}-${state.opponentSetsWon}")
        HeaderActionButton(
            label = "Set",
            modifier = Modifier.testTag("openSettings"),
            onClick = onOpenSettings,
        )
        HeaderActionButton(
            label = "Undo",
            enabled = state.undoStack.isNotEmpty(),
            modifier = Modifier
                .widthIn(min = 42.dp)
                .testTag("undoPoint"),
            onClick = onUndo,
        )
        HeaderActionButton(
            label = "New",
            modifier = Modifier
                .widthIn(min = 36.dp)
                .testTag("newMatchDuringPlay"),
            onClick = onNewMatch,
        )
    }
}

@Composable
private fun CueAndServerArea(
    activeCue: MatchCue?,
    server: Player?,
    settings: AppSettings,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .testTag("cueBanner"),
                contentAlignment = Alignment.Center,
        ) {
            activeCue?.let { cue ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(cue.color())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = cue.text,
                        color = AppColors.darkText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        ServerIndicator(server = server, settings = settings)
    }
}

@Composable
private fun ServerIndicator(
    server: Player?,
    settings: AppSettings,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(CircleShape)
            .background(AppColors.surface)
            .semantics { contentDescription = "Serving: ${server?.let(settings::displayName) ?: "Not selected"}" }
            .testTag("serverIndicator"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Serving: ${server?.let(settings::displayName) ?: "-"}",
            color = AppColors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScoreBoard(
    set: SetScore,
    server: Player?,
    settings: AppSettings,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScoreColumn(Player.UWE, set.uwePoints, server == Player.UWE, settings, Modifier.weight(1f))
        ScoreColumn(Player.OPPONENT, set.opponentPoints, server == Player.OPPONENT, settings, Modifier.weight(1f))
    }
}

@Composable
private fun ScoreColumn(
    player: Player,
    points: Int,
    isServing: Boolean,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isServing) AppColors.serve else AppColors.outline
    val name = settings.displayName(player)
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.surface)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .semantics { contentDescription = "$name score $points" }
            .testTag(if (player == Player.UWE) "scoreUwe" else "scoreOpponent")
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = name,
            color = AppColors.muted,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = points.toString(),
            color = AppColors.text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PointButton(
    player: Player,
    points: Int,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (player == Player.UWE) AppColors.uwe else AppColors.opponent
    val name = settings.displayName(player)
    Box(
        modifier = modifier
            .height(60.dp)
            .semantics { contentDescription = "Point for $name" }
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "+ ${shortName(player, settings)}",
                color = AppColors.darkText,
                fontSize = 15.sp,
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
            .height(48.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = AppColors.darkText,
            fontSize = 14.sp,
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
            modifier = Modifier.padding(horizontal = 8.dp),
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
private fun HeaderActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .border(1.dp, AppColors.outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) AppColors.text else AppColors.disabled,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HeaderScorePill(text: String) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(AppColors.surface)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AppColors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TableTennisTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun EndEffectPlayback(
    effect: EndEffect?,
    soundsEnabled: Boolean,
    consumedToken: String,
    onConsumeEffect: (String) -> Unit,
    onPlayEffect: suspend (EndEffectType) -> Unit,
) {
    LaunchedEffect(effect?.token, soundsEnabled, consumedToken) {
        val current = effect ?: return@LaunchedEffect
        if (current.token == consumedToken) return@LaunchedEffect
        if (soundsEnabled) {
            onPlayEffect(current.type)
        }
        onConsumeEffect(current.token)
    }
}

@Composable
private fun CelebrationConfetti(
    effect: EndEffect?,
    modifier: Modifier = Modifier,
) {
    val current = effect ?: return
    val progress = remember(current.token) { Animatable(0f) }
    val particles = remember(current.type) {
        val count = if (current.type == EndEffectType.HAPPY_MATCH) 14 else 9
        List(count) { index ->
            ConfettiParticle(
                angle = (index * (PI / (count / 2f))).toFloat(),
                radius = 0.25f + (index % 4) * 0.08f,
                color = if (index % 2 == 0) AppColors.action else AppColors.uwe,
                size = 4.dp + (index % 3).dp,
            )
        }
    }

    LaunchedEffect(current.token) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (current.type == EndEffectType.HAPPY_MATCH) 1500 else 950,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Canvas(modifier = modifier.testTag("winnerConfettiCanvas")) {
        val maxDistance = size.minDimension * if (current.type == EndEffectType.HAPPY_MATCH) 0.42f else 0.32f
        val centerX = size.width / 2f
        val centerY = size.height * 0.3f
        particles.forEachIndexed { index, particle ->
            val particleProgress = (progress.value - index * 0.03f).coerceIn(0f, 1f)
            if (particleProgress <= 0f) return@forEachIndexed
            val alpha = (1f - particleProgress).coerceIn(0f, 1f)
            val distance = maxDistance * particle.radius * particleProgress
            val x = centerX + cos(particle.angle) * distance
            val y = centerY + sin(particle.angle) * distance + particleProgress * 24f
            drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = particle.size.toPx(),
                center = androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat()),
            )
            drawCircle(
                color = AppColors.text.copy(alpha = alpha * 0.25f),
                radius = particle.size.toPx() + 1.5f,
                center = androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat()),
                style = Stroke(width = 1.5f),
            )
        }
    }
}

private data class ConfettiParticle(
    val angle: Float,
    val radius: Float,
    val color: Color,
    val size: androidx.compose.ui.unit.Dp,
)

private data class ChartPoint(
    val rallyNumber: Int,
    val uwePoints: Int,
    val opponentPoints: Int,
)

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
    val deuce = Color(0xFFF9A8D4)
    val matchPoint = Color(0xFFFCA5A5)
}

@Composable
private fun rememberEndEffect(
    state: MatchState,
    isMatch: Boolean,
): EndEffect? {
    val winner = if (isMatch) state.matchWinner else state.currentSet.winner
    winner ?: return null
    val type = when {
        winner == Player.UWE && isMatch -> EndEffectType.HAPPY_MATCH
        winner == Player.UWE -> EndEffectType.HAPPY_SET
        isMatch -> EndEffectType.NEUTRAL_MATCH
        else -> EndEffectType.NEUTRAL_SET
    }
    val token = buildString {
        append(if (isMatch) "match" else "set")
        append(':')
        append(state.currentSetIndex)
        append(':')
        append(winner.code)
        append(':')
        append(state.uweSetsWon)
        append(':')
        append(state.opponentSetsWon)
    }
    return remember(token) { EndEffect(type = type, token = token) }
}

private fun shortName(player: Player, settings: AppSettings): String {
    val fullName = settings.displayName(player)
    return if (player == Player.OPPONENT && fullName.length > 5) {
        fullName.take(5)
    } else {
        fullName
    }
}

private fun currentSetTimeline(state: MatchState): List<ChartPoint> {
    val currentIndex = state.currentSetIndex
    val priorSnapshots = state.undoStack
        .asSequence()
        .filter { it.currentSetIndex == currentIndex && it.sets.size > currentIndex }
        .map { it.sets[currentIndex] }
        .map { it.uwePoints to it.opponentPoints }
        .distinct()
        .toList()

    val currentPair = state.currentSet.uwePoints to state.currentSet.opponentPoints
    val allPoints = buildList {
        if (priorSnapshots.firstOrNull() != (0 to 0)) {
            add(0 to 0)
        }
        addAll(priorSnapshots)
        if (isEmpty() || last() != currentPair) {
            add(currentPair)
        }
    }

    return allPoints.mapIndexed { index, (uwePoints, opponentPoints) ->
        ChartPoint(
            rallyNumber = index,
            uwePoints = uwePoints,
            opponentPoints = opponentPoints,
        )
    }
}

private fun MatchCue.color(): Color = when (kind) {
    MatchCueKind.SERVE_CHANGE -> AppColors.serve
    MatchCueKind.DEUCE -> AppColors.deuce
    MatchCueKind.SET_POINT -> AppColors.action
    MatchCueKind.MATCH_POINT -> AppColors.matchPoint
    MatchCueKind.CHANGE_ENDS -> AppColors.opponent
}

private fun MatchCue.hapticType(): HapticFeedbackType = when (kind) {
    MatchCueKind.SERVE_CHANGE -> HapticFeedbackType.TextHandleMove
    MatchCueKind.DEUCE -> HapticFeedbackType.LongPress
    MatchCueKind.SET_POINT -> HapticFeedbackType.TextHandleMove
    MatchCueKind.MATCH_POINT -> HapticFeedbackType.LongPress
    MatchCueKind.CHANGE_ENDS -> HapticFeedbackType.LongPress
}

private fun MatchState.isPristine(): Boolean =
    currentSetIndex == 0 &&
        matchWinner == null &&
        undoStack.isEmpty() &&
        sets.size == 1 &&
        currentSet.uwePoints == 0 &&
        currentSet.opponentPoints == 0 &&
        currentSet.firstServer == null

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Preview(device = Devices.WEAR_OS_LARGE_ROUND, showSystemUi = true)
@Composable
private fun ScorePreview() {
    val settings = AppSettings()
    TableTennisTheme {
        ScoreScreen(
            state = MatchRules.chooseFirstServer(MatchRules.newMatch(settings), Player.UWE)
                .let { MatchRules.addPoint(it, Player.UWE) }
                .let { MatchRules.addPoint(it, Player.OPPONENT) },
            settings = settings,
            activeCue = MatchCue(MatchCueKind.SERVE_CHANGE, "Opponent serves"),
            onPoint = {},
            onUndo = {},
            onNewMatch = {},
            onOpenSettings = {},
        )
    }
}
