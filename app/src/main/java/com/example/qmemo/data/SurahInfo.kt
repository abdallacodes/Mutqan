package com.example.qmemo.data

import java.util.Locale

/**
 * Static metadata for a single Surah.
 *
 * [nameEnglish] is the Arabic transliteration used in English contexts (e.g. "Al-Baqarah").
 * [nameArabic]  is the native Arabic script name (e.g. "البقرة").
 * [meaning]     is the English translation of the Surah's name (e.g. "The Cow").
 *
 * Call [getDisplayName] from any context to get the locale-appropriate name.
 */
data class SurahInfo(
    val id: Int,
    val nameEnglish: String,
    val nameArabic: String,
    val meaning: String
) {
    /**
     * Returns [nameArabic] when the device locale is Arabic, [nameEnglish] otherwise.
     * Safe to call from both Composable and non-Composable contexts; locale is read
     * at call-time so it is always correct after an activity-recreation triggered by
     * [AppCompatDelegate.setApplicationLocales].
     */
    fun getDisplayName(): String =
        if (Locale.getDefault().language == "ar") nameArabic else nameEnglish
}

/**
 * Static catalogue of all 114 Surahs.
 * Names and meanings are hardcoded (well-known, never change).
 * verseCount, startJuz, and startPage come from the live DB via [QuranDao.getSurahMetaList]
 * so they are always in sync with the pre-populated verses table.
 */
object SurahData {

    val ALL: List<SurahInfo> = listOf(
        SurahInfo(1,   "Al-Fatihah",     "الفاتحة",    "The Opening"),
        SurahInfo(2,   "Al-Baqarah",     "البقرة",     "The Cow"),
        SurahInfo(3,   "Ali 'Imran",     "آل عمران",   "Family of Imran"),
        SurahInfo(4,   "An-Nisa",        "النساء",     "The Women"),
        SurahInfo(5,   "Al-Ma'idah",     "المائدة",    "The Table Spread"),
        SurahInfo(6,   "Al-An'am",       "الأنعام",    "The Cattle"),
        SurahInfo(7,   "Al-A'raf",       "الأعراف",    "The Heights"),
        SurahInfo(8,   "Al-Anfal",       "الأنفال",    "The Spoils of War"),
        SurahInfo(9,   "At-Tawbah",      "التوبة",     "The Repentance"),
        SurahInfo(10,  "Yunus",          "يونس",       "Jonah"),
        SurahInfo(11,  "Hud",            "هود",        "Hud"),
        SurahInfo(12,  "Yusuf",          "يوسف",       "Joseph"),
        SurahInfo(13,  "Ar-Ra'd",        "الرعد",      "The Thunder"),
        SurahInfo(14,  "Ibrahim",        "إبراهيم",    "Abraham"),
        SurahInfo(15,  "Al-Hijr",        "الحجر",      "The Rocky Tract"),
        SurahInfo(16,  "An-Nahl",        "النحل",      "The Bee"),
        SurahInfo(17,  "Al-Isra",        "الإسراء",    "The Night Journey"),
        SurahInfo(18,  "Al-Kahf",        "الكهف",      "The Cave"),
        SurahInfo(19,  "Maryam",         "مريم",       "Mary"),
        SurahInfo(20,  "Ta-Ha",          "طه",         "Ta-Ha"),
        SurahInfo(21,  "Al-Anbiya",      "الأنبياء",   "The Prophets"),
        SurahInfo(22,  "Al-Hajj",        "الحج",       "The Pilgrimage"),
        SurahInfo(23,  "Al-Mu'minun",    "المؤمنون",   "The Believers"),
        SurahInfo(24,  "An-Nur",         "النور",      "The Light"),
        SurahInfo(25,  "Al-Furqan",      "الفرقان",    "The Criterion"),
        SurahInfo(26,  "Ash-Shu'ara",    "الشعراء",    "The Poets"),
        SurahInfo(27,  "An-Naml",        "النمل",      "The Ant"),
        SurahInfo(28,  "Al-Qasas",       "القصص",      "The Stories"),
        SurahInfo(29,  "Al-'Ankabut",    "العنكبوت",   "The Spider"),
        SurahInfo(30,  "Ar-Rum",         "الروم",      "The Romans"),
        SurahInfo(31,  "Luqman",         "لقمان",      "Luqman"),
        SurahInfo(32,  "As-Sajdah",      "السجدة",     "The Prostration"),
        SurahInfo(33,  "Al-Ahzab",       "الأحزاب",    "The Combined Forces"),
        SurahInfo(34,  "Saba",           "سبأ",        "Sheba"),
        SurahInfo(35,  "Fatir",          "فاطر",       "Originator"),
        SurahInfo(36,  "Ya-Sin",         "يس",         "Ya-Sin"),
        SurahInfo(37,  "As-Saffat",      "الصافات",    "Those Who Set The Ranks"),
        SurahInfo(38,  "Sad",            "ص",          "Sad"),
        SurahInfo(39,  "Az-Zumar",       "الزمر",      "The Troops"),
        SurahInfo(40,  "Ghafir",         "غافر",       "The Forgiver"),
        SurahInfo(41,  "Fussilat",       "فصلت",       "Explained in Detail"),
        SurahInfo(42,  "Ash-Shuraa",     "الشورى",     "The Consultation"),
        SurahInfo(43,  "Az-Zukhruf",     "الزخرف",     "The Ornaments of Gold"),
        SurahInfo(44,  "Ad-Dukhan",      "الدخان",     "The Smoke"),
        SurahInfo(45,  "Al-Jathiyah",    "الجاثية",    "The Crouching"),
        SurahInfo(46,  "Al-Ahqaf",       "الأحقاف",    "The Wind-Curved Sandhills"),
        SurahInfo(47,  "Muhammad",       "محمد",       "Muhammad"),
        SurahInfo(48,  "Al-Fath",        "الفتح",      "The Victory"),
        SurahInfo(49,  "Al-Hujurat",     "الحجرات",    "The Rooms"),
        SurahInfo(50,  "Qaf",            "ق",          "Qaf"),
        SurahInfo(51,  "Adh-Dhariyat",   "الذاريات",   "The Winnowing Winds"),
        SurahInfo(52,  "At-Tur",         "الطور",      "The Mount"),
        SurahInfo(53,  "An-Najm",        "النجم",      "The Star"),
        SurahInfo(54,  "Al-Qamar",       "القمر",      "The Moon"),
        SurahInfo(55,  "Ar-Rahman",      "الرحمن",     "The Beneficent"),
        SurahInfo(56,  "Al-Waqi'ah",     "الواقعة",    "The Inevitable"),
        SurahInfo(57,  "Al-Hadid",       "الحديد",     "The Iron"),
        SurahInfo(58,  "Al-Mujadila",    "المجادلة",   "The Pleading Woman"),
        SurahInfo(59,  "Al-Hashr",       "الحشر",      "The Exile"),
        SurahInfo(60,  "Al-Mumtahanah",  "الممتحنة",   "She That is to be Examined"),
        SurahInfo(61,  "As-Saf",         "الصف",       "The Ranks"),
        SurahInfo(62,  "Al-Jumu'ah",     "الجمعة",     "The Congregation, Friday"),
        SurahInfo(63,  "Al-Munafiqun",   "المنافقون",  "The Hypocrites"),
        SurahInfo(64,  "At-Taghabun",    "التغابن",    "The Mutual Disillusion"),
        SurahInfo(65,  "At-Talaq",       "الطلاق",     "The Divorce"),
        SurahInfo(66,  "At-Tahrim",      "التحريم",    "The Prohibition"),
        SurahInfo(67,  "Al-Mulk",        "الملك",      "The Sovereignty"),
        SurahInfo(68,  "Al-Qalam",       "القلم",      "The Pen"),
        SurahInfo(69,  "Al-Haqqah",      "الحاقة",     "The Reality"),
        SurahInfo(70,  "Al-Ma'arij",     "المعارج",    "The Ascending Stairways"),
        SurahInfo(71,  "Nuh",            "نوح",        "Noah"),
        SurahInfo(72,  "Al-Jinn",        "الجن",       "The Jinn"),
        SurahInfo(73,  "Al-Muzzammil",   "المزمل",     "The Enshrouded One"),
        SurahInfo(74,  "Al-Muddaththir", "المدثر",     "The Cloaked One"),
        SurahInfo(75,  "Al-Qiyamah",     "القيامة",    "The Resurrection"),
        SurahInfo(76,  "Al-Insan",       "الإنسان",    "The Human"),
        SurahInfo(77,  "Al-Mursalat",    "المرسلات",   "The Emissaries"),
        SurahInfo(78,  "An-Naba",        "النبأ",      "The Tidings"),
        SurahInfo(79,  "An-Nazi'at",     "النازعات",   "Those Who Drag Forth"),
        SurahInfo(80,  "'Abasa",         "عبس",        "He Frowned"),
        SurahInfo(81,  "At-Takwir",      "التكوير",    "The Overthrowing"),
        SurahInfo(82,  "Al-Infitar",     "الانفطار",   "The Cleaving"),
        SurahInfo(83,  "Al-Mutaffifin",  "المطففين",   "The Defrauding"),
        SurahInfo(84,  "Al-Inshiqaq",    "الانشقاق",   "The Sundering"),
        SurahInfo(85,  "Al-Buruj",       "البروج",     "The Mansions of the Stars"),
        SurahInfo(86,  "At-Tariq",       "الطارق",     "The Morning Star"),
        SurahInfo(87,  "Al-A'la",        "الأعلى",     "The Most High"),
        SurahInfo(88,  "Al-Ghashiyah",   "الغاشية",    "The Overwhelming"),
        SurahInfo(89,  "Al-Fajr",        "الفجر",      "The Dawn"),
        SurahInfo(90,  "Al-Balad",       "البلد",      "The City"),
        SurahInfo(91,  "Ash-Shams",      "الشمس",      "The Sun"),
        SurahInfo(92,  "Al-Layl",        "الليل",      "The Night"),
        SurahInfo(93,  "Ad-Duha",        "الضحى",      "The Morning Hours"),
        SurahInfo(94,  "Ash-Sharh",      "الشرح",      "The Relief"),
        SurahInfo(95,  "At-Tin",         "التين",      "The Fig"),
        SurahInfo(96,  "Al-'Alaq",       "العلق",      "The Clot"),
        SurahInfo(97,  "Al-Qadr",        "القدر",      "The Power"),
        SurahInfo(98,  "Al-Bayyinah",    "البينة",     "The Clear Proof"),
        SurahInfo(99,  "Az-Zalzalah",    "الزلزلة",    "The Earthquake"),
        SurahInfo(100, "Al-'Adiyat",     "العاديات",   "The Courser"),
        SurahInfo(101, "Al-Qari'ah",     "القارعة",    "The Calamity"),
        SurahInfo(102, "At-Takathur",    "التكاثر",    "The Rivalry in World Increase"),
        SurahInfo(103, "Al-'Asr",        "العصر",      "The Declining Day"),
        SurahInfo(104, "Al-Humazah",     "الهمزة",     "The Traducer"),
        SurahInfo(105, "Al-Fil",         "الفيل",      "The Elephant"),
        SurahInfo(106, "Quraysh",        "قريش",       "Quraysh"),
        SurahInfo(107, "Al-Ma'un",       "الماعون",    "The Small Kindnesses"),
        SurahInfo(108, "Al-Kawthar",     "الكوثر",     "The Abundance"),
        SurahInfo(109, "Al-Kafirun",     "الكافرون",   "The Disbelievers"),
        SurahInfo(110, "An-Nasr",        "النصر",      "The Divine Support"),
        SurahInfo(111, "Al-Masad",       "المسد",      "The Palm Fiber"),
        SurahInfo(112, "Al-Ikhlas",      "الإخلاص",    "The Sincerity"),
        SurahInfo(113, "Al-Falaq",       "الفلق",      "The Daybreak"),
        SurahInfo(114, "An-Nas",         "الناس",      "Mankind"),
    )

    private val byId: Map<Int, SurahInfo> = ALL.associateBy { it.id }

    fun getById(id: Int): SurahInfo? = byId[id]

    /** Returns the locale-aware Surah name: Arabic script for "ar", transliteration otherwise. */
    fun nameOf(id: Int): String = byId[id]?.getDisplayName() ?: "Surah $id"
}
