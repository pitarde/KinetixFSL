package com.example.kinetixfsl.profile

import com.example.kinetixfsl.modules.model.FslSignData
import com.example.kinetixfsl.progress.PlayerProgress
import com.example.kinetixfsl.progress.ProgressRepository
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * All the real, computed values the four analytics tabs need — descriptive
 * (Progress), diagnostic (Weak Spots), predictive (Forecast), and prescriptive
 * (Coach's Picks). Built from the activity log + XP snapshot by [computeAnalytics].
 */
data class AnalyticsData(
    // Progress
    val weeklyBars: List<DayBar>,
    val categoryMastery: List<CategoryMastery>,
    val heatmap: List<List<Int>>,
    val studyMinutes: Int,
    // Weak Spots
    val confusionPairs: List<ConfusionPair>,
    val dropOffs: List<ModuleDropOff>,
    val errorBreakdown: ErrorBreakdown,
    // Forecast
    val forecastActual: List<Int>,
    val forecastProjected: List<Int>,
    val streakRiskPercent: Int,
    val dropOffWindow: String,
    val decayWatch: List<DecayItem>,
    // Coach's Picks
    val recommendedQueue: List<QueueItem>,
    val fixWeakSpotStat: String,
    val fixWeakSpotCta: String,
    val bestTimeInsight: String,
    val adaptiveLessons: List<AdaptiveItem>,
)

private val signDynamic: Map<String, Boolean> =
    FslSignData.categories.flatMap { it.signs }.associate { it.id to it.isDynamic }
private val signName: Map<String, String> =
    FslSignData.categories.flatMap { it.signs }.associate { it.id to it.name }
private val categoryTitle: Map<String, String> =
    FslSignData.categories.associate { it.id to it.title }

/**
 * Computes the analytics snapshot from a [ProgressRepository]'s activity log and
 * an XP [progress] snapshot. Empty/early-user data degrades gracefully to
 * sensible placeholders rather than crashing or lying.
 */
fun computeAnalytics(repo: ProgressRepository, progress: PlayerProgress): AnalyticsData {
    val log = repo.activityLog()
    val today = LocalDate.now()
    val todayEpoch = today.toEpochDay()

    // ── Progress: weekly bars (last 7 days) ─────────────────────────────
    val weeklyBars = (6 downTo 0).map { back ->
        val d = today.minusDays(back.toLong())
        DayBar(d.dayOfWeek.name.take(3), log.dailyLearned[d.toEpochDay()] ?: 0)
    }

    // ── Progress: category mastery (from XP snapshot) ───────────────────
    val categoryMastery = progress.categories.map { CategoryMastery(it.name, it.fraction) }

    // ── Progress: 5-week practice heatmap (Mon..Sun) ────────────────────
    val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val heatmap = (4 downTo 0).map { w ->
        (0..6).map { dow ->
            intensity(thisMonday.minusWeeks(w.toLong()).plusDays(dow.toLong()).toEpochDay(), log)
        }
    }

    val studyMinutes = (log.dailyStudySeconds.values.sum() / 60).toInt()

    // ── Weak Spots: confusion pairs (unordered, by frequency) ───────────
    val totalMistakes = log.mistakes.size
    val confusionPairs = log.mistakes
        .groupingBy { listOf(it.targetWord, it.chosenWord).sorted() }
        .eachCount()
        .entries.sortedByDescending { it.value }
        .take(4)
        .map { (pair, count) ->
            ConfusionPair(pair[0], pair[1], if (totalMistakes > 0) count * 100 / totalMistakes else 0)
        }

    // ── Weak Spots: module drop-off (started vs completed) ──────────────
    val dropOffs = log.lessonsStarted.entries
        .sortedByDescending { it.value }
        .take(4)
        .map { (catId, started) ->
            ModuleDropOff(
                module = categoryTitle[catId] ?: catId,
                started = started,
                completed = min(started, log.lessonsCompleted[catId] ?: 0),
            )
        }

    // ── Weak Spots: error breakdown from real Camera-Practice failures ──
    // handshape = you formed a different sign; motion = hand not tracked;
    // timing = close but ran out of time.
    val hand = log.cameraErrors["handshape"] ?: 0
    val motion = log.cameraErrors["motion"] ?: 0
    val timing = log.cameraErrors["timing"] ?: 0
    val errTotal = hand + motion + timing
    val errorBreakdown = if (errTotal <= 0) ErrorBreakdown(0f, 0f, 0f)
    else ErrorBreakdown(hand.toFloat() / errTotal, motion.toFloat() / errTotal, timing.toFloat() / errTotal)

    // ── Forecast: cumulative signs learned + linear projection ──────────
    val weekNew = (3 downTo 0).map { w ->
        val start = thisMonday.minusWeeks(w.toLong())
        (0..6).sumOf { log.dailyLearned[start.plusDays(it.toLong()).toEpochDay()] ?: 0 }
    }
    val total = progress.signsLearned
    var running = max(0, total - weekNew.sum())
    val forecastActual = weekNew.map { running += it; running }
    val slope = if (weekNew.isNotEmpty()) weekNew.average() else 0.0
    val lastActual = forecastActual.lastOrNull() ?: total
    val forecastProjected = (0..3).map { (lastActual + slope * it).roundToInt() }

    // ── Forecast: streak risk (missed days in the last 14) ──────────────
    val missed = (0..13).count { today.minusDays(it.toLong()).toEpochDay() !in log.activeDays }
    val streakRiskPercent = (missed * 100 / 14).coerceIn(0, 100)
    val dropOffWindow = worstWeekday(log.activeDays, today)

    // ── Forecast: skill decay (days since last practice) ────────────────
    val decayWatch = log.signLastPracticed.entries
        .map { (id, day) ->
            val daysSince = (todayEpoch - day).toInt().coerceAtLeast(0)
            DecayItem(signName[id] ?: id, daysSince, min(1f, daysSince / 14f))
        }
        .sortedByDescending { it.risk }
        .take(4)

    // ── Coach's Picks: recommended queue ────────────────────────────────
    val weakestCategory = progress.categories.filter { !it.complete }.minByOrNull { it.fraction }
    val recommendedQueue = buildList {
        decayWatch.firstOrNull()?.let {
            add(QueueItem("Review: ${it.sign}", "Highest forget-risk — ${it.daysSince} days since practice"))
        }
        confusionPairs.firstOrNull()?.let {
            add(QueueItem("Drill: ${it.first} vs ${it.second}", "Your most-confused pair at ${it.confusedPercent}%"))
        }
        weakestCategory?.let {
            add(QueueItem("Finish: ${it.name}", "${it.learned} of ${it.total} items done"))
        }
    }.ifEmpty {
        listOf(QueueItem("Start a lesson", "Practice a few signs to build your plan"))
    }

    val topPair = confusionPairs.firstOrNull()
    val fixWeakSpotStat = topPair
        ?.let { "You confuse ${it.first} and ${it.second} ${it.confusedPercent}% of the time." }
        ?: "No clear weak spot yet — keep practicing to reveal one."
    val fixWeakSpotCta = if (topPair != null) "Start 3-min drill" else "Start practice"

    // Best time from real quiz accuracy per hour ("when you perform best").
    val bestTimeInsight = bestTimeByAccuracy(log.hourCorrect, log.hourAttempts)

    // Adaptive difficulty from real mastery + recent quiz accuracy per category.
    fun catAccuracy(id: String): Float? {
        val a = log.categoryAttempts[id] ?: 0
        return if (a > 0) (log.categoryCorrect[id] ?: 0).toFloat() / a else null
    }
    val sortedCats = progress.categories.sortedByDescending { it.fraction }
    val adaptiveLessons = buildList {
        sortedCats.firstOrNull { it.fraction > 0f }?.let {
            val acc = catAccuracy(it.id)
            val accTxt = acc?.let { a -> ", ${(a * 100).roundToInt()}% accuracy" } ?: ""
            add(AdaptiveItem(it.name, Trend.UP, "${(it.fraction * 100).roundToInt()}% mastery$accTxt — harder rounds"))
        }
        sortedCats.lastOrNull { it.fraction < 0.6f }?.let {
            val acc = catAccuracy(it.id)
            val accTxt = acc?.let { a -> "${(a * 100).roundToInt()}% accuracy — " } ?: ""
            add(AdaptiveItem(it.name, Trend.DOWN, "${accTxt}easing off with guided reps"))
        }
    }

    return AnalyticsData(
        weeklyBars = weeklyBars,
        categoryMastery = categoryMastery,
        heatmap = heatmap,
        studyMinutes = studyMinutes,
        confusionPairs = confusionPairs,
        dropOffs = dropOffs,
        errorBreakdown = errorBreakdown,
        forecastActual = forecastActual,
        forecastProjected = forecastProjected,
        streakRiskPercent = streakRiskPercent,
        dropOffWindow = dropOffWindow,
        decayWatch = decayWatch,
        recommendedQueue = recommendedQueue,
        fixWeakSpotStat = fixWeakSpotStat,
        fixWeakSpotCta = fixWeakSpotCta,
        bestTimeInsight = bestTimeInsight,
        adaptiveLessons = adaptiveLessons,
    )
}

private fun intensity(day: Long, log: com.example.kinetixfsl.progress.ActivityLogState): Int {
    val active = day in log.activeDays
    if (!active) return 0
    val minutes = (log.dailyStudySeconds[day] ?: 0L) / 60
    val learned = log.dailyLearned[day] ?: 0
    return when {
        minutes >= 30 || learned >= 4 -> 4
        minutes >= 15 || learned >= 3 -> 3
        minutes >= 5 || learned >= 2 -> 2
        else -> 1
    }
}

/** The weekday the user most often skips, over the last 5 weeks. */
private fun worstWeekday(activeDays: Set<Long>, today: LocalDate): String {
    val totals = IntArray(7); val actives = IntArray(7)
    for (back in 0..34) {
        val d = today.minusDays(back.toLong())
        val idx = d.dayOfWeek.value - 1
        totals[idx]++
        if (d.toEpochDay() in activeDays) actives[idx]++
    }
    var worst = -1; var worstRatio = -1.0
    for (i in 0..6) {
        if (totals[i] < 2) continue
        val skip = 1.0 - actives[i].toDouble() / totals[i]
        if (skip > worstRatio) { worstRatio = skip; worst = i }
    }
    if (worst < 0 || worstRatio <= 0.0) return "no clear pattern yet"
    return DayOfWeek.of(worst + 1).name.lowercase().replaceFirstChar { it.uppercase() } + "s"
}

/** "You score best around 8 PM (82% correct)" — the hour with the highest quiz
 *  accuracy, among hours with enough answers to be meaningful. */
private fun bestTimeByAccuracy(correct: List<Int>, attempts: List<Int>): String {
    val candidates = (0..23).filter { (attempts.getOrNull(it) ?: 0) >= 3 }
    if (candidates.isEmpty()) return "Take a few more quizzes to reveal when you perform best."
    val best = candidates.maxByOrNull { correct[it].toFloat() / attempts[it] } ?: return ""
    val acc = correct[best] * 100 / attempts[best]
    val ampm = if (best < 12) "AM" else "PM"
    val h12 = when (val h = best % 12) { 0 -> 12; else -> h }
    return "You score best around $h12 $ampm ($acc% correct) — a great time to practice."
}
