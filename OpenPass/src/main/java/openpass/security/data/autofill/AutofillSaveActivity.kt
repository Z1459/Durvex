package openpass.security

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import openpass.security.data.AppDatabase
import openpass.security.data.CryptoManager
import openpass.security.data.PasswordRepository

class AutofillSaveActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVICE_NAME = "EXTRA_SERVICE_NAME"
        const val EXTRA_USERNAME = "EXTRA_USERNAME"
        const val EXTRA_PASSWORD = "EXTRA_PASSWORD"
        const val EXTRA_ENTRY_ID = "EXTRA_ENTRY_ID"
    }

    private val repository: PasswordRepository by lazy {
        val db = AppDatabase.getDatabase(this)
        val cryptoManager = CryptoManager()
        PasswordRepository(db.passwordEntryDao(), cryptoManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This activity has no UI and finishes immediately.
        handleSave()
    }

    private fun handleSave() {
        val serviceName = intent.getStringExtra(EXTRA_SERVICE_NAME)
        val username = intent.getStringExtra(EXTRA_USERNAME)
        val password = intent.getStringExtra(EXTRA_PASSWORD)
        val entryId = intent.getIntExtra(EXTRA_ENTRY_ID, -1)

        if (serviceName.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) {
            Log.e("AutofillSaveActivity", "Save intent was missing required data.")
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (entryId != -1) {
                // Update existing entry
                repository.updateEntry(entryId, serviceName, username, password, null)
            } else {
                // Insert new entry
                repository.insertEntry(serviceName, username, password, null)
            }
        }
        setResult(Activity.RESULT_OK)
        finish()
    }
}
