package dev.lumenchess.data.persistence

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [
        ParticipantEntity::class,
        ParticipantExternalIdentityEntity::class,
        GameEntity::class,
        GameHeaderEntity::class,
        GameNodeEntity::class,
        GameNodeCommentEntity::class,
        GameNodeNagEntity::class,
        GameNodeAnnotationEntity::class,
        GameSourceEntity::class,
        GameSourceMetadataEntity::class,
        ReviewEntity::class,
        ReviewPlyEntity::class,
        ReviewHeavyAnalysisEntity::class,
        SavedPositionEntity::class,
        RatingEventEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun participantDao(): ParticipantDao
    abstract fun sourceDao(): SourceDao
    abstract fun reviewDao(): ReviewDao
    abstract fun savedPositionDao(): SavedPositionDao
    abstract fun ratingDao(): RatingDao
}

object LumenDatabaseFactory {
    const val DEFAULT_NAME = "lumenchess.db"

    fun open(context: Context, name: String = DEFAULT_NAME): LumenDatabase =
        Room.databaseBuilder(context.applicationContext, LumenDatabase::class.java, name)
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(MIGRATION_1_2)
            .build()

    fun inMemory(context: Context): LumenDatabase =
        Room.inMemoryDatabaseBuilder(context.applicationContext, LumenDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()

    /** Keeps the Room superclass implementation detail inside the persistence module boundary. */
    fun close(database: LumenDatabase) {
        database.close()
    }
}
