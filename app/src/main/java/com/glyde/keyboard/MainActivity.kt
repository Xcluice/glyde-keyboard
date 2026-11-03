package com.glyde.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlydeApp(tick) }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) tick++
    }
}

@Composable
fun GlydeApp(tick: Int) {
    val ctx = LocalContext.current
    val prefs = remember { GlydePrefs(ctx) }
    var themeId by remember { mutableStateOf(prefs.theme) }
    var height by remember { mutableFloatStateOf(prefs.heightScale) }
    var glass by remember { mutableFloatStateOf(prefs.glass) }
    var haptic by remember { mutableIntStateOf(prefs.haptic) }
    var sound by remember { mutableStateOf(prefs.sound) }
    var numberRow by remember { mutableStateOf(prefs.numberRow) }
    var preview by remember { mutableStateOf(prefs.keyPreview) }
    var liquid by remember { mutableStateOf(prefs.liquid) }
    var ambient by remember { mutableStateOf(prefs.ambient) }
    var testText by remember { mutableStateOf("") }

    val theme = GlydeThemes.byId(themeId)
    val ink = Color(theme.ink)
    val accent = Color(theme.accent)
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF0B1A1F) else Color.White
    val bgTop by animateColorAsState(Color(theme.bgTop), tween(700), label = "bgTop")
    val bgBottom by animateColorAsState(Color(theme.bgBottom), tween(700), label = "bgBottom")

    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabled = remember(tick) { imm.enabledInputMethodList.any { it.packageName == ctx.packageName } }
    val selected = remember(tick) {
        (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "")
            .startsWith(ctx.packageName)
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = accent)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgTop, bgBottom)))) {
            Orbs(theme)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Header(ink, accent)

                GlassCard(theme, Modifier.enterAnim(1)) {
                    SectionTitle("Set up", ink)
                    StepRow("1", "Enable Glyde", enabled, "Turn Glyde on", ink, accent, onAccent) {
                        ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }
                    StepRow("2", "Switch to Glyde", selected, "Pick keyboard", ink, accent, onAccent) {
                        imm.showInputMethodPicker()
                    }
                }

                GlassCard(theme, Modifier.enterAnim(2)) {
                    SectionTitle("Theme", ink)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        items(GlydeThemes.all) { t ->
                            ThemeCard(t, t.id == themeId, ink) {
                                themeId = t.id
                                prefs.theme = t.id
                            }
                        }
                    }
                }

                GlassCard(theme, Modifier.enterAnim(3)) {
                    SectionTitle("Feel", ink)
                    SliderRow("Key height", "${(height * 100).toInt()}%", height, 0.8f..1.3f, ink, accent) {
                        height = it
                        prefs.heightScale = it
                    }
                    SliderRow("Glass strength", "${(glass * 100).toInt()}%", glass, 0.4f..1.5f, ink, accent) {
                        glass = it
                        prefs.glass = it
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Haptics", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf("Off", "Light", "Medium", "Strong"), haptic, ink, accent, onAccent) {
                        haptic = it
                        prefs.haptic = it
                    }
                }

                GlassCard(theme, Modifier.enterAnim(4)) {
                    SectionTitle("Options", ink)
                    ToggleRow("Number row", "Digits above the letters", numberRow, ink, accent) {
                        numberRow = it
                        prefs.numberRow = it
                    }
                    ToggleRow("Key preview", "Bubble above the pressed key", preview, ink, accent) {
                        preview = it
                        prefs.keyPreview = it
                    }
                    ToggleRow("Liquid effects", "Ripples and keys that bulge to your finger", liquid, ink, accent) {
                        liquid = it
                        prefs.liquid = it
                    }
                    ToggleRow("Live background", "Orbs drift all the time (uses more battery)", ambient, ink, accent) {
                        ambient = it
                        prefs.ambient = it
                    }
                    ToggleRow("Key sounds", "Click sound on every key", sound, ink, accent) {
                        sound = it
                        prefs.sound = it
                    }
                }

                GlassCard(theme, Modifier.enterAnim(5)) {
                    SectionTitle("Try it", ink)
                    BasicTextField(
                        value = testText,
                        onValueChange = { testText = it },
                        textStyle = TextStyle(color = ink, fontSize = 18.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ink.copy(alpha = 0.08f))
                            .padding(16.dp),
                        decorationBox = { inner ->
                            Box {
                                if (testText.isEmpty()) {
                                    Text("Type here to try Glyde…", color = ink.copy(alpha = 0.45f), fontSize = 18.sp)
                                }
                                inner()
                            }
                        }
                    )
                }

                Text(
                    "Glyde 0.1 · more features on the way",
                    color = ink.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun Modifier.enterAnim(index: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        p.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 70f
    }
}

@Composable
private fun Orbs(theme: GlydeTheme) {
    val t = rememberInfiniteTransition(label = "orbs")
    val a = t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    val b = t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )
    Box(Modifier.fillMaxSize()) {
        OrbBlob(
            Color(theme.orbA), 340.dp,
            Modifier.align(Alignment.TopStart).graphicsLayer {
                translationX = a.value * 260f
                translationY = b.value * 160f
            }
        )
        OrbBlob(
            Color(theme.orbB), 380.dp,
            Modifier.align(Alignment.CenterEnd).graphicsLayer {
                translationX = -b.value * 220f
                translationY = a.value * 300f
            }
        )
        OrbBlob(
            Color(theme.orbC), 300.dp,
            Modifier.align(Alignment.BottomStart).graphicsLayer {
                translationX = b.value * 300f
                translationY = -a.value * 200f
            }
        )
    }
}

@Composable
private fun OrbBlob(c: Color, size: Dp, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .background(Brush.radialGradient(listOf(c.copy(alpha = 0.75f), Color.Transparent)), CircleShape)
    )
}

@Composable
private fun Header(ink: Color, accent: Color) {
    Row(
        Modifier.fillMaxWidth().enterAnim(0).padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF19E3C8), Color(0xFF0A84FF))))
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("G", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Glyde", color = ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Liquid glass keyboard", color = ink.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun GlassCard(
    theme: GlydeTheme,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val fill = if (theme.dark) listOf(0.14f, 0.05f) else listOf(0.70f, 0.45f)
    val edge = if (theme.dark) listOf(0.55f, 0.06f, 0.22f) else listOf(0.95f, 0.35f, 0.7f)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = fill[0]), Color.White.copy(alpha = fill[1]))))
            .border(
                1.dp,
                Brush.linearGradient(edge.map { Color.White.copy(alpha = it) }),
                shape
            )
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun SectionTitle(text: String, ink: Color) {
    Text(text, color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun StepRow(
    number: String, title: String, done: Boolean, button: String,
    ink: Color, accent: Color, onAccent: Color, onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (done) accent else ink.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (done) "✓" else number, color = if (done) onAccent else ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        PillButton(button, accent, onAccent, ink, !done, onClick)
    }
}

@Composable
private fun PillButton(
    text: String, accent: Color, onAccent: Color, ink: Color, filled: Boolean, onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.92f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "pill"
    )
    Box(
        Modifier
            .scale(s)
            .clip(CircleShape)
            .background(
                if (filled) Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.75f)))
                else SolidColor(ink.copy(alpha = 0.14f))
            )
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) onAccent else ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun ThemeCard(t: GlydeTheme, selected: Boolean, ink: Color, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.93f else if (selected) 1.04f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 400f), label = "themeCard"
    )
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .scale(s)
            .width(112.dp)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(112.dp, 78.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(t.bgTop), Color(t.bgBottom))))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Brush.linearGradient(listOf(Color.White, Color(t.accent)))
                    else SolidColor(Color.White.copy(alpha = 0.25f)),
                    shape
                )
        ) {
            Box(
                Modifier
                    .size(70.dp)
                    .offset((-14).dp, (-14).dp)
                    .background(Brush.radialGradient(listOf(Color(t.orbA).copy(alpha = 0.85f), Color.Transparent)), CircleShape)
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(80.dp)
                    .offset(16.dp, 16.dp)
                    .background(Brush.radialGradient(listOf(Color(t.orbB).copy(alpha = 0.8f), Color.Transparent)), CircleShape)
            )
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(4) { MiniKey() }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            t.name, color = ink, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun MiniKey() {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(16.dp, 20.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.15f))))
            .border(0.8.dp, Color.White.copy(alpha = 0.5f), shape)
    )
}

@Composable
private fun ToggleRow(
    title: String, sub: String, checked: Boolean, ink: Color, accent: Color, onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        GlassToggle(checked, accent, onChange)
    }
}

@Composable
private fun GlassToggle(checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(
        if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "toggle"
    )
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(CircleShape)
            .background(lerp(Color.White.copy(alpha = 0.18f), accent.copy(alpha = 0.9f), x.coerceIn(0f, 1f)))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(interactionSource = src, indication = null) { onChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = (4f + 22f * x).dp, y = 4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(
    title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
    ink: Color, accent: Color, onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueText, color = ink.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                activeTrackColor = accent,
                inactiveTrackColor = ink.copy(alpha = 0.2f)
            ),
            thumb = { Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White)) }
        )
    }
}

@Composable
private fun Segmented(
    options: List<String>, selected: Int, ink: Color, accent: Color, onAccent: Color, onSelect: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ink.copy(alpha = 0.1f))
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) accent else Color.Transparent, tween(250), label = "seg")
            val src = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable(interactionSource = src, indication = null) { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) onAccent else ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
