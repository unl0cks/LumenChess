package dev.lumenchess.data

import android.content.Context
import dev.lumenchess.analysis.openings.OpeningBook
import dev.lumenchess.data.persistence.GameIndexRepository
import dev.lumenchess.data.persistence.GameLibraryRepository
import dev.lumenchess.data.persistence.GamePersistenceRepository
import dev.lumenchess.data.persistence.LumenDatabase
import dev.lumenchess.data.persistence.LumenDatabaseFactory
import dev.lumenchess.data.persistence.PersistenceRetention
import dev.lumenchess.data.persistence.ReviewRepository

/**
 * Process-wide handles for features added after the first persistence consumers (Analysis, Review,
 * Explorer, Ratings, Insights, storage). One shared Room instance keeps their invalidation and
 * write transactions consistent with each other.
 */
object AppData {
    @Volatile private var database: LumenDatabase? = null

    fun database(context: Context): LumenDatabase =
        database ?: synchronized(this) {
            database ?: LumenDatabaseFactory.open(context.applicationContext).also { database = it }
        }

    fun games(context: Context) = GamePersistenceRepository(database(context))
    fun library(context: Context) = GameLibraryRepository(database(context))
    fun reviews(context: Context) = ReviewRepository(database(context))
    fun index(context: Context) = GameIndexRepository(database(context))
    fun retention(context: Context) = PersistenceRetention.forDatabase(database(context))

    /** Bundled CC0 opening names, parsed once (about 3,800 lines). */
    val openingBook: OpeningBook by lazy { OpeningBook.bundled() }
}
