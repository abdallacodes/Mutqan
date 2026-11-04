package com.example.mutqan.ui.feedback

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace

/** Where feedback goes. */
const val FEEDBACK_ADDRESS = "support.mutqan@gmail.com"

private enum class Kind(val label: Int, val subject: String) {
    IDEA(R.string.feedback_kind_idea, "Idea"),
    PROBLEM(R.string.feedback_kind_problem, "Problem"),
    OTHER(R.string.feedback_kind_other, "Other")
}

/**
 * A message to the developer. Sending opens the reader's own mail app with the email ready, so nothing is stored
 * anywhere else; with no mail app the message is copied for them to send some other way.
 */
// The note after a failed send is read when the button is pressed, not while composing.
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(Kind.IDEA) }
    var message by rememberSaveable { mutableStateOf("") }
    var includeInfo by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.feedback_title),
                subtitle = stringResource(R.string.feedback_subtitle),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PaperSpace.Gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Kind.entries.forEach { option ->
                    PaperChip(
                        selected = option == kind,
                        onClick = { kind = option },
                        label = { Text(stringResource(option.label)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text(stringResource(R.string.feedback_hint)) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.feedback_include_info),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = includeInfo, onCheckedChange = { includeInfo = it })
            }

            PaperButton(
                text = stringResource(R.string.feedback_send),
                onClick = {
                    val body = message.trim() + if (includeInfo) "\n\n—\n" + deviceInfo(context) else ""
                    if (!sendMail(context, "Mutqan feedback (${kind.subject})", body)) {
                        copy(context, body)
                        Toast.makeText(context, context.getString(R.string.feedback_no_mail, FEEDBACK_ADDRESS), Toast.LENGTH_LONG).show()
                    }
                },
                enabled = message.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.feedback_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun deviceInfo(context: Context): String {
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val version = info?.versionName ?: "?"
    @Suppress("DEPRECATION")
    val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() } ?: 0L
    return "Mutqan $version ($code) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
}

/** Opens a mail app with the email filled in; false when there is none. */
private fun sendMail(context: Context, subject: String, body: String): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_ADDRESS))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("feedback", text))
}
