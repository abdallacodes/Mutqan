package com.example.mutqan.ui.habit

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.mutqan.R
import com.example.mutqan.domain.Milestone
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.StarMedallion
import com.example.mutqan.ui.components.rememberHaptics
import com.example.mutqan.ui.theme.FolioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Shares pictures from the cache (a milestone's card); its own class so it never clashes with the updater's provider. */
class ShareFileProvider : FileProvider()

/**
 * A milestone's moment: a card with its number in a star, what was reached and a line on it, shown once over Today.
 * The card itself can be shared as a picture; "Alhamdulillah" closes it.
 */
@Composable
fun MilestoneDialog(milestone: Milestone, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val layer = rememberGraphicsLayer()
    val grow = remember(milestone) { Animatable(0.82f) }
    LaunchedEffect(milestone) {
        haptics.confirm()
        grow.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 240f))
    }
    val badge: String
    val title: String
    val body: String
    when (milestone) {
        is Milestone.Days -> {
            badge = milestone.days.toString()
            title = pluralStringResource(R.plurals.habit_streak_days, milestone.days, milestone.days)
            body = stringResource(R.string.milestone_days_body)
        }
        is Milestone.Pass -> {
            badge = milestone.number.toString()
            title = if (milestone.number == 1) stringResource(R.string.milestone_pass_first) else stringResource(R.string.milestone_pass_n, milestone.number)
            body = pluralStringResource(R.plurals.milestone_pass_body, milestone.days, milestone.days)
        }
        is Milestone.StrongJuz -> {
            badge = milestone.juz.toString()
            title = stringResource(R.string.milestone_juz_title, milestone.juz)
            body = stringResource(R.string.milestone_juz_body)
        }
    }
    val shareText = stringResource(R.string.milestone_share_text, title)
    val shareTitle = stringResource(R.string.milestone_share_title)
    val background = MaterialTheme.colorScheme.background

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .graphicsLayer { scaleX = grow.value; scaleY = grow.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // The card on the page's colour, recorded so it can be shared as it looks.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .background(background)
                    .padding(14.dp)
            ) {
                HeroCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StarMedallion(text = badge, size = 116.dp, onHero = true)
                        Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = FolioTheme.colors.onHero.copy(alpha = 0.85f)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.labelMedium,
                            color = FolioTheme.colors.heroAccent
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaperOutlinedButton(
                    text = stringResource(R.string.milestone_share),
                    onClick = { scope.launch { share(context, layer, shareText, shareTitle) } },
                    modifier = Modifier.weight(1f)
                )
                PaperButton(text = stringResource(R.string.milestone_close), onClick = onClose, modifier = Modifier.weight(1f))
            }
        }
    }
}

private suspend fun share(context: Context, layer: GraphicsLayer, text: String, title: String) {
    val bitmap = layer.toImageBitmap().asAndroidBitmap()
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "milestone.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, context.packageName + ".share", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
