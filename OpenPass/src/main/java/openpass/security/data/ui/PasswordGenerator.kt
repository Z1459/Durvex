package openpass.security.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.SecureRandom
import kotlin.math.log2

private enum class PasswordStrength(val text: String, val color: Color) {
    VERY_WEAK("Very Weak", Color(0xFFD32F2F)),
    WEAK("Weak", Color(0xFFF57C00)),
    MEDIUM("Medium", Color(0xFFFFC107)),
    STRONG("Strong", Color(0xFF8BC34A)),
    VERY_STRONG("Very Strong", Color(0xFF4CAF50))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(onNavigateBack: () -> Unit, onPasswordGenerated: (String) -> Unit) {
    var length by remember { mutableStateOf(16) }
    var useUppercase by remember { mutableStateOf(true) }
    var useLowercase by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var generatedPassword by remember { mutableStateOf("") }
    var strength by remember { mutableStateOf<PasswordStrength?>(null) }
    val context = LocalContext.current

    fun calculateStrength(password: String, charPoolSize: Int) {
        if (password.isEmpty()) {
            strength = null
            return
        }
        val bitsOfEntropy = password.length * log2(charPoolSize.toFloat())
        strength = when {
            bitsOfEntropy < 35 -> PasswordStrength.VERY_WEAK
            bitsOfEntropy < 60 -> PasswordStrength.WEAK
            bitsOfEntropy < 80 -> PasswordStrength.MEDIUM
            bitsOfEntropy < 100 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }
    }

    fun generatePassword() {
        val charSets = mutableMapOf<String, String>()
        if (useUppercase) charSets["upper"] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if (useLowercase) charSets["lower"] = "abcdefghijklmnopqrstuvwxyz"
        if (useNumbers) charSets["nums"] = "0123456789"
        if (useSymbols) charSets["sym"] = "!@#$%^&*()_+-=[]{}|;:,.<>/?"

        if (charSets.isEmpty()) {
            Toast.makeText(context, "Select at least one character type", Toast.LENGTH_SHORT).show()
            return
        }

        val allChars = charSets.values.joinToString("")
        val random = SecureRandom()
        var password = (1..length)
            .map { allChars[random.nextInt(allChars.length)] }
            .joinToString("")

        generatedPassword = password
        calculateStrength(password, allChars.length)
        onPasswordGenerated(password)
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("Password Generator") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = generatedPassword.ifEmpty { "..." },
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                    strength?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { (it.ordinal + 1) / 5f },
                                color = it.color,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(it.text, color = it.color, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Generated Password", generatedPassword)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Password copied!", Toast.LENGTH_SHORT).show()
                },
                enabled = generatedPassword.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Length:", style = MaterialTheme.typography.titleMedium)
                        Text(length.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = length.toFloat(),
                        onValueChange = { length = it.toInt() },
                        valueRange = 8f..64f,
                        steps = (64 - 8) - 1
                    )
                    Spacer(Modifier.height(16.dp))
                    OptionSwitch("Uppercase (A-Z)", useUppercase) { useUppercase = it }
                    OptionSwitch("Lowercase (a-z)", useLowercase) { useLowercase = it }
                    OptionSwitch("Numbers (0-9)", useNumbers) { useNumbers = it }
                    OptionSwitch("Symbols (!@#...)", useSymbols) { useSymbols = it }
                }
            }

            Button(
                onClick = { generatePassword() },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Generate", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun OptionSwitch(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}