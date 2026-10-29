package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.BackButton
import app.gameport.core.designsystem.DangerTrashButton
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.GlassIconButton
import app.gameport.core.designsystem.glass
import kotlinx.coroutines.delay

/*
 * A mock-up to try, not a real page: fixed data, no game, nothing installed. It shows the proposed layout of a game's page (the buttons in three groups, the installation in a card of
 * its own that fills in step by step while the buttons stay in place, greyed). The panel at the bottom plays the installation. Texts are written here, as it is thrown away.
 */

private enum class Stage { WAITING, ACTIVE, DONE, FAILED }

private class Line(val name: String, val seconds: Float) {
    var stage by mutableStateOf(Stage.WAITING)
    var progress by mutableFloatStateOf(0f)
}

private val Green = Color(0xFF4CAF50)
private val Orange = Color(0xFFFF9800)
private val Red = Color(0xFFE53935)

@Composable
fun GameMockupScreen(onBack: () -> Unit) {
    val steps = remember { listOf(Line("Téléchargement", 5f), Line("Vérification", 1.5f), Line("Patch pour cet appareil", 1.5f)) }
    val extras = remember { listOf(Line("Pack de skins", 2.5f), Line("Pack de cartes", 3f), Line("Bande-son", 2f)) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var failOnMaps by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }

    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        running = true
        failed = false
        for (line in steps + extras) {
            if (line.stage == Stage.DONE) continue
            line.stage = Stage.ACTIVE
            val ticks = (line.seconds * 20).toInt()
            for (tick in 1..ticks) {
                while (paused) delay(100)
                delay(50)
                line.progress = tick / ticks.toFloat()
            }
            if (failOnMaps && line.name == "Pack de cartes") {
                line.stage = Stage.FAILED
                failed = true
                running = false
                return@LaunchedEffect
            }
            line.stage = Stage.DONE
        }
        running = false
        installed = true
    }

    fun reset() {
        run = 0
        running = false
        paused = false
        installed = false
        failed = false
        (steps + extras).forEach { it.stage = Stage.WAITING; it.progress = 0f }
    }

    // Greyed while the installation goes, and while there is nothing to act on yet.
    val usable = installed && !running
    val shade = if (usable) 1f else 0.4f
    val showCard = running || failed

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BackButton(onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text("Jeu d'exemple", style = MaterialTheme.typography.headlineLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("VR")
                        GlassChip("Possédé")
                        GlassChip("Fonctionne", accent = Green)
                    }
                }
                // The secondary actions of the game, in the header.
                GlassIconButton(onClick = {}) { Icon(Icons.Filled.Favorite, contentDescription = "Favori") }
                GlassIconButton(onClick = {}) { Icon(Icons.Filled.VisibilityOff, contentDescription = "Masquer", tint = Red) }
                GlassIconButton(onClick = {}, enabled = usable) { Icon(Icons.Filled.Settings, contentDescription = "Réglages") }
            }

            // The main action, alone.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (installed) {
                    Button(onClick = {}, enabled = !running, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Jouer")
                    }
                    DangerTrashButton(onClick = { reset() }, contentDescription = "Désinstaller", enabled = !running)
                } else {
                    Button(onClick = { run++ }, enabled = !running, modifier = Modifier.height(48.dp)) { Text(if (failed) "Reprendre" else "Installer") }
                }
            }

            if (showCard) InstallCard(steps, extras, paused, failed, onPause = { paused = !paused }, onRetry = { failOnMaps = false; run++ }, onCancel = { reset() })

            Group("Contenu", shade) {
                GlassButton(onClick = {}, enabled = usable) { Icon(Icons.Filled.Extension, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Workshop") }
                GlassButton(onClick = {}, enabled = usable) { Text("Extras (DLC)") }
                GlassButton(onClick = {}, enabled = usable) { Icon(Icons.Filled.CloudSync, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Sauvegardes") }
            }
            Group("Appareil", shade) {
                GlassButton(onClick = {}, enabled = usable) { Icon(Icons.Filled.SportsEsports, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Manettes") }
                GlassButton(onClick = {}, enabled = usable) { Icon(Icons.Filled.Build, null, Modifier.size(18.dp), tint = Orange); Spacer(Modifier.width(8.dp)); Text("Compatibilité") }
                GlassButton(onClick = {}, enabled = usable) { Icon(Icons.Filled.BugReport, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Signaler un problème") }
            }

            // The panel that plays the installation: only in this mock-up.
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Simulation (maquette)", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = failOnMaps, onCheckedChange = { failOnMaps = it }, enabled = !running)
                    Text("Une erreur sur « Pack de cartes »", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    GlassButton(onClick = { reset(); run = 1 }, enabled = !running) { Text("Rejouer l'installation") }
                    GlassButton(onClick = { reset() }) { Text("Remettre à zéro") }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, shade: Float, content: @Composable () -> Unit) {
    Column(Modifier.alpha(shade), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun InstallCard(steps: List<Line>, extras: List<Line>, paused: Boolean, failed: Boolean, onPause: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Installation", style = MaterialTheme.typography.titleMedium)
        val gameFraction = steps.sumOf { (if (it.stage == Stage.DONE) 1.0 else it.progress.toDouble()) * it.seconds } / steps.sumOf { it.seconds.toDouble() }
        Section("Jeu", gameFraction.toFloat(), steps)
        Section("Extras", null, extras)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failed) {
                Button(onClick = onRetry) { Text("Réessayer") }
            } else {
                OutlinedButton(onClick = onPause) { Text(if (paused) "Reprendre" else "Mettre en pause") }
            }
            OutlinedButton(onClick = onCancel) { Text("Annuler") }
        }
    }
}

@Composable
private fun Section(title: String, fraction: Float?, lines: List<Line>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (fraction != null) Text("${(fraction * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
        }
        if (fraction != null) LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
        lines.forEach { LineRow(it) }
    }
}

@Composable
private fun LineRow(line: Line) {
    // What is done goes grey as the next one lights up.
    val (icon, tint) = when (line.stage) {
        Stage.DONE -> Icons.Filled.CheckCircle to Green
        Stage.FAILED -> Icons.Filled.Error to Red
        Stage.ACTIVE -> Icons.Filled.PlayArrow to MaterialTheme.colorScheme.primary
        Stage.WAITING -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().alpha(if (line.stage == Stage.WAITING || line.stage == Stage.DONE) 0.55f else 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(line.name, Modifier.width(220.dp), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f)) {
            when (line.stage) {
                Stage.ACTIVE -> LinearProgressIndicator(progress = { line.progress }, Modifier.fillMaxWidth())
                Stage.DONE -> Text("terminé", style = MaterialTheme.typography.bodySmall)
                Stage.FAILED -> Text("a échoué", style = MaterialTheme.typography.bodySmall, color = Red)
                Stage.WAITING -> Text("en attente", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
