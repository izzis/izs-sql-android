package id.web.izs.sqlclient.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionProfileDao {
    @Query("SELECT * FROM connection_profiles ORDER BY created_at DESC")
    fun getAllProfiles(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ConnectionProfileEntity?

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    fun getProfileByIdFlow(id: Long): Flow<ConnectionProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ConnectionProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ConnectionProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ConnectionProfileEntity)

    @Query("DELETE FROM connection_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("SELECT COUNT(*) FROM connection_profiles")
    suspend fun getProfileCount(): Int
}
