package com.example.kinetixfsl.community.moderator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.CommunityRepository
import com.example.kinetixfsl.community.model.Post
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** One bar in the "engagement per post" chart — chronological, oldest first. */
data class PostPoint(val label: String, val score: Long, val upvotes: Long)

/** "What happened" — the raw totals, plus the series the bar chart draws. */
data class DescriptiveStats(
    val postCount: Int,
    val totalViews: Long,
    val totalUpvotes: Long,
    val totalDownvotes: Long,
    val totalComments: Long,
    val totalShares: Long,
    val netScore: Long,
    /** Average of (upvotes+comments+shares)/views across posts, 0f..1f+. */
    val avgEngagementRate: Float,
    val postSeries: List<PostPoint>,
)

/** Image-vs-video performance — backs a paired bar chart. */
data class MediaComparison(
    val imageAvgScore: Double,
    val videoAvgScore: Double,
    val imageCount: Int,
    val videoCount: Int,
)

/** One destination's (community, or Home Feed) average performance. */
data class CommunityStat(val name: String, val avgScore: Double, val postCount: Int)

/** How many posts carry at least one #hashtag — backs a completed/total bar. */
data class HashtagCoverage(val tagged: Int, val total: Int)

/** "What's likely next" — a two-half trend comparison plus a run-rate projection. */
data class PredictiveData(
    /** Cumulative upvotes over time, oldest first — the chart's solid line. */
    val actualSeries: List<Long>,
    /** Continuation points (including the last actual point as the anchor) — the dashed line. */
    val projectedSeries: List<Long>,
    val trendUp: Boolean,
    val recentAvgScore: Double,
    val earlierAvgScore: Double,
    val projectedUpvotes30d: Int,
)

sealed class ModeratorAnalyticsUiState {
    object Loading : ModeratorAnalyticsUiState()
    data class Ready(
        val hasContent: Boolean,
        val asOfDate: String,
        val descriptive: DescriptiveStats,
        val mediaComparison: MediaComparison?,
        val communityBreakdown: List<CommunityStat>,
        val hashtagCoverage: HashtagCoverage?,
        val predictive: PredictiveData?,
        val recommendations: List<String>,
        val topPosts: List<Post>,
    ) : ModeratorAnalyticsUiState()
}

/**
 * Backs the moderator's own content-analytics view — reached from
 * [EligibilityScreen] once the user is an approved moderator, in the spirit of
 * YouTube Studio's Analytics tab.
 *
 * Deliberately scoped to posts created ON OR AFTER
 * [com.example.kinetixfsl.community.model.UserProfile.moderatorSince] that are
 * actually validated: pre-moderator content was never held to the
 * auto-validation guarantee, so mixing it in would credit the moderator
 * program for engagement it had nothing to do with.
 */
class ModeratorAnalyticsViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<ModeratorAnalyticsUiState>(ModeratorAnalyticsUiState.Loading)
    val uiState: StateFlow<ModeratorAnalyticsUiState> = _uiState.asStateFlow()

    init {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            combine(
                repository.postsByAuthor(uid),
                repository.observeUserProfile(uid),
            ) { posts, profile ->
                val moderatorSinceMillis = profile?.moderatorSince?.toDate()?.time
                val eligible = if (moderatorSinceMillis == null) {
                    emptyList()
                } else {
                    posts.filter { post ->
                        post.isValidated && (post.createdAt?.toDate()?.time ?: 0L) >= moderatorSinceMillis
                    }.sortedBy { it.createdAt?.toDate()?.time ?: 0L }
                }
                buildUiState(eligible, moderatorSinceMillis)
            }.onEach { _uiState.value = it }.launchIn(viewModelScope)
        }
    }

    private fun buildUiState(posts: List<Post>, moderatorSinceMillis: Long?): ModeratorAnalyticsUiState.Ready {
        val mediaComparison = mediaComparison(posts)
        val communityBreakdown = communityBreakdown(posts)
        val hashtagCoverage = if (posts.isEmpty()) null else HashtagCoverage(posts.count { it.hashtags.isNotEmpty() }, posts.size)
        val predictive = predictiveData(posts, moderatorSinceMillis)
        val recommendations = recommendations(posts, mediaComparison, communityBreakdown, hashtagCoverage, predictive)
        val topPosts = posts.sortedByDescending { it.score }.take(3)

        return ModeratorAnalyticsUiState.Ready(
            hasContent = posts.isNotEmpty(),
            asOfDate = "Showing data as of: ${todayFormatted()}",
            descriptive = descriptiveStats(posts),
            mediaComparison = mediaComparison,
            communityBreakdown = communityBreakdown,
            hashtagCoverage = hashtagCoverage,
            predictive = predictive,
            recommendations = recommendations,
            topPosts = topPosts,
        )
    }

    private fun descriptiveStats(posts: List<Post>): DescriptiveStats {
        if (posts.isEmpty()) {
            return DescriptiveStats(0, 0, 0, 0, 0, 0, 0, 0f, emptyList())
        }
        val totalViews = posts.sumOf { it.viewCount }
        val totalUpvotes = posts.sumOf { it.upvoteCount }
        val totalDownvotes = posts.sumOf { it.downvoteCount }
        val totalComments = posts.sumOf { it.commentCount }
        val totalShares = posts.sumOf { it.shareCount }
        val avgEngagement = posts.map { post ->
            val engaged = (post.upvoteCount + post.commentCount + post.shareCount).toFloat()
            val views = post.viewCount.coerceAtLeast(1)
            engaged / views
        }.average().toFloat()

        // Last 8 posts, chronological — enough to read as a trend without the
        // bars becoming hairlines on a phone screen.
        val series = posts.takeLast(8).mapIndexed { index, post ->
            PostPoint(label = "${index + 1}", score = post.score, upvotes = post.upvoteCount)
        }

        return DescriptiveStats(
            postCount = posts.size,
            totalViews = totalViews,
            totalUpvotes = totalUpvotes,
            totalDownvotes = totalDownvotes,
            totalComments = totalComments,
            totalShares = totalShares,
            netScore = totalUpvotes - totalDownvotes,
            avgEngagementRate = avgEngagement,
            postSeries = series,
        )
    }

    private fun mediaComparison(posts: List<Post>): MediaComparison? {
        val videoPosts = posts.filter { post -> post.mediaItems.any { it.isVideo } }
        val imagePosts = posts.filter { post -> post.mediaItems.isNotEmpty() && post.mediaItems.none { it.isVideo } }
        if (videoPosts.isEmpty() || imagePosts.isEmpty()) return null
        return MediaComparison(
            imageAvgScore = imagePosts.map { it.score }.average(),
            videoAvgScore = videoPosts.map { it.score }.average(),
            imageCount = imagePosts.size,
            videoCount = videoPosts.size,
        )
    }

    private fun communityBreakdown(posts: List<Post>): List<CommunityStat> {
        val byCommunity = posts.groupBy { it.communityName.ifBlank { "Home Feed" } }
        if (byCommunity.size < 2) return emptyList()
        return byCommunity.map { (name, list) ->
            CommunityStat(name = name, avgScore = list.map { it.score }.average(), postCount = list.size)
        }.sortedByDescending { it.avgScore }
    }

    private fun predictiveData(posts: List<Post>, moderatorSinceMillis: Long?): PredictiveData? {
        if (posts.size < 4 || moderatorSinceMillis == null) return null

        var running = 0L
        val actualSeries = posts.map { running += it.upvoteCount; running }

        val mid = posts.size / 2
        val earlierAvg = posts.subList(0, mid).map { it.score }.average()
        val recentAvg = posts.subList(mid, posts.size).map { it.score }.average()

        val daysActive = TimeUnit.MILLISECONDS.toDays(
            (System.currentTimeMillis() - moderatorSinceMillis).coerceAtLeast(TimeUnit.DAYS.toMillis(1)),
        )
        val perDay = posts.sumOf { it.upvoteCount }.toDouble() / daysActive
        val projectedUpvotes30d = (perDay * 30).toInt()

        // Three projected points continuing from the last actual total, evenly
        // spaced — just enough for a dashed line to read as "the road ahead".
        val projectedSeries = listOf(
            actualSeries.last(),
            actualSeries.last() + (perDay * 10).toLong(),
            actualSeries.last() + (perDay * 20).toLong(),
            actualSeries.last() + (perDay * 30).toLong(),
        )

        return PredictiveData(
            actualSeries = actualSeries,
            projectedSeries = projectedSeries,
            trendUp = recentAvg >= earlierAvg,
            recentAvgScore = recentAvg,
            earlierAvgScore = earlierAvg,
            projectedUpvotes30d = projectedUpvotes30d,
        )
    }

    private fun recommendations(
        posts: List<Post>,
        media: MediaComparison?,
        communities: List<CommunityStat>,
        hashtags: HashtagCoverage?,
        predictive: PredictiveData?,
    ): List<String> {
        if (posts.isEmpty()) return emptyList()
        val recs = mutableListOf<String>()

        media?.let {
            if (it.videoAvgScore > it.imageAvgScore) {
                recs += "Lean into video — it's earning more engagement than your image posts."
            } else if (it.imageAvgScore > it.videoAvgScore) {
                recs += "Your image posts are outperforming video — keep prioritizing clear photo demonstrations."
            }
        }
        communities.firstOrNull()?.let {
            recs += "Post more in \"${it.name}\" — it's where your audience engages most."
        }
        hashtags?.let {
            if (it.tagged < it.total) {
                recs += "Add #hashtags to every tutorial so it surfaces in Text-to-Sign search."
            }
        }
        if (predictive != null && !predictive.trendUp) {
            recs += "Engagement has cooled recently — try posting at a steadier pace to rebuild momentum."
        }
        if (posts.size < 4) {
            recs += "Post a few more times as a moderator to unlock deeper trend insights."
        }
        if (recs.isEmpty()) {
            recs += "Keep up your current posting habits — your content is performing consistently."
        }
        return recs
    }
}

private fun todayFormatted(): String =
    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(java.util.Date())
