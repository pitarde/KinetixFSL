# How KinetixFSL Trains and Detects Filipino Sign Language

*A plain-language explainer of the machine learning pipeline behind the app — written so it can be explained to an adviser or panel without needing a machine learning background.*

---

## 1. The Big Picture

KinetixFSL recognizes two very different *kinds* of signs, and it trains a separate, specialized model for each kind rather than one giant model for everything.

| Sign type | Examples | What defines the sign | How it's collected | How it's trained |
|---|---|---|---|---|
| **Static** (a held pose) | Letters A–I, K–Y; Numbers 0–9 | The *shape* of the hand at one instant | Photos/images | A small Dense (fully-connected) neural network |
| **Dynamic** (a motion) | Letters J, Z, Ñ, NG; all word signs (Kamusta, Oo, Danger, Eat, …) | How the hand *moves and changes shape over time* | Short video clips | A 1D-CNN (convolutional network) over a sequence of frames |

This split exists because a static letter has no "before and after" — it's one pose, so one frame is enough. A dynamic sign like "Kamusta" only means something as a *sequence* of hand positions across roughly a second or two — a single frame from the middle of the wave looks like nothing.

Every model in the app is small enough to run **entirely on the phone**, with no internet connection needed. That offline capability is a deliberate design choice, not a limitation — the training pipeline was built specifically to produce models that match exactly what the phone's camera captures at runtime.

---

## 2. How a Hand Becomes Numbers (the "feature encoding")

Neither the training scripts nor the app ever look at raw pixels to decide what sign is being shown. Instead, everything goes through **MediaPipe Hand Landmarker**, a hand-tracking model (made by Google) that finds **21 key points** on a hand — the wrist, each knuckle, and each fingertip — and reports each one as an (x, y, z) coordinate.

```
        8   12   16   20
        |    |    |    |
        7   11   15   19
        |    |    |    |
    4   6   10   14   18
     \  |    |    |    |
      3 5    9   13   17
       \ \   |   /   /
        \ \  |  /   /
         \ \ | /   /
          \ \|/   /
     2 --- 0 (wrist)
    /
   1 (thumb)
```

So a single hand, in a single frame, becomes **21 points × 3 numbers (x, y, z) = 63 numbers**. That's the fundamental unit everything else is built from.

### 2.1 Making it fair — normalization

Raw coordinates are a problem: the same "letter A" sign looks like different numbers depending on whether your hand is close to the camera or far away, top-left of the frame or centered. To fix this, every hand is normalized the same way, in both the Python training scripts *and* the Android app's live camera code (this consistency is critical — more on that in Section 5):

1. **Subtract the wrist position** from all 21 points. Now the wrist sits at (0, 0, 0), and every other point is described *relative to the wrist* instead of relative to the camera.
2. **Divide by the wrist-to-middle-knuckle distance.** This makes a big hand and a small hand (or a hand close to the camera vs. far away) produce the same numbers, as long as the *shape* is the same.

This two-step process means the model learns "what does this handshape look like," not "where in the picture is this person standing" — which is exactly what we want for a sign to be recognized regardless of who is signing or where they're standing.

### 2.2 Two encoding variants — shape-only vs. shape-and-motion

There's one important variation on top of this:

- **Static signs and handshape-only motion letters** *erase* the wrist's raw position (it becomes exactly (0,0,0) after step 1), because the model should judge them purely by handshape.
- **Word signs and trajectory-based motion letters** *keep* the raw wrist position in one of those 63 slots instead of zeroing it. This is what lets the model see the hand's *path* across the frame — essential for a sign like "Kamusta" (a wave) or "NG" (a movement), where the motion itself carries the meaning, not just the final handshape.

### 2.3 Two hands at once

Word signs can use one or two hands. The app always looks for up to two hands and, when it finds two, sorts them by which one is more to the left — this ordering is deterministic and doesn't depend on MediaPipe's sometimes-unreliable left/right hand labels. Each hand gets its own 63-number block, giving **126 numbers per frame** for two-handed signs (a single hand's block is filled with zeros when only one hand is present, so a one-handed sign and a two-handed sign share the same input shape).

---

## 3. Static Signs — Letters and Numbers

### 3.1 Collecting

For a static sign, **one photograph = one training example**. The collection script (`collect_alphabet_static.py` / `collect_numbers_static.py`) does this for every image in a folder:

1. Load the image.
2. Flip it horizontally (mirror it) — because the app's front camera is mirrored, and if the training photo isn't flipped to match, the model would learn the sign backwards.
3. Run MediaPipe to find the hand.
4. Normalize it into the 63-number vector described above.
5. Save it, tagged with the letter/number it represents.

### 3.2 Training

The training script (`train_alphabet_static.py` / `train_numbers_static.py`) builds a small **Dense neural network** — no need for anything more complex, since a static pose has no time dimension to analyze:

```
Input (63 numbers)
  → Dense layer (128 neurons) → normalize → drop out some neurons (regularization)
  → Dense layer (64 neurons)  → drop out some neurons
  → Dense output layer (one neuron per letter/digit) → softmax (turns into probabilities)
```

**Key trick — automatic mirroring for both hands.** You only need to photograph a sign with *one* hand (whichever is natural for you). The training script then creates a **mirrored copy** of every image — flipping the x-coordinate of every point — and trains on *both* the original and the mirror. That one flip is mathematically exactly what a left-handed vs. right-handed signer looks like, so this single technique means the model learns to recognize the sign from **either hand** without you ever needing to record it twice.

On top of that, the script adds small random noise and slight hand-size scaling to the mirrored dataset ("jitter") — simulating natural human variation (a slightly different hand angle, a bit closer or farther from the camera) so the model doesn't memorize your exact hand too rigidly.

---

## 4. Dynamic Signs — Word Signs and Motion Letters

### 4.1 Collecting

Dynamic signs need **video**, not photos, because the motion itself is the sign. The collection script (`collect_from_video_category1.py` through `category5.py`, and `collect_from_video_letters.py`) processes each video clip like this:

1. Read the video and sample frames at a fixed rate — **5 frames per second**.
2. For each sampled frame: crop it to match the phone camera's 4:3 shape, mirror it, run MediaPipe, and normalize it (same math as above).
3. Collect frames until there are exactly **30 of them** (= 6 seconds at 5 fps). Shorter clips are padded with zero-frames; longer clips are cut off at 30.
4. The result is one **30 × 126 matrix** per clip — 30 time-steps, 126 numbers each — saved as "one take" of that sign.

This 5 fps / 30-frame design isn't arbitrary — it's chosen to exactly match what the phone will do live, which matters enormously (explained in Section 5).

### 4.2 Training — the 1D-CNN

Because a dynamic sign is a *sequence*, the model needs to look across time, not just at one frame. The architecture is a small **1D Convolutional Neural Network**:

```
Input (30 time-steps × 126 numbers)
  → Conv1D (96 filters) → normalize → pool (shrink time axis by half)
  → Conv1D (128 filters) → normalize → pool (shrink time axis by half again)
  → Conv1D (128 filters) → normalize → average across all remaining time-steps
  → Dense layer (64 neurons) → drop out some neurons
  → Dense output layer (one neuron per sign) → softmax
```

A 1D convolution slides a small window across the time axis, learning to recognize short "motion motifs" — like the upward flick that starts a wave — regardless of exactly which frame they happen to occur in. Stacking three of these lets the network build up from small motion fragments to the whole gesture.

### 4.3 The augmentation pipeline — the real engine behind small datasets

This is the most important part to understand and explain, because it's what makes training possible without needing hundreds of different people to record every sign. Four techniques run automatically, layered on top of each other:

**1. Mirroring (left ↔ right hand).** Just like the static case, but more involved, because a 126-number two-handed frame carries two kinds of coordinates:
   - The wrist-relative points flip sign (`x → -x`) — a straightforward mirror.
   - The *raw* wrist position flips around the center of the frame (`x → 1 - x`), because it lives in absolute image-space, not wrist-relative space.
   - When two hands are present, their **left/right slots swap** — mirroring the image makes what was your left hand appear on the right.

   Getting all three of these right *together* is what makes the mirrored copy physically correct instead of nonsense. One recording, correctly mirrored, teaches the model both a left-handed and right-handed version of the sign.

**2. Slot-swap synthesis.** The app always scans for *two* hands, even on a one-handed sign, in case a learner's idle hand drifts into frame. If every training clip was recorded with the idle hand carefully kept out of frame, the model would never see what that looks like and could get confused live. So the script creates synthetic copies where the signing hand is deliberately moved to the "other" slot — teaching the model that layout without you ever needing to film it that way.

**3. Time-warping (speed variation).** Every signer has a different tempo. This step resamples a clip to a randomly faster or slower speed (75%–130% of original), then pads or trims it back to 30 frames — simulating a faster or slower performer from a *single* recording.

**4. Jitter (small random noise).** Tiny random shifts in position, hand size, and landmark coordinates, applied after time-warping — simulating natural hand-tracking noise and small performance variation, without changing what sign is being shown.

Put together, one raw recording can become roughly a dozen effectively different training examples (original + mirror, each further multiplied by several jittered/time-warped copies), all while remaining faithful to the actual sign. This is what makes it realistic to train a good model from a modest number of recordings per sign.

### 4.4 Motion letters — a special case (J, Z, Ñ, NG)

These four letters are technically part of the "alphabet," but they involve movement, so they're trained with the **dynamic** pipeline (30-frame sequences, one-handed 63-number version) instead of the static one. Early on, these letters used the shape-only encoding (wrist zeroed every frame) and NG in particular was hard to detect reliably — because NG's meaning lives almost entirely in its *movement*, and the shape-only encoding was throwing that information away before the model ever saw it. Switching motion letters to the wrist-*preserving* encoding (Section 2.2) fixed this, because it let the model finally see the hand's path, not just its shape.

---

## 5. Real-Time Detection Inside the App — Full Pipeline

This is the part most worth walking an adviser through, because it demonstrates that the "offline, real-time" claim in the manuscript is actually implemented, not just aspirational.

### Step 1 — Camera capture
The front camera streams frames continuously via CameraX. Frames are throttled to roughly **20 frames per second** for the on-screen hand skeleton (fast enough to feel responsive even during quick motion signs like "Drink"), independent of how often a frame is actually fed to the classifier (see Step 4).

### Step 2 — Hand landmark extraction (MediaPipe)
Each frame goes through MediaPipe's Hand Landmarker, which finds up to two hands and returns their 21 points each. Two performance choices matter here:
- **GPU delegate, with automatic CPU fallback.** The app tries to run detection on the phone's GPU first, which is significantly faster than CPU — this is what makes the live skeleton keep up with fast hand motion. If a device's GPU driver doesn't support it, the app silently falls back to CPU instead of crashing.
- **VIDEO running mode with frame-to-frame tracking.** Instead of re-detecting the hand from scratch on every single frame (expensive), MediaPipe tracks the hand it already found and only re-runs full detection occasionally. This is a major speed advantage over naively processing each frame independently.

### Step 3 — Encoding (identical math to training)
The detected landmarks are normalized using **the exact same formula** used when building the training data (Section 2): subtract the wrist, divide by hand size, and either zero or preserve the raw wrist depending on whether the current sign is static or dynamic. This identical-math guarantee between training and inference is what makes the trained model actually applicable to what the phone sees live — any mismatch here (different cropping, different normalization) would silently degrade accuracy no matter how good the training data was.

### Step 4 — Buffering and feeding the classifier
- For a **static** sign, every processed frame is classified immediately — there's no sequence to build, just "what shape is the hand right now."
- For a **dynamic** sign, frames are pushed into a **sliding window buffer** of 30 frames, but only at **5 frames per second** — matching the training data's cadence exactly, even though the camera/overlay itself is sampled faster. Once at least 15 frames (half the window) have been collected, the buffer is already fed to the classifier on every subsequent frame — this is what allows the app to recognize a sign *before* the full 30-frame window is even complete.

### Step 5 — Classification
The 63 or 126 numbers (single frame for static, or the 30-frame buffer for dynamic) are fed into the corresponding `.tflite` model — the exact same architecture trained in Python, converted into a compact format that runs natively on the phone's processor. The output is a probability for each possible sign in that category (e.g., 92% Kamusta, 5% Salamat, 3% something else).

### Step 6 — Decision logic (early-exit, forgiving, timeout)
Rather than waiting a fixed amount of time and then judging once, the app runs a continuous decision loop during each practice attempt:
- **Instant success** — the moment the correct sign is predicted above a 65% confidence threshold, the attempt is marked correct immediately, without waiting for the rest of the window.
- **Forgiving retries** — a wrong or low-confidence guess never fails the attempt outright; the buffer keeps sliding and re-evaluating, so a learner can adjust mid-attempt.
- **Best-guess tracking** — the highest-confidence prediction seen anywhere during the whole attempt is remembered, so if nothing reaches the threshold, the feedback can show the closest match instead of a blank failure.
- **Timeout fallback** — after 5 seconds with no confident match, the attempt ends gracefully and shows that closest guess.

This whole loop — camera → GPU-accelerated landmark tracking → identical-to-training encoding → sliding-window buffering at the training frame rate → on-device inference → confidence-based decision — runs **entirely offline**, with no data leaving the phone and no network dependency, which is the offline-first requirement the manuscript specifies.

---

## 6. Why a Local Python Script Instead of Google Colab

This is a legitimate, defensible engineering decision, not just a convenience preference. Here's the honest comparison:

| Concern | Local Python Script (what this project uses) | Google Colab |
|---|---|---|
| **Matching the app exactly** | The script runs the *identical* MediaPipe model version, cropping, and normalization math as the Android app, because both were written and tested together, side by side. | Colab runs in Google's cloud environment; keeping every version and preprocessing step in perfect sync with the Android app requires extra discipline and is easy to let drift. |
| **Iterating on a real problem** | When a specific sign like "NG" or "Drink" is misdetected, the script can be modified, re-run, and re-checked against the *actual saved dataset* in seconds, right where the data lives. | Every iteration means re-uploading data or re-mounting Google Drive, which is slower and creates version-control headaches for the dataset itself. |
| **Data stays local and private** | Recorded videos of people signing (a form of personal biometric-adjacent data) never leave the researcher's machine. | Uploading recordings to Google's servers to train in Colab raises privacy/data-handling questions that don't exist with a fully local pipeline — relevant for a thesis dealing with real human subjects' recordings. |
| **No session limits** | The script runs for as long as needed, with no forced disconnects. | Free Colab sessions can disconnect after a period of inactivity or after a time limit, which can interrupt a long training run without warning. |
| **Reproducibility for the manuscript** | Anyone can re-run `python train_category1.py` on the same machine and get the same result — useful for defending methodology to a panel. | Colab notebooks can silently behave differently depending on which GPU Google assigns that session, and free-tier GPU availability isn't guaranteed. |
| **Direct control over TFLite compatibility** | The script includes an explicit check (`check_opcode_versions`) that verifies the exported model will actually load with the exact TensorFlow Lite runtime version pinned in the Android app — catching an incompatibility *before* it becomes a runtime crash on a phone. | This kind of tight, app-specific compatibility checking is easy to skip in a generic Colab notebook, since Colab isn't aware of the target Android app's dependency versions at all. |
| **Cost** | Free — uses the researcher's own CPU (and GPU, if available). | Free tier is limited and usage-capped; a paid tier is needed for guaranteed GPU access and longer runtimes. |

**In one sentence for the adviser:** *A local script was used instead of Google Colab because the entire pipeline — from how a video frame is cropped and normalized to which TensorFlow Lite operations are allowed — had to match the Android app exactly, and building that pipeline locally, next to the app's own source code, made it possible to test, debug, and fix a specific misdetected sign in minutes instead of re-uploading data to the cloud each time.*

---

## 7. Quick Reference — What's Trained, and How

| Category | Type | Encoding | Frames | Model | Status |
|---|---|---|---|---|---|
| Filipino Alphabet (A–I, K–Y) | Static | 63-dim, wrist-zeroed | 1 (image) | Dense network | ✅ Trained |
| Motion Letters (J, Z, Ñ, NG) | Dynamic | 63-dim, wrist-preserving | 30 | 1D-CNN | ✅ Trained |
| Numbers (0–9) | Static | 63-dim, wrist-zeroed | 1 (image) | Dense network | ✅ Trained |
| Greetings (Kamusta, Salamat, …) | Dynamic | 126-dim, two-handed | 30 | 1D-CNN | ✅ Trained |
| School (Pag-aaral, Basahin, …) | Dynamic | 126-dim, two-handed | 30 | 1D-CNN | ✅ Trained |
| Emergency (Danger, Stop, …) | Dynamic | 126-dim, two-handed | 30 | 1D-CNN | 🔲 Scripts ready, not yet trained |
| Daily Needs (Eat, Drink, …) | Dynamic | 126-dim, two-handed | 30 | 1D-CNN | 🔲 Scripts ready, not yet trained |
| Social Interaction (Oo, Hindi, …) | Dynamic | 126-dim, two-handed | 30 | 1D-CNN | 🔲 Scripts ready, not yet trained |

> **Note on Greetings:** an experimental upgrade to the Greetings collection/training scripts adds a 7-point upper-body pose block (147 numbers per frame instead of 126) to help distinguish signs that use similar handshapes but different body positioning. This upgrade is implemented on the Python side but **not yet wired into the Android app**, which still runs the standard 126-number version for Greetings. It's mentioned here for completeness, not as something currently active in the shipped app.

---

## 8. One-Paragraph Summary (for a quick verbal explanation)

*"Every sign in KinetixFSL is reduced to a set of numbers describing hand shape and, when relevant, hand position — extracted using Google's MediaPipe hand-tracking model. Static signs (letters, numbers) are one snapshot of those numbers, classified by a small neural network. Dynamic signs (word signs, motion letters) are a 30-frame sequence of those numbers, classified by a network that looks across time. All the training data is collected and processed by Python scripts we wrote ourselves — deliberately kept local rather than using Google Colab, so the exact same math used to build the training data is guaranteed to match what the Android app does live, frame for frame. Automatic mirroring, speed variation, and noise injection multiply a modest number of recordings into a much richer training set without needing dozens of different people to record every sign. At runtime, the phone runs the same MediaPipe extraction (accelerated by the GPU when available) and feeds it into the trained model completely offline, with an early-exit/timeout decision loop that gives learners fast, forgiving feedback instead of a rigid pass/fail."*
