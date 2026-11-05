package com.example.mutqan.ui.onboarding

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat
import com.example.mutqan.R
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChoice
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import com.example.mutqan.ui.components.StarMedallion
import com.example.mutqan.ui.theme.FolioTheme

/** One page of the introduction: what the app does here, and where in the app to find it. */
private data class OnboardingSlide(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @StringRes val whereRes: Int
)

private val featureSlides = listOf(
    OnboardingSlide(R.string.onboarding_slide1_title, R.string.onboarding_slide1_body, R.string.onboarding_slide1_where),
    OnboardingSlide(R.string.onboarding_slide2_title, R.string.onboarding_slide2_body, R.string.onboarding_slide2_where),
    OnboardingSlide(R.string.onboarding_slide3_title, R.string.onboarding_slide3_body, R.string.onboarding_slide3_where),
    OnboardingSlide(R.string.onboarding_slide4_title, R.string.onboarding_slide4_body, R.string.onboarding_slide4_where)
)

/** First run: choose the language, then four short pages that say how the app is used. */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { UserPreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    // Step 0: language, step 1: the introduction pages.
    var step by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { featureSlides.size })

    fun finish() {
        scope.launch {
            prefs.completeOnboarding()
            onComplete()
        }
    }

    fun advance() {
        when {
            step == 0 -> step = 1
            pagerState.currentPage < featureSlides.lastIndex ->
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            else -> finish()
        }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "onboarding_step",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) { currentStep ->
        if (currentStep == 0) {
            LanguageStep(
                onLanguageSelected = { tag -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                onNext = { advance() }
            )
        } else {
            SlidesStep(pagerState = pagerState, onSkip = { finish() }, onAdvance = { advance() })
        }
    }
}

@Composable
private fun LanguageStep(onLanguageSelected: (String) -> Unit, onNext: () -> Unit) {
    // The language the screen is actually shown in, not the stored choice, so the two cannot disagree.
    val currentLang = LocalConfiguration.current.locales[0].language
    val isEnglish = currentLang == "en"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .padding(horizontal = PaperSpace.Gutter, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_title, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            OrnamentDivider()
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.onboarding_lang_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.onboarding_lang_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            PaperChoice(title = "العربية", selected = !isEnglish, onClick = { onLanguageSelected("ar") })
            PaperChoice(title = "English", selected = isEnglish, onClick = { onLanguageSelected("en") })
        }
        PaperButton(
            text = stringResource(R.string.onboarding_next),
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SlidesStep(pagerState: PagerState, onSkip: () -> Unit, onAdvance: () -> Unit) {
    val last = pagerState.currentPage == featureSlides.lastIndex
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter - 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel(
                text = stringResource(R.string.onboarding_page_of, pagerState.currentPage + 1, featureSlides.size)
            )
            // Keeps the row the same height on the last page, where there is nothing to skip.
            if (last) Spacer(Modifier.height(44.dp))
            else PaperLink(stringResource(R.string.onboarding_skip), onClick = onSkip, soft = true)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1
        ) { page -> Slide(featureSlides[page], number = page + 1) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(featureSlides.size) { index ->
                val on = pagerState.currentPage == index
                val width by animateDpAsState(if (on) 28.dp else 8.dp, label = "dotWidth")
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(8.dp)
                        .background(
                            if (on) MaterialTheme.colorScheme.primary else FolioTheme.colors.tonal,
                            FolioTheme.shapes.pill
                        )
                )
            }
        }

        PaperButton(
            text = stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next),
            onClick = onAdvance,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter).padding(bottom = 20.dp)
        )
    }
}

/** A page of the introduction: a large number in the mushaf's own type, the title, the sentence, and where to find it. */
@Composable
private fun Slide(slide: OnboardingSlide, number: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PaperSpace.Gutter),
        verticalArrangement = Arrangement.Center
    ) {
        StarMedallion(text = number.toString(), size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(slide.titleRes),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(slide.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 28.sp
        )
        Spacer(Modifier.height(24.dp))
        OrnamentDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(slide.whereRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
