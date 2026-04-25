package com.example.qmemo.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qmemo.R
import com.example.qmemo.data.UserPreferencesRepository
import kotlinx.coroutines.launch

// ── Slide metadata ────────────────────────────────────────────────────────────

private data class OnboardingSlide(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int
)

private val slides = listOf(
    OnboardingSlide(Icons.Default.Timeline, R.string.onboarding_slide1_title, R.string.onboarding_slide1_body),
    OnboardingSlide(Icons.Default.Hub,      R.string.onboarding_slide2_title, R.string.onboarding_slide2_body),
    OnboardingSlide(Icons.Default.Whatshot, R.string.onboarding_slide3_title, R.string.onboarding_slide3_body)
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context    = LocalContext.current
    val prefs      = UserPreferencesRepository(context)
    val scope      = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { slides.size })

    fun skip() {
        scope.launch {
            prefs.completeOnboarding()
            onComplete()
        }
    }

    fun advance() {
        if (pagerState.currentPage < slides.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        } else {
            scope.launch { 
                prefs.completeOnboarding()
                onComplete()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Skip button — hidden on last slide
        if (pagerState.currentPage < slides.lastIndex) {
            TextButton(
                onClick  = { skip() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                Text(
                    text      = stringResource(R.string.onboarding_skip),
                    style     = MaterialTheme.typography.labelLarge,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(
            modifier              = Modifier.fillMaxSize(),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            // ── Pager content ─────────────────────────────────────────────
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                SlideContent(slide = slides[page])
            }

            // ── Page dots ─────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.padding(bottom = 24.dp)
            ) {
                repeat(slides.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        label = "dot_color"
                    )
                    val dotSize = if (isSelected) 10.dp else 6.dp
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            // ── CTA button ────────────────────────────────────────────────
            Button(
                onClick  = { advance() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                val label = if (pagerState.currentPage == slides.lastIndex)
                    stringResource(R.string.onboarding_get_started)
                else
                    stringResource(R.string.onboarding_next)

                Text(
                    text          = label,
                    style         = MaterialTheme.typography.titleSmall,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Single slide ──────────────────────────────────────────────────────────────

@Composable
private fun SlideContent(slide: OnboardingSlide) {
    Column(
        modifier              = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        // Icon in a primary-tinted circle
        Box(
            modifier         = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = slide.icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier           = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text          = stringResource(slide.titleRes),
            style         = MaterialTheme.typography.headlineSmall,
            fontWeight    = FontWeight.ExtraBold,
            color         = MaterialTheme.colorScheme.onBackground,
            textAlign     = TextAlign.Center,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text       = stringResource(slide.bodyRes),
            style      = MaterialTheme.typography.bodyLarge,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign  = TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}
