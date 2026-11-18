package com.flux.reader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.flux.reader.data.local.dao.FluxDao
import com.flux.reader.data.local.entity.BookEntity
import com.flux.reader.data.local.entity.BookmarkEntity
import com.flux.reader.data.local.entity.CollectionEntity
import com.flux.reader.data.local.entity.ReadingSessionEntity

@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        ReadingSessionEntity::class,
        CollectionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FluxDatabase : RoomDatabase() {
    abstract fun fluxDao(): FluxDao
}
