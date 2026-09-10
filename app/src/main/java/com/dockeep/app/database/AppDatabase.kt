package com.dockeep.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dockeep.app.utils.Converters

@Database(
    entities = [
        Document::class, DocumentImage::class, Person::class,
        Tag::class, DocumentTag::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun personDao(): PersonDao
    abstract fun tagDao(): TagDao

    companion object {
        // Migration from version 1 to 2: Add Person table
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `people` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
            }
        }
        
        // Migration from version 2 to 3: Add personId column to documents table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `personId` INTEGER")
            }
        }
        
        // Migration from version 3 to 4: Add order column to document_images table
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `document_images` ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        // Migration from version 4 to 5: Add order column to people table
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `people` ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration 5 to 6: a document becomes an ordered list of blocks, so
         * each row now declares whether it is a scan or a note. Existing rows
         * are all scans, which is what the default gives them.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `document_images` ADD COLUMN `blockType` TEXT NOT NULL DEFAULT 'IMAGE'"
                )
                database.execSQL("ALTER TABLE `document_images` ADD COLUMN `text` TEXT")
            }
        }

        /**
         * Migration 6 to 7: text read off a scan is cached on its row so
         * search can look inside documents, and tags arrive as their own
         * table plus a join.
         *
         * ocrText is left null on existing rows; they are read lazily the
         * next time their document is opened.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `document_images` ADD COLUMN `ocrText` TEXT")

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)"
                )

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `document_tags` (" +
                        "`documentId` INTEGER NOT NULL, " +
                        "`tagId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`documentId`, `tagId`), " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_document_tags_documentId` " +
                        "ON `document_tags` (`documentId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_document_tags_tagId` " +
                        "ON `document_tags` (`tagId`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dockeep_database"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}