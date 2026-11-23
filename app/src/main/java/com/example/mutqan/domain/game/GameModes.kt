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

    /** A slow, steady sky with the next word always up; no helpers, since nothing can be lost. */
    val calmDials = Dials(speed = 0.06f, interval = 1.7f, sky = 3, helperEvery = NEVER, ahead = 1)

    /** No heart is lost and the round waits after each verse while its meaning is read. */
    val calmRules = RoundRules(forgiving = true, waitAfterVerse = true)

    /**
     * As fast as a late level and quicker with every right word in a row, five lanterns with the two words after the
     * wanted one among them, some wind, golden lanterns, and now and then an hourglass.
     */
    val masteryDials = Dials(
        speed = 0.17f,
        interval = 0.8f,
        sky = 5,
        helperEvery = 45f,
        helpersAtOnce = true,
        wind = 0.02f,
        golden = 0.1f,
        ahead = 2,
        flow = 0.05f
    )

    /** One heart: a single slip ends the run. */
    val masteryRules = RoundRules(lives = 1)

    /** The share of wrong lanterns that carry a look-alike word, where the verse has one. */
    const val MASTERY_LOOK_ALIKE = 0.8f

    /** Seconds the wanted word shows after a slip in a calm sitting. */
    const val CALM_HINT = 3f

    private const val NEVER = 1e9f
}
