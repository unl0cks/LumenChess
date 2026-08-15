package dev.lumenchess.data.persistence

import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

val MIGRATION_1_2 = Migration(1, 2) { connection ->
    connection.execSQL("ALTER TABLE `games` ADD COLUMN `contentFingerprint` TEXT")
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_games_contentFingerprint` ON `games` (`contentFingerprint`)",
    )

    connection.execSQL(
        "ALTER TABLE `game_sources` ADD COLUMN `sourceAccountScope` TEXT NOT NULL DEFAULT ''",
    )
    connection.execSQL(
        "UPDATE `game_sources` SET `sourceAccountScope` = COALESCE(`sourceAccountId`, '')",
    )
    connection.execSQL("DROP INDEX IF EXISTS `index_game_sources_sourceType_externalGameId`")
    connection.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_game_sources_sourceType_sourceAccountScope_externalGameId` " +
            "ON `game_sources` (`sourceType`, `sourceAccountScope`, `externalGameId`)",
    )

    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `participant_external_identities` (
            `sourceType` TEXT NOT NULL,
            `sourceAccountScope` TEXT NOT NULL,
            `externalParticipantId` TEXT NOT NULL,
            `participantId` TEXT NOT NULL,
            PRIMARY KEY(`sourceType`, `sourceAccountScope`, `externalParticipantId`),
            FOREIGN KEY(`participantId`) REFERENCES `participants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_participant_external_identities_participantId` " +
            "ON `participant_external_identities` (`participantId`)",
    )

    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_review_heavy_analysis_createdAtEpochMillis_id` " +
            "ON `review_heavy_analysis` (`createdAtEpochMillis`, `id`)",
    )
}
