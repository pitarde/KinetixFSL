package com.example.kinetixfsl.game.data

import com.example.kinetixfsl.game.model.LevelPlan
import com.example.kinetixfsl.game.model.QuestionType
import com.example.kinetixfsl.game.model.QuizQuestion
import com.example.kinetixfsl.game.model.QuizSign
import com.example.kinetixfsl.game.model.SIGNS_PER_LEVEL
import com.example.kinetixfsl.game.model.Tier

/**
 * Builds level content: which five signs a level teaches, and the five questions
 * that follow. Implements the three selection modes from §4–5 of the spec.
 */
object QuizGenerator {

    /**
     * (A) First-time assignment during a tier's initial unlock pass. Enforces
     * no-repeat across that tier's levels by excluding anything already handed to
     * an earlier level this pass, then records what it picked.
     */
    fun assignFirstTimeSigns(
        tierBank: List<QuizSign>,
        usedInInitialPass: MutableSet<String>,
    ): List<QuizSign> {
        val available = tierBank.filterNot { it.id in usedInInitialPass }
        // If the pass somehow ran dry (shouldn't, banks are sized exactly), fall
        // back to the whole bank so a level is never left short of five signs.
        val pool = available.ifEmpty { tierBank }
        val picked = pool.shuffled().take(SIGNS_PER_LEVEL)
        usedInInitialPass += picked.map { it.id }
        return picked
    }

    /** (B) Voluntary replay of an already-cleared level — fresh random five from
     *  the whole tier pool, independent of other levels. */
    fun assignReplaySigns(tierBank: List<QuizSign>): List<QuizSign> =
        tierBank.shuffled().take(SIGNS_PER_LEVEL)

    /**
     * Builds a full [LevelPlan] for [level] from its five [signs], drawing
     * distractors from [tierBank]. Each of the five signs becomes one question;
     * the four question types are spread across the five slots (all four appear,
     * one repeats) and shuffled so a level never feels formulaic.
     */
    fun buildPlan(level: Int, signs: List<QuizSign>, tierBank: List<QuizSign>): LevelPlan {
        val allTypes = QuestionType.entries
        val typeOrder = (allTypes + allTypes.random()).shuffled()
        val questions = signs.mapIndexed { index, target ->
            buildQuestion(typeOrder[index], target, tierBank)
        }
        return LevelPlan(level = level, signIds = signs.map { it.id }, questions = questions)
    }

    /** (§5) Retry after a fail: same five questions, presentation reshuffled — both
     *  the order of questions and the option order within each. */
    fun reshuffleForRetry(plan: LevelPlan): LevelPlan =
        plan.copy(questions = plan.questions.map { reshuffleOptions(it) }.shuffled())

    // ── internals ────────────────────────────────────────────────────────────

    private fun buildQuestion(
        type: QuestionType,
        target: QuizSign,
        bank: List<QuizSign>,
    ): QuizQuestion {
        val others = bank.filter { it.word != target.word }
        return when (type) {
            QuestionType.WORD_FROM_VIDEO -> {
                val distractors = others.map { it.word }.distinct().shuffled().take(3)
                QuizQuestion(
                    type = type,
                    targetSignId = target.id,
                    targetWord = target.word,
                    wordOptions = (distractors + target.word).shuffled(),
                    correctWord = target.word,
                )
            }

            QuestionType.VIDEO_FROM_WORD -> {
                val distractor = others.shuffled().first()
                QuizQuestion(
                    type = type,
                    targetSignId = target.id,
                    targetWord = target.word,
                    signOptions = listOf(target.id, distractor.id).shuffled(),
                    correctSignId = target.id,
                )
            }

            QuestionType.MATCH -> {
                val other = others.shuffled().first()
                QuizQuestion(
                    type = type,
                    targetSignId = target.id,
                    targetWord = target.word,
                    signOptions = listOf(target.id, other.id).shuffled(),
                    matchWords = listOf(target.word, other.word).shuffled(),
                )
            }

            QuestionType.TRUE_FALSE -> {
                val claimTrue = kotlin.random.Random.nextBoolean()
                val claimed = if (claimTrue) target.word else others.shuffled().first().word
                QuizQuestion(
                    type = type,
                    targetSignId = target.id,
                    targetWord = target.word,
                    claimedWord = claimed,
                    claimedIsCorrect = claimTrue,
                )
            }
        }
    }

    private fun reshuffleOptions(q: QuizQuestion): QuizQuestion = when (q.type) {
        QuestionType.WORD_FROM_VIDEO -> q.copy(wordOptions = q.wordOptions.shuffled())
        QuestionType.VIDEO_FROM_WORD -> q.copy(signOptions = q.signOptions.shuffled())
        QuestionType.MATCH -> q.copy(
            signOptions = q.signOptions.shuffled(),
            matchWords = q.matchWords.shuffled(),
        )
        QuestionType.TRUE_FALSE -> q
    }
}
