# Messaging & Notifications — setup

Everything the Inbox feature needs that lives **outside** the Android project.
The app code is already written and compiles; until these are done you'll see
specific, predictable failures, listed under each part.

Do them in this order. Each part ends with a test — don't move on until it passes.

Your values, carried over from `SETUP.md`:

| Thing | Value |
| --- | --- |
| Firebase project id | `kinetixfsl-73d88` |
| Worker host | `kinetix-upload.pitardeken2024.workers.dev` |
| Android package | `com.example.kinetixfsl` |

**What works without any of this:** nothing. Part 1 is mandatory. Parts 2–5 each
add one capability and the app degrades quietly without them (no green dots, no
push banners, message media lands in the post folder).

---

## Part 1 — Firestore rules  *(required)*

Two new trees need rules: `notifications` and `conversations`. Without them every
message send and every notification write is denied and the Inbox stays empty.

### Steps

1. <https://console.firebase.google.com/> → **kinetixfsl-73d88**.
2. **Build → Firestore Database → Rules**.
3. Select everything and replace it with the full contents of
   [`web/firestore.rules`](firestore.rules) from this repo. That file is
   complete — it includes the existing posts/users/communities rules plus the
   new ones.
4. **Publish**, and wait for the "Rules published" confirmation.

### What the new rules say, in one line each

- `notifications/{userId}/items` — **anyone** may write you a notification (that
  is what "Maria followed you" is), but only **you** may read, mark read, or
  delete. A write must be attributed to whoever is signing it.
- `conversations/{id}` — readable and writable only by the two participants. The
  id is the two uids sorted and joined with `_`, so membership is checked from
  the id without an extra document read.
- `conversations/{id}/messages` — send as yourself only; the only field the
  recipient may change is `isRead` (the "Seen" receipt). Message text can't be
  edited by anyone, and messages can't be deleted.
- `users/{uid}/devices` — private. It only exists so the app can say "this is a
  device you've used before".

### Test

Open the app on two accounts (two devices, or one device and one emulator).
Follow one from the other. The followed account should get a **Follow**
notification in **Inbox → Notification** within a second or two.

If nothing arrives: Firebase Console → Firestore → check a
`notifications/{uid}/items` document exists. If it doesn't, the rules didn't
publish. If it does but the app shows nothing, you're signed in as the wrong uid.

---

## Part 2 — Firestore indexes  *(required, but automatic)*

The app deliberately avoids composite indexes almost everywhere — conversation
lists and profile posts are sorted in Kotlin for exactly this reason. Two
single-field ordered queries remain, and Firestore builds single-field indexes
by itself:

- `notifications/{uid}/items` ordered by `createdAt`
- `conversations/{id}/messages` ordered by `createdAt`

**Nothing to do**, unless Logcat shows a `FAILED_PRECONDITION` with an index
link. If it does, tap the link in the error — it opens the console with the
index pre-filled. Press **Create** and wait for "Enabled" (a minute or two).

---

## Part 3 — Cloudflare R2, for photos and videos in messages

Message attachments go through the **same Worker and the same bucket** as post
media. So the honest answer is: **it already works, with nothing changed.** What
follows is the tidying-up that makes it right rather than merely working.

### 3a — Separate message media into its own folder *(recommended)*

Post media is public — anyone with a share link can load it. Message media
should not be aged, cached, or eventually locked down on the same terms. Right
now both land in `images/` and `videos/`.

The app **already sends** a `folder=messages` form field on message uploads. The
Worker currently ignores it. Teach it to read it:

1. Cloudflare dashboard → **Workers & Pages** → **kinetix-upload** → **Edit code**.
2. Find these two lines (about line 58):

   ```js
   const resourceType = formData.get("resource_type") || "image";
   const folder = resourceType === "video" ? "videos" : "images";
   ```

3. Replace them with:

   ```js
   const resourceType = formData.get("resource_type") || "image";

   // An explicit folder from the client (currently only "messages") wins;
   // otherwise fall back to the post-media layout. Sanitised to a short
   // alphanumeric slug so a crafted request can't write outside the bucket
   // layout or escape the prefix with "../".
   const requested = (formData.get("folder") || "").toString();
   const safeFolder = /^[a-z0-9-]{1,20}$/.test(requested) ? requested : null;

   const folder = safeFolder
     ? `${safeFolder}/${resourceType === "video" ? "videos" : "images"}`
     : (resourceType === "video" ? "videos" : "images");
   ```

4. **Deploy**.

New message media now lands under `messages/images/…` and `messages/videos/…`.
Existing files stay where they are and keep working — every URL is stored whole
in Firestore, so nothing needs migrating.

### 3b — Lifecycle rule for message media *(optional, saves money)*

Chat attachments are looked at once and then effectively never again, but they
sit in storage forever.

1. Cloudflare dashboard → **R2** → your bucket → **Settings**.
2. **Object lifecycle rules** → **Add rule**.
3. Name: `expire-message-media`. Prefix: `messages/`.
4. Action: **Delete objects** after `365` days.
5. Save.

Do **not** put a lifecycle rule on `images/` or `videos/` — that's post media,
and a post whose photo vanished after a year is a bug.

### 3c — Check CORS *(only if the web share pages ever show message media)*

They don't today, so there is nothing to do. If that changes: R2 → bucket →
**Settings → CORS policy**, allow `GET` from your Worker host.

### Test

Send a photo in a chat. It should appear in the bubble within a few seconds.
Then check Cloudflare → R2 → your bucket → **Objects**: a new file under
`messages/images/` (after 3a) or `images/` (before it).

---

## Part 4 — Push notifications (FCM via your Cloudflare Worker)

This is what makes a banner appear when the app is **closed or backgrounded**.
The in-app Inbox list works without any of this.

The chain is: **the app writes a notification document (source of truth) →
the same code asks your Cloudflare Worker's `/send-push` route to also push
it → the Worker calls Google's FCM API → the phone shows a banner.**

This is deliberately **not** a Firebase Cloud Function. A Cloud Function needs
Firebase's paid Blaze plan — the free monthly quota would cover this app's
traffic, but a card still has to be on file. Routing the push through the
Worker this app already runs for uploads means **Firebase stays on the free
Spark plan, permanently, with nothing to upgrade.** The trade-off: the Worker
now holds a Google service-account credential, which is more setup than "click
Enable" but is a one-time cost.

The client never talks to FCM directly, and never holds the service-account
key — only the Worker does. A key that could push to *any* user has to live
somewhere that isn't an extractable APK.

### 4a — Turn on Cloud Messaging (still needed — this is the receiving side)

1. Firebase Console → **kinetixfsl-73d88** → ⚙️ **Project settings** → **Cloud Messaging**.
2. If **Firebase Cloud Messaging API (V1)** shows as disabled, click **Enable**
   (it opens Google Cloud Console; press Enable there, then come back).
3. Nothing to download. `google-services.json` already covers the app, and the
   `firebase-messaging` dependency is already in `app/build.gradle.kts`.

This step alone doesn't send anything — it's what lets a phone *receive* a
push once something sends one, which is what Part 4b sets up.

### 4b — Create the service account key

The Worker needs to authenticate to Google as something with permission to
send FCM messages. A service account is that "something".

1. Firebase Console → ⚙️ **Project settings** → **Service accounts** tab.
2. Click **Generate new private key**. Confirm. A `.json` file downloads.
3. Open it. You need exactly two fields out of it:
   - `client_email` — looks like `firebase-adminsdk-xxxxx@kinetixfsl-73d88.iam.gserviceaccount.com`
   - `private_key` — a long string starting `-----BEGIN PRIVATE KEY-----`

   **Treat this file like a password.** Anyone holding it can send a push as
   your app to any of your users. Don't commit it, don't paste it anywhere
   public. Delete the downloaded file once the next step is done.

### 4c — Add the secrets to your Worker

1. Cloudflare dashboard → **Workers & Pages** → **kinetix-upload** → **Settings → Variables and Secrets**.
2. Add three, all as **Secret** (not plain text):

   | Name | Value |
   | --- | --- |
   | `PUSH_SECRET` | Any string you make up — this is the app's own key for calling `/send-push`, unrelated to Google. Something like `kinetix-push-<random>` |
   | `FCM_CLIENT_EMAIL` | The `client_email` from the JSON file |
   | `FCM_PRIVATE_KEY` | The whole `private_key` value, **including** the `-----BEGIN PRIVATE KEY-----` / `-----END PRIVATE KEY-----` lines |

3. **Save and deploy.**

`FIREBASE_PROJECT_ID` is already set from Part 1 of `SETUP.md` and is reused
here — no new variable needed for it.

### 4d — Match the app's secret

[`PushSender.kt`](../app/src/main/java/com/example/kinetixfsl/community/inbox/push/PushSender.kt)
has a `PUSH_SECRET` constant hardcoded to `"kinetix-push-2026"`. Either:

- set the Cloudflare secret to that exact same value, **or**
- change the constant in `PushSender.kt` to whatever you set in Cloudflare,
  and rebuild the app.

Either way, the two have to match — same relationship as
`R2MediaUploader.DELETE_SECRET` and the Worker's `DELETE_SECRET` already have.

### 4e — Deploy the Worker

The `/send-push` route is already written into
[`web/worker.js`](worker.js) — nothing to author, just deploy it if you
haven't already:

1. Cloudflare dashboard → **kinetix-upload** → **Edit code**.
2. Select all, paste the current contents of `web/worker.js`.
3. **Deploy**.

### 4f — Grant the notification permission on the phone

Android 13+ won't show anything without it. The app asks on first launch. If you
tapped "Don't allow", it won't ask again — fix it at
**Settings → Apps → KinetixFSL → Notifications → Allow**.

### Test

1. Sign in on device A. Confirm `users/{uidA}.fcmToken` exists in Firestore
   (Console → Firestore → `users` → your document).
2. **Fully close** the app on device A (swipe it away).
3. From device B, send device A a message.
4. A banner should appear on device A within a few seconds.

If it doesn't:

- **Cloudflare dashboard → kinetix-upload → Logs** (turn on real-time logs,
  then trigger a message) — this is where a bad `FCM_PRIVATE_KEY` or a wrong
  `PUSH_SECRET` shows up, as a 401 or 500 on `/send-push`.
- If nothing hits the Worker at all: the notification write itself may be
  failing — recheck Part 1's rules.
- A **401** means `PUSH_SECRET` doesn't match between the app and the Worker
  (Part 4d).
- A **500** mentioning "service account is not configured" means
  `FCM_CLIENT_EMAIL` or `FCM_PRIVATE_KEY` is missing or malformed in Cloudflare.

---

## Part 5 — Online/offline dots (Realtime Database)

The green dot next to an avatar. Everything else in the app is Firestore; this
one thing is not, and it can't be.

**Why:** Firestore has no way to notice that a phone dropped off the network. A
user who force-quits or walks into a tunnel would stay "online" forever, because
nothing ever writes them offline. The Realtime Database has `onDisconnect()` —
it registers the write with the server *up front* and fires it when the socket
dies, however it dies. That one primitive is the whole reason this part exists.

Without this, the app simply shows no dots. Messaging is unaffected.

### Steps

1. Firebase Console → **Build → Realtime Database** → **Create Database**.
2. Location: pick the one nearest you (`asia-southeast1` for the Philippines).
3. Start in **locked mode**.
4. Open the **Rules** tab and replace with:

   ```json
   {
     "rules": {
       "presence": {
         "$uid": {
           ".read": "auth != null",
           ".write": "auth != null && auth.uid == $uid"
         }
       }
     }
   }
   ```

   Anyone signed in may see who's online; only you can say that *you* are.

5. **Publish**.
6. **Re-download `google-services.json`**: ⚙️ **Project settings** → **Your apps**
   → Android app → **google-services.json**. Replace `app/google-services.json`
   with it and rebuild.

   This step is the one people miss. The file currently in the repo has no
   `firebase_url` entry, which is how the app knows the Realtime Database
   doesn't exist. Presence stays silently off until the new file is in place —
   by design, so a missing database never crashes anything.

### Test

Open the app on device A and leave it on the Inbox. On device B, open a chat
with A — the header should show a green dot and "Active now". Force-quit A; the
dot should go within a few seconds.

---

## Part 6 — What's deliberately not built

Stated plainly so nobody goes looking for it:

- **Password-change notification.** The app uses Firebase's emailed reset link,
  so the password is changed in a browser, on a page this app never sees. There
  is no client-side moment to notify from. Doing it properly needs something
  running server-side on Auth's `beforeSignIn` blocking trigger, or watching for
  a `passwordUpdatedAt` change with the Admin SDK — both need a server, which is
  the same reason push notifications almost needed Blaze here. If it's ever
  worth building, the same Worker-based pattern from Part 4 (a small server
  holding a service-account key) is the template, this time reacting to an Auth
  event via a webhook instead of an FCM send.
- **Block and report.** The chat's ⋮ menu shows "coming soon" on purpose — both
  belong to a moderation queue on the admin web app, which doesn't exist yet.
- **Tapping a push to open the exact thread.** A push opens the app; it doesn't
  yet jump to the conversation. The payload already carries `type` and
  `targetId` and `KinetixMessagingService` already puts them on the launch
  intent — what's missing is `MainActivity` reading them and routing, the same
  way it routes share links today.
- **Multi-device push.** `users/{uid}.fcmToken` is one token, so pushes go to
  the most recently used device. A token *array* is the upgrade.
- **Message deletion.** Rules forbid it for both sides. Adding it means deciding
  whether "delete" means for you or for everyone, and cleaning up the R2 object.
