package com.example.mutqan.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperHeader

private class Topic(val title: Int, val body: Int)

private val topics = listOf(
    Topic(R.string.how_s1_title, R.string.how_s1_body),
    Topic(R.string.how_s2_title, R.string.how_s2_body),
    Topic(R.string.how_s3_title, R.string.how_s3_body),
    Topic(R.string.how_s4_title, R.string.how_s4_body),
    Topic(R.string.how_s5_title, R.string.how_s5_body),
    Topic(R.string.how_s6_title, R.string.how_s6_body)
)

/** How the app works, and when a page moves between weak, fair and strong. */
@Composable
fun HowItWorksScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.how_title),
                subtitle = stringResource(R.string.how_subtitle),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            topics.forEach { topic ->
                Text(
                    text = stringResource(topic.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(topic.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Hairline(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
