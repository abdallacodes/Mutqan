package com.example.qmemo.ui.onboarding

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.os.LocaleListCompat
import com.example.qmemo.R
import com.example.qmemo.data.UserPreferencesRepository
import kotlinx.coroutines.launch

// ── Slide metadata ────────────────────────────────────────────────────────────

private data class OnboardingSlide(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int
)

private val featureSlides = listOf(
    OnboardingSlide(Icons.Default.Timeline, R.string.onboarding_slide1_title, R.string.onboarding_slide1_body),
    OnboardingSlide(Icons.Default.Hub,      R.string.onboarding_slide2_title, R.string.onboarding_slide2_body),
    OnboardingSlide(Icons.Default.Whatshot, R.string.onboarding_slide3_title, R.string.onboarding_slide3_body)
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context    = LocalContext.current
    val prefs      = remember { UserPreferencesRepository(context) }
    val scope      = rememberCoroutineScope()
    
    // Step 0: Language Selection, Step 1: Feature Slides
    var step by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { featureSlides.size })

    fun skip() {
        scope.launch {
            prefs.completeOnboarding()
            onComplete()
        }
    }

    fun advance() {
        if (step == 0) {
            step = 1
        } else {
            if (pagerState.currentPage < featureSlides.lastIndex) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            } else {
                scope.launch { 
                    prefs.completeOnboarding()
                    onComplete()
                }
            }
        }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.92f))
        },
        label = "onboarding_step"
    ) { currentStep ->
        if (currentStep == 0) {
            LanguageSelectionSlide(
                onLanguageSelected = { tag ->
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                },
                onNext = { advance() }
            )
        } else {
            FeatureOnboardingContent(
                pagerState = pagerState,
                onSkip = { skip() },
                onAdvance = { advance() }
            )
        }
    }
}

@Composable
private fun LanguageSelectionSlide(
    onLanguageSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    val currentLang = AppCompatDelegate.getApplicationLocales()[0]?.language ?: "en"

    val appName = stringResource(R.string.app_name)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    0.35f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.background
                )
            )
            .padding(horizontal = 32.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_title, appName),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(R.string.onboarding_lang_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_lang_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(20.dp))

        // Language Buttons
        LanguageOption(
            label = "English",
            isSelected = currentLang == "en" || currentLang.isEmpty(),
            onClick = { onLanguageSelected("en") }
        )
        Spacer(Modifier.height(12.dp))
        LanguageOption(
            label = "العربية",
            isSelected = currentLang == "ar",
            onClick = { onLanguageSelected("ar") }
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = stringResource(R.string.onboarding_next),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureOnboardingContent(
    pagerState: androidx.compose.foundation.pager.PagerState,
    onSkip: () -> Unit,
    onAdvance: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    0.45f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.background
                )
            )
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state                   = pagerState,
                modifier                = Modifier.weight(1f),
                beyondViewportPageCount = 1
            ) { page ->
                SlideContent(slide = featureSlides[page])
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.padding(bottom = 20.dp)
            ) {
                repeat(featureSlides.size) { index ->
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

            Button(
                onClick  = onAdvance,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                val label = if (pagerState.currentPage == featureSlides.lastIndex)
                    stringResource(R.string.onboarding_get_started)
                else
                    stringResource(R.string.onboarding_next)

                Text(
                    text          = label,
                    style         = MaterialTheme.typography.titleSmall,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        if (pagerState.currentPage < featureSlides.lastIndex) {
            TextButton(
                onClick  = onSkip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(1f)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 4.dp, end = 4.dp)
            ) {
                Text(
                    text       = stringResource(R.string.onboarding_skip),
                    style      = MaterialTheme.typography.labelLarge,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SlideContent(slide: OnboardingSlide) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = slide.icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier           = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text          = stringResource(slide.titleRes),
            style         = MaterialTheme.typography.headlineSmall,
            fontWeight    = FontWeight.ExtraBold,
            color         = MaterialTheme.colorScheme.onBackground,
            textAlign     = TextAlign.Center,
            letterSpacing = 0.2.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text       = stringResource(slide.bodyRes),
            style      = MaterialTheme.typography.bodyLarge,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign  = TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}
