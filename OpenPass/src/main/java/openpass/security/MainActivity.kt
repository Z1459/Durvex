package openpass.security

import android.content.ClipData
import androidx.compose.material.icons.outlined.PushPin
import android.content.ClipboardManager
import android.content.Context
import androidx.biometric.BiometricManager
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import openpass.security.data.AppDatabase
import openpass.security.data.CryptoManager
import openpass.security.data.PasswordEntry
import openpass.security.data.PasswordRepository
import openpass.security.ui.PasswordGeneratorScreen
import openpass.security.data.SettingsRepository
import openpass.security.ui.MainViewModel
import openpass.security.ui.MainViewModelFactory
import openpass.security.ui.theme.OpenPassTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : FragmentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val cryptoManager by lazy { CryptoManager() }
    private val repository by lazy { PasswordRepository(database.passwordEntryDao(), cryptoManager) }
    private val settingsRepository by lazy { SettingsRepository(this) }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(repository, cryptoManager, settingsRepository)}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This enables edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val isUnlocked by viewModel.isUnlocked.collectAsState()
            val preventScreenshots by viewModel.preventScreenshots.collectAsState()

            OpenPassTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // Apply the secure flag dynamically based on the setting
                    LaunchedEffect(preventScreenshots) {
                        if (preventScreenshots) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                    }

                    if (isUnlocked) {
                        OpenPassApp(viewModel = viewModel)
                    } else {
                            LockScreen(viewModel = this@MainActivity.viewModel, activity = this@MainActivity)
                        }
                }
            }
        }
    }
}

@Composable
fun OpenPassApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage by viewModel.errorMessage.collectAsState()
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }
    NavHost(navController = navController, startDestination = "password_list") {
        composable("password_list") {
            PasswordListScreen(
                viewModel = viewModel,
                onAddPassword = { navController.navigate("add_edit_password") },
                onSettingsClicked = { navController.navigate("settings") },
                onEntryClicked = { entryId -> navController.navigate("detail/$entryId") },
                onGenerateClicked = { navController.navigate("password_generator") },
                snackbarHostState = snackbarHostState
            )
        }
        composable(
            route = "add_edit_password?entryId={entryId}",
            arguments = listOf(navArgument("entryId") {
                type = NavType.IntType
                defaultValue = -1
            })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getInt("entryId") ?: -1
            AddEditPasswordScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                entryId = entryId
            )
        }
        composable(
            route = "detail/{entryId}",
            arguments = listOf(navArgument("entryId") { type = NavType.IntType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getInt("entryId") ?: -1
            DetailScreen(
                viewModel = viewModel,
                entryId = entryId,
                onNavigateBack = { navController.popBackStack() },
                onEdit = { navController.navigate("add_edit_password?entryId=$entryId") }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("password_generator") {
            PasswordGeneratorScreen(
                onNavigateBack = { navController.popBackStack() },
                onPasswordGenerated = { /* We can handle this later if needed */ }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PasswordListScreen(
    viewModel: MainViewModel,
    onAddPassword: () -> Unit,
    onSettingsClicked: () -> Unit,
    onEntryClicked: (Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    onGenerateClicked: () -> Unit
) {
    val entries by viewModel.allEntries.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(

        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("OpenPass") },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort by Name") },
                                onClick = {
                                    viewModel.setSortOrder("name")
                                    showSortMenu = false
                                          },
                                leadingIcon = { if (sortOrder == "name") Icon(Icons.Default.Check, contentDescription = "Selected") }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Date Created") },
                                onClick = {
                                    viewModel.setSortOrder("date")
                                    showSortMenu = false
                                          },
                                leadingIcon = { if (sortOrder == "date") Icon(Icons.Default.Check, contentDescription = "Selected") }
                            )
                        }
                    }
                    IconButton(onClick = onGenerateClicked) {
                        Icon(Icons.Default.Password, contentDescription = "Password Generator")
                    }
                    IconButton(onClick = onSettingsClicked) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                          },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPassword,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Password")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (entries.isEmpty()) {
                Text("No passwords saved.", modifier = Modifier.padding(16.dp))
            } else {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        PasswordListItem(
                            entry = entry,
                            onClick = { onEntryClicked(entry.id) },
                            onPinClick = { viewModel.togglePin(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordListItem(entry: PasswordEntry, onClick: () -> Unit, onPinClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.serviceName, style = MaterialTheme.typography.titleMedium)
                Text(text = entry.username, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pin Entry",
                    tint = if (entry.isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: MainViewModel,
    entryId: Int,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit
) {
    LaunchedEffect(key1 = entryId) {
        viewModel.selectEntry(entryId)
    }

    val entry by viewModel.selectedEntry.collectAsState()
    val isPasswordVisible by viewModel.isDetailPasswordVisible.collectAsState()
    val decryptedPassword by viewModel.decryptedPassword.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                viewModel.deleteSelectedEntry()
                showDeleteDialog = false
                onNavigateBack() },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text(entry?.serviceName ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { paddingValues ->
        entry?.let {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailRow(
                    label = "Username",
                    value = it.username,
                    onCopy = { context ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Username", it.username)
                        clipboard.setPrimaryClip(clip)
                    }
                )
                DetailRow(
                    label = "Password",
                    value = if (isPasswordVisible) decryptedPassword ?: "..." else "••••••••••",
                    isSecret = true,
                    isSecretVisible = isPasswordVisible,
                    onToggleVisibility = { viewModel.toggleDetailPasswordVisibility() },
                    onCopy = { context ->
                        decryptedPassword?.let { pass ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Password", pass)
                            clipboard.setPrimaryClip(clip)
                        }
                    }
                )
                // Display notes if they exist
                it.notes?.let { notes ->
                    if (notes.isNotBlank()) {
                        DetailRow(label = "Notes", value = notes)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    isSecret: Boolean = false,
    isSecretVisible: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    onCopy: ((Context) -> Unit)? = null
) {
    val context = LocalContext.current
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (isSecret && onToggleVisibility != null) {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle visibility"
                    )

                }
            }
            onCopy?.let { copyAction ->
                               IconButton(onClick = { copyAction(context) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label")
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion") },
        text = { Text("Are you sure you want to delete this password entry? This action cannot be undone.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            } },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsClickableItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupPasswordDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Enter Backup Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            } },
        confirmButton = {
            Button(onClick = {
                if (password.length < 4) error = "Password must be at least 4 characters."
                else onConfirm(password)
            }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isAppLockEnabled by viewModel.isAppLockEnabled.collectAsState()
    val preventScreenshots by viewModel.preventScreenshots.collectAsState()
    val isBiometricsEnabled by viewModel.isBiometricsEnabled.collectAsState()
    var showSetPasswordDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val canUseBiometrics = remember {
        val biometricManager = BiometricManager.from(context)
        // Check if the user can authenticate with biometrics or their device screen lock
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var exportPasswordHolder by remember { mutableStateOf<String?>(null) }
    var importFileUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                exportPasswordHolder?.let { password ->
                    viewModel.exportBackup(it, context.contentResolver, password)
                }
            }
            exportPasswordHolder = null
        }
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                importFileUri = it
                showImportPasswordDialog = true
            }
        }
    )
    if (showExportPasswordDialog) {
        BackupPasswordDialog(
            title = "Create Export Password",
            onDismiss = { showExportPasswordDialog = false },
            onConfirm = { password ->
                exportPasswordHolder = password
                showExportPasswordDialog = false
                exportLauncher.launch("openpass_encrypted_backup.json")
            }
        )
    }
    if (showImportPasswordDialog) {
        BackupPasswordDialog(
            title = "Enter Backup Password",
            onDismiss = {
                showImportPasswordDialog = false
                importFileUri = null },
            onConfirm = { password ->
                importFileUri?.let { uri ->
                    viewModel.importBackup(uri, context.contentResolver, password)
                }
                showImportPasswordDialog = false
                importFileUri = null
            }
        )
    }

    if (showSetPasswordDialog) {
        SetPasswordDialog(
            onConfirm = { password ->
                viewModel.setAppLockPassword(password)
                viewModel.setAppLock(true)
                showSetPasswordDialog = false },
            onDismiss = { showSetPasswordDialog = false }
        )
    }
    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader(title = "Display")
            SettingsSwitchItem(
                title = "Dark Mode",
                checked = isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode() }
            )
            SectionHeader(title = "App Security")
            SettingsSwitchItem(
                title = "Enable App Lock",
                checked = isAppLockEnabled,
                onCheckedChange = { isEnabled ->
                    if (isEnabled) {
                        showSetPasswordDialog = true
                    } else {
                            viewModel.setAppLock(false)
                        }
                }
            )
            SettingsSwitchItem(
                title = "Unlock with Biometrics",
                checked = isBiometricsEnabled,
                onCheckedChange = { viewModel.setBiometrics(it) },
                enabled = isAppLockEnabled && canUseBiometrics
            )
            SettingsSwitchItem(
                title = "Prevent Screenshots",
                checked = preventScreenshots,
                onCheckedChange = { viewModel.setPreventScreenshots(it) }
            )
            SectionHeader(title = "Backup")
            SettingsClickableItem(
                title = "Export Backup",
                icon = Icons.Default.Upload,
                onClick = { showExportPasswordDialog = true }
            )
            SettingsClickableItem(
                title = "Import Backup",
                icon = Icons.Default.Download,
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )
        }
    }
}

@Composable
fun LockScreen(viewModel: MainViewModel, activity: FragmentActivity) {
    var password by remember { mutableStateOf("") }
    val isBiometricsEnabled by viewModel.isBiometricsEnabled.collectAsState()
    val context = LocalContext.current
    val biometricPrompt = BiometricPrompt(
        activity,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.onUnlockSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Don't show toast for user cancellation
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NO_BIOMETRICS
                    ) {
                    Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric Unlock")
        .setSubtitle("Log in using your screen lock or biometrics")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enter Password", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = {
                viewModel.verifyPassword(password) { success ->
                    if (!success) {
                        Toast.makeText(context, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text("Unlock")
            }
            if (isBiometricsEnabled) {
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = { biometricPrompt.authenticate(promptInfo) }) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Unlock with Biometrics", modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
fun SetPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set App Lock Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Enter Password") },
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
        confirmButton = {
            Button(onClick = {
                if (password.length < 4) error = "Password must be at least 4 characters."
                else if (password != confirmPassword) error = "Passwords do not match."
            else onConfirm(password)
            }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPasswordScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit, entryId: Int) {
    val uiState by viewModel.addEditState.collectAsState()
    val isEditMode = entryId != -1
    LaunchedEffect(key1 = entryId) {
        if (isEditMode) {
            viewModel.loadEntryForEdit(entryId)
        }
    }


    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Password" else "Add New Password") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.savePassword(onSuccess = onNavigateBack)
                    }) {
                        Icon(Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.serviceName,
                onValueChange = { viewModel.onServiceNameChange(it) },
                label = { Text("Service (e.g. Google)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.username,
                onValueChange = { viewModel.onUsernameChange(it) },
                label = { Text("Username or Email") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.onNotesChange(it) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
        }
    }
}