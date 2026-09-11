package dev.lumenchess.data.persistence

import dev.lumenchess.core.chess.Variant

enum class LibraryFilter { ALL, LOCAL, ENGINE_ARENA, CHESS_COM, LICHESS, IMPORTED, BRANCHES, FAVORITES }

data class LibraryQuery(val filter: LibraryFilter = LibraryFilter.ALL, val search: String = "")

/** Continue the same query after this newest-created/ascending-UUID key. */
data class LibraryCursor(val createdAtEpochMillis: Long, val id: PersistentGameId)

data class LibraryPage(val entries: List<LibraryEntry>, val nextCursor: LibraryCursor?)

data class LibraryEntry(
    val id: PersistentGameId,
    val variant: Variant,
    val result: String?,
    val metadata: GamePersistenceMetadata,
    val whiteName: String?,
    val blackName: String?,
    val whiteEngineName: String?,
    val blackEngineName: String?,
    val headers: Map<String, String>,
    val sources: Set<GameSourceType>,
    val latestReviewState: ReviewState?,
    val isFavorite: Boolean,
    val isProtected: Boolean,
)
