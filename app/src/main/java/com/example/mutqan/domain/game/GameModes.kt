package com.example.mutqan.domain.game

/** The three ways to play «فوانيس». */
enum class GameMode {
    /** تدبّر: calm, nothing to lose, the meaning of each verse after it is built. */
    CALM,

    /** تحدّي: the levels of the map, with stars. */
    CHALLENGE,

    /** إتقان: the hardest, one heart, endless. */
    MASTERY
}

/** How the calm and the mastery rounds play (the challenge is the levels: [LanternsLevels]). */
object GameModes {

    /** A calm sitting: this many verses (a few minutes, a break), then what was built. */
    const val CALM_VERSES = 7

    /**
     * A slow, steady sky with the next word always up; no helpers, since nothing can be lost. The sky brings the word
     * wanted up quickly when it is not where it can be read yet, then rises calmly again: the verse goes at the reader's
     * pace, not the sky's.
     */
    val calmDials = Dials(speed = 0.06f, interval = 1.2f, sky = 4, helperEvery = NEVER, ahead = 1, catchUp = 4f)

    /** The share of wrong lanterns that are traps in a calm sitting: some, to think about, nothing to lose. */
    const val CALM_TRAPS = 0.35f

    /** No heart is lost and the round waits after each verse while its meaning is read. */
    val calmRules = RoundRules(forgiving = true, waitAfterVerse = true)

    /**
     * As fast as a late level and quicker with every right word in a row, six lanterns with the two words after the
     * wanted one among them, some wind, golden lanterns, and now and then an hourglass.
     */
    val masteryDials = Dials(
        speed = 0.17f,
        interval = 0.8f,
        sky = 6,
        helperEvery = 45f,
        helpersAtOnce = true,
        wind = 0.02f,
        golden = 0.1f,
        ahead = 2,
        flow = 0.05f
    )

    /** One heart: a single slip ends the run. */
    val masteryRules = RoundRules(lives = 1)

    /** The share of wrong lanterns that are traps (words that could come next, and near forms), where the word has some. */
    const val MASTERY_TRAPS = 0.95f

    /** The chance a verse with look-alikes is put before the others in an endless run. */
    const val MASTERY_FAVOUR = 0.5f

    /** Seconds the wanted word shows after a slip in a calm sitting. */
    const val CALM_HINT = 3f

    private const val NEVER = 1e9f
}
