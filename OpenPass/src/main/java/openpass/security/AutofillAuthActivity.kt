package openpass.security

import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import openpass.security.data.AppDatabase
import openpass.security.data.CryptoManager
import openpass.security.data.PasswordRepository

class AutofillAuthActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "openpass.security.EXTRA_ENTRY_ID"
        const val EXTRA_USERNAME_ID = "openpass.security.EXTRA_USERNAME_ID"
        const val EXTRA_PASSWORD_ID = "openpass.security.EXTRA_PASSWORD_ID"
    }

    private val repository: PasswordRepository by lazy {
        val db = AppDatabase.getDatabase(this)
        val cryptoManager = CryptoManager()
        PasswordRepository(db.passwordEntryDao(), cryptoManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setVisible(false)

        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric authentication not available", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock OpenPass to Autofill")
            .setSubtitle("Confirm your identity to continue")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    handleAuthenticationSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // User cancelled or an error occurred
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }
    private fun handleAuthenticationSuccess() {
        val entryId = intent.getIntExtra(EXTRA_ENTRY_ID, -1)
        val usernameId = intent.getParcelableExtra<AutofillId>(EXTRA_USERNAME_ID)
        val passwordId = intent.getParcelableExtra<AutofillId>(EXTRA_PASSWORD_ID)


        if (entryId == -1 || usernameId == null || passwordId == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        // Perform database and decryption work on a background thread.
        CoroutineScope(Dispatchers.IO).launch {

            val entry = repository.getEntry(entryId)
            if (entry == null) {

                withContext(Dispatchers.Main) {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
                return@launch
            }

            val decryptedPassword = repository.getDecryptedPassword(entry)
            // Create a simple presentation to be shown as a toast-like confirmation.
            // This is required by the Autofill framework for the fill to work.
            val presentation = RemoteViews(packageName, R.layout.autofill_suggestion_item).apply {
                setTextViewText(R.id.service_name, "Filling from OpenPass")
                setTextViewText(R.id.username, entry.username)
            }
            val dataset = Dataset.Builder(presentation)
                .setValue(usernameId, AutofillValue.forText(entry.username))
            .setValue(passwordId, AutofillValue.forText(decryptedPassword))
            .build()
            val fillResponse = FillResponse.Builder().addDataset(dataset).build()
            // Return the response to the Autofill framework.
            val replyIntent = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, fillResponse)
            setResult(Activity.RESULT_OK, replyIntent)
            finish()
        }
    }
}