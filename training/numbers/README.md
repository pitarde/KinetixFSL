# FSL Numbers (0–9) — Training Pipeline

Produces `fsl_numbers.tflite` for the **Numbers in Filipino** module.
Same static-sign approach as the alphabet model: MediaPipe 21-point hand
landmarks → 63-dim normalized vector → small Dense classifier.

## Setup

Your `.venv` was created but nothing was installed into it — that's the
`ModuleNotFoundError: No module named 'cv2'`. Install from PowerShell in this
folder:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Takes a few minutes — TensorFlow alone is ~300 MB. Verify:

```powershell
python -c "import cv2, mediapipe, tensorflow; print(cv2.__version__, mediapipe.__version__, tensorflow.__version__)"
```

Python 3.12 is fine (verified). `hand_landmarker.task` must sit next to the
scripts — it's already copied here from the Android project's assets folder,
and the collection script uses that exact file so training landmarks and
on-device landmarks come from the same model.

> **Note on MediaPipe 1.0.0:** it removed the legacy `mp.solutions` API that
> most tutorials use. `collect_numbers.py` uses the Tasks API
> (`mp.tasks.vision.HandLandmarker`) instead — same API as the Android app.
> Don't "fix" it back to `mp.solutions.hands`; that import no longer exists.

## 1. Collect

One session per digit. 300 samples each is a reasonable floor; 400+ is better.

```bash
python collect_numbers.py --label 0
python collect_numbers.py --label 1
...
python collect_numbers.py --label 9
```

Controls: `SPACE` start/pause · `R` undo last 25 · `Q` save and quit.
Valid labels are `0`–`9` only; anything else is rejected by argparse.

Everything appends to `data/numbers_landmarks.csv`, so you can stop and resume,
and re-run a digit later to add more variety without losing what you have.

The 10 digits are the only classes — no negative/NONE class, matching how the
rest of the modules are being trained. The model always returns one of `0`–`9`,
so rejection of non-number poses relies on the `CONFIRM_THRESHOLD` (0.65) and
`CONFIRM_FRAMES` (4) gate in `CameraPracticeScreen` rather than on the model
itself. If you see false confirmations on-device, raise `CONFIRM_THRESHOLD`.

### Getting data that survives contact with a real phone

The model only sees what you showed it. Vary deliberately:

- rotate the hand ±20° through the session, don't hold one frozen pose
- move nearer and farther from the camera
- change background and lighting between sessions
- record with both hands if learners may sign either way
- record a session for each participant, not just yourself

## 2. Train

```bash
python train_numbers.py
```

Options: `--epochs 200`, `--batch 64`, `--augment 6` (jittered copies per sample).

Writes to `out/`:

| File | What it's for |
|---|---|
| `fsl_numbers.tflite` | ships in the APK |
| `fsl_numbers_labels.txt` | ships in the APK — row order **is** the model's output index order |
| `confusion_matrix.png` | Ch. 4 figure; shows which digits get confused |
| `training_curves.png` | Ch. 4 figure; accuracy/loss over epochs |
| `metrics.txt` | per-class precision/recall/F1 for the writeup |

The split is 70/15/15 **before** augmentation, so jittered twins of a training
sample can't leak into the test set and inflate the number you report.

## 3. Ship

```
copy out\fsl_numbers.tflite       C:\Users\pitar\Documents\Kinetix-FSL\app\src\main\assets\
copy out\fsl_numbers_labels.txt   C:\Users\pitar\Documents\Kinetix-FSL\app\src\main\assets\
```

Rebuild. The app picks the model up automatically —
`SignClassifier.forCategory(context, "numbers")` is already wired through
`CameraPracticeScreen` from the NavHost. Until those two files exist, the
Numbers practice screen shows "This module's recognition model isn't available
yet" instead of crashing.

## What to check before you trust the accuracy number

1. **Per-class recall, not just overall accuracy.** A model that's 94% overall
   but 61% on `6` will feel broken to any learner practicing 6.
2. **The confusion matrix off-diagonals.** Digits that share a handshape family
   (`6/7/8/9` in many FSL variants, `1` vs `D`-like poses) will cluster there.
   If two digits are genuinely near-identical from a single frame, more data
   won't fix it — that pair may need the dynamic model instead.
3. **On-device before on-paper.** Test accuracy over your own recordings is the
   optimistic case. Install and try it on the phone, watch the "Seeing: X (n%)"
   debug overlay, and only then write the number into Ch. 4.

## Preprocessing parity — do not break this

`collect_numbers.py:normalize_landmarks()` and
`HandLandmarkHelper.normalizeLandmarks()` in the Android app must stay
identical:

```
mirror frame horizontally     # app: preScale(-1, 1) for the front camera
coords -= coords[0]           # wrist-relative
coords /= ||coords[9]||       # scale by wrist → middle-finger MCP
flatten → [x0,y0,z0, ... x20,y20,z20]
```

A mismatch here does not throw an error. It produces a model that trains to 97%
and behaves randomly on the phone. If you touch one file, touch the other.

## Not done here

The `steps` list for `num_0`–`num_9` in `SignCategory.kt` is still empty, so the
Learning Room shows the "Steps for this sign are being prepared" placeholder.
Those instructions should come from your licensed SPED teacher rather than being
written from a reference chart — same validation path as the dataset itself.
