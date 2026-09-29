package dev.lumenchess.games.imports

import dev.lumenchess.data.persistence.GameContentFingerprint
import dev.lumenchess.data.persistence.GamePersistenceRepository
import dev.lumenchess.data.persistence.GameSourceDraft
import dev.lumenchess.data.persistence.ParticipantDraft
import dev.lumenchess.data.persistence.ParticipantKind
import dev.lumenchess.data.persistence.PersistGameRequest
import dev.lumenchess.games.ImportResult
import kotlin.coroutines.cancellation.CancellationException

data class ImportCounts(val added: Int = 0, val existing: Int = 0, val failed: Int = 0)

/** The one path imported games take into the library (manual import and account sync alike). */
internal object GameImporter {
    suspend fun import(games: GamePersistenceRepository, candidate: ImportCandidate): ImportResult {
        // Site games are identified by the site's game id; plain PGN by its content, so the same
        // file imported twice (or a site game pasted after an account import) is not duplicated.
        val source = GameSourceDraft(
            type = candidate.sourceType,
            externalGameId = candidate.externalGameId ?: GameContentFingerprint.compute(candidate.tree),
            externalUrl = candidate.externalUrl,
            importedAtEpochMillis = candidate.metadata.importedAtEpochMillis,
        )
        if (games.hasExternalGame(source)) return ImportResult.ALREADY_IN_LIBRARY
        games.persistExternalGame(
            PersistGameRequest(
                tree = candidate.tree,
                metadata = candidate.metadata,
                whiteParticipant = candidate.whiteName?.let { ParticipantDraft(ParticipantKind.EXTERNAL, displayName = it) },
                blackParticipant = candidate.blackName?.let { ParticipantDraft(ParticipantKind.EXTERNAL, displayName = it) },
            ),
            source,
        )
        return ImportResult.ADDED
    }

    suspend fun importAll(games: GamePersistenceRepository, batch: PgnImportBatch): ImportCounts {
        var counts = ImportCounts(failed = batch.unreadable)
        for (candidate in batch.candidates) {
            val result = try {
                import(games, candidate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            counts = when (result) {
                ImportResult.ADDED -> counts.copy(added = counts.added + 1)
                ImportResult.ALREADY_IN_LIBRARY -> counts.copy(existing = counts.existing + 1)
                ImportResult.UNSUPPORTED, null -> counts.copy(failed = counts.failed + 1)
            }
        }
        return counts
    }
}
