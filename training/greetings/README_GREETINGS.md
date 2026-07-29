# Greetings & Courtesies — Dynamic Sign Pipeline

Produces `fsl_greetings.tflite` for the **Greetings & Courtesies** module:
Kamusta, Welcome, Salamat, Pakiusap.

Same venv as the numbers pipeline — nothing new to install except `tflite`:

```powershell
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

## What's different from the numbers module

| | Numbers | Greetings |
|---|---|---|
| Sample | one frame | 30-frame sequence (6 s at 5 fps) |
| Hands | 1 | up to 2 |
| Features/frame | 63 | **126** |
| Model | Dense | 1D-CNN over time |
| File | `numbers_landmarks.csv` | `greetings_sequences.npz` |

### The 126-dim frame

Two 63-float hand blocks end to end. Slot 0 is the hand with the smaller
wrist x (leftmost in the mirrored frame); slot 1 is the other. One hand
detected goes in slot 0 and slot 1 stays zero, so one- and two-handed signs
share a single tensor shape.

Inside each block, **index 0 holds the raw wrist position** in 0..1 image
space instead of the (0,0,0) it would otherwise always be. That one slot is
what preserves *trajectory*. Without it every frame is re-centred on its own
wrist, and a wave, a sweep and a circle all become "open palm held still" —
which is most of what separates these four signs.

Hand ordering is geometric, never MediaPipe's handedness label. The frame is
mirrored before detection, so that label is flipped and would scramble the
two blocks.

## 1. Collect

```powershell
python collect_landmarks_category1.py --label Kamusta
python collect_landmarks_category1.py --label Welcome
python collect_landmarks_category1.py --label Salamat
python collect_landmarks_category1.py --label Pakiusap
```

`SPACE` starts a take (3-2-1 countdown, then 6 s of recording). After each
take: `K` keeps it, `D` discards. `Q` saves and quits. Takes append to the
same `.npz`, so you can stop and resume.

**30+ takes per sign is the floor.** Motion varies far more than a static
pose — this is not the place to save time.

Recording a take well:

- start at rest, perform the sign once, return to rest
- fill the 6 seconds; don't rush the sign into the first second then freeze
- keep **both** hands in frame for two-handed signs, including the idle one
- stand back far enough that your hands never leave frame at full extent
- vary speed, distance, angle, lighting and clothing between takes

The review screen counts frames where no hand was found. More than ~30% and
it tells you to discard — that take is mostly padding.

## 2. Train

```powershell
python train_category1.py
```

Options: `--epochs 250`, `--batch 32`, `--augment 8`.

Augmentation, in order: mirror every sequence, then add jittered +
time-warped copies of both originals and mirrors.

- **Mirror** flips relative coords (`x -> -x`), reflects the raw wrist
  (`x -> 1-x`, *not* a sign flip — it lives in 0..1 space), and swaps the two
  hand slots on frames where both hands are present. Verified to be an exact
  involution: mirroring twice returns the original.
- **Time warp** resamples to 0.75–1.30× speed and zero-pads the remainder,
  matching what the app produces on a partial buffer.
- **Jitter** adds landmark noise, per-sequence hand-size wobble, and a
  whole-sign translation so the model doesn't memorise screen position.

Split is 70/15/15 **before** augmentation. Val and test get the mirror but no
jitter, so the score reflects both-handed use honestly.

Output in `out/`:

| File | For |
|---|---|
| `fsl_greetings.tflite` | ships in the APK |
| `fsl_greetings_labels.txt` | ships in the APK — row order is the output index order |
| `fsl_greetings.keras` | re-convert later without retraining |
| `greetings_confusion_matrix.png` | Ch. 4 figure |
| `greetings_training_curves.png` | Ch. 4 figure |
| `greetings_metrics.txt` | per-class precision/recall/F1 |

## 3. Ship

```powershell
copy out\fsl_greetings.tflite       C:\Users\pitar\Documents\Kinetix-FSL\app\src\main\assets\
copy out\fsl_greetings_labels.txt   C:\Users\pitar\Documents\Kinetix-FSL\app\src\main\assets\
```

Then **Build → Clean Project**, **Build → Rebuild Project**. The app already
routes `greetings` to this model and switches to 2-hand extraction on its own.

## Read these numbers before trusting the model

The script prints three things the overall accuracy hides:

1. **As-recorded vs mirrored accuracy.** A gap over 10% means the mirror
   augmentation isn't carrying and left-handed learners will struggle.
2. **Partial-buffer accuracy** at 15/20/25/30 frames. The app starts
   classifying at 15 frames with the rest zero-padded, so a model that only
   works on the full window will feel broken in practice. If accuracy at 15
   is poor, raise `MIN_FRAMES_FOR_EARLY` in `DynamicSignClassifier.kt`.
3. **Confusion off-diagonals.** Salamat and Pakiusap both start near the
   face/chest; if they trade errors, that's the pair to record more of.

## Parity — do not break this

`collect_landmarks_category1.py:encode_frame()` and
`HandLandmarkHelper.detectTwoHandsWithLandmarks()` must stay identical:
mirror the frame, sort hands by wrist x, per-hand wrist-relative +
`/‖L9‖` normalization, raw wrist written back into index 0, zero-fill the
missing hand. Capture rate must stay 5 fps in both (`FRAME_INTERVAL` here,
`FRAME_INTERVAL_MS = 200` in `CameraPracticeScreen.kt`), and
`SEQUENCE_LENGTH` must stay 30 on both sides.

A mismatch throws no error. It trains to 95% and behaves randomly on the phone.

## Not done here

`SignCategory.kt` still has no `steps` for the four greeting signs, so the
Learning Room shows the placeholder. Fill those in from the reference videos
you're sourcing — and have the SPED teacher validate them, same as the
dataset.

The other three word-sign modules (Responses, Inquiries, Commerce, Everyday)
currently fall back to the one-handed letters model, which can never match
their labels. They'll show low confidence rather than crash. Each needs its
own model; copy these two scripts and change the label list and filenames.
