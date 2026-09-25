# Implementation prompt: Community Moderator Eligibility

Paste this whole file as the prompt when you're ready to implement. It is self-contained — it already names the exact files, existing patterns, and field names in both repos so no extra back-and-forth exploration is needed.

---

## Goal

Add a **"Become a Moderator"** program to the Community module. A regular user who hits four activity thresholds can apply to become a moderator. Once an admin approves the application, that user's posts are **auto-validated** going forward (they skip the normal admin validation queue), and a **Moderator badge** appears on their community profile.

This has three parts:
1. **Mobile app (`Kinetix-FSL`)** — a new "Eligibility" screen with YouTube-Studio-style progress bars, reachable from the side drawer and from the bottom of Discover Communities.
2. **Firestore data model** — new fields + a new collection to track applications.
3. **Admin console (`Kinetix-FSL-Admin`)** — a new review queue, mirroring the existing Validation/Reports pages exactly.

Reference image for the Eligibility screen's visual language: YouTube Studio's monetization eligibility screen — a "Showing data as of: <date>" info card, then one row per requirement showing `current value` on the left and `target value` on the right with a horizontal progress bar underneath, then a primary button at the bottom that's disabled/greyed out until every requirement is met.

---

## 1. Eligibility requirements (exact thresholds)

| Requirement | Threshold | Data source |
|---|---:|---|
| Upvotes received (sum across all your posts) | **1,000** | `Post.upvoteCount` summed over `CommunityRepository.postsByAuthor(uid)` |
| Followers | **200** | `UserProfile.followerCount` (`community/model/UserProfile.kt:26`) |
| Posts published | **20** | `postsByAuthor(uid).size` |
| Account age | **1 month** (30 days) | `UserProfile.createdAt` (`community/model/UserProfile.kt:35`) vs. now |

All four already exist in Firestore/Room today — nothing needs to be denormalized or backfilled to compute them. `postsByAuthor(uid)` is a real-time `Flow<List<Post>>` already defined at `community/CommunityRepository.kt:784`; both `upvoteCount` and the list size can be derived from a single subscription to it, no new query needed.

---

## 2. Data model changes

### `community/model/UserProfile.kt`
Add two fields to the existing `UserProfile` data class (`users/{uid}`):
```kotlin
val isModerator: Boolean = false,
val moderatorSince: Timestamp? = null,
```

### New Firestore collection: `moderatorApplications/{uid}`
Doc ID = the applicant's uid (one application slot per user — applying again overwrites the previous doc, so there's never more than one record to reconcile). Shape:
```
{
  uid: string
  displayName: string
  avatarUrl: string | null
  // stats snapshot at the moment of applying, for the admin to see without a join:
  upvotesTotal: number
  followerCount: number
  postCount: number
  accountAgeDays: number
  status: "pending" | "approved" | "rejected"
  appliedAt: Timestamp
  reviewedAt: Timestamp | null
  reviewedBy: string | null   // admin uid
  rejectionNote: string | null
}
```
Add a matching Kotlin data class `ModeratorApplication` in `community/model/` following the same style as `UserProfile.kt`.

### Firestore security rules
Add rules so:
- A user can create/overwrite only their own `moderatorApplications/{uid}` doc, and only with `status: "pending"` (can't self-approve).
- Only admins (existing `admins/{uid}` allowlist pattern used in the Admin repo) can write `status: "approved"` / `"rejected"`, or write `users/{uid}.isModerator`.

---

## 3. Mobile app — new Eligibility screen

New package: `community/moderator/` (mirrors the existing `community/discover/`, `community/upload/` sibling-package style).

Files to create:
- `community/moderator/EligibilityScreen.kt` — the Composable screen.
- `community/moderator/EligibilityViewModel.kt` — combines `postsByAuthor(uid)` + the user's own `UserProfile` doc + their own `moderatorApplications/{uid}` doc into one UI state.
- `community/moderator/ModeratorRepository.kt` — `submitApplication()` (writes the doc above) and `observeMyApplication(uid)`.

### UI state shape (`EligibilityViewModel`)
```kotlin
data class RequirementProgress(val label: String, val current: Long, val target: Long) {
    val fraction: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
    val met: Boolean get() = current >= target
}

sealed class EligibilityUiState {
    object Loading : EligibilityUiState()
    data class Ready(
        val requirements: List<RequirementProgress>, // the 4 rows, in the table order above
        val allMet: Boolean,
        val applicationStatus: String, // "" | "pending" | "approved" | "rejected"
        val asOfDate: String,          // "Showing data as of: <today, formatted>"
    ) : EligibilityUiState()
}
```

### Screen layout (top to bottom)
1. Top bar: back arrow + "Eligibility" title — reuse the same slim top-bar pattern as `community/discover/DiscoverCommunitiesScreen.kt`'s `DiscoverTopBar` (KinetixNavy in light mode / `colorScheme.surface` in dark).
2. A short explainer card: what a moderator is and what they get ("Moderators are trusted educators and SPED professionals whose posts are automatically marked Validated, no review queue.").
3. "Showing data as of: <date>" info card, same tone as the reference screenshot.
4. Four requirement rows, each: label + "current / target" text, then a full-width horizontal progress bar (`LinearProgressIndicator` from Material3, or a custom bar matching the existing app's progress-bar visual style if one already exists elsewhere in the app — check `game/ui/QuizChrome.kt`'s shared progress bar first and reuse it if the visual language matches).
5. Bottom sticky button:
   - `applicationStatus == ""` and `!allMet` → button reads **"Apply as Moderator"**, disabled/greyed out.
   - `applicationStatus == ""` and `allMet` → same label, enabled, calls `viewModel.apply()`.
   - `applicationStatus == "pending"` → button reads **"Pending Review"**, disabled.
   - `applicationStatus == "approved"` → replace the whole bottom section with a success state: "You're a moderator 🎉".
   - `applicationStatus == "rejected"` → button reads **"Reapply"**, re-enabled if `allMet` (show `rejectionNote` above it if present).

### Wiring into navigation
- `navigation/KinetixNavhost.kt`: add `const val ELIGIBILITY = "eligibility"` next to `DISCOVER_COMMUNITIES` (around line 84), add it to the same push/recede transition group it's already grouped with (see the route list around lines 319–323 — add `Route.ELIGIBILITY` there too so back-gesture/transition behavior matches Discover/Start Community), and add a `composable(route = Route.ELIGIBILITY, enterTransition = { communityPushEnter() }, ...)` block right after the existing Discover Communities block (~line 767–785), calling `EligibilityScreen(onClose = { navController.popBackStack() })`.
- `ui/home/KinetixDrawerContent.kt`: add a new parameter `onEligibilityClick: () -> Unit`, and a new `DrawerItem(HomeIcons.Eligibility, "Eligibility", onEligibilityClick)` row placed directly under the existing `DrawerItem(HomeIcons.Search, "Discover communities", onDiscoverCommunitiesClick)` line (~line 120), inside the same Community section (above the divider that precedes "About"). Update both `@Preview` functions at the bottom of the file to pass a no-op lambda for the new parameter so they keep compiling.
- `ui/home/HomeIcons.kt`: add a new hand-drawn vector icon `val Eligibility: ImageVector` following the exact style already used for `Menu`/`Close`/`StreakStar` in that file (a 24×24 outline path — a simple shield-with-checkmark or medal glyph reads well for "eligibility/qualification").
- `community/discover/DiscoverCommunitiesScreen.kt`: add one more `item { }` at the very bottom of the `LazyColumn` (after the communities list, before the closing of the `when` block, so it always renders regardless of load state) — a card CTA: "Want to post as a moderator? Check your eligibility →", tapping it calls a new `onOpenEligibility: () -> Unit` parameter threaded in from the nav host the same way `onOpenCommunity`/`onOpenCategory` already are.
- Wherever `KinetixDrawerContent` is instantiated in `KinetixNavhost.kt` (the Home drawer instance and the Community-screen drawer instance both call it — see the `onDiscoverCommunitiesClick` wiring around lines 559–561 and 640–644), pass a matching `onEligibilityClick = { navController.navigate(Route.ELIGIBILITY) }`.

---

## 4. Auto-validation on post creation

Two call sites in `community/CommunityRepository.kt` currently decide `validationStatus` from a user-facing `requestValidation` toggle:
- Line ~1058–1060 (one post-creation path)
- Line ~1142–1143 (the other post-creation path — check `CreatePostViewModel.kt` to see which path is actually used for new posts vs. edits)

Change the logic at both sites from:
```kotlin
"validationStatus" to if (requestValidation) "pending" else "",
```
to something like:
```kotlin
"validationStatus" to when {
    isModerator -> "validated"
    requestValidation -> "pending"
    else -> "",
},
"validatedBy" to if (isModerator) "auto:moderator" else null,
"validatedAt" to if (isModerator) FieldValue.serverTimestamp() else null,
```
`isModerator` should come from the current user's own already-loaded `UserProfile` (check whether `CreatePostViewModel`/`CommunityRepository` already holds a cached copy of the signed-in user's profile — if not, a single `get()` on `users/{uid}` at submit time is acceptable; avoid adding a live listener just for this).

Also: since a moderator's posts are always validated, consider hiding/disabling the manual "Submit for validation" toggle in `CreatePostScreen.kt` for moderators (it becomes redundant — every post is validated automatically) — read the current toggle's implementation there before deciding whether to hide it or just leave it inert.

---

## 5. Moderator badge on the profile

`community/CommunityProfileScreen.kt` around line 649 renders the `displayName` Text. Add a small badge icon immediately after it, shown only `if (state.isModerator)` — a filled shield/checkmark glyph (reuse or extend the icon added in `HomeIcons.kt`, or add a twin to `CommunityIcons.kt` if that's the more natural home given this screen already imports from there), with `contentDescription = "Moderator"`. This should be visible to **any viewer** of the profile, not just the moderator themselves — it's a public trust signal, the same way Twitter/YouTube verified checkmarks work.

Thread `isModerator` through `CommunityProfileViewModel.kt`'s state the same way `followerCount`/`displayName` already flow from the `UserProfile` doc.

---

## 6. Admin console — Moderator Applications queue

Mirror the existing Validation/Reports pattern exactly — same file shapes, same routing style, same audit-log habit (`Kinetix-FSL-Admin/src/firestore/moderation.js` and `Kinetix-FSL-Admin/src/pages/Reports.jsx` are the closest existing analogs; `Kinetix-FSL-Admin/src/pages/Validation.jsx` + `Kinetix-FSL-Admin/src/firestore/validation.js` are the other close analog since they already review admin-facing approve/reject decisions on user-submitted content).

New files:
- `Kinetix-FSL-Admin/src/firestore/moderatorApplications.js` — `subscribeApplications(status = "pending")` (`onSnapshot` on the `moderatorApplications` collection, matching the real-time pattern the existing Firestore modules use), `approveApplication(uid, adminUid)` (batched write: set `moderatorApplications/{uid}.status = "approved"` + `reviewedAt`/`reviewedBy`, set `users/{uid}.isModerator = true` + `moderatorSince`, and append an `auditLog` entry — copy the exact audit-log shape used elsewhere, e.g. in `moderation.js`), `rejectApplication(uid, adminUid, note)` (same shape, `status = "rejected"`, `rejectionNote`).
- `Kinetix-FSL-Admin/src/pages/ModeratorApplications.jsx` — a page listing pending applications in a table (name, avatar, the 4 stat values snapshotted at apply-time, applied date), with Approve / Reject buttons per row (Reject opens a small text input for the optional note, matching whatever confirmation pattern `Reports.jsx` already uses for its destructive actions).

Wire it in:
- `Kinetix-FSL-Admin/src/App.jsx` — add `<Route path="moderator-applications" element={<Suspense fallback={<PageFallback />}><ModeratorApplications /></Suspense>} />` alongside the existing `validation`/`reports` routes (~line 57–58).
- Add a sidebar nav entry (find wherever `Validation`/`Reports` links live in the shell/layout component — likely a `Sidebar.jsx` or inside `App.jsx`'s layout) with a live pending-count badge, the same badge treatment already used for the Validation/Reports queues.

No push notification / FCM work is needed — the existing admin pages already work by live-querying Firestore and showing a badge count for anything pending; this feature should behave identically, not introduce a new notification channel.

---

## 7. Acceptance checklist

- [ ] A user with fewer than all 4 thresholds sees accurate current/target numbers and a disabled Apply button.
- [ ] Crossing the last threshold live-updates the button to enabled without needing to leave/reopen the screen (state should react to the same flows driving the progress bars).
- [ ] Tapping Apply writes `moderatorApplications/{uid}` with `status: "pending"` and an accurate stats snapshot.
- [ ] The admin's Moderator Applications page shows the new application in real time.
- [ ] Approving sets `users/{uid}.isModerator = true` and the badge appears on that user's `CommunityProfileScreen` for every viewer.
- [ ] After approval, that user's next new post is written with `validationStatus: "validated"` immediately, with no admin action needed.
- [ ] Rejecting sets status back to a state that lets the user see the note and reapply once eligible again.
- [ ] Drawer, Discover Communities CTA, and direct back-navigation all reach/leave the Eligibility screen with the same transition feel as Discover Communities / Start a Community.
