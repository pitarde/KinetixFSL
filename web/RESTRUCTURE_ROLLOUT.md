# Firestore Restructure — Rollout Guide (implementation of FIRESTORE_RESTRUCTURE.md)

This is the **operator's runbook** for the restructure. The code for every phase
is already written across both repos; this file is the ordered sequence to
deploy it safely, plus the deviations from the original plan and the Phase 5
removal checklist.

> **Deviation summary (read first).** Two decisions differ from the plan doc, on
> purpose, and were confirmed with the project owner:
>
> 1. **No Cloud Function.** The plan's Phase 0 executor was a `deleteAccount`
>    Cloud Function (Admin SDK). This project stays on the **free Spark plan** by
>    design (see `worker.js` header), so deletion stays **client-side**: the
>    existing sweeps in `AccountEraser.kt` (app) and `deleteAccountData` in
>    `moderation.js` (admin console) were made **manifest-driven** instead. The
>    backfill/one-off scripts use the Admin SDK **locally**, which needs only a
>    service-account key, not Blaze.
> 2. **`accountStatus` root is retained.** The account-wipe marker
>    (`purgeAuth`/`wipedAt`) must **outlive** the deleted profile, so it stays in
>    the root `accountStatus/{uid}` collection (a nested doc would leave
>    `users/{uid}` as a ghost). Live **disable/penalty** state is *mirrored* into
>    `users/{uid}/status/moderation` for the mobile enforcement reads. So Phase 5
>    removes the nested/root *duplication of disable-penalty state*, but the
>    `accountStatus` root collection itself is kept for the wipe marker + the
>    admin Users overview.

## What "manifest" means here (Phase 0)

Every cross-user write now also writes one small row to
`users/{uid}/authored/{docId}` (`AuthoredManifest` in the app). Deletion reads
that one collection and clears exactly what it lists, then **recounts** the
affected post's counters from surviving children — no dependence on
collection-group indexes. The old collection-group sweeps are **kept as a
fallback** and are idempotent with the manifest pass (both recount).

- Instrumented (manifest rows written): **comments, post votes, comment votes,
  shares, outgoing 1:1 notifications**. These are the collection-group-dependent
  cases plus shares (previously never cleaned off other people's posts) and
  outgoing notifications (previously never cleaned at all).
- **Not** manifested (kept on their existing robust, index-free sweeps):
  follows (`clearFollowGraph`), community memberships (`leaveJoinedCommunities`),
  direct messages (`deleteOwnChatData` — queried by `participants`), and
  announcement fan-outs (bounded, low value).

---

## Deploy order

Each phase is independently deployable and revertable. **Do not start a phase
until the previous one is verified in production.** Rules already permit **both**
old and new paths simultaneously, so deploying rules early is safe.

### Common first step — rules + indexes

```bash
cd Kinetix-FSL
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

Then **wait for the new indexes to finish building** in the Firebase console
(Firestore → Indexes) before shipping code that queries them:
- `progress` collection-group (`__name__`) — needed by Phase 3 analytics.
- `notifications.fromUserId` (collection + collection-group) — needed by Phase 4
  rename propagation.

Backfill setup (once), for the scripts below:
```bash
cd Kinetix-FSL-Admin
npm i -D firebase-admin
export GOOGLE_APPLICATION_CREDENTIALS=/abs/path/serviceAccount.json
export FIREBASE_PROJECT_ID=kinetixfsl-73d88
node scripts/backfill-restructure.mjs <phase> --dry-run   # preview first
```

### Phase 0 — deletion manifest (no path change) — **the actual goal**

1. Rules already deployed (adds `users/{uid}/authored`).
2. Ship the app build (writes manifest rows going forward) and the admin console
   build (manifest-driven `deleteAccountData`).
3. Backfill existing data: `node scripts/backfill-restructure.mjs manifest`.
4. Verify with the checklist in FIRESTORE_RESTRUCTURE.md §10 (deletion section).

**Ghost documents are fixed at the end of this phase.** Phases 1–4 are structural
tidiness on top.

### Phase 1 — `otp_codes` → `users/{uid}/private/otp` (admin console only)
Ship the admin console. Codes expire in minutes; no backfill. `OtpContext.jsx`
writes/reads the new path with an old-path fallback read while it drains.

### Phase 2 — `accountStatus` → `users/{uid}/status/moderation`
1. Admin console already **dual-writes** both paths (`setAccountStatus`).
2. Ship the app: it reads **new-first, old-fallback** at sign-in
   (`AuthRepository`), in the live watcher (`AccountStatusWatcher` watches both),
   in `CommunityRepository.isPurgedAccount`, and in `ProgressSync`.
   ⚠️ **Adoption-gated** — a pre-update client that only knew the old path is
   still covered because the admin keeps dual-writing the root. Do not stop
   dual-writing (Phase 5) until app adoption is complete.
3. Backfill: `node scripts/backfill-restructure.mjs accountStatus` (copies live
   state to nested; skips wiped accounts to avoid ghosts).

### Phase 3 — `progress` → `users/{uid}/progress/current`
1. Ensure the `progress` collection-group index is **built**.
2. App already **dual-writes** both (`ProgressSyncWorker`) and reads new-first
   (`ProgressSync.restoreFromCloudIfLocalEmpty`).
3. Admin analytics already reads `collectionGroup('progress')` with **dedupe by
   uid** (`learners.js`), so it is correct before, during and after the move.
4. Backfill: `node scripts/backfill-restructure.mjs progress`. Verify the
   Analytics numbers match the pre-migration values exactly.

### Phase 4 — `notifications/{uid}/items` → `users/{uid}/notifications`
1. Ensure the `notifications.fromUserId` index is **built**.
2. App writes new-path only and **reads both paths merged**
   (`NotificationRepository`); mark-read / delete / clear / rename act on both.
   Admin `notifyUser` and broadcast fan-out write the new path.
   ⚠️ Per the plan, a learner on a pre-Phase-4 build won't see new-path
   notifications until they update — the accepted Phase 4 tradeoff.
3. Backfill: `node scripts/backfill-restructure.mjs notifications` (preserves
   doc ids so the merged read de-dupes).

---

## Phase 5 — removals (do LAST, after full app adoption)

Only after production telemetry shows the old app versions are gone. Search both
repos for the marker **`OLD PATH`** — each tags a line to delete. Summary:

- **App** `NotificationRepository.kt`: drop the old-path listener in
  `observeNotifications`, the old-path writes in `markRead`/`delete`/`clearAll`,
  and the `rewrite(ITEMS)` call in `propagateSenderName`.
- **App** `AccountEraser.kt`: drop the old-root notification + progress deletes.
- **App** `AccountStatusWatcher.kt`: drop the second (`accountStatus`)
  registration.
- **App** `AuthRepository` / `CommunityRepository` / `ProgressSync`: drop the
  old-path fallback reads (keep new-path only). `ProgressSyncWorker`: drop the
  old-root write.
- **Admin** `moderation.js`: drop the old-path halves in `setAccountStatus`
  (keep root only if you still want the Users overview — see note), the old
  notification/progress deletes. `broadcasts.js` / `OtpContext.jsx`: drop old
  fallbacks.
- **Rules** (`firestore.rules`): remove the old root `match` blocks for
  `otp_codes`, `progress`, `notifications/{uid}/items` + its `{path=**}/items`
  collection-group rule. **Keep** `accountStatus` (wipe marker — see deviation).
- **Indexes** (`firestore.indexes.json`): remove the `items.fromUserId`
  fieldOverride (marked `PHASE 5 REMOVAL`).
- **Drain the old collections** (`otp_codes`, `progress`,
  `notifications/*/items`) with a delete pass, e.g.
  `firebase firestore:delete --recursive <path>`, only **after** the rules still
  permit your cleanup tooling to reach them (delete data before removing the
  rule).

⚠️ Never remove a `match` block before the old data is gone — deleting the rule
doesn't delete the data, it makes it permanently unreachable, including to your
own cleanup code.
