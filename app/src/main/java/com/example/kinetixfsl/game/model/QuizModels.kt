package com.example.kinetixfsl.game.model

/**
 * Core data model for the Quiz Game.
 *
 * See `docs/QUIZ_GAME.md` for the full feature spec. In short: 10 levels split
 * across 3 sequential-unlock tiers, each tier drawing from its own fixed bank of
 * signs. Everything here is plain data — no Android or Compose imports — so it
 * stays trivially unit-testable and JSON-serialisable for offline persistence.
 */

/** How many signs are taught (tutorial) and then quizzed per level. */
const val SIGNS_PER_LEVEL = 5

/** Correct answers needed (out of [SIGNS_PER_LEVEL]) to pass a level. */
const val PASS_THRESHOLD = 4

/** Highest level in the game. */
const val MAX_LEVEL = 10

/**
 * The three difficulty tiers. Labels are internal logic only — never shown in
 * the UI (§2 of the spec). Each bank size is exactly `levels × SIGNS_PER_LEVEL`,
 * so the first playthrough of a tier uses every sign in its bank exactly once.
 */
enum class Tier(val levels: IntRange, val bankSize: Int) {
    EASY(1..3, 15),
    MEDIUM(4..6, 15),
    HARD(7..10, 20);

    companion object {
        fun forLevel(level: Int): Tier = entries.first { level in it.levels }
    }
}

/** A single sign, referenced by the quiz. [id] matches both the FslSignData sign
 *  id and the `res/raw` video filename (minus extension) — see SignVideoAssets. */
data class QuizSign(
    val id: String,
    val word: String,
    val categoryId: String,
)

/** The four question formats, one per mockup. */
enum class QuestionType {
    /** Show the target's video, pick the correct WORD from four. ("Ano ang tama dito?") */
    WORD_FROM_VIDEO,

    /** Given a word, pick the correct VIDEO from two. ("Alin dito ang sign na X?") */
    VIDEO_FROM_WORD,

    /** Match two videos to their two words. ("Pagtugmain ang salita") */
    MATCH,

    /** A video is claimed to be a word — is it true? ("Ito ba ay sign na X?" → Oo/Hindi) */
    TRUE_FALSE,
}

/**
 * One fully-resolved question. Flattened (nullable-ish fields per type rather
 * than a sealed hierarchy) so it serialises to/from JSON in one shape, which is
 * what lets us persist and resume the *exact* question mid-attempt (§6).
 *
 * Field usage by type:
 *  - WORD_FROM_VIDEO: [targetSignId] video shown; [wordOptions] shown; [correctWord].
 *  - VIDEO_FROM_WORD: [targetWord] prompt; [signOptions] videos shown; [correctSignId].
 *  - MATCH:           [signOptions] = left video column; [matchWords] = right word column.
 *                     A word matches a video when their words are equal.
 *  - TRUE_FALSE:      [targetSignId] video shown; [claimedWord] claim; [claimedIsCorrect] answer.
 */
data class QuizQuestion(
    val type: QuestionType,
    val targetSignId: String,
    val targetWord: String,
    val wordOptions: List<String> = emptyList(),
    val signOptions: List<String> = emptyList(),
    val matchWords: List<String> = emptyList(),
    val claimedWord: String = "",
    val claimedIsCorrect: Boolean = false,
    val correctWord: String = "",
    val correctSignId: String = "",
)

/**
 * Everything a single level attempt needs: the five signs taught in the tutorial
 * (in shown order) and the five questions (in presentation order). Persisted
 * wholesale so a resumed session replays byte-for-byte the same content.
 */
data class LevelPlan(
    val level: Int,
    val signIds: List<String>,
    val questions: List<QuizQuestion>,
) {
    val tier: Tier get() = Tier.forLevel(level)
}

/** Which half of a level attempt is active. */
enum class LevelPhase { VIDEO, QUIZ }

/** Unlock/clear state of a level, used to render the level map. */
enum class LevelStatus { LOCKED, UNLOCKED, PASSED }

/**
 * A short theme label per level for the roadmap UI. These are umbrella terms for
 * each tier's content (levels draw a random five from the whole tier bank, so
 * the labels are indicative rather than exact). Edit freely — display only.
 */
fun levelTitle(level: Int): String = when (level) {
    1 -> "Greetings & Social"
    2 -> "Numbers & Basics"
    3 -> "Everyday Signs"
    4 -> "Daily Needs"
    5 -> "School Words"
    6 -> "Letters & Numbers"
    7 -> "Emergency Signs"
    8 -> "Alphabet · A–G"
    9 -> "Alphabet · H–N"
    else -> "Alphabet · O–T"
}
