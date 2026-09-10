# Firestore Restructure Plan — KinetixFSL

**Goal as stated:** make `users` and `admins` the only root collections, with everything
else nested underneath them, so that deleting an account (from the app or from the admin
console) is a single clean operation that leaves no ghost documents behind.

**Status:** planning document only. Nothing in `firestore.rules`, `firestore.indexes.json`,
`worker.js`, the Android app or the admin console has been changed.

**Baseline:** the current shape is documented in the *Kinetix Firestore Map* — 13 root
collections, 15 subcollection paths, 3 collection-group queries, derived from
`web/firestore.rules` and `web/firestore.indexes.json`.

---

## 1. The verdict first

Two things need saying before the plan, because they change what the plan should be.

### 1.1 Nesting is not what fixes ghost documents

Firestore **does not cascade deletes**. Deleting `users/{uid}` does not delete
`users/{uid}/followers`, `users/{uid}/devices`, or anything else beneath it. The
subcollections survive, and the console then shows the parent in italics — a document ID
that does not exist but still has children. That *is* the ghost document.

So moving more data underneath `users/{uid}` does not make deletion cleaner by itself. On
its own it makes it **worse**: today `progress/{uid}`, `accountStatus/{uid}` and
`otp_codes/{uid}` are flat leaf documents with no children, which are the single easiest
things in the whole database to delete. Turn them into subcollections and each one becomes
a new thing that can be orphaned.

The thing that actually removes ghosts is a **recursive delete run with admin
privileges** — `recursiveDelete()` in the Admin SDK, or `firebase firestore:delete
--recursive`. Once that exists, consolidation under `users/{uid}` becomes genuinely
valuable, because one call clears the entire subtree. Consolidation and recursive delete
are a package; adopting either one alone is a downgrade.

**Therefore: Phase 0 of this plan is the deletion mechanism, not the path changes.**

### 1.2 "Only `users` and `admins`" is not reachable — 9 roots is

Your note allows extra roots for data that cannot live under a single owner. Applying that
test honestly, four categories fail it, and each fails for a concrete reason documented
below in §3:

| Cannot nest | Because |
|---|---|
| `posts` | Public share links carry only the post ID — `worker.js` route `/p/{postId}` |
| `communities` | Shared object with many members; must outlive its creator's account |
| `conversations` | Two owners, not one; nesting duplicates every message |
| `auditLog`, `broadcasts`, `contentOverrides`, `reports` | Global records that must survive the deletion of any user *or* admin |

That gives a target of **9 roots, down from 13** — with all four per-learner collections
folded into `users/{uid}`, which is the part of your goal that both works and pays off.
§9 describes an optional variant that presents as 6 roots if the count itself matters to
you.

---

## 2. Target structure

```
users/{uid}                                     ← profile (public read)
├── followers/{followerId}                      unchanged
├── following/{targetId}                        unchanged
├── joinedCommunities/{communityId}             unchanged
├── hiddenPosts/{postId}                        unchanged
├── recentCommunities/{communityId}             unchanged
├── blocked/{targetId}                          unchanged
├── blockedBy/{blockerId}                       unchanged
├── devices/{deviceId}                          unchanged
├── notifications/{itemId}                MOVED  ← was notifications/{uid}/items/{itemId}
├── progress/current                      MOVED  ← was progress/{uid}
├── status/moderation                     MOVED  ← was accountStatus/{uid}
├── private/otp                           MOVED  ← was otp_codes/{uid}
└── authored/{docId}                      NEW    ← deletion manifest, see §4.2

admins/{uid}                                    ← allowlist, no client write path

posts/{postId}                                  ← STAYS ROOT (§3.1)
├── votes/{userId}
├── shares/{userId}
└── comments/{commentId}
    └── votes/{userId}

communities/{communityId}                       ← STAYS ROOT (§3.2)
└── members/{memberId}

conversations/{uidA_uidB}                       ← STAYS ROOT (§3.3)
└── messages/{messageId}

reports/{reportId}                              ← STAYS ROOT (§3.4)
auditLog/{entryId}                              ← STAYS ROOT (§3.4)
broadcasts/{id}                                 ← STAYS ROOT (§3.4)
contentOverrides/{categoryId}                   ← STAYS ROOT (§3.4)
```

**Net effect:** 13 roots → 9. Four per-learner collections collapse into `users/{uid}`,
so a single `recursiveDelete(users/{uid})` now clears the profile, the entire follow graph,
devices, notifications, progress, moderation status, the OTP secret and the deletion
manifest in one server-side call.

---

## 3. Why each remaining root cannot move

This section is the answer to "which roots am I allowed to keep". Each entry states the
concrete failure, not a preference.

### 3.1 `posts` — public share links carry no author ID

`worker.js:661` handles `/p/{postId}` by slicing the post ID out of the path and fetching
`documents/posts/{postId}` over the Firestore REST API, **unauthenticated**. The URL
contains no author UID. If posts move to `users/{authorId}/posts/{postId}`:

- Every share link already sent to a person, posted in a chat, or crawled by a link
  preview bot becomes unresolvable. There is no way to recover the author UID from an
  existing `/p/{postId}` URL.
- The Worker would need either a lookup index (a root collection mapping postId → authorId,
  which is a root collection again, and a second network round trip per page render) or a
  new URL format `/p/{authorId}/{postId}`, which does not fix the old links.

There are three further costs even if the link problem were solved:

- **The home feed** becomes `collectionGroup("posts")` ordered by `createdAt`/`score`. That
  works, but it needs new collection-group indexes and it re-exposes every post globally
  anyway — so the nesting buys no privacy.
- **The community feed** (`where communityId == …` + `orderBy createdAt`) needs a
  collection-group composite index.
- **The admin validation queue** (`validationStatus` ASC + `createdAt` DESC, per
  `firestore.indexes.json`, used by `validation.js subscribeValidationQueue`) must be
  re-promoted to collection-group scope.

**Decision: `posts` stays root.** Its three subcollections stay exactly where they are —
see §5.2 for why their names must not change.

### 3.2 `communities` — shared, and must outlive its creator

- `worker.js:667` serves `/c/{communityId}` unauthenticated, with the same no-owner-in-URL
  problem as posts.
- Discover browses and filters across *all* communities on `categories`. Under
  `users/{creatorId}/communities` that becomes a collection-group query over data that is
  public anyway.
- A community is a shared space with a roster. Nesting it under the creator ties its
  lifetime to one account: `recursiveDelete(users/{creatorUid})` would silently destroy a
  community and its entire member roster. That is a data-loss bug, not a cleanup.

**Decision: `communities` stays root.** Community deletion stays an explicit, separate
action with its own rules (creator or admin), as it is today.

### 3.3 `conversations` — two owners, so nesting means duplicating

A thread belongs to both participants. `users/{uidA}/conversations/{uidB}/messages` forces
one of two bad outcomes:

- **Duplicate the thread** under both users — every message written twice, storage and
  write cost doubled, and the two copies can drift. `isRead` and the typing flags would
  need cross-writes into the other person's subtree, which the current uid-scoped rules are
  designed to forbid.
- **Store it under one user only** — then deleting that user destroys the other person's
  chat history.

The current design already solves the ownership problem more cheaply: the document ID is
the two uids sorted and joined with `_`, so `inThread()` reads membership off the path with
no document read at all. Nesting throws that away.

It also breaks the existing account-deletion logic. The delete rule today permits removing
a thread when `!exists(users/{otherParticipant})` — "the other person is gone, so this
thread is a ghost nobody can open." That check needs both uids to be derivable from the
conversation's own ID, which is exactly what the flat ID gives it.

**Decision: `conversations` stays root, and the `uidA_uidB` ID convention is load-bearing.**

### 3.4 `reports`, `auditLog`, `broadcasts`, `contentOverrides` — global by definition

| Collection | Why it cannot nest |
|---|---|
| `auditLog` | It records what admins did *to* users. Nesting under the admin means deleting an admin erases the record of their actions; nesting under the target user means deleting the user erases it. Append-only accountability requires a location that outlives every participant. |
| `reports` | The moderation queue is read globally, sorted by `status` + `createdAt` (`moderation.js subscribeReports`, backed by an existing composite index). Nesting under the reporter means a deleted reporter takes the evidence with them, and turns the queue into a collection-group query. |
| `broadcasts` | Authored by admins, addressed to everyone. Not owned by any single user, and must survive the author leaving. |
| `contentOverrides` | Configuration keyed by FSL sign category, world-readable so the app can honour a disabled module without a privileged read. Keyed by `categoryId`, not by any uid — there is no user to nest it under. |

**Decision: all four stay root.** None of them is a ghost-document risk: they are flat
collections of leaf documents with no subcollections.

---

## 4. The ghost-document problem, and the actual fix

### 4.1 Where ghosts come from today

Four distinct sources, only one of which nesting touches:

1. **Subcollections under a deleted parent.** `users/{uid}` has 8 subcollections today (12
   after the move). `posts/{postId}` has 3, one of them two levels deep. Deleting the parent
   document leaves all of them.
2. **Rows this user wrote inside *other people's* documents.** Their votes on other posts,
   their comments, their share markers, their entries in other users' `followers` lists,
   their membership markers in `communities/*/members`, the notification rows they caused in
   other users' inboxes, their messages in shared conversations. Nesting cannot reach any of
   these — they live under a different owner.
3. **Denormalised name and photo copies** in five places (§5.3), which outlive the profile
   they were copied from.
4. **Counters left wrong.** Removing a vote or comment without decrementing
   `upvoteCount` / `commentCount` / `memberCount` / `followerCount` leaves the surviving
   documents displaying counts that no longer match reality.

Category 1 is fixed by structure plus recursive delete. Categories 2, 3 and 4 are fixed
only by an explicit sweep — which is what `account/AccountEraser.kt` does today with
collection-group queries.

### 4.2 The fix, in three parts

**Part A — server-side recursive delete (Phase 0, do this first).**

A callable Cloud Function running with Admin SDK privileges:

```
deleteAccount(uid):
  1. verify caller is the uid, or an admin
  2. write auditLog entry (before anything is destroyed)
  3. read users/{uid}/authored  → delete each listed path      (category 2 + 3)
  4. repair counters for each deleted vote/comment/member       (category 4)
  5. recursiveDelete(users/{uid})                               (category 1)
  6. delete the Firebase Auth user
```

This replaces the client-side sweep as the primary path. The existing client sweep should
stay as a fallback for the offline / function-unavailable case, exactly as the anonymise
fallback works today.

**Part B — the deletion manifest, `users/{uid}/authored/{docId}`.**

The manifest is what makes category 2 deterministic. Every time the user writes a document
into someone else's subtree, the *same batch* also writes one small manifest row:

```
users/{uid}/authored/{docId} = {
  path:      "posts/abc123/comments/def456",   // full document path
  type:      "comment",                        // post | comment | vote | share | member | follower | notification | message
  parentPath:"posts/abc123",                   // for counter repair
  counter:   "commentCount",                   // which field to decrement, or null
  createdAt: <timestamp>
}
```

Deletion then reads one collection and deletes exactly what it lists. No collection-group
queries, no dependence on the three field-override indexes, no risk of a query silently
returning nothing because an index was not deployed — which is the failure mode called out
in `firestore.indexes.json` for `items.fromUserId` ("the sync silently no-ops").

The manifest is itself inside `users/{uid}`, so `recursiveDelete` clears it in step 5 after
it has been consumed.

**Part C — ordering and counter repair.**

- Always delete **leaves before parents**. For `posts`: `comments/*/votes` → `comments` →
  `votes` → `shares` → the post document.
- Every deletion that had a counter decrements it in the **same batch or transaction**, so a
  partial failure cannot leave the count wrong.
- Delete in batches of ≤ 500 writes (Firestore's limit), with a resume cursor so a large
  account can be cleared across multiple function invocations.

### 4.3 What remains a ghost even after all this

Be aware of two residues, and decide deliberately:

- **`conversations` with a surviving participant.** The thread cannot be deleted while the
  other person still exists — deleting it would take their copy. Today's rule handles this by
  allowing deletion only once `users/{otherUid}` is gone. That means a thread with a live
  partner correctly *stays*, showing an anonymised or missing sender. This is intended
  behaviour, not a ghost to fix.
- **`auditLog` entries naming a deleted user.** These must persist — that is the point of an
  append-only log. Store the uid, not the display name, so the log carries no personal data
  after the account is erased.

---

## 5. Resolving the four constraints from "Worth knowing before you reorganise"

### 5.1 Constraint — six root collections hang off the same uid

*Current:* `users`, `progress`, `accountStatus`, `admins`, `otp_codes`, `notifications` are
all keyed by the learner's uid. They are split because `users` is world-readable, so private
data cannot be a field on the profile.

*Resolution:* the split is only necessary at the **field** level, not the **collection**
level. A subcollection under `users/{uid}` has its own rule and does not inherit the
parent's public read. So the private data can move inside `users/{uid}` while staying just
as private:

| From | To | New access rule |
|---|---|---|
| `progress/{uid}` | `users/{uid}/progress/current` | owner read+write; admin read (collection group) + delete |
| `accountStatus/{uid}` | `users/{uid}/status/moderation` | owner read; admin write |
| `otp_codes/{uid}` | `users/{uid}/private/otp` | owner read+write only |
| `notifications/{uid}/items/{itemId}` | `users/{uid}/notifications/{itemId}` | owner read; authenticated create; sender may update own name/photo |

`admins/{uid}` **does not move.** `isAdmin()` is evaluated on nearly every admin rule as a
single `exists(/admins/{uid})`. Moving it to `users/{uid}/roles/admin` costs the same one
read but makes the allowlist a child of a document the user themself can write and delete —
`users/{uid}` currently allows the owner to hard-delete their own profile. That is a
privilege-escalation surface for no gain.

⚠️ **Conditional:** only make these four moves together with Phase 0 (§4.2 Part A). Without
recursive delete they convert three clean single-document deletes into three new ghostable
subcollections. See §1.1.

**Code touched by these moves**

| Move | Android | Admin console | Rules | Indexes |
|---|---|---|---|---|
| progress | `progress/ProgressRepository.kt`, `progress/ProgressSyncWorker.kt` (`COLLECTION = "progress"`) | analytics module (reads every learner's doc) | new owner + admin collection-group rule | **new** collection-group index for `progress` |
| accountStatus | `auth/AuthRepository.kt` — 4 call sites of `collection("accountStatus")` | account moderation actions | owner read / admin write, now nested | none |
| otp_codes | — | `src/OtpContext.jsx` | uid-scoped, now nested | none |
| notifications | `community/inbox/NotificationRepository.kt` | account-deletion sweep | nested rule + revised collection-group rule | **replace** `items.fromUserId` with `notifications.fromUserId` |

> **Naming note.** The leaf segment `items` becomes `notifications`. The path is changing
> regardless, so renaming costs only the one field-override index. If you would rather not
> re-create it, keep the segment name `items` — `users/{uid}/items/{itemId}` — and the
> existing `items.fromUserId` collection-group index keeps working untouched. Cleaner name
> vs. one fewer moving part; either is defensible.

### 5.2 Constraint — document IDs carry meaning

*Current:* `votes/{userId}`, `shares/{userId}`, `members/{memberId}`, `followers/{followerId}`,
`blocked/{targetId}`, `blockedBy/{blockerId}` are all named with a uid, so "has this person
already acted?" is one `get()` rather than a query. `conversations/{uidA_uidB}` encodes both
participants in the ID.

*Resolution:* **do not touch any of them.** The plan preserves every document-ID convention
in the database. This is deliberate, and it constrains the plan in three specific ways:

- `conversations` cannot be nested (§3.3) — nesting destroys the derived-membership trick
  that `inThread()` and `otherInThread()` depend on.
- The rules' `exists()` guards stay valid as written: `postCommunityAllowed()` checks
  `communities/{id}/members/{uid}`, and message creation checks
  `users/{other}/blockedBy/{me}`. Both are single reads off a known path.
- The `blocked` / `blockedBy` mirror stays exactly as-is. It exists because a security rule
  can only read documents, so the block must be written somewhere the *blocked* person's own
  send can be checked against. Any restructuring that breaks the mirror breaks blocking.

The manifest in §4.2 Part B is what lets deletion work *without* changing these IDs — it
records the full path of every uid-named document the user created, so the sweep does not
need to search for them.

### 5.3 Constraint — names and photos are copied into five places

*Current:* `displayName` and `avatarUrl` are denormalised onto `posts`, `comments`,
`users/*/followers`, `users/*/following` and notification `items`. Each has its own narrow
update rule (`hasOnly(['displayName','avatarUrl'])`, `hasOnly(['fromUserName','fromUserPhoto'])`)
so a rename can reach it, and three collection-group indexes exist purely to find them:
`items.fromUserId`, `comments.authorId`, `votes.userId`.

*Resolution:* all five stay reachable, and the manifest makes them cheaper to reach.

| Copy | After restructure | Rename path | Delete path |
|---|---|---|---|
| `posts.authorName` | root, unchanged | direct query by `authorId` | manifest |
| `comments.authorName` / `avatarUrl` | root, unchanged | `collectionGroup("comments")` on `authorId` — index unchanged | manifest |
| `users/*/followers.displayName` | unchanged | existing narrow update rule | manifest |
| `users/*/following.displayName` | unchanged | existing narrow update rule | manifest |
| notification `fromUserName` / `fromUserPhoto` | moves to `users/*/notifications` | `collectionGroup("notifications")` on `fromUserId` — **index must be re-created** | manifest |

Two rules to hold to during the migration:

1. **Keep the collection-group segment names stable** wherever you are not deliberately
   re-creating the index. `comments` and `votes` must keep those exact segment names, at any
   depth, or `CommunityRepository.commentsByAuthor`, `propagateAuthorName`, `propagateAvatarUrl`
   and `AccountEraser.deleteOwnVotesEverywhere` all break.
2. **Deploy the index before the code that queries it.** A collection-group query without its
   field override does not error loudly — it fails, and the sync silently no-ops. This is
   already documented as a known failure mode in `firestore.indexes.json`.

### 5.4 Constraint — the tail rule denies everything unnamed

*Current:* `match /{document=**} { allow read, write: if false }` closes the rules file. A
collection is unreachable until it is named above that block.

*Resolution:* this is a feature and the plan leans on it. It means:

- **The migration cannot silently half-work.** A new path that you forgot to add a rule for
  is denied, loudly, in testing — not quietly readable.
- **Nothing is missing from this plan.** Every path in §2 came from a `match` statement; the
  tail rule guarantees there is no thirteenth collection someone added and forgot.
- **Write the rules for the new paths *before* migrating data**, with the old rules still in
  place. Both shapes must be permitted simultaneously during the dual-write window (§6).

⚠️ Do not delete the old `match` blocks until the migration is verified and the old data is
gone. Deleting the rule does not delete the data — it makes orphaned data permanently
unreachable, including to your own cleanup code.

---

## 6. Migration plan

Each phase is independently deployable and independently revertable. Do not begin a phase
before the previous one is verified in production.

### Phase 0 — deletion mechanism (no path changes)

The highest-value phase, and it changes no paths at all.

1. Add the `users/{uid}/authored/{docId}` manifest; write a manifest row in the same batch as
   every cross-user write (post, comment, vote, share, member marker, follower entry,
   notification row, message).
2. Backfill the manifest for existing users with a one-off Admin SDK script that walks
   `posts`, the three collection groups and `communities/*/members`.
3. Ship the `deleteAccount` Cloud Function (§4.2 Part A). Keep the client sweep as fallback.
4. Verify: delete a seeded test account, then confirm every source in §4.1 is clear.

**Ghost documents are fixed at the end of this phase.** Everything after it is structural
tidiness. If you stop here, you have met the actual goal.

### Phase 1 — `otp_codes` → `users/{uid}/private/otp`

Lowest risk: one writer (`OtpContext.jsx`), short-lived data, no index, no Android code.
Rehearse the whole migration pattern here.

1. Deploy rules permitting both paths.
2. Update `OtpContext.jsx` to write and read the new path.
3. Codes expire within minutes — no backfill needed; let the old collection drain.
4. Delete the old collection, then remove the old `match` block.

### Phase 2 — `accountStatus` → `users/{uid}/status/moderation`

1. Deploy rules for both paths.
2. Dual-write from the admin console: write both locations on every moderation action.
3. Update `AuthRepository.kt` (4 call sites) to read the new path, with a fallback read of
   the old one. **Ship the app update and wait for adoption** — this is enforced at sign-in,
   and a stale client that reads only the old path will let a disabled account back in.
4. Backfill, verify, stop dual-writing, delete old, remove old rule.

⚠️ This is the one phase gated on app-version adoption. Budget for it.

### Phase 3 — `progress` → `users/{uid}/progress/current`

1. Create the `progress` collection-group index. **Wait for it to build.**
2. Deploy rules for both paths, including the admin collection-group read.
3. Dual-write from `ProgressSyncWorker.kt`.
4. Switch the admin analytics module to `collectionGroup("progress")`; verify the numbers
   match the old module exactly before proceeding.
5. Backfill, verify, stop dual-writing, delete old, remove old rule.

### Phase 4 — `notifications/{uid}/items` → `users/{uid}/notifications`

The largest phase: highest write volume, and it touches a collection-group index.

1. Create the `notifications.fromUserId` collection-group field override. **Wait for it to
   build** — see §5.3 rule 2.
2. Deploy rules for both paths.
3. `NotificationRepository.kt` writes to the new path, reads from both and merges.
4. Backfill existing inboxes with an Admin SDK script.
5. Verify sender-name propagation works against the new index before switching reads.
6. Stop dual-writing, delete old, remove old rule, drop the `items.fromUserId` override.

### Phase 5 — cleanup

Remove the fallback read paths from the app, drop the old `match` blocks, and confirm the
rules file lists exactly the 9 roots in §2 above the tail rule.

---

## 7. Rules changes required

New or revised blocks. Existing helper functions — `isAdmin()`, `isPostAuthor()`,
`isCommunityCreator()`, `isPostCommunityCreator()`, `postCommunityAllowed()`, `inThread()`,
`otherInThread()`, `onlyCounterFieldsChanged()`, `onlyCommentCounterFieldsChanged()`,
`onlyFollowCountsChanged()`, `onlyMemberCountChanged()` — are all unaffected, because no
path they reference changes.

| Path | Access |
|---|---|
| `users/{uid}/private/otp` | read, write: owner only |
| `users/{uid}/status/moderation` | read: owner or admin · write: admin only |
| `users/{uid}/progress/current` | read: owner or admin · create, update: owner · delete: owner or admin |
| `{path=**}/progress/{docId}` | read: admin only — backs the analytics module |
| `users/{uid}/notifications/{itemId}` | read: owner or admin · create: authenticated, `isRead == false`, attributed · update: owner, or sender for `fromUserName`/`fromUserPhoto` only · delete: owner or admin |
| `{path=**}/notifications/{itemId}` | read: `resource.data.fromUserId == request.auth.uid` — replaces the `items` collection-group rule |
| `users/{uid}/authored/{docId}` | read, write: owner or admin |

The recursive admin grant `match /users/{userId}/{document=**} { allow read, delete: if isAdmin() }`
already exists and automatically covers every new subcollection — no change needed, and it is
what makes `recursiveDelete` work from the console path too.

---

## 8. Index changes required

| Action | Index | Phase |
|---|---|---|
| **Create** | `progress` — `__name__`, collection-group scope, for the admin analytics read | 3 |
| **Create** | `notifications` — `fromUserId` ASC, collection *and* collection-group scope | 4 |
| **Drop** (after Phase 4 verified) | `items` — `fromUserId` | 5 |
| **Unchanged** | `comments.authorId` collection group | — |
| **Unchanged** | `votes.userId` collection group | — |
| **Unchanged** | `reports` — `status` ASC + `createdAt` DESC | — |
| **Unchanged** | `posts` — `validationStatus` ASC + `createdAt` DESC | — |

Always deploy and let an index finish building **before** shipping code that queries it.

---

## 9. Optional — presenting as 6 roots

If the root count itself matters, the four global admin collections can sit under one
`system` root:

```
system/config/contentOverrides/{categoryId}
system/moderation/reports/{reportId}
system/moderation/auditLog/{entryId}
system/announcements/broadcasts/{id}
```

Roots become: `users`, `admins`, `posts`, `communities`, `conversations`, `system` — six.

**Recommendation: skip it, or do it last.** It buys nothing operationally — those four are
flat leaf collections that were never a ghost risk — while costing a rewrite of both
composite indexes (`reports` becomes collection-group scoped), new rules, and changes to
`ReportRepository.kt`, `ContentOverridesRepository.kt`, `moderation.js` and `validation.js`.
It is cosmetics priced like a migration. Included here because it is the only remaining way
to reduce the root count, so the choice is yours to make with the cost visible.

---

## 10. Verification checklist

Run against a seeded test account after each phase.

**Deletion — the actual goal**
- [ ] Delete an account from the mobile app → no document remains under `users/{uid}`, at any depth
- [ ] Delete the same from the admin console → same result, plus an `auditLog` entry
- [ ] The deleted user's posts, comments, votes and share markers are gone from other users' documents
- [ ] `upvoteCount`, `downvoteCount`, `commentCount`, `shareCount`, `memberCount`, `followerCount`, `followingCount` are all correct on every affected document
- [ ] The Firebase console shows no italicised (non-existent, has-children) document ID anywhere
- [ ] A conversation with a still-live partner survives; one where both have left is gone
- [ ] The Auth user is deleted, and re-registering with the same email starts clean

**Non-regression — nothing broken**
- [ ] `/p/{postId}`, `/c/{communityId}` and `/u/{userId}` share links still render, signed out
- [ ] Home feed, community feed and Discover load and order correctly
- [ ] Voting, commenting and sharing on someone else's post still move the counters
- [ ] Following, unfollowing, joining and leaving all still work
- [ ] Blocking still prevents a message send (the `blockedBy` mirror)
- [ ] A rename propagates to all five denormalised copies (§5.3)
- [ ] Notifications arrive, mark read, and delete
- [ ] Admin sign-in OTP works; the moderation queue and validation queue both load
- [ ] A disabled account is refused at sign-in, on both old and new app versions during Phase 2
- [ ] The analytics module's numbers match the pre-migration values exactly

---

## 11. Summary

| Your goal | Outcome |
|---|---|
| Only `users` and `admins` as roots | **Not reachable.** 9 roots, each remaining one justified in §3 by a concrete failure — share links, shared ownership, or records that must outlive their author. |
| Consolidate per-user data | **Achieved.** All four per-learner collections move into `users/{uid}`; one `recursiveDelete` clears the whole subtree. |
| No ghost documents | **Achieved — by Phase 0, not by nesting.** Firestore has no cascade delete; the fix is server-side recursive delete plus the `users/{uid}/authored` manifest for data written into other users' subtrees. |
| Break nothing | **Achieved.** Every document-ID convention, every public share URL, all five denormalised name copies and both existing composite indexes are preserved. The one adoption-gated change is `accountStatus` in Phase 2. |

**If you do only one thing: do Phase 0.** It fixes ghost documents completely, changes no
paths, requires no app-version adoption, and is fully revertable. Phases 1–4 are structural
tidiness on top of a problem already solved.
