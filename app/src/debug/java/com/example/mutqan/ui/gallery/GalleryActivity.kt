package com.example.mutqan.ui.gallery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.mutqan.data.Palette
import com.example.mutqan.data.ThemeKey
import com.example.mutqan.ui.components.ActionRow
import com.example.mutqan.ui.components.ActionRowInset
import com.example.mutqan.ui.components.ActionTile
import com.example.mutqan.ui.components.Folio
import com.example.mutqan.ui.components.FolioDivider
import com.example.mutqan.ui.components.FolioGroup
import com.example.mutqan.ui.components.FilterPill
import com.example.mutqan.ui.components.HeroButton
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.IconMedallion
import com.example.mutqan.ui.components.PageStrip
import com.example.mutqan.ui.components.PrimaryButton
import com.example.mutqan.ui.components.ProgressRing
import com.example.mutqan.ui.components.ScopePill
import com.example.mutqan.ui.components.StarMedallion
import com.example.mutqan.ui.components.TextAction
import com.example.mutqan.ui.components.TonalButton
import com.example.mutqan.ui.components.rememberHaptics
import com.example.mutqan.ui.theme.FolioTheme
import com.example.mutqan.ui.theme.MutqanTheme
import com.example.mutqan.ui.theme.folioTypography

/**
 * Debug builds only: every component of design system v2 on one page, with the palette and the light or dark version
 * switchable on the page, to judge them before a screen uses them. Samples are Arabic or English with the app language.
 */
class GalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startPalette = Palette.fromString(intent.getStringExtra("palette"))
        val startDark = intent.getBooleanExtra("dark", false)
        setContent {
            var palette by remember { mutableStateOf(startPalette) }
            var dark by remember { mutableStateOf(startDark) }
            MutqanTheme(themeKey = if (dark) ThemeKey.DARK else ThemeKey.LIGHT, palette = palette) {
                val arabic = LocalConfiguration.current.locales[0].language == "ar"
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    shapes = MaterialTheme.shapes,
                    typography = folioTypography(arabic)
                ) {
                    Gallery(arabic, palette, dark, onPalette = { palette = it }, onDark = { dark = it })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gallery(arabic: Boolean, palette: Palette, dark: Boolean, onPalette: (Palette) -> Unit, onDark: (Boolean) -> Unit) {
    fun t(ar: String, en: String) = if (arabic) ar else en
    val space = FolioTheme.space
    val colors = FolioTheme.colors
    val haptics = rememberHaptics()
    var presses by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(1) }
    val fraction = listOf(0.08f, 0.62f, 0.97f)[step]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = space.gutter, vertical = space.s16),
        verticalArrangement = Arrangement.spacedBy(space.s24)
    ) {
        Text(t("معرض المكونات", "Component gallery"), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)

        Section(t("الثيم", "Theme")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s8)) {
                Palette.entries.forEach { p -> FilterPill(selected = p == palette, onClick = { onPalette(p) }) { Text(p.name) } }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s8)) {
                FilterPill(selected = !dark, onClick = { onDark(false) }) { Text(t("فاتح", "Light")) }
                FilterPill(selected = dark, onClick = { onDark(true) }) { Text(t("داكن", "Dark")) }
            }
        }

        Section(t("البطاقة الرئيسية", "Hero card")) {
            HeroCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s12)) {
                    StarMedallion("29", onHero = true)
                    Column(Modifier.weight(1f)) {
                        Text(t("ورد اليوم", "Today's portion"), style = MaterialTheme.typography.headlineSmall)
                        Text(t("4 صفحات من الجزء 29", "4 pages of juz 29"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Box(Modifier.height(space.s16))
                PageStrip(fraction = fraction, track = colors.heroAccent.copy(alpha = 0.25f), color = colors.heroAccent, description = t("التقدم", "Progress"))
                Box(Modifier.height(space.s16))
                HeroButton(t("ابدأ المراجعة", "Start revision"), onClick = { presses++ }, icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth())
            }
        }

        Section(t("البطاقات", "Cards")) {
            Folio(modifier = Modifier.fillMaxWidth()) {
                Text(t("بطاقة ثابتة", "A plain card"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(t("تحمل محتوى مجمّعًا، ولا تُضغط.", "Holds grouped content; not pressable."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Folio(modifier = Modifier.fillMaxWidth(), onClick = { presses++ }) {
                Text(t("بطاقة تُضغط", "A pressable card"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(t("ضُغطت $presses مرات", "Pressed $presses times"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(space.s12)) {
                ActionTile(Icons.Rounded.Quiz, t("اختبار الآيات", "Verse quiz"), { presses++ }, Modifier.weight(1f).fillMaxHeight(), t("أكمل الآية من ذاكرتك", "Complete the verse from memory"))
                ActionTile(Icons.Rounded.Layers, t("المتشابهات", "Similar verses"), { presses++ }, Modifier.weight(1f).fillMaxHeight(), t("فرّق بين الآيات المتشابهة", "Tell similar verses apart"))
            }
            FolioGroup(Modifier.fillMaxWidth()) {
                ActionRow(Icons.Rounded.CalendarMonth, t("الخطة", "Plan"), { presses++ }, description = t("مقدار المراجعة اليومية", "How much to revise each day"))
                FolioDivider(inset = ActionRowInset)
                ActionRow(Icons.Rounded.Insights, t("التغطية", "Coverage"), { presses++ })
                FolioDivider(inset = ActionRowInset)
                ActionRow(Icons.Rounded.History, t("السجل", "History"), { presses++ }, description = t("كل ما راجعته", "Everything you revised"))
            }
        }

        Section(t("الأزرار", "Buttons")) {
            PrimaryButton(t("سجّل المراجعة", "Log revision"), { presses++ }, Modifier.fillMaxWidth(), icon = Icons.Rounded.MenuBook)
            TonalButton(t("اختر نطاقًا", "Choose a scope"), { presses++ }, Modifier.fillMaxWidth())
            PrimaryButton(t("غير متاح", "Not available"), {}, Modifier.fillMaxWidth(), enabled = false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s8)) {
                TextAction(t("عرض الكل", "See all"), { presses++ }, chevron = true)
                TextAction(t("لاحقًا", "Later"), { presses++ })
            }
        }

        Section(t("الشرائح", "Pills")) {
            var a by remember { mutableStateOf(true) }
            var b by remember { mutableStateOf(false) }
            var c by remember { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s8)) {
                FilterPill(a, { a = !a; haptics.tick() }) { Text(t("الكل", "All")) }
                FilterPill(b, { b = !b; haptics.tick() }) { Text(t("ضعيف", "Weak")) }
                FilterPill(c, { c = !c; haptics.tick() }) { Text(t("هذا الأسبوع", "This week")) }
            }
            ScopePill(t("الجزء 30", "Juz 30"), { presses++ })
        }

        Section(t("الأرقام", "Numbers")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s12), verticalArrangement = Arrangement.spacedBy(space.s12)) {
                StarMedallion("1")
                StarMedallion("18")
                StarMedallion("114")
                StarMedallion("604", size = 56.dp)
                IconMedallion(Icons.Rounded.ReportProblem)
            }
        }

        Section(t("التقدّم", "Progress")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s16)) {
                ProgressRing(fraction, description = t("التقدم", "Progress")) {
                    Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.s12)) {
                    PageStrip(fraction)
                    PageStrip(fraction, segments = 30)
                }
            }
            TonalButton(t("حرّك", "Animate"), { step = (step + 1) % 3 })
        }

        Section(t("الاهتزاز", "Haptics")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s8)) {
                TonalButton(t("تأكيد", "Confirm"), { haptics.confirm() })
                TonalButton(t("رفض", "Reject"), { haptics.reject() })
                TonalButton(t("نقرة", "Tick"), { haptics.tick() })
            }
        }

        Section(t("الألوان", "Colours")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s12), verticalArrangement = Arrangement.spacedBy(space.s12)) {
                val scheme = MaterialTheme.colorScheme
                Swatch("bg", scheme.background)
                Swatch("folio", colors.folio)
                Swatch("hero", colors.hero)
                Swatch("accent", colors.heroAccent)
                Swatch("tonal", colors.tonal)
                Swatch("primary", scheme.primary)
                Swatch("ink", scheme.onBackground)
                Swatch("soft", scheme.onSurfaceVariant)
                Swatch("edge", colors.edge)
            }
        }

        Section(t("الخطوط", "Type")) {
            val type = MaterialTheme.typography
            val ink = MaterialTheme.colorScheme.onBackground
            Text(t("متقن", "Mutqan"), style = type.displaySmall, color = ink)
            Text(t("ورد اليوم", "Today's portion"), style = type.headlineSmall, color = ink)
            Text(t("عنوان كبير", "Large title"), style = type.titleLarge, color = ink)
            Text(t("عنوان متوسط للبطاقة", "Medium title"), style = type.titleMedium, color = ink)
            Text(t("عنوان صغير", "Small title"), style = type.titleSmall, color = ink)
            Text(t("نص الفقرة الأساسي، بخط يُقرأ بسهولة في الحجم المعتاد.", "Body text at the usual size reads easily."), style = type.bodyLarge, color = ink)
            Text(t("نص ثانوي أصغر قليلًا لسطر الوصف.", "Secondary text a little smaller."), style = type.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t("ملاحظة صغيرة · 604 صفحة", "A small note · 604 pages"), style = type.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t("تسمية الزر", "Label"), style = type.labelLarge, color = ink)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FolioTheme.space.s12)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .background(color, RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
