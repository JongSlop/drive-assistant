package dev.smto.driveassistant.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.smto.driveassistant.data.SettingsRepository.SttMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        val c = cfg ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Model backend (OpenAI-compatible)")

            FieldState(c.baseUrl, vm::setBaseUrl, "Base URL", "https://api.deepseek.com")
            FieldState(c.apiKey, vm::setApiKey, "API key", secret = true)
            FieldState(c.model, vm::setModel, "Model", "deepseek-chat")

            Section("Voice input")
            SttModePicker(current = c.sttMode, onPick = vm::setSttMode)
            if (c.sttMode == SttMode.EXTERNAL_POPUP) {
                RecognizerAppPicker(
                    current = c.recognizerPackage,
                    apps = remember { vm.recognizerApps() },
                    onPick = vm::setRecognizerPackage,
                )
            }

            Section("Assistant")
            LanguagePicker(
                current = c.language,
                options = vm.languages,
                onPick = vm::setLanguage,
            )
            FieldState(c.homeLocation, vm::setHomeLocation, "Home location (weather fallback)", "Berlin")
            FieldState(
                c.systemPrompt, vm::setSystemPrompt, "System prompt", singleLine = false,
            )
            OutlinedButton(onClick = { vm.setSystemPrompt(vm.defaultSystemPrompt) }) {
                Text("Reset system prompt")
            }

            Section("Notifications")
            SwitchRow(
                label = "Read out incoming notifications",
                checked = c.notificationReadout,
                onChange = vm::setNotificationReadout,
            )
            SwitchRow(
                label = "Only while Android Auto is connected",
                checked = c.notificationReadoutOnlyInCar,
                enabled = c.notificationReadout,
                onChange = vm::setNotificationReadoutOnlyInCar,
            )

            Section("Permissions & roles")
            LinkButton("Grant notification access") {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            LinkButton("Set as default assistant app") { openAssistantRole(context) }
            LinkButton("App permissions (mic, location, phone)") {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(
    current: String,
    options: Map<String, String>,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.entries.firstOrNull { it.value == current }?.key ?: current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language (speech, voice, replies)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (name, tag) ->
                DropdownMenuItem(
                    text = { Text("$name  ·  $tag") },
                    onClick = {
                        onPick(tag)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecognizerAppPicker(
    current: String,
    apps: List<dev.smto.driveassistant.voice.RecognizerIntents.App>,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = apps.firstOrNull { it.packageName == current }?.label
        ?: if (current.isBlank()) "Ask every time" else current

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Recognizer app") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Ask every time") },
                onClick = { onPick(""); expanded = false },
            )
            apps.forEach { app ->
                DropdownMenuItem(
                    text = { Text("${app.label}  ·  ${app.packageName}") },
                    onClick = { onPick(app.packageName); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SttModePicker(current: SttMode, onPick: (SttMode) -> Unit) {
    val options = listOf(
        SttMode.SYSTEM to ("System recognizer" to
            "Direct call to the OS speech service. Fast, hands-free, one tap."),
        SttMode.EXTERNAL_POPUP to ("Recognizer popup (FUTO)" to
            "Tap the mic to launch the recognizer app's own overlay (FUTO Voice Input's " +
            "floating popup). Set it as the default 'Voice input' app. This is how Dicio uses FUTO."),
        SttMode.IME to ("Keyboard dictation (IME)" to
            "A text field you dictate into with a voice keyboard such as FUTO Voice Input. " +
            "Set that keyboard as an input method first; tap the field, then its mic."),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (mode, texts) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.Top,
            ) {
                RadioButton(selected = current == mode, onClick = { onPick(mode) })
                Column(Modifier.padding(top = 12.dp)) {
                    Text(texts.first, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        texts.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun FieldState(
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    placeholder: String = "",
    secret: Boolean = false,
    singleLine: Boolean = true,
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onCommit(it) },
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = singleLine,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

private fun openAssistantRole(context: Context) {
    val roleIntent = runCatching {
        val rm = context.getSystemService(android.app.role.RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT)) {
            rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT)
        } else null
    }.getOrNull()

    val intent = roleIntent ?: Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
