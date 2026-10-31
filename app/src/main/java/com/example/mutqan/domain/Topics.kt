package com.example.mutqan.domain

import android.content.Context
import com.example.mutqan.quran.ArabicNormalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A topic (concept) of the Quran with the verses it is mentioned in. [name] is English; [arabic] is empty when
 * QUL has no Arabic name for it. [verses] are global ayah ids in reading order.
 */
class Topic(
    val id: Int,
    val name: String,
    val arabic: String,
    val thematicParent: Int,
    val ontologyParent: Int,
    val parent: Int,
    val description: String,
    val related: IntArray,
    val verses: IntArray
) {
    val normalizedArabic: String by lazy { ArabicNormalization.normalizeForSearch(arabic) }
}

/** Which names the interface can show. QUL names about a third of its topics in Arabic. */
data class TopicLanguage(val arabicUi: Boolean, val showAll: Boolean) {

    /** True when the topic may be listed: always in English; in Arabic only with an Arabic name, unless all are shown. */
    fun visible(topic: Topic): Boolean = !arabicUi || showAll || topic.arabic.isNotEmpty()

    /** The name in the interface language, falling back to English when there is no Arabic name. */
    fun title(topic: Topic): String = if (arabicUi && topic.arabic.isNotEmpty()) topic.arabic else topic.name

    /** A second name worth showing under the first: the Arabic one in an English interface. */
    fun secondary(topic: Topic): String? = if (!arabicUi && topic.arabic.isNotEmpty()) topic.arabic else null
}

/**
 * The bundled topics (`assets/topics.json`, built by `scripts/build_topics.py`). Topics form three overlapping
 * hierarchies (thematic, ontology and a general parent); [childrenOf] joins them.
 */
class TopicIndex(val topics: List<Topic>) {
    private val byId: Map<Int, Topic> = topics.associateBy { it.id }

    operator fun get(id: Int): Topic? = byId[id]

    private val children: Map<Int, List<Topic>> by lazy {
        val map = HashMap<Int, MutableList<Topic>>()
        for (t in topics) {
            for (p in setOf(t.thematicParent, t.ontologyParent, t.parent)) {
                if (p != -1) map.getOrPut(p) { ArrayList() } += t
            }
        }
        map
    }

    fun childrenOf(id: Int): List<Topic> = children[id].orEmpty()

    /** The tops of the thematic tree (Doctrine, Stories, The Unseen). */
    val themeRoots: List<Topic> by lazy {
        topics.filter { t -> t.thematicParent == -1 && topics.any { it.thematicParent == t.id } }
    }

    /** The tops of the ontology (Allah, Event, Location ...). */
    val conceptRoots: List<Topic> by lazy {
        topics.filter { t -> t.ontologyParent == -1 && t.thematicParent == -1 && topics.any { it.ontologyParent == t.id } }
    }

    private val topicsOfVerse: Map<Int, IntArray> by lazy {
        val map = HashMap<Int, MutableList<Int>>()
        for (t in topics) for (v in t.verses) map.getOrPut(v) { ArrayList() } += t.id
        map.mapValues { it.value.toIntArray() }
    }

    /** The topics a verse (by global ayah id) is listed under, most specific (fewest verses) first. */
    fun topicsOf(ayahId: Int): List<Topic> =
        (topicsOfVerse[ayahId] ?: IntArray(0)).toList().mapNotNull(byId::get).sortedBy { it.verses.size }

    /** Topics whose name contains [query] (case-insensitive English, or Arabic ignoring diacritics), widest first. */
    fun search(query: String, language: TopicLanguage, limit: Int): List<Topic> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val arabic = ArabicNormalization.normalizeForSearch(q)
        return topics
            .filter { language.visible(it) && it.verses.isNotEmpty() }
            .filter { it.name.contains(q, ignoreCase = true) || (arabic.isNotEmpty() && it.normalizedArabic.contains(arabic)) }
            .sortedByDescending { it.verses.size }
            .take(limit)
    }

    /** The most mentioned topics that can be listed. */
    fun popular(language: TopicLanguage, limit: Int): List<Topic> =
        topics.filter { language.visible(it) && it.verses.isNotEmpty() && it.id !in rootIds }
            .sortedByDescending { it.verses.size }
            .take(limit)

    private val rootIds: Set<Int> by lazy { (themeRoots + conceptRoots).map { it.id }.toSet() }
}

/** Loads `topics.json` once. */
object TopicsLibrary {
    private const val ASSET = "topics.json"

    @Volatile
    private var cache: TopicIndex? = null

    suspend fun load(context: Context): TopicIndex {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val json = context.applicationContext.assets.open(ASSET).use { it.readBytes().decodeToString() }
            val array = JSONObject(json).getJSONArray("t")
            fun ints(a: JSONArray): IntArray = IntArray(a.length()) { a.getInt(it) }
            TopicIndex(
                List(array.length()) { i ->
                    val o = array.getJSONArray(i)
                    Topic(
                        id = o.getInt(0), name = o.getString(1), arabic = o.getString(2),
                        thematicParent = o.getInt(3), ontologyParent = o.getInt(4), parent = o.getInt(5),
                        description = o.getString(6), related = ints(o.getJSONArray(7)), verses = ints(o.getJSONArray(8))
                    )
                }
            ).also { cache = it }
        }
    }
}
