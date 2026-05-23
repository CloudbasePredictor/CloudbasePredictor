package com.cloudbasepredictor.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `paragliding_launch_sites` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `altitudeMeters` INTEGER,
                `countryCode` TEXT,
                `description` TEXT,
                `flightRules` TEXT,
                `access` TEXT,
                `comments` TEXT,
                `weather` TEXT,
                `lastEdit` TEXT,
                `link` TEXT,
                `orientationsJson` TEXT NOT NULL,
                `activitiesJson` TEXT NOT NULL,
                `landingName` TEXT,
                `landingLatitude` REAL,
                `landingLongitude` REAL,
                `fetchedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_paragliding_launch_sites_latitude_longitude`
            ON `paragliding_launch_sites` (`latitude`, `longitude`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `paragliding_launch_bounds_cache` (
                `boundsKey` TEXT NOT NULL,
                `south` REAL NOT NULL,
                `west` REAL NOT NULL,
                `north` REAL NOT NULL,
                `east` REAL NOT NULL,
                `fetchedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`boundsKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `paragliding_launch_bounds_sites` (
                `boundsKey` TEXT NOT NULL,
                `siteId` TEXT NOT NULL,
                PRIMARY KEY(`boundsKey`, `siteId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_paragliding_launch_bounds_sites_siteId`
            ON `paragliding_launch_bounds_sites` (`siteId`)
            """.trimIndent()
        )
    }
}
