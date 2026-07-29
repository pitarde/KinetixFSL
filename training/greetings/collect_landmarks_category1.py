"""
collect_landmarks_category1.py — Greetings & Courtesies (dynamic, 2 hands).

Records fixed-length landmark *sequences* for the four word signs:
Kamusta, Welcome, Salamat, Pakiusap.

Unlike the static number signs, each sample here is a whole motion:
SEQUENCE_LENGTH frames captured at CAPTURE_FPS, saved as one "take".
You perform the sign once per take.

Feature layout — must match HandLandmarkHelper.detectTwoHandsWithLandmarks()
in the Android app exactly:

    126 floats per frame = two 63-float hand blocks laid end to end.

    Slot assignment
      1 hand detected   -> slot 0, slot 1 all zeros
      2 hands detected  -> slot 0 = smaller wrist x (leftmost in the
                           already-mirrored frame), slot 1 = the other
      0 hands           -> all 126 zeros (frame still recorded)

    Per hand (63 floats)
      coords -= wrist                     (wrist-relative)
      coords /= ||coords[9]||             (scale by wrist -> middle MCP)
      coords[0] = raw wrist position      (0..1 image space)
      flatten

That last line matters. Index 0 would otherwise always be (0,0,0), so it
carries the raw wrist position instead. It is the only thing preserving the
sign's *trajectory* — without it every frame is re-centred on its own wrist
and a wave, a sweep and a circle all collapse to "open palm held still".

Hand ordering is geometric, never MediaPipe's handedness label: the frame is
mirrored before detection, so that label is flipped and would scramble the
two blocks.

Usage
-----
    python collect_landmarks_category1.py --label Kamusta
    python collect_landmarks_category1.py --label Salamat --takes 40

Controls
--------
    SPACE  start a take (3-2-1 countdown, then records)
    K      keep the take you just recorded
    D      discard the take you just recorded
    Q      quit and save

Output
------
    data/greetings_sequences.npz   (appended; X: (N, 30, 126), y: (N,))
"""

import argparse
import os
import sys
import time

import cv2
import mediapipe as mp
import numpy as np

from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision

# ── Config ──────────────────────────────────────────────────────────

HERE = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(HERE, "data")
NPZ_PATH = os.path.join(DATA_DIR, "greetings_sequences.npz")
TASK_PATH = os.path.join(HERE, "hand_landmarker.task")

LABELS = ["Kamusta", "Welcome", "Salamat", "Pakiusap"]

# Must match DynamicSignClassifier.SEQUENCE_LENGTH and the app's
# FRAME_INTERVAL_MS (200ms -> 5 fps).
SEQUENCE_LENGTH = 30
CAPTURE_FPS = 5.0
FRAME_INTERVAL = 1.0 / CAPTURE_FPS      # 0.2 s, same as the app
TAKE_SECONDS = SEQUENCE_LENGTH / CAPTURE_FPS   # 6 s per take

SINGLE_HAND_FEATURES = 63
TWO_HAND_FEATURES = 126

NUM_HANDS = 2
MIN_HAND_DETECTION_CONFIDENCE = 0.7
MIN_HAND_PRESENCE_CONFIDENCE = 0.7
MIN_TRACKING_CONFIDENCE = 0.3

MIRROR_FRAME = True  # app mirrors the front-camera frame before detection

COUNTDOWN_SECONDS = 3

HAND_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 4),
    (0, 5), (5, 6), (6, 7), (7, 8),
    (0, 9), (9, 10), (10, 11), (11, 12),
    (0, 13), (13, 14), (14, 15), (15, 16),
    (0, 17), (17, 18), (18, 19), (19, 20),
    (5, 9), (9, 13), (13, 17),
]


# ── Feature encoding (mirror of the Kotlin) ─────────────────────────

def encode_hand(landmarks) -> np.ndarray:
    """One hand -> 63 floats. See module docstring for the layout."""
    coords = np.array([[lm.x, lm.y, lm.z] for lm in landmarks], dtype=np.float32)

    wrist = coords[0].copy()
    coords -= wrist

    hand_size = float(np.linalg.norm(coords[9]))
    if hand_size > 0.0:
        coords /= hand_size

    # Index 0 would be (0,0,0) — reuse it for the raw wrist position
    # so the model can see where the hand is, not just its shape.
    coords[0] = wrist

    return coords.flatten()


def encode_frame(result) -> tuple[np.ndarray, list]:
    """One detection result -> 126 floats + the hands to draw."""
    features = np.zeros(TWO_HAND_FEATURES, dtype=np.float32)

    hands = list(result.hand_landmarks)
    if not hands:
        return features, []

    # Deterministic slot order: leftmost wrist first. Geometric on purpose —
    # MediaPipe's handedness label is flipped by the mirror above.
    if len(hands) >= 2:
        hands = sorted(hands, key=lambda h: h[0].x)[:2]

    for slot, hand in enumerate(hands):
        start = slot * SINGLE_HAND_FEATURES
        features[start:start + SINGLE_HAND_FEATURES] = encode_hand(hand)

    return features, hands


def draw_skeletons(frame, hands) -> None:
    h, w = frame.shape[:2]
    colors = [(0, 0, 255), (255, 140, 0)]  # slot 0 red, slot 1 orange
    for slot, hand in enumerate(hands):
        pts = [(int(lm.x * w), int(lm.y * h)) for lm in hand]
        for a, b in HAND_CONNECTIONS:
            cv2.line(frame, pts[a], pts[b], (255, 255, 255), 2)
        for p in pts:
            cv2.circle(frame, p, 4, colors[slot % 2], -1)


# ── Persistence ─────────────────────────────────────────────────────

def load_existing() -> tuple[np.ndarray, np.ndarray]:
    if os.path.exists(NPZ_PATH):
        d = np.load(NPZ_PATH, allow_pickle=True)
        return d["X"], d["y"]
    return (np.empty((0, SEQUENCE_LENGTH, TWO_HAND_FEATURES), dtype=np.float32),
            np.empty((0,), dtype=object))


def save(X: np.ndarray, y: np.ndarray) -> None:
    os.makedirs(DATA_DIR, exist_ok=True)
    np.savez_compressed(NPZ_PATH, X=X, y=y)


# ── Collection loop ─────────────────────────────────────────────────

def collect(label: str, target_takes: int, camera_index: int) -> None:
    if not os.path.exists(TASK_PATH):
        sys.exit(
            f"Missing {TASK_PATH}\n"
            "Copy it from the Android project:\n"
            "  app\\src\\main\\assets\\hand_landmarker.task"
        )

    X_all, y_all = load_existing()
    existing = int((y_all == label).sum()) if len(y_all) else 0
    print(f"  Existing takes for '{label}': {existing}")

    cap = cv2.VideoCapture(camera_index, cv2.CAP_DSHOW)
    if not cap.isOpened():
        sys.exit(f"Could not open camera index {camera_index}.")
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 960)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    options = mp_vision.HandLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=TASK_PATH),
        running_mode=mp_vision.RunningMode.IMAGE,
        num_hands=NUM_HANDS,
        min_hand_detection_confidence=MIN_HAND_DETECTION_CONFIDENCE,
        min_hand_presence_confidence=MIN_HAND_PRESENCE_CONFIDENCE,
        min_tracking_confidence=MIN_TRACKING_CONFIDENCE,
    )

    # State machine: idle -> countdown -> recording -> review -> idle
    state = "idle"
    countdown_start = 0.0
    take_frames: list[np.ndarray] = []
    last_frame_time = 0.0
    session_takes: list[np.ndarray] = []
    frames_with_no_hand = 0

    with mp_vision.HandLandmarker.create_from_options(options) as detector:
        while cap.isOpened():
            ok, frame = cap.read()
            if not ok:
                continue

            if MIRROR_FRAME:
                frame = cv2.flip(frame, 1)

            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            result = detector.detect(mp_image)

            features, hands = encode_frame(result)
            draw_skeletons(frame, hands)

            now = time.time()

            # ── State transitions ──
            if state == "countdown":
                remaining = COUNTDOWN_SECONDS - (now - countdown_start)
                if remaining <= 0:
                    state = "recording"
                    take_frames = []
                    frames_with_no_hand = 0
                    last_frame_time = 0.0

            elif state == "recording":
                if now - last_frame_time >= FRAME_INTERVAL:
                    take_frames.append(features)
                    if not hands:
                        frames_with_no_hand += 1
                    last_frame_time = now

                if len(take_frames) >= SEQUENCE_LENGTH:
                    state = "review"

            # ── HUD ──
            total = existing + len(session_takes)
            cv2.rectangle(frame, (0, 0), (frame.shape[1], 88), (30, 30, 30), -1)
            cv2.putText(frame, f"Sign: {label}", (12, 32),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.85, (255, 255, 255), 2)
            cv2.putText(frame, f"takes: {total}/{target_takes}   hands: {len(hands)}",
                        (12, 66), cv2.FONT_HERSHEY_SIMPLEX, 0.65, (200, 200, 200), 2)

            fh, fw = frame.shape[:2]

            if state == "idle":
                cv2.putText(frame, "SPACE = record a take    Q = save+quit",
                            (12, fh - 16), cv2.FONT_HERSHEY_SIMPLEX,
                            0.6, (220, 220, 220), 1)

            elif state == "countdown":
                remaining = COUNTDOWN_SECONDS - (now - countdown_start)
                n = max(1, int(np.ceil(remaining)))
                cv2.putText(frame, str(n), (fw // 2 - 40, fh // 2),
                            cv2.FONT_HERSHEY_SIMPLEX, 5.0, (0, 215, 255), 8)
                cv2.putText(frame, "get ready...", (12, fh - 16),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 215, 255), 2)

            elif state == "recording":
                done = len(take_frames)
                cv2.circle(frame, (fw - 40, 44), 14, (0, 0, 255), -1)
                bar_w = int((fw - 24) * done / SEQUENCE_LENGTH)
                cv2.rectangle(frame, (12, fh - 40), (12 + bar_w, fh - 20),
                              (0, 200, 0), -1)
                cv2.putText(frame, f"PERFORM THE SIGN  {done}/{SEQUENCE_LENGTH}",
                            (12, fh - 52), cv2.FONT_HERSHEY_SIMPLEX,
                            0.7, (0, 255, 0), 2)

            elif state == "review":
                empty = frames_with_no_hand
                bad = empty > SEQUENCE_LENGTH * 0.3
                msg = f"take done - {empty} frames had no hand"
                color = (0, 0, 255) if bad else (0, 200, 0)
                cv2.putText(frame, msg, (12, fh - 52),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2)
                cv2.putText(frame, "K = keep    D = discard",
                            (12, fh - 16), cv2.FONT_HERSHEY_SIMPLEX,
                            0.7, (255, 255, 255), 2)
                if bad:
                    cv2.putText(frame, "too many empty frames - discard this one",
                                (12, fh - 88), cv2.FONT_HERSHEY_SIMPLEX,
                                0.6, (0, 0, 255), 2)

            cv2.imshow("FSL Greetings - Sequence Collection", frame)

            # ── Keys ──
            key = cv2.waitKey(1) & 0xFF
            if key in (ord("q"), ord("Q"), 27):
                break
            elif key == ord(" ") and state == "idle":
                state = "countdown"
                countdown_start = now
            elif state == "review":
                if key in (ord("k"), ord("K")):
                    session_takes.append(np.array(take_frames, dtype=np.float32))
                    state = "idle"
                elif key in (ord("d"), ord("D")):
                    state = "idle"

            if target_takes and (existing + len(session_takes)) >= target_takes \
                    and state == "idle":
                print("\n  Target reached — press Q to save and quit.")

    cap.release()
    cv2.destroyAllWindows()

    if not session_takes:
        print("No takes kept — nothing written.")
        return

    X_new = np.stack(session_takes)                       # (n, 30, 126)
    y_new = np.array([label] * len(session_takes), dtype=object)

    X_all = np.concatenate([X_all, X_new]) if len(X_all) else X_new
    y_all = np.concatenate([y_all, y_new]) if len(y_all) else y_new

    save(X_all, y_all)

    print(f"\nSaved {len(session_takes)} new takes for '{label}'.")
    print(f"  {NPZ_PATH}")
    print("  totals:")
    for lab in LABELS:
        print(f"    {lab:<10} {int((y_all == lab).sum())}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Collect FSL Greetings sequences (2 hands, 30 frames).")
    parser.add_argument("--label", required=True, choices=LABELS)
    parser.add_argument("--takes", type=int, default=30,
                        help="Target total takes for this sign (default 30).")
    parser.add_argument("--camera", type=int, default=0)
    args = parser.parse_args()

    print(f"""
  Sign: {args.label}     target: {args.takes} takes
  Each take is {SEQUENCE_LENGTH} frames at {CAPTURE_FPS:.0f} fps = {TAKE_SECONDS:.0f} seconds.

  How to record a good take:
    - Start from a neutral rest position, perform the sign once, return to rest.
    - Fill the {TAKE_SECONDS:.0f} seconds. Don't rush the sign into the first second
      and then hold still — the model reads the whole window.
    - Keep BOTH hands in frame for two-handed signs, even the idle one.
    - Stand far enough back that your hands never leave the frame at full extent.
    - Vary between takes: speed, distance, angle, lighting, clothing.
    - 30+ takes per sign is a sensible floor. More is better for word signs
      than it was for static digits — motion has far more variation.

  SPACE to start a take.
""")
    collect(args.label, args.takes, args.camera)


if __name__ == "__main__":
    main()
