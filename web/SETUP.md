# Shared post links — setup

Three things to configure outside the Android project. Do them in this order:
**Firestore rules → Cloudflare Worker → App Links.** Each part has a test at the
end; don't move on until that test passes.

Your values, filled in already:

| Thing | Value |
| --- | --- |
| Firebase project id | `kinetixfsl-73d88` |
| Firebase Web API key | `AIzaSyDKSVIOoUPN6xsE4sXmV5MpZrUeLC2zaus` |
| Worker host | `kinetix-upload.pitardeken2024.workers.dev` |
| Android package | `com.example.kinetixfsl` |
| Debug signing SHA-256 | `3D:90:CF:59:E2:16:E8:95:94:37:F0:BF:F5:E7:97:4A:DE:CB:93:5B:32:81:94:3B:76:30:F1:F0:96:1E:2A:75` |

---

## Part 1 — Firestore rules

The `posts` collection is the only collection this app uses, so these rules are
complete. They allow the public reads the web page needs, keep writes limited to
signed-in users, and let any signed-in user bump the counter fields (which is
what voting, commenting, and sharing do).

### Steps

1. Go to <https://console.firebase.google.com/> and open the **kinetixfsl-73d88**
   project.
2. Left sidebar → **Build** → **Firestore Database**.
3. Click the **Rules** tab at the top.
4. Select everything in the editor and replace it with the block below.
5. Click **Publish**. Wait for the "Rules published" confirmation.

```
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    // Fields any signed-in user may change on someone else's post.
    // Voting, commenting and sharing all work by incrementing these.
    function onlyCounterFieldsChanged() {
      return request.resource.data.diff(resource.data).affectedKeys()
        .hasOnly(['upvoteCount', 'downvoteCount', 'commentCount',
                  'shareCount', 'viewCount', 'score']);
    }

    match /posts/{postId} {
      // Public: the shared-link web page reads this without signing in.
      allow read: if true;

      // Only a signed-in user, and only as themselves.
      allow create: if request.auth != null
                    && request.resource.data.authorId == request.auth.uid;

      // The author can edit their own post; anyone signed in may move counters.
      allow update: if request.auth != null
                    && (resource.data.authorId == request.auth.uid
                        || onlyCounterFieldsChanged());

      allow delete: if request.auth != null
                    && resource.data.authorId == request.auth.uid;

      // One vote document per user, named with their uid.
      match /votes/{userId} {
        allow read: if request.auth != null && request.auth.uid == userId;
        allow write: if request.auth != null && request.auth.uid == userId;
      }

      // One share marker per user — this is what keeps the share count
      // to one per account. Create only: never updated, never deleted.
      match /shares/{userId} {
        allow read: if request.auth != null && request.auth.uid == userId;
        allow create: if request.auth != null && request.auth.uid == userId;
        allow update, delete: if false;
      }

      match /comments/{commentId} {
        allow read: if true;
        allow create: if request.auth != null
                      && request.resource.data.authorId == request.auth.uid;
        allow update, delete: if request.auth != null
                              && resource.data.authorId == request.auth.uid;
      }
    }

    // Nothing else is reachable.
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

### Test it

Still on the Rules tab, click **Rules Playground** and run:

- **Simulation type** `get`, **Location** `/posts/anything`, **Authenticated** OFF
  → must show **Allowed**. (This is what makes the web page work.)
- **Simulation type** `create`, **Location** `/posts/anything`, **Authenticated** OFF
  → must show **Denied**.

If the first one is denied, the rules didn't publish — re-check and republish.

---

## Part 2 — Cloudflare Worker

`web/worker.js` in this project **is your existing upload Worker with the share
routes already merged in** — your R2 upload code is in there unchanged. So this
is a straight copy-paste, no hand-editing.

The one thing worth knowing, in case you ever re-merge by hand: the share hook
sits **above** the `if (request.method !== "POST")` guard. The share routes are
GETs, so putting it below that guard would make every shared link return
`405 Method not allowed`.

### 2a. Paste the Worker

1. Go to <https://dash.cloudflare.com/> → **Workers & Pages**.
2. Click **kinetix-upload**.
3. Click **Edit code** (or **Quick edit**).
4. Select all of the existing code and delete it.
5. Open `web/worker.js` from this project, copy the whole file, paste it in.
6. Click **Save and deploy**.

### 2b. Add the environment variables

1. Leave the editor (**Save and deploy** first if it prompts).
2. On the Worker page: **Settings** → **Variables and Secrets**.
3. Add three **new** variables of type **Text**. Leave `KINETIX_BUCKET` and
   `PUBLIC_BUCKET_URL` exactly as they are — uploads need them.

   | Name | Value |
   | --- | --- |
   | `FIREBASE_PROJECT_ID` | `kinetixfsl-73d88` |
   | `FIREBASE_API_KEY` | `AIzaSyDKSVIOoUPN6xsE4sXmV5MpZrUeLC2zaus` |
   | `ANDROID_CERT_SHA256` | `3D:90:CF:59:E2:16:E8:95:94:37:F0:BF:F5:E7:97:4A:DE:CB:93:5B:32:81:94:3B:76:30:F1:F0:96:1E:2A:75` |

4. Click **Deploy**.

### Test it

1. **Uploads still work** — open the app, create a post with an image. If this
   fails, re-check that `KINETIX_BUCKET` and `PUBLIC_BUCKET_URL` are still set.
2. **Get a real post id.** Firebase Console → Firestore Database → `posts`
   collection → click any document → copy its **document ID** from the top.
3. In a browser, open:
   `https://kinetix-upload.pitardeken2024.workers.dev/p/PASTE_ID_HERE`
   You should see the post rendered with its title, body, and image.
4. Open:
   `https://kinetix-upload.pitardeken2024.workers.dev/.well-known/assetlinks.json`
   You should see JSON containing your fingerprint — **not** an empty
   `sha256_cert_fingerprints: []`.

**If step 3 shows "Post unavailable":** Part 1 didn't publish, or the API key /
project id variables are wrong. Check Part 1's Rules Playground test first.

---

## Part 3 — App Links

This makes the link open the app directly instead of showing a "which app?"
chooser. **Part 2's assetlinks test must pass before this will work.**

### Steps

1. Confirm Google can read your file — open this in a browser:
   ```
   https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://kinetix-upload.pitardeken2024.workers.dev&relation=delegate_permission/common.handle_all_urls
   ```
   You want `"maxAge"` and a statement listing `com.example.kinetixfsl`.
   If you see an error here, fix it before continuing — the phone uses this
   exact service.

2. Reinstall the app so Android re-runs verification (it only verifies on
   install, not on launch):

   ```bash
   ./gradlew installDebug
   ```

3. Check that Android verified it:

   ```bash
   adb shell pm get-app-links com.example.kinetixfsl
   ```

   You want `verified` next to the host. `legacy_failure` or `1024` means it
   didn't verify — go back to step 1.

### Test it

Send yourself a post link from the app's share button (via any messenger, or
just paste it into a note) and tap it.

- **App installed** → opens straight to the post. Back or X → community feed.
- **App not installed** → opens the web page. X → closes the tab.

You can also fire the link directly without messaging anyone:

```bash
adb shell am start -a android.intent.action.VIEW -d "https://kinetix-upload.pitardeken2024.workers.dev/p/PASTE_ID_HERE"
```

### If it opens the browser instead of the app

That's verification failing, not a code problem — the app still works if you
choose it from the chooser. The custom scheme always works regardless:

```bash
adb shell am start -a android.intent.action.VIEW -d "kinetix://post/PASTE_ID_HERE"
```

---

## When you make a release build

The fingerprint above is your **debug** key. A release APK is signed with a
different key, so links will stop opening the app until you add it:

1. Get the release fingerprint:
   ```bash
   keytool -list -v -keystore YOUR_RELEASE_KEYSTORE.jks -alias YOUR_ALIAS
   ```
2. Copy the `SHA256:` line.
3. In the Worker's `ANDROID_CERT_SHA256` variable, append it after a comma:
   ```
   3D:90:...:2A:75,AB:CD:...:EF:12
   ```
   Both certs stay listed, so debug and release builds both verify.
4. Redeploy the Worker, reinstall the app, re-run the `pm get-app-links` check.

If you publish through Google Play with Play App Signing, use the SHA-256 from
**Play Console → Release → Setup → App integrity**, not your local keystore's.
