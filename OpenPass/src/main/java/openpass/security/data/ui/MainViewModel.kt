package openpass.security.ui

import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.*

import android.util.Base64
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import openpass.security.data.CryptoManager
import openpass.security.data.PasswordEntry
import openpass.security.data.PasswordRepository
import openpass.security.data.SettingsRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupEntry(val serviceName: String, val username: String, val password: String, val notes: String?)
data class BackupData(val entries: List<BackupEntry>)
data class EncryptedBackupData(val iv: String, val salt: String, val ciphertext: String)

data class AddEditState(
    val id: Int? = null, // Track ID for editing
    val serviceName: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = ""
)

class MainViewModel(
    private val repository: PasswordRepository,
    private val cryptoManager: CryptoManager,
    private val settingsRepository: SettingsRepository

) : ViewModel() {
    // --- State for App Lock ---
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked = _isUnlocked.asStateFlow()
    val isAppLockEnabled = settingsRepository.isAppLockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isBiometricsEnabled = settingsRepository.isBiometricsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    init {
        viewModelScope.launch {
            if (!settingsRepository.isAppLockEnabled.first()) {
                _isUnlocked.value = true
            }
        }
    }
    fun onUnlockSuccess() {
        _isUnlocked.value = true
    }
    fun verifyPassword(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val storedPassword = settingsRepository.appLockPassword.first()
            val success = storedPassword == password
            if (success) onUnlockSuccess()
            onResult(success)
        }
    }


    // --- State for the Password List Screen ---
    val sortOrder: StateFlow<String> = settingsRepository.sortOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, "name")
    val allEntries: StateFlow<List<PasswordEntry>> = sortOrder.flatMapLatest { sort ->
        val sourceFlow = if (sort == "date") {
            repository.getAllEntriesSortedByDate()
        } else {
                repository.getAllEntriesSortedByName()
            }
        sourceFlow.map { list ->
            list.sortedByDescending { it.isPinned }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // --- State for the Detail Screen ---
    private val _selectedEntry = MutableStateFlow<PasswordEntry?>(null)
    val selectedEntry = _selectedEntry.asStateFlow()

    private val _decryptedPassword = MutableStateFlow<String?>(null)
    val decryptedPassword = _decryptedPassword.asStateFlow()

    private val _isDetailPasswordVisible = MutableStateFlow(false)
    val isDetailPasswordVisible = _isDetailPasswordVisible.asStateFlow()

    fun selectEntry(id: Int) {
        viewModelScope.launch {
            // Reset previous state
            _isDetailPasswordVisible.value = false
            _decryptedPassword.value = null
            // Fetch the new entry
            _selectedEntry.value = allEntries.value.find { it.id == id }
        }
    }
    fun loadEntryForEdit(id: Int) {
        viewModelScope.launch {
            val entry = allEntries.value.find { it.id == id }
            entry?.let {
                val plaintextPassword = withContext(Dispatchers.IO) { cryptoManager.decrypt(it.encryptedPassword) }
                _addEditState.value = AddEditState(
                    id = it.id,
                    serviceName = it.serviceName,
                    username = it.username,
                    password = plaintextPassword,
                    notes = it.notes ?: ""
                )
            }
        }
    }

    fun toggleDetailPasswordVisibility() {
        val entry = _selectedEntry.value ?: return
        if (_decryptedPassword.value == null) {
            viewModelScope.launch {
                val plaintext = withContext(Dispatchers.IO) { cryptoManager.decrypt(entry.encryptedPassword) }
                _decryptedPassword.value = plaintext
            }
        }
        _isDetailPasswordVisible.update { !it }
    }

    fun deleteSelectedEntry() {
        viewModelScope.launch {
            _selectedEntry.value?.let {
                repository.deleteEntry(it.id)
            }
        }
    }
    fun togglePin(id: Int) = viewModelScope.launch {
        repository.togglePin(id)
    }

    // --- State for the Settings Screen ---
    val isDarkMode: StateFlow<Boolean> = settingsRepository.isDarkMode
        .stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = false
    )
    // --- State for Prevent Screenshots ---
    val preventScreenshots: StateFlow<Boolean> = settingsRepository.preventScreenshots
        .stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = false
    )
    fun setPreventScreenshots(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setPreventScreenshots(enabled)
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun toggleDarkMode() {
        viewModelScope.launch {
            settingsRepository.setDarkMode(!isDarkMode.value)
        }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAppLockEnabled(enabled)
            // If disabling app lock, also disable biometrics for a clean state
            if (!enabled) {
                settingsRepository.setBiometricsEnabled(false)
            }
        }
    }
    fun setBiometrics(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBiometricsEnabled(enabled)
    }
    fun setAppLockPassword(password: String) = viewModelScope.launch {
        settingsRepository.setAppLockPassword(password)
    }

    fun setSortOrder(order: String) = viewModelScope.launch {
        settingsRepository.setSortOrder(order)
    }

    // --- State and Logic for the Add/Edit Screen ---
    private val _addEditState = MutableStateFlow(AddEditState())
    val addEditState = _addEditState.asStateFlow()

    fun onServiceNameChange(newName: String) {
        _addEditState.update { it.copy(serviceName = newName) }
    }

    fun onUsernameChange(newUsername: String) {
        _addEditState.update { it.copy(username = newUsername) }
    }

    fun onPasswordChange(newPassword: String) {
        _addEditState.update { it.copy(password = newPassword) }
    }
    fun onNotesChange(newNotes: String) {
        _addEditState.update { it.copy(notes = newNotes) }
    }

    fun savePassword(onSuccess: () -> Unit) {
        val currentState = _addEditState.value
        // Prevent saving if service name is blank
        if (currentState.serviceName.isBlank()) {
            _errorMessage.value = "Service name cannot be empty."
            return
        }
        viewModelScope.launch {
            // Check for duplicates only when creating a new entry
            val isDuplicate = allEntries.value.any {
                it.serviceName.equals(currentState.serviceName, ignoreCase = true) && it.id != currentState.id
            }
            if (isDuplicate) {
                _errorMessage.value = "An entry with this service name already exists."
            } else {
                // If there's an ID, update; otherwise, insert.
                if (currentState.id != null) {
                    repository.updateEntry(
                        id = currentState.id,
                        serviceName = currentState.serviceName,
                        username = currentState.username,
                        plaintextPassword = currentState.password,
                        notes = currentState.notes.takeIf { it.isNotBlank() }
                    )
                } else {
                        repository.insertEntry(
                        serviceName = currentState.serviceName,
                        username = currentState.username,
                            plaintextPassword = currentState.password,
                            notes = currentState.notes.takeIf { it.isNotBlank() }
                    )
                    }
                // Reset the fields after saving
                _addEditState.value = AddEditState()
                onSuccess()
                }
        }
    }
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    // --- Backup and Restore Logic ---
    fun exportBackup(uri: Uri, contentResolver: ContentResolver, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = allEntries.value
                val backupEntries = entries.map { entry ->
                    val decryptedPassword = cryptoManager.decrypt(entry.encryptedPassword)
                    BackupEntry(entry.serviceName, entry.username, decryptedPassword, entry.notes)
                }
                val backupData = BackupData(backupEntries)
                val plaintextJson = Gson().toJson(backupData)
                val encryptedData = encrypt(plaintextJson, password)
                val finalJson = Gson().toJson(encryptedData)
                contentResolver.openOutputStream(uri)?.use { it.write(finalJson.toByteArray()) }
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Export successful."
                }
            } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                    _errorMessage.value = "Export failed: ${e.message}"
                }
                }
        }
    }
    fun importBackup(uri: Uri, contentResolver: ContentResolver, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                } ?: throw Exception("Could not read file.")
                val encryptedBackupData: EncryptedBackupData = Gson().fromJson(jsonString, EncryptedBackupData::class.java)
                val decryptedJson = decrypt(encryptedBackupData, password)
                val backupDataType = object : TypeToken<BackupData>() {}.type
                val backupData: BackupData = Gson().fromJson(decryptedJson, backupDataType)
                val currentEntries = allEntries.value
                val currentServiceNames = currentEntries.map { it.serviceName.lowercase() }.toSet()
                var importedCount = 0
                var skippedCount = 0
                backupData.entries.forEach { backupEntry ->
                    if (backupEntry.serviceName.lowercase() in currentServiceNames) {
                        skippedCount++
                    } else {
                        repository.insertEntry(
                            backupEntry.serviceName,
                            backupEntry.username,
                            backupEntry.password,
                            backupEntry.notes
                        )
                        importedCount++
                        }
                }
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Import complete. $importedCount entries added, $skippedCount duplicates skipped."
                }
            } catch (e: javax.crypto.AEADBadTagException) {
                    withContext(Dispatchers.Main) {
                    _errorMessage.value = "Import failed: Invalid password or corrupted file."
                }
            } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                    _errorMessage.value = "Import failed: ${e.message}"
                }
                }
        }
    }
    private fun encrypt(plaintext: String, password: String): EncryptedBackupData {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val keySpec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return EncryptedBackupData(
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }
    private fun decrypt(data: EncryptedBackupData, password: String): String {
        val salt = Base64.decode(data.salt, Base64.NO_WRAP)
        val iv = Base64.decode(data.iv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(data.ciphertext, Base64.NO_WRAP)
        val keySpec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes)
    }
}

class MainViewModelFactory(
    private val repository: PasswordRepository,
    private val cryptoManager: CryptoManager,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, cryptoManager, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}