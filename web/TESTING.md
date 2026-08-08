# Testing the Inbox — messaging & notifications

A checklist to confirm every piece from `MESSAGING_SETUP.md` actually works,
in the order things depend on each other. If something fails, the section
number tells you which Setup part to recheck.

**You need two accounts.** Almost everything here is "account A does something,
account B should see it" — one phone signed into two accounts, or two
phones/emulators, either works. Two emulators is easiest if you have one:
`Tools → Device Manager → Create Device` for a second, boot both, sign into a
different account on each.

Call the two **A** and **B** for the rest of this doc.

---

## 0. Before you start

- [ ] Both accounts have opened the app at least once (so `ensureUserProfile`
      has written their `users/{uid}` document).
- [ ] Firestore rules are published (`MESSAGING_SETUP.md` Part 1).
- [ ] You know how to see Logcat for at least one device, in case something
      needs debugging (`adb logcat` or Android Studio's Logcat panel).

---

## 1. In-app notifications (no push needed yet)

Tests the Firestore rules and the trigger wiring in `CommunityRepository`.
Everything here should show up in **Inbox → Notification** within a second or
two — these are live listeners, not something you need to refresh.

| # | Action (on A) | Expected (on B) |
|---|---|---|
| 1.1 | A follows B | B gets a **Follow** notification: "*A's name* started following you" |
| 1.2 | A upvotes B's post | B gets a **Like**: "upvoted your post". Downvoting produces nothing — that's correct, not a bug |
| 1.3 | A comments on B's post | B gets a **Comment**: "commented on your post" |
| 1.4 | A replies to B's comment (on anyone's post) | B gets a **Comment**: "replied to your comment" — note this notifies the comment's author, not necessarily the post's author |
| 1.5 | A comments `@BsDisplayName nice work` on any post | B gets a **Mention** — see the autocomplete steps below |
| 1.6 | A posts inside a community B has joined | Every other member (including B) gets an **Announcement** |
| 1.7 | Sign in as a fresh account | That account gets a **System** welcome notice within a few seconds of first launch |
| 1.8 | Sign into an existing account on a *second* device/emulator | That account gets a **System** "new device" notice — but only if it already has at least one other device on record. The very first device an account ever uses does **not** get this notice (it would be telling you your own signup was suspicious) |

**Retesting 1.8 after the device-recording fix:** the first device is only
recorded once the app has opened with the fix installed. So the order matters:

1. Install the updated app on phone A and open it (this records A).
2. Confirm it worked: Firebase Console → Firestore → `users/{uid}/devices`
   should now have exactly one document.
3. *Then* sign in on phone B. The notice appears on that account.

If you sign in on B before A has ever recorded itself, B becomes "the first
device" and correctly stays silent. Delete the whole `devices` subcollection in
the console to start this test over.

### Mention autocomplete (test 1.5 in detail)

A must follow B first — the picker offers **only accounts you follow**. B
following A is not enough and is not meant to be: following is one-sided, so a
list built on your followers is effectively open to strangers. Following back is
the deliberate act that puts someone in your mention list.

(The direct-message picker is intentionally wider — it *does* include people who
follow you, so you can reply to someone who messaged you first.)

- [ ] In a comment composer, type `@` — a horizontal strip of suggestions
      appears just above the toolbar, showing everyone you can mention
- [ ] Keep typing part of B's name — the strip narrows to matching people
- [ ] Tap B in the strip — `@B's Full Name ` is inserted into the comment and
      the strip disappears
- [ ] Post the comment — B gets a **Mention** notification
- [ ] **Multi-word names now work.** Pick someone whose display name has a
      space in it and confirm the notification still arrives — this is the
      case that used to fail
- [ ] Type `user@example.com` in a comment — the strip should **not** appear
      (an email address isn't a mention)
- [ ] Pick someone from the strip, then delete their name from the text before
      posting — they should **not** get a notification

Typing a name by hand without using the picker still works, but only for
single-word display names — there's no way to tell where a multi-word name
ends without the picker having said so. Use the strip and it always works.

**Checks while testing this section:**

- [ ] Tapping a Follow notification opens that person's profile
- [ ] Tapping a Like/Comment/Mention/Announcement notification opens the post
- [ ] Tapping **any** row — including a System/Welcome notice, which has
      nowhere to navigate to — drops it from bold to normal and decreases the
      badge by one
- [ ] The unread count badge on the **Inbox** bottom-nav icon matches the
      number of unread rows
- [ ] Opening the Notification tab clears the badge and marks everything read
      (rows go from bold to normal weight). This should happen **instantly**,
      not after a delay — the list is updated locally without waiting on the
      server, so it also works with the phone in airplane mode
- [ ] "Clear all" empties the list
- [ ] Swiping/tapping the ✕ on one row deletes just that row

If nothing arrives at all: open Firebase Console → Firestore → look for a
`notifications/{B's uid}/items` collection. If it's empty, the write itself is
being rejected — recheck the rules (Setup Part 1). If the document exists but
the app shows nothing, you're probably looking at the wrong account in the app.

---

## 2. Direct messages

### 2.1 Starting a thread

- [ ] From B's profile (opened via A's account), tap **Message** — a chat
      screen opens
- [ ] From **Inbox → Chat**, tap the pencil icon (top right) — a "New message"
      sheet opens listing people you follow or who follow you
- [ ] If A follows nobody and nobody follows A, the sheet should say so rather
      than showing an empty list with no explanation

### 2.2 Sending

- [ ] Type text on A, tap send — it appears immediately in A's own view (no
      wait for the round trip)
- [ ] The same message appears on B's device within a couple of seconds,
      without B needing to reopen the chat
- [ ] The **Inbox → Chat** list on B now shows this conversation at the top,
      with the message as the preview line, and an unread badge
- [ ] Opening the thread on B clears that badge and marks the message read

### 2.3 Media

- [ ] Tap the photo icon, pick an image, send — the bubble appears in the
      thread **immediately**, dimmed, labelled "Sending…", showing the photo
      from your own phone before any upload has finished
- [ ] While that's still uploading, type and send another message — the
      composer must stay usable the whole time, and the second message can
      overtake the first
- [ ] When the upload finishes the dimmed bubble becomes a normal one
- [ ] Turn on airplane mode and send a photo — the bubble should end up marked
      **"Not sent"** with **Retry** and **Discard** beside it, never silently
      vanish. Turn networking back on and tap Retry; it should go through
- [ ] Tap the video icon, pick a short clip, send — check Cloudflare R2
      (**dashboard → your bucket → Objects**) for a new file. If you did the
      optional Setup step 3a, it's under `messages/`; otherwise under
      `images/` or `videos/` same as posts
- [ ] Tap a received photo bubble — it opens **full screen** on a black
      background with a close button top-left; back also closes it
- [ ] Tap a received video bubble — it opens full screen and **plays with
      sound and seek controls**

### 2.3b Links

- [ ] Send a message containing `https://google.com` — it renders underlined
      and tapping it opens the browser
- [ ] Send one containing a bare `google.com` (no `https://`) — it should
      still be tappable and still open correctly
- [ ] Send `Check google.com, it's good.` — the link must stop before the
      comma, not swallow it
- [ ] Send a message with no link at all — it should look completely normal

### 2.4 Typing & presence

- [ ] Start typing on A (don't send) — B's chat header should show
      "typing…" within ~1 second
- [ ] Stop typing and wait ~3 seconds without sending — B's header should
      drop back to "Active now" or blank
- [ ] Sending a message should immediately clear the typing indicator, even
      if you were mid-debounce

**Presence (green dot) only works if you completed Setup Part 5** (Realtime
Database). If you skipped it:
- [ ] No dot ever appears, no "Active now" text, and nothing crashes — this is
      the expected silent-degradation behavior, not a bug

If you did set up Part 5:
- [ ] With both apps open, B should see a green dot on A's avatar in the chat
      header and in the Inbox list
- [ ] Force-quit A's app (swipe it away, not just background it) — B's dot
      should disappear within a few seconds

### 2.5 Read receipts

- [ ] A sends a message. While B has the chat open, it's marked read almost
      immediately — check A's side: under A's own last bubble, "Seen" with a
      double-checkmark should appear
- [ ] If B has the app closed or is on a different screen, A should **not**
      see "Seen" until B actually opens that thread

### 2.6 Search & day separators

- [ ] In **Inbox → Chat**, typing part of a contact's name into the search box
      filters the list to matching threads only (matches on name, not message
      content — that's intentional)
- [ ] In an open chat, send a message today; if you can, back-date test by
      checking a thread with older messages — you should see "Today",
      "Yesterday", or a weekday/date label between groups of messages from
      different days

---

## 3. Push notifications (banner when the app is closed)

This is the part that needs Setup Part 4 (Cloudflare Worker `/send-push`,
`FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY`, `PUSH_SECRET`) done and deployed.
Everything in sections 1–2 above works without this — push is purely "did a
banner appear while the app wasn't open."

### 3.1 Confirm the token registered

- [ ] Firebase Console → Firestore → `users/{A's uid}` → confirm an `fcmToken`
      field exists and looks like a long string. If it's missing, `onResume`
      hasn't run yet — just open the app once and check again.

### 3.2 Confirm the phone can receive notifications at all

- [ ] Android Settings → Apps → KinetixFSL → Notifications → confirm it's
      **allowed**. If you tapped "Don't allow" on first launch, the app won't
      ask again — you have to flip this manually.

### 3.3 The actual test

1. On device A: **fully close the app** — swipe it away from Recents, not
   just press Home.
2. On device B: send A a direct message (or do anything from section 1's
   table — a follow, an upvote, etc.).
3. Within a few seconds, a system notification banner should appear on
   device A, even though the app isn't running.
4. Tap the banner — it should open the app (it currently opens to the app's
   normal launch screen, not straight to the thread — see the "not built yet"
   note in `MESSAGING_SETUP.md` Part 6).

### 3.4 If no banner appears

Check in this order — each one rules out a different layer:

1. **Cloudflare dashboard → kinetix-upload → Logs** (enable "Begin log
   stream" first, then trigger step 3.2's test again). Look for a request to
   `/send-push`:
   - **No request logged at all** → the write to
     `notifications/{uid}/items` may itself be failing. Go back to section 1
     and confirm those tests pass first — push always follows an in-app write,
     never happens on its own.
   - **401 Unauthorized** → `PUSH_SECRET` doesn't match between
     `PushSender.kt` and the Cloudflare secret. Recheck Setup 4d.
   - **500 with "service account is not configured"** → `FCM_CLIENT_EMAIL` or
     `FCM_PRIVATE_KEY` is missing or malformed in Cloudflare. Recheck 4b/4c —
     a common mistake is pasting the private key without the
     `-----BEGIN/END-----` lines.
   - **200 but still no banner** → the push reached FCM successfully; the
     phone is the problem. Recheck 3.1 (token exists) and 3.2 (permission
     granted). Also confirm you're testing on a **real device or an emulator
     with Google Play services** — a bare AOSP emulator image can't receive FCM
     at all.
2. If Cloudflare's logs show nothing whatsoever reaching `/send-push`, add a
   temporary breakpoint or log line in `PushSender.send()` on the Android side
   to confirm it's even being called and isn't silently swallowing an
   exception (it's written to be best-effort/silent by design, which is great
   for users and slightly annoying for debugging — this is the one place it's
   worth temporarily loosening that).

---

## Quick reference: what "done" looks like

| Layer | Works without any extra setup? |
|---|---|
| Section 1, in-app notifications | Yes — only needs Firestore rules published (Part 1) |
| Section 2.1–2.3, 2.5–2.6, chat text/media/receipts | Yes — same, just Part 1 |
| Section 2.4, typing indicator | Yes — no extra setup, it's plain Firestore |
| Section 2.4, presence (green dot) | **No** — needs Realtime Database, Part 5. Silently absent otherwise |
| Section 3, push banners | **No** — needs the Cloudflare Worker secrets, Part 4. In-app notifications still work without it |

If sections 1 and 2 (minus the green dot) all pass, your core feature is done
and correct — push is the last mile on top, not a prerequisite for anything
else working.
