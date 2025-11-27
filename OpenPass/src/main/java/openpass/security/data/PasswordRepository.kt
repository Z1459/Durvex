package openpass.security.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class PasswordRepository(
    private val passwordEntryDao: PasswordEntryDao,
    private val cryptoManager: CryptoManager
) {
    fun getAllEntriesSortedByName(): Flow<List<PasswordEntry>> = passwordEntryDao.getAllEntriesSortedByName()
    fun getAllEntriesSortedByDate(): Flow<List<PasswordEntry>> = passwordEntryDao.getAllEntriesSortedByDate()

    suspend fun findEntryByServiceName(serviceName: String): PasswordEntry? {
        return withContext(Dispatchers.IO) { passwordEntryDao.findByServiceName(serviceName) }
    }


    fun getEntriesForDomain(domain: String): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getAllEntriesSortedByName().map { list -> list.filter { it.serviceName.contains(domain, ignoreCase = true) } }
    }


    // This function will handle encrypting the password before inserting it.
    suspend fun insertEntry(serviceName: String, username: String, plaintextPassword: String, notes: String?) {
        // Make sure database and crypto operations happen off the main thread.
        withContext(Dispatchers.IO) {
            val encryptedPassword = cryptoManager.encrypt(plaintextPassword)
            val entry = PasswordEntry(
                serviceName = serviceName,
                username = username,
                encryptedPassword = encryptedPassword,
                notes = notes,
                createdAt = System.currentTimeMillis()
            )
            passwordEntryDao.insert(entry)
        }
    }

    suspend fun updateEntry(id: Int, serviceName: String, username: String, plaintextPassword: String, notes: String?) {
        withContext(Dispatchers.IO) {
            // We need to fetch the original entry to preserve its pinned status and creation date.
            val originalEntry = passwordEntryDao.getEntryById(id).first() ?: return@withContext
            val encryptedPassword = cryptoManager.encrypt(plaintextPassword)
            val updatedEntry = PasswordEntry(
                id = id,
                serviceName = serviceName,
                username = username,
                encryptedPassword = encryptedPassword,
                notes = notes,
                isPinned = originalEntry.isPinned, // Preserve pinned status
                createdAt = originalEntry.createdAt // Preserve original creation date
            )
            passwordEntryDao.update(updatedEntry)
        }
    }
    suspend fun deleteEntry(id: Int) {
        withContext(Dispatchers.IO) {
            passwordEntryDao.deleteById(id)
        }
    }
    suspend fun togglePin(id: Int) {
        withContext(Dispatchers.IO) {
            val entry = passwordEntryDao.getEntryById(id).first() ?: return@withContext
            passwordEntryDao.setPinnedStatus(id, !entry.isPinned)
        }
    }
    suspend fun getDecryptedPassword(entry: PasswordEntry): String {
        return withContext(Dispatchers.IO) {
            cryptoManager.decrypt(entry.encryptedPassword)
        }
    }
    suspend fun getEntry(id: Int): openpass.security.data.PasswordEntry? {
        return withContext(Dispatchers.IO) {
            passwordEntryDao.getEntryById(id).first()
        }
    }
}