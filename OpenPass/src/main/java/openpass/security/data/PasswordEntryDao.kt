package openpass.security.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PasswordEntry)

    @Update
    suspend fun update(entry: PasswordEntry)

    @Delete
    suspend fun delete(entry: PasswordEntry)

    @Query("DELETE FROM password_entries WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE password_entries SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinnedStatus(id: Int, isPinned: Boolean)

    @Query("SELECT * FROM password_entries WHERE id = :id")
    fun getEntryById(id: Int): Flow<PasswordEntry?>

    @Query("SELECT * FROM password_entries ORDER BY isPinned DESC, serviceName ASC")
    fun getAllEntriesSortedByName(): Flow<List<PasswordEntry>>
    @Query("SELECT * FROM password_entries ORDER BY isPinned DESC, createdAt DESC")
    fun getAllEntriesSortedByDate(): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM password_entries WHERE serviceName LIKE :serviceName LIMIT 1")
    suspend fun findByServiceName(serviceName: String): PasswordEntry?

}