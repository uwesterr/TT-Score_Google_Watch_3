package com.uwe.tabletennisscore.phone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompanionPhoneApp()
        }
    }
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
                val item = event.dataItem
                scope.launch {
                    _snapshot.value = item.toSnapshot()
                }
            }
        }
    }
}

@Composable
private fun CompanionPhoneApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { PhoneScoreRepository(context) }
    val snapshot by repository.snapshot.collectAsState()

    DisposableEffect(repository) {
        repository.startListening()
        onDispose {
            repository.stopListening()
        }
    }

    LaunchedEffect(repository) {
        repository.refresh()
    }

    MaterialTheme {
        Surface(color = Color(0xFF05070A)) {
            if (snapshot.hasData) {
                CompanionScoreScreen(snapshot = snapshot)
            } else {
                EmptyCompanionScreen(message = snapshot.emptyMessage)
            }
        }
    }
}

@Composable
private fun EmptyCompanionScreen(message: String) {
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
private fun CompanionScoreScreen(snapshot: CompanionScoreSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF05070A), Color(0xFF101820))))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "TT Score Pro",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )

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
                    ScoreTile(label = snapshot.meName, value = snapshot.mePoints.toString(), accent = Color(0xFFD9F99D))
                    ScoreTile(label = snapshot.opponentName, value = snapshot.opponentPoints.toString(), accent = Color(0xFF93C5FD))
                }

                DetailRow(label = "Current server", value = snapshot.currentServerName.ifBlank { "-" })
                DetailRow(label = "Sets", value = "${snapshot.meSetsWon} - ${snapshot.opponentSetsWon}")
                DetailRow(label = "Current set", value = snapshot.currentSetNumber.toString())
                DetailRow(label = "Status", value = snapshot.matchStatus)
            }
        }
    }
}

@Composable
private fun RowScope.ScoreTile(
    label: String,
    value: String,
    accent: Color,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(Color(0xFF101820), RoundedCornerShape(20.dp))
            .padding(vertical = 18.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFFD1D5DB),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            color = accent,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF9CA3AF),
            fontSize = 16.sp,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
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
