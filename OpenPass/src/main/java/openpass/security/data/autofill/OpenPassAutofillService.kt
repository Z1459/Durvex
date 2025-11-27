package openpass.security.data.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.util.Log
import android.service.autofill.*
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import openpass.security.AutofillAuthActivity
import openpass.security.R
import openpass.security.data.AppDatabase
import openpass.security.data.CryptoManager
import openpass.security.data.PasswordRepository

class OpenPassAutofillService : AutofillService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Lazy initialization of the repository
    private val repository: PasswordRepository by lazy {
        val db = AppDatabase.getDatabase(this)
        val cryptoManager = CryptoManager()
        PasswordRepository(db.passwordEntryDao(), cryptoManager)
    }

    // Replace the entire onFillRequest function with this stable version for now
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {


        val structure = request.fillContexts.last().structure
        val webDomain = structure.getWindowNodeAt(0).rootViewNode.webDomain
        Log.d("OpenPassAutofill", "onFillRequest triggered. Domain: $webDomain")
        val screenData = AutofillParser.parse(structure, webDomain)

        // If we can't find any username/password fields, we abort.
        // This prevents the crash and lets us focus on improving the parser.
        if (screenData.usernameId == null || screenData.passwordId == null) {
            Log.w("OpenPassAutofill", "Parser could not find username or password fields. Aborting.")
            callback.onSuccess(null)
            return
        }


        serviceScope.launch {
            val entries = if (webDomain != null) {
                repository.getEntriesForDomain(webDomain).first()
            } else {
                    repository.getAllEntriesSortedByName().first()
                }
            val responseBuilder = FillResponse.Builder()

            entries.forEach { entry ->
                val presentation = RemoteViews(packageName, R.layout.autofill_suggestion_item).apply {
                    setTextViewText(R.id.service_name, entry.serviceName)
                    setTextViewText(R.id.username, entry.username)
                }
                // Create an authentication intent for this specific entry.
                // This intent will be triggered when the user selects this suggestion.
                val authIntent = Intent(this@OpenPassAutofillService, AutofillAuthActivity::class.java).apply {
                    putExtra(AutofillAuthActivity.EXTRA_ENTRY_ID, entry.id)
                    putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, screenData.usernameId)
                    putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, screenData.passwordId)
                }
                // Every PendingIntent needs a unique request code if its extras are different.
                // Using the entry ID ensures uniqueness.
                val requestCode = entry.id
                val authIntentSender = PendingIntent.getActivity(
                    this@OpenPassAutofillService,
                    requestCode,
                    authIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                ).intentSender

                val dataset = Dataset.Builder(presentation)
                    .setAuthentication(authIntentSender)
                    .setValue(screenData.usernameId, AutofillValue.forText(entry.username))
                    .build()
                responseBuilder.addDataset(dataset)
            }
            val response = responseBuilder.build()
            callback.onSuccess(response)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    override fun onDisconnected() {
        super.onDisconnected()
        serviceJob.cancel()
    }
}