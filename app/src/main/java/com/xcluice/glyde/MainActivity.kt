@file:OptIn(ExperimentalMaterial3Api::class)

package com.xcluice.glyde

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlydeApp(resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) resumeTick++
    }
}

@Composable
private fun GlydeApp(tick: Int) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SettingsScreen(tick)
        }
    }
}

@Composable
private fun SettingsScreen(tick: Int) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }

    var theme by remember { mutableStateOf(prefs.themeId) }
    var haptic by remember { mutableIntStateOf(prefs.hapticLevel) }
    var anim by remember { mutableStateOf(prefs.keyAnim) }
    var keyPreview by remember { mutableStateOf(prefs.keyPreview) }
    var keySound by remember { mutableStateOf(prefs.keySound) }
    var numberRow by remember { mutableStateOf(prefs.numberRow) }
    var autoCap by remember { mutableStateOf(prefs.autoCap) }
    var dblSpace by remember { mutableStateOf(prefs.doubleSpacePeriod) }
    var height by remember { mutableFloatStateOf(prefs.heightScale) }
    var radius by remember { mutableFloatStateOf(prefs.keyRadius) }
    val echo = remember { mutableStateOf("") }
    var testText by remember { mutableStateOf("") }

    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabled = remember(tick) { imm.enabledInputMethodList.any { it.packageName == ctx.packageName } }
    val selected = remember(tick) {
        (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "")
            .startsWith(ctx.packageName + "/")
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("Glyde", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "Glass keyboard. Set it up, then style it below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StepRow(
                    done = enabled,
                    title = "1. Enable Glyde",
                    subtitle = "Turn it on in system keyboard settings",
                    action = "Open",
                ) { ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                StepRow(
                    done = selected,
                    title = "2. Switch to Glyde",
                    subtitle = "Pick it as your active keyboard",
                    action = "Switch",
                ) { imm.showInputMethodPicker() }
            }
        }

        Section("Preview")
        OutlinedTextField(
            value = echo.value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Preview output") },
            modifier = Modifier.fillMaxWidth(),
        )
        AndroidView(
            factory = { c ->
                GlydeKeyboardView(c).apply {
                    listener = object : GlydeKeyboardView.Listener {
                        override fun onText(text: String) { echo.value += text }
                        override fun onDelete() { echo.value = echo.value.dropLast(1) }
                        override fun onEnter() { echo.value += "\n" }
                        override fun onOpenSettings() {}
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
        )
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            label = { Text("Type here to test the real keyboard") },
            modifier = Modifier.fillMaxWidth(),
        )

        Section("Glass themes")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Themes.all.filter { it.glass }.forEach { t ->
                ThemeSwatch(t, theme == t.id) { theme = t.id; prefs.themeId = t.id }
            }
        }
        Section("Gradient themes")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Themes.all.filter { !it.glass }.forEach { t ->
                ThemeSwatch(t, theme == t.id) { theme = t.id; prefs.themeId = t.id }
            }
        }

        Section("Haptics")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Off", "Light", "Medium", "Strong").forEachIndexed { i, label ->
                FilterChip(
                    selected = haptic == i,
                    onClick = {
                        haptic = i
                        prefs.hapticLevel = i
                        Haptics.tap(ctx, i)
                    },
                    label = { Text(label) },
                )
            }
        }

        Section("Key animation")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ripple" to "Ripple", "pop" to "Pop", "glow" to "Glow", "none" to "None").forEach { (id, label) ->
                FilterChip(
                    selected = anim == id,
                    onClick = { anim = id; prefs.keyAnim = id },
                    label = { Text(label) },
                )
            }
        }

        Section("Layout")
        Text("Keyboard height  ${"%.0f".format(height * 100)}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = height,
            onValueChange = { height = it; prefs.heightScale = it },
            valueRange = 0.8f..1.3f,
        )
        Text("Key roundness  ${"%.0f".format(radius)} dp", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = radius,
            onValueChange = { radius = it; prefs.keyRadius = it },
            valueRange = 2f..20f,
        )
        SwitchRow("Number row", "Always show 1-0 above the letters", numberRow) {
            numberRow = it; prefs.numberRow = it
        }

        Section("Typing")
        SwitchRow("Key preview", "Show a bubble above the pressed key", keyPreview) {
            keyPreview = it; prefs.keyPreview = it
        }
        SwitchRow("Key sound", "Play a click on each key", keySound) {
            keySound = it; prefs.keySound = it
        }
        SwitchRow("Auto-capitalize", "Capitalize at the start of sentences", autoCap) {
            autoCap = it; prefs.autoCap = it
        }
        SwitchRow("Double-space period", "Tap space twice to insert \". \"", dblSpace) {
            dblSpace = it; prefs.doubleSpacePeriod = it
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StepRow(done: Boolean, title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked = done, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done) FilledTonalButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemeSwatch(t: GlydeTheme, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(width = 88.dp, height = 64.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(t.bgTop), Color(t.bgBottom))))
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(width = 18.dp, height = 22.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(t.keyTop)),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            t.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
