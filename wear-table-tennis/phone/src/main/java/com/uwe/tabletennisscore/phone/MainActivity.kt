package com.uwe.tabletennisscore.phone

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            CompanionPhoneApp()
        }
    }
}

private enum class PhoneScreen {
    SCOREBOARD,
    SETTINGS,
}

private data class CompanionScoreSnapshot(
    val meName: String = "Me",
    val opponentName: String = "Opponent",
    val mePoints: Int = 0,
    val opponentPoints: Int = 0,
    val currentServerName: String = "",
    val meSetsWon: Int = 0,
    val opponentSetsWon: Int = 0,
    val currentSetNumber: Int = 1,
    val matchStatus: String = "Waiting for watch score",
    val emptyMessage: String = "Open the watch app and start a match to mirror the live score here.",
    val updatedAt: Long = 0L,
) {
    val hasData: Boolean
        get() = updatedAt > 0L

    val isSetComplete: Boolean
        get() = matchStatus.contains("won set")

    val isMatchComplete: Boolean
        get() = matchStatus.contains("won the match")

    val winnerName: String?
        get() = matchStatus.substringBefore(" won", "").takeIf { " won" in matchStatus }
}

private enum class PhoneEndEffectType {
    HAPPY_SET,
    HAPPY_MATCH,
    NEUTRAL_SET,
    NEUTRAL_MATCH,
}

private data class PhoneEndEffect(
    val type: PhoneEndEffectType,
    val token: String,
)

private data class PhoneSpeechAnnouncement(
    val text: String,
    val key: String,
)

private data class PhoneConfettiParticle(
    val angle: Float,
    val radius: Float,
    val color: Color,
    val size: Float,
)

private data class PhoneUiSettings(
    val soundsEnabled: Boolean = true,
)

private class PhoneEndSoundPlayer(context: Context) {
    private val loadedEffects = mutableSetOf<PhoneEndEffectType>()
    private val pendingEffect = AtomicReference<PhoneEndEffectType?>(null)
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val soundIds = mapOf(
        PhoneEndEffectType.HAPPY_SET to soundPool.load(context, R.raw.win_set, 1),
        PhoneEndEffectType.HAPPY_MATCH to soundPool.load(context, R.raw.win_match, 1),
        PhoneEndEffectType.NEUTRAL_SET to soundPool.load(context, R.raw.lose_set, 1),
        PhoneEndEffectType.NEUTRAL_MATCH to soundPool.load(context, R.raw.lose_match, 1),
    )

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            val effect = soundIds.entries.firstOrNull { it.value == sampleId }?.key ?: return@setOnLoadCompleteListener
            loadedEffects += effect
            if (pendingEffect.compareAndSet(effect, null)) {
                playLoaded(effect)
            }
        }
    }

    fun play(effect: PhoneEndEffectType) {
        if (effect in loadedEffects) {
            playLoaded(effect)
        } else {
            pendingEffect.set(effect)
        }
    }

    fun release() {
        soundPool.release()
    }

    private fun playLoaded(effect: PhoneEndEffectType) {
        val soundId = soundIds.getValue(effect)
        val playbackRate = if (effect == PhoneEndEffectType.HAPPY_MATCH) 1.03f else 1f
        soundPool.play(soundId, 1f, 1f, 1, 0, playbackRate)
    }
}

private class PhoneSpeechPlayer(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = TextToSpeech(appContext, this)
    private var isReady = false
    private var pendingAnnouncement: PhoneSpeechAnnouncement? = null

    override fun onInit(status: Int) {
        val tts = textToSpeech ?: return
        if (status != TextToSpeech.SUCCESS) return
        val languageResult = tts.setLanguage(Locale.GERMANY)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            return
        }
        tts.setSpeechRate(0.95f)
        isReady = true
        pendingAnnouncement?.let {
            pendingAnnouncement = null
            speakNow(it)
        }
    }

    fun speak(announcement: PhoneSpeechAnnouncement) {
        if (isReady) {
            speakNow(announcement)
        } else {
            pendingAnnouncement = announcement
        }
    }

    fun stop() {
        pendingAnnouncement = null
        textToSpeech?.stop()
    }

    fun release() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun speakNow(announcement: PhoneSpeechAnnouncement) {
        textToSpeech?.speak(
            announcement.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            announcement.key,
        )
    }
}

private class PhoneSettingsStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PHONE_SETTINGS_PREFS, Context.MODE_PRIVATE)

    fun load(): PhoneUiSettings = PhoneUiSettings(
        soundsEnabled = preferences.getBoolean(PREF_SOUNDS_ENABLED, true),
    )

    fun save(settings: PhoneUiSettings) {
        preferences.edit()
            .putBoolean(PREF_SOUNDS_ENABLED, settings.soundsEnabled)
            .apply()
    }
}

private class PhoneScoreRepository(context: Context) : DataClient.OnDataChangedListener {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(CompanionScoreSnapshot())

    val snapshot: StateFlow<CompanionScoreSnapshot> = _snapshot

    fun startListening() {
        dataClient.addListener(this)
    }

    fun stopListening() {
        dataClient.removeListener(this)
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val buffer = Tasks.await(dataClient.dataItems)
            try {
                val item = buffer.firstOrNull { it.uri.path == COMPANION_SCORE_PATH }
                if (item != null) {
                    _snapshot.value = item.toSnapshot()
                }
            } finally {
                buffer.release()
            }
        } catch (_: Throwable) {
            _snapshot.value = _snapshot.value.copy(
                emptyMessage = "Pair the phone emulator with the Wear emulator, then open the watch app to mirror the live score here.",
            )
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == COMPANION_SCORE_PATH) {
                val snapshot = event.dataItem.toSnapshot()
                scope.launch {
                    _snapshot.value = snapshot
                }
            }
        }
    }
}

@Composable
private fun CompanionPhoneApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { PhoneScoreRepository(context) }
    val settingsStore = remember(context) { PhoneSettingsStore(context) }
    val snapshot by repository.snapshot.collectAsState()
    val soundPlayer = remember(context) { PhoneEndSoundPlayer(context.applicationContext) }
    val speechPlayer = remember(context) { PhoneSpeechPlayer(context.applicationContext) }
    var screen by rememberSaveable { mutableStateOf(PhoneScreen.SCOREBOARD) }
    var phoneSettings by remember { mutableStateOf(settingsStore.load()) }
    var consumedEffectToken by rememberSaveable { mutableStateOf("") }
    var consumedSpeechKey by rememberSaveable { mutableStateOf("") }
    var previousSnapshot by remember { mutableStateOf<CompanionScoreSnapshot?>(null) }
    val endEffect = remember(snapshot) { snapshot.toEndEffect() }
    val speechAnnouncement = remember(snapshot, previousSnapshot) {
        snapshot.toSpeechAnnouncement(previousSnapshot)
    }

    DisposableEffect(repository) {
        repository.startListening()
        onDispose {
            repository.stopListening()
        }
    }

    DisposableEffect(soundPlayer) {
        onDispose {
            soundPlayer.release()
        }
    }

    DisposableEffect(speechPlayer) {
        onDispose {
            speechPlayer.release()
        }
    }

    LaunchedEffect(repository) {
        repository.refresh()
    }

    LaunchedEffect(snapshot.updatedAt, snapshot.matchStatus, snapshot.mePoints, snapshot.opponentPoints, snapshot.currentServerName) {
        previousSnapshot = snapshot
    }

    LaunchedEffect(phoneSettings.soundsEnabled) {
        if (!phoneSettings.soundsEnabled) {
            speechPlayer.stop()
        }
    }

    PhoneEndEffectPlayback(
        effect = endEffect,
        consumedToken = consumedEffectToken,
        onConsumeEffect = { consumedEffectToken = it },
        onPlayEffect = { effectType ->
            if (phoneSettings.soundsEnabled) {
                soundPlayer.play(effectType)
            }
        },
    )

    PhoneSpeechPlayback(
        announcement = speechAnnouncement,
        consumedKey = consumedSpeechKey,
        onConsume = { consumedSpeechKey = it },
        onSpeak = { announcement ->
            if (phoneSettings.soundsEnabled) {
                speechPlayer.speak(announcement)
            }
        },
    )

    MaterialTheme {
        Surface(color = Color(0xFF05070A)) {
            when (screen) {
                PhoneScreen.SCOREBOARD -> {
                    if (snapshot.hasData) {
                        CompanionScoreScreen(
                            snapshot = snapshot,
                            endEffect = endEffect,
                            onOpenSettings = { screen = PhoneScreen.SETTINGS },
                        )
                    } else {
                        EmptyCompanionScreen(
                            message = snapshot.emptyMessage,
                            onOpenSettings = { screen = PhoneScreen.SETTINGS },
                        )
                    }
                }
                PhoneScreen.SETTINGS -> {
                    PhoneSettingsScreen(
                        settings = phoneSettings,
                        onSettingsChange = { updated ->
                            phoneSettings = updated
                            settingsStore.save(updated)
                        },
                        onDone = { screen = PhoneScreen.SCOREBOARD },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCompanionScreen(
    message: String,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF05070A), Color(0xFF101820)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "TT Score Pro",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = onOpenSettings) {
                Text("Settings")
            }
            Text(
                text = message,
                color = Color(0xFFD1D5DB),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CompanionScoreScreen(
    snapshot: CompanionScoreSnapshot,
    endEffect: PhoneEndEffect?,
    onOpenSettings: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val serverName = snapshot.currentServerName.ifBlank { "-" }
    val setScore = "${snapshot.meSetsWon} - ${snapshot.opponentSetsWon}"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF05070A), Color(0xFF101820)))),
    ) {
        if (isLandscape) {
            LandscapeCompanionScoreLayout(
                snapshot = snapshot,
                serverName = serverName,
                setScore = setScore,
                onOpenSettings = onOpenSettings,
            )
        } else {
            PortraitCompanionScoreLayout(
                snapshot = snapshot,
                serverName = serverName,
                setScore = setScore,
                onOpenSettings = onOpenSettings,
            )
        }

        if (endEffect?.type == PhoneEndEffectType.HAPPY_SET || endEffect?.type == PhoneEndEffectType.HAPPY_MATCH) {
            PhoneCelebrationBurst(
                effect = endEffect,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PortraitCompanionScoreLayout(
    snapshot: CompanionScoreSnapshot,
    serverName: String,
    setScore: String,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeaderRow(onOpenSettings = onOpenSettings)

        if (snapshot.isSetComplete || snapshot.isMatchComplete) {
            ResultCard(snapshot = snapshot, setScore = setScore)
        }

        ServingCard(serverName = serverName)
        SetScoreCard(setScore = setScore)

        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF18212B))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ScoreTile(
                        label = snapshot.meName,
                        value = snapshot.mePoints.toString(),
                        accent = Color(0xFFD9F99D),
                        isServing = snapshot.meName == serverName,
                    )
                    ScoreTile(
                        label = snapshot.opponentName,
                        value = snapshot.opponentPoints.toString(),
                        accent = Color(0xFF93C5FD),
                        isServing = snapshot.opponentName == serverName,
                    )
                }

                DetailRow(label = "Current set", value = snapshot.currentSetNumber.toString())
                DetailRow(label = "Status", value = snapshot.matchStatus)
            }
        }
    }
}

@Composable
private fun LandscapeCompanionScoreLayout(
    snapshot: CompanionScoreSnapshot,
    serverName: String,
    setScore: String,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Card(
            modifier = Modifier.weight(1.7f),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF18212B))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HeaderRow(onOpenSettings = onOpenSettings, compact = true)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LandscapeScoreTile(
                        label = snapshot.meName,
                        value = snapshot.mePoints.toString(),
                        accent = Color(0xFFD9F99D),
                        isServing = snapshot.meName == serverName,
                    )
                    LandscapeScoreTile(
                        label = snapshot.opponentName,
                        value = snapshot.opponentPoints.toString(),
                        accent = Color(0xFF93C5FD),
                        isServing = snapshot.opponentName == serverName,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (snapshot.isSetComplete || snapshot.isMatchComplete) {
                ResultCard(snapshot = snapshot, setScore = setScore)
            }
            ServingCard(serverName = serverName, compact = true)
            SetScoreCard(setScore = setScore, compact = true)
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18212B))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DetailRow(
                        label = "Current set",
                        value = snapshot.currentSetNumber.toString(),
                        compact = true,
                    )
                    DetailRow(
                        label = "Status",
                        value = snapshot.matchStatus,
                        compact = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    onOpenSettings: () -> Unit,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "TT Score Pro",
            color = Color.White,
            fontSize = if (compact) 24.sp else 34.sp,
            fontWeight = FontWeight.Bold,
        )
        OutlinedButton(onClick = onOpenSettings) {
            Text(
                text = "Settings",
                fontSize = if (compact) 14.sp else 16.sp,
            )
        }
    }
}

@Composable
private fun PhoneSettingsScreen(
    settings: PhoneUiSettings,
    onSettingsChange: (PhoneUiSettings) -> Unit,
    onDone: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF05070A), Color(0xFF101820)))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Phone settings",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Card(shape = RoundedCornerShape(24.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18212B))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Sounds",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Turn all phone-side jingles and speech on or off.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 16.sp,
                        )
                    }
                    Switch(
                        checked = settings.soundsEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(soundsEnabled = enabled))
                        },
                    )
                }
            }
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ServingCard(
    serverName: String,
    compact: Boolean = false,
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10243A))
                .padding(
                    horizontal = if (compact) 18.dp else 20.dp,
                    vertical = if (compact) 14.dp else 16.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Serving",
                color = Color(0xFF93C5FD),
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = serverName,
                color = Color.White,
                fontSize = if (compact) 24.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun SetScoreCard(
    setScore: String,
    compact: Boolean = false,
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C2532))
                .padding(
                    horizontal = if (compact) 18.dp else 20.dp,
                    vertical = if (compact) 14.dp else 16.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Set score",
                color = Color(0xFF9CA3AF),
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = setScore,
                color = Color.White,
                fontSize = if (compact) 26.sp else 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PhoneSpeechPlayback(
    announcement: PhoneSpeechAnnouncement?,
    consumedKey: String,
    onConsume: (String) -> Unit,
    onSpeak: (PhoneSpeechAnnouncement) -> Unit,
) {
    LaunchedEffect(announcement?.key, consumedKey) {
        val current = announcement ?: return@LaunchedEffect
        if (current.key == consumedKey) return@LaunchedEffect
        onSpeak(current)
        onConsume(current.key)
    }
}

@Composable
private fun PhoneEndEffectPlayback(
    effect: PhoneEndEffect?,
    consumedToken: String,
    onConsumeEffect: (String) -> Unit,
    onPlayEffect: (PhoneEndEffectType) -> Unit,
) {
    LaunchedEffect(effect?.token, consumedToken) {
        val current = effect ?: return@LaunchedEffect
        if (current.token == consumedToken) return@LaunchedEffect
        onPlayEffect(current.type)
        onConsumeEffect(current.token)
    }
}

@Composable
private fun PhoneCelebrationBurst(
    effect: PhoneEndEffect,
    modifier: Modifier = Modifier,
) {
    val progress = remember(effect.token) { Animatable(0f) }
    val particles = remember(effect.type) {
        val count = if (effect.type == PhoneEndEffectType.HAPPY_MATCH) 28 else 14
        List(count) { index ->
            PhoneConfettiParticle(
                angle = (index * (PI / (count / 2f))).toFloat(),
                radius = 0.28f + (index % 4) * 0.08f,
                color = when (index % 3) {
                    0 -> Color(0xFFFDE68A)
                    1 -> Color(0xFF86EFAC)
                    else -> Color(0xFF93C5FD)
                },
                size = 8f + (index % 4) * 2f,
            )
        }
    }

    LaunchedEffect(effect.token) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (effect.type == PhoneEndEffectType.HAPPY_MATCH) 1700 else 1100,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Canvas(modifier = modifier) {
        val maxDistance = size.minDimension * if (effect.type == PhoneEndEffectType.HAPPY_MATCH) 0.45f else 0.34f
        val centerX = size.width / 2f
        val centerY = size.height * 0.18f
        val center = Offset(centerX, centerY)
        val pulseAlpha = (1f - progress.value).coerceIn(0f, 1f)
        val pulseRadius = size.minDimension * (0.12f + progress.value * if (effect.type == PhoneEndEffectType.HAPPY_MATCH) 0.22f else 0.14f)

        drawCircle(
            color = Color(0xFFFDE68A).copy(alpha = pulseAlpha * if (effect.type == PhoneEndEffectType.HAPPY_MATCH) 0.22f else 0.14f),
            radius = pulseRadius,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = pulseAlpha * 0.18f),
            radius = pulseRadius * 0.72f,
            center = center,
            style = Stroke(width = if (effect.type == PhoneEndEffectType.HAPPY_MATCH) 6f else 4f),
        )

        if (effect.type == PhoneEndEffectType.HAPPY_MATCH) {
            val secondaryAlpha = (1f - (progress.value - 0.12f).coerceAtLeast(0f)).coerceIn(0f, 1f)
            drawCircle(
                color = Color(0xFF93C5FD).copy(alpha = secondaryAlpha * 0.14f),
                radius = size.minDimension * (0.18f + progress.value * 0.3f),
                center = center,
                style = Stroke(width = 3f),
            )
            drawCircle(
                color = Color(0xFF86EFAC).copy(alpha = secondaryAlpha * 0.12f),
                radius = size.minDimension * (0.09f + progress.value * 0.4f),
                center = center,
                style = Stroke(width = 2f),
            )
        }

        particles.forEachIndexed { index, particle ->
            val particleProgress = (progress.value - index * 0.025f).coerceIn(0f, 1f)
            if (particleProgress <= 0f) return@forEachIndexed
            val alpha = (1f - particleProgress).coerceIn(0f, 1f)
            val distance = maxDistance * particle.radius * particleProgress
            val x = centerX + cos(particle.angle) * distance
            val y = centerY + sin(particle.angle) * distance + particleProgress * 48f
            drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = particle.size,
                center = Offset(x.toFloat(), y.toFloat()),
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.25f),
                radius = particle.size + 2f,
                center = Offset(x.toFloat(), y.toFloat()),
                style = Stroke(width = 2f),
            )

            if (effect.type == PhoneEndEffectType.HAPPY_MATCH && index % 4 == 0) {
                val sparkle = particle.size + 5f
                drawLine(
                    color = Color.White.copy(alpha = alpha * 0.4f),
                    start = Offset(x.toFloat() - sparkle, y.toFloat()),
                    end = Offset(x.toFloat() + sparkle, y.toFloat()),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = Color.White.copy(alpha = alpha * 0.4f),
                    start = Offset(x.toFloat(), y.toFloat() - sparkle),
                    end = Offset(x.toFloat(), y.toFloat() + sparkle),
                    strokeWidth = 2f,
                )
            }
        }
    }
}

private fun CompanionScoreSnapshot.toEndEffect(): PhoneEndEffect? {
    val winner = winnerName ?: return null
    val youWon = winner == meName
    val type = when {
        isMatchComplete && youWon -> PhoneEndEffectType.HAPPY_MATCH
        isMatchComplete -> PhoneEndEffectType.NEUTRAL_MATCH
        isSetComplete && youWon -> PhoneEndEffectType.HAPPY_SET
        isSetComplete -> PhoneEndEffectType.NEUTRAL_SET
        else -> return null
    }
    return PhoneEndEffect(
        type = type,
        token = "$updatedAt|$matchStatus",
    )
}

private fun CompanionScoreSnapshot.toSpeechAnnouncement(
    previous: CompanionScoreSnapshot?,
): PhoneSpeechAnnouncement? {
    if (!hasData || previous == null || !previous.hasData) return null

    val transitionedToMatchComplete = isMatchComplete && !previous.isMatchComplete
    val transitionedToSetComplete = isSetComplete && !isMatchComplete && !previous.isSetComplete
    val pointsChanged = mePoints != previous.mePoints || opponentPoints != previous.opponentPoints
    val serveChanged = currentServerName.isNotBlank() && currentServerName != previous.currentServerName

    return when {
        transitionedToMatchComplete -> {
            val winner = winnerName ?: return null
            PhoneSpeechAnnouncement(
                text = "$winner gewinnt das Match mit ${winnerFirstSetScoreText()} Sätzen.",
                key = "match|$updatedAt|$matchStatus",
            )
        }
        transitionedToSetComplete -> {
            val winner = winnerName ?: return null
            PhoneSpeechAnnouncement(
                text = "$winner gewinnt den Satz mit ${winnerFirstPointScoreText()}. Satzstand ${winnerFirstSetScoreText()}.",
                key = "set|$updatedAt|$matchStatus",
            )
        }
        pointsChanged -> {
            PhoneSpeechAnnouncement(
                text = spokenBallScoreText(serveChanged),
                key = "ball|$updatedAt|$mePoints|$opponentPoints|$currentServerName",
            )
        }
        else -> null
    }
}

private fun CompanionScoreSnapshot.spokenBallScoreText(
    includeServeChange: Boolean,
): String {
    val scoreText = if (currentServerName == opponentName) {
        "$opponentPoints zu $mePoints"
    } else {
        "$mePoints zu $opponentPoints"
    }
    return if (includeServeChange) {
        "$scoreText. Aufschlag $currentServerName."
    } else {
        scoreText
    }
}

private fun CompanionScoreSnapshot.winnerFirstPointScoreText(): String {
    val winner = winnerName ?: return "$mePoints-$opponentPoints"
    return if (winner == meName) {
        "$mePoints zu $opponentPoints"
    } else {
        "$opponentPoints zu $mePoints"
    }
}

private fun CompanionScoreSnapshot.winnerFirstSetScoreText(): String {
    val winner = winnerName ?: return "$meSetsWon zu $opponentSetsWon"
    return if (winner == meName) {
        "$meSetsWon zu $opponentSetsWon"
    } else {
        "$opponentSetsWon zu $meSetsWon"
    }
}

@Composable
private fun ResultCard(
    snapshot: CompanionScoreSnapshot,
    setScore: String,
) {
    val accent = if (snapshot.isMatchComplete) Color(0xFFFDE68A) else Color(0xFF86EFAC)
    val title = if (snapshot.isMatchComplete) "Match finished" else "Set finished"
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1F2937))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = snapshot.matchStatus,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Final points ${snapshot.mePoints} - ${snapshot.opponentPoints}",
                color = Color(0xFFD1D5DB),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Set score $setScore",
                color = Color(0xFF9CA3AF),
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun RowScope.LandscapeScoreTile(
    label: String,
    value: String,
    accent: Color,
    isServing: Boolean,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .background(Color(0xFF101820), RoundedCornerShape(24.dp))
            .border(
                width = if (isServing) 4.dp else 1.dp,
                color = if (isServing) accent else Color(0xFF223041),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = Color(0xFFD1D5DB),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (isServing) {
                Text(
                    text = "SERVING",
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = value,
            color = accent,
            fontSize = 136.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 136.sp,
        )
    }
}

@Composable
private fun RowScope.ScoreTile(
    label: String,
    value: String,
    accent: Color,
    isServing: Boolean,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(Color(0xFF101820), RoundedCornerShape(20.dp))
            .border(
                width = if (isServing) 3.dp else 1.dp,
                color = if (isServing) accent else Color(0xFF223041),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFFD1D5DB),
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
        if (isServing) {
            Text(
                text = "SERVING",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = value,
            color = accent,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF9CA3AF),
            fontSize = if (compact) 15.sp else 16.sp,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = if (compact) 16.sp else 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

private fun com.google.android.gms.wearable.DataItem.toSnapshot(): CompanionScoreSnapshot {
    val dataMap = DataMapItem.fromDataItem(this).dataMap
    return CompanionScoreSnapshot(
        meName = dataMap.getString(CompanionScoreKeys.meName) ?: "Me",
        opponentName = dataMap.getString(CompanionScoreKeys.opponentName) ?: "Opponent",
        mePoints = dataMap.getInt(CompanionScoreKeys.mePoints),
        opponentPoints = dataMap.getInt(CompanionScoreKeys.opponentPoints),
        currentServerName = dataMap.getString(CompanionScoreKeys.currentServerName).orEmpty(),
        meSetsWon = dataMap.getInt(CompanionScoreKeys.meSetsWon),
        opponentSetsWon = dataMap.getInt(CompanionScoreKeys.opponentSetsWon),
        currentSetNumber = dataMap.getInt(CompanionScoreKeys.currentSetNumber).coerceAtLeast(1),
        matchStatus = dataMap.getString(CompanionScoreKeys.matchStatus) ?: "Waiting for watch score",
        updatedAt = dataMap.getLong(CompanionScoreKeys.updatedAt),
    )
}

private const val COMPANION_SCORE_PATH = "/tt_score/current_state"

private object CompanionScoreKeys {
    const val meName = "me_name"
    const val opponentName = "opponent_name"
    const val mePoints = "me_points"
    const val opponentPoints = "opponent_points"
    const val currentServerName = "current_server_name"
    const val meSetsWon = "me_sets_won"
    const val opponentSetsWon = "opponent_sets_won"
    const val currentSetNumber = "current_set_number"
    const val matchStatus = "match_status"
    const val updatedAt = "updated_at"
}

private const val PHONE_SETTINGS_PREFS = "tt_score_phone_settings"
private const val PREF_SOUNDS_ENABLED = "sounds_enabled"
