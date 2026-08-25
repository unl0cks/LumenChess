package dev.lumenchess.board

import dev.lumenchess.settings.AppearanceSettings

data class PersonalPieceMetadata(
    val id: String,
    val sourceDirectory: String,
    val displayName: String,
) {
    val assetDirectory: String = "pieces/$sourceDirectory"
}

object PersonalPieceMetadataCodec {
    private val privateId = Regex("private\\.chesscom\\.[a-z0-9][a-z0-9_-]{0,127}")
    private val sourceDirectory = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")

    fun decode(encoded: String): List<PersonalPieceMetadata> {
        if (encoded.isBlank()) return emptyList()
        val usedIds = mutableSetOf<String>()
        val usedDirectories = mutableSetOf<String>()
        return encoded.split(';').mapNotNull { record ->
            val fields = record.split('|')
            if (fields.size != 3) return@mapNotNull null
            val id = fields[0]
            val directory = fields[1]
            val displayName = fields[2].trim()
            if (!privateId.matches(id) || !sourceDirectory.matches(directory) || displayName.isEmpty()) {
                return@mapNotNull null
            }
            if (!usedIds.add(id) || !usedDirectories.add(directory)) return@mapNotNull null
            PersonalPieceMetadata(id, directory, displayName)
        }
    }
}

object PieceSelectionResolver {
    private val legacyAliases = mapOf(
        "personal-classic" to "private.chesscom.classic",
        "personal-club" to "private.chesscom.club",
        "personal-bases" to "private.chesscom.bases",
        "personal-3d-staunton" to "private.chesscom.3d_staunton",
        "personal-wood" to "private.chesscom.wood",
        "personal-game-room" to "private.chesscom.game_room",
        "personal-marble" to "private.chesscom.marble",
    )

    fun effectiveId(storedId: String, availableIds: Collection<String>): String {
        if (storedId in availableIds) return storedId
        val migratedId = legacyAliases[storedId]
        if (migratedId != null && migratedId in availableIds) return migratedId
        return AppearanceSettings.DEFAULT_PIECE_SET_ID
    }
}
