package openpass.security.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "password_entries")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val serviceName: String,
    val username: String,
    // This field will hold the encrypted password string. Encryption is next.
    val encryptedPassword: String,
    val notes: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)