package com.uwe.tabletennisscore

import androidx.compose.runtime.saveable.Saver

val MatchStateSaver: Saver<MatchState, String> = Saver(
    save = { state -> state.encode() },
    restore = { encoded -> encoded.decodeMatchState() },
)

private fun MatchState.encode(): String {
    val current = encodeSnapshot(snapshot())
    val undo = undoStack.joinToString(";") { encodeSnapshot(it) }
    return "$current|$undo"
}

private fun String.decodeMatchState(): MatchState {
    val parts = split("|", limit = 2)
    val snapshot = decodeSnapshot(parts.firstOrNull().orEmpty()) ?: return MatchState()
    val undoStack = parts.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.split(";")
        ?.mapNotNull { decodeSnapshot(it) }
        .orEmpty()

    return MatchState(
        sets = snapshot.sets.ifEmpty { listOf(SetScore()) },
        currentSetIndex = snapshot.currentSetIndex.coerceIn(snapshot.sets.indices),
        matchWinner = snapshot.matchWinner,
        undoStack = undoStack,
    )
}

private fun encodeSnapshot(snapshot: MatchSnapshot): String {
    val sets = snapshot.sets.joinToString(",") { set ->
        listOf(set.uwePoints, set.opponentPoints, set.firstServer?.code ?: "N").joinToString(":")
    }
    val winner = snapshot.matchWinner?.code ?: "N"
    return listOf(sets, snapshot.currentSetIndex, winner).joinToString("#")
}

private fun decodeSnapshot(value: String): MatchSnapshot? {
    val parts = value.split("#")
    if (parts.size != 3) return null

    val sets = parts[0].split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { encodedSet ->
            val setParts = encodedSet.split(":")
            if (setParts.size != 3) return@mapNotNull null
            SetScore(
                uwePoints = setParts[0].toIntOrNull() ?: return@mapNotNull null,
                opponentPoints = setParts[1].toIntOrNull() ?: return@mapNotNull null,
                firstServer = Player.fromCode(setParts[2]),
            )
        }
        .ifEmpty { listOf(SetScore()) }

    val setIndex = parts[1].toIntOrNull() ?: return null
    return MatchSnapshot(
        sets = sets,
        currentSetIndex = setIndex.coerceIn(sets.indices),
        matchWinner = Player.fromCode(parts[2]),
    )
}
