package com.okeanoss.somafm.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Favori kanalları temsil eden Room Entity (Veritabanı Tablosu).
 */
@Entity(tableName = "favorite_channels")
data class FavoriteChannel(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val imageUrl: String
)

/**
 * Veritabanı sorgularını (Ekle, Sil, Listele) yöneten DAO.
 */
@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_channels")
    fun getAllFavorites(): Flow<List<FavoriteChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(channel: FavoriteChannel)

    @Delete
    suspend fun deleteFavorite(channel: FavoriteChannel)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_channels WHERE id = :channelId)")
    fun isFavorite(channelId: String): Flow<Boolean>
}

/**
 * Ana Room Veritabanı sınıfı.
 */
@Database(entities = [FavoriteChannel::class], version = 1)
abstract class SomaDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
}
