"""
collect_numbers.py — FSL Numbers (0-9) landmark collection.

Captures 21 MediaPipe hand landmarks from your webcam and writes a normalized
63-dim feature vector per frame into a CSV file.

Uses the MediaPipe **Tasks** HandLandmarker with the very same
`hand_landmarker.task` file the Android app bundles in assets/. That is
deliberate: training on one landmark model and running inference on another is
a silent accuracy killer. Keep the .task file next to this script.

CRITICAL: the normalization here must stay byte-for-byte equivalent to
HandLandmarkHelper.normalizeLandmarks() in the Android app:

    1. mirror the frame horizontally      (app does preScale(-1, 1) for front cam)
    2. coords -= coords[0]                (wrist-relative)
    3. coords /= ||coords[9]||            (scale by wrist -> middle-finger MCP)
    4. flatten to [x0,y0,z0, ..., x20,y20,z20]

If you change any of these, you must change the Kotlin too or the model will
score well in training and fail on-device.

Usage
-----
    python collect_numbers.py --label 0
    python collect_numbers.py --label 7 --samples 400

Controls
--------
    SPACE  start/pause recording
    R      discard the last 25 captured samples (fix a bad burst)
    Q      quit and save

Output
------
    data/numbers_landmarks.csv   (appended; columns: label, f0..f62)
"""

import argparse
import csv
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
CSV_PATH = os.path.join(DATA_DIR, "numbers_landmarks.csv")
TASK_PATH = os.path.join(HERE, "hand_landmarker.task")

VALID_LABELS = [str(d) for d in range(10)]

# Mirrors HandLandmarkHelper's HandLandmarkerOptions exactly.
NUM_HANDS = 1
MIN_HAND_DETECTION_CONFIDENCE = 0.7
MIN_HAND_PRESENCE_CONFIDENCE = 0.7
MIN_TRACKING_CONFIDENCE = 0.3

MIRROR_FRAME = True  # app mirrors the front-camera frame before detection

# Same skeleton the app draws in HandLandmarkOverlay.
HAND_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 4),            # thumb
    (0, 5), (5, 6), (6, 7), (7, 8),            # index
    (0, 9), (9, 10), (10, 11), (11, 12),       # middle
    (0, 13), (13, 14), (14, 15), (15, 16),     # ring
    (0, 17), (17, 18), (18, 19), (19, 20),     # pinky
    (5, 9), (9, 13), (13, 17),                 # palm
]


# ── Normalization (mirror of the Kotlin) ────────────────────────────

def normalize_landmarks(landmarks) -> np.ndarray:
    """Return the 63-dim feature vector for one detected hand."""
    coords = np.array(
        [[lm.x, lm.y, lm.z] for lm in landmarks],
        dtype=np.float32,
    )  # (21, 3)

    # 1. wrist-relative
    coords -= coords[0]

    # 2. scale by hand size (wrist -> middle finger MCP, landmark 9)
    hand_size = float(np.linalg.norm(coords[9]))
    if hand_size > 0.0:
        coords /= hand_size

    # 3. flatten
    return coords.flatten()  # (63,)


def draw_skeleton(frame, landmarks) -> None:
    """Draw the 21-point hand skeleton (the Tasks API has no drawing helper)."""
    h, w = frame.shape[:2]
    pts = [(int(lm.x * w), int(lm.y * h)) for lm in landmarks]

    for a, b in HAND_CONNECTIONS:
        cv2.line(frame, pts[a], pts[b], (255, 255, 255), 2)
    for p in pts:
        cv2.circle(frame, p, 4, (0, 0, 255), -1)


# ── Collection loop ─────────────────────────────────────────────────

def collect(label: str, target_samples: int, camera_index: int) -> None:
    if not os.path.exists(TASK_PATH):
        sys.exit(
            f"Missing {TASK_PATH}\n"
            "Copy it from the Android project:\n"
            "  app\\src\\main\\assets\\hand_landmarker.task"
        )

    os.makedirs(DATA_DIR, exist_ok=True)
    write_header = not os.path.exists(CSV_PATH) or os.path.getsize(CSV_PATH) == 0

    # CAP_DSHOW avoids the ~2s MSMF open delay on Windows.
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

    buffer: list[np.ndarray] = []
    recording = False
    last_capture = 0.0
    capture_interval = 0.05  # ~20 samples/sec; keeps poses varied, not identical

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

            hand_found = bool(result.hand_landmarks)

            if hand_found:
                landmarks = result.hand_landmarks[0]
                draw_skeleton(frame, landmarks)

                now = time.time()
                if recording and (now - last_capture) >= capture_interval:
                    buffer.append(normalize_landmarks(landmarks))
                    last_capture = now

            # ── HUD ──
            count = len(buffer)
            pct = min(100, int(100 * count / target_samples)) if target_samples else 0

            status = "RECORDING" if recording else "PAUSED"
            status_color = (0, 200, 0) if recording else (0, 165, 255)
            if not hand_found:
                status = "NO HAND"
                status_color = (0, 0, 255)

            cv2.rectangle(frame, (0, 0), (frame.shape[1], 78), (30, 30, 30), -1)
            cv2.putText(frame, f"Label: {label}", (12, 30),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
            cv2.putText(frame, f"{count}/{target_samples}  ({pct}%)", (12, 62),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (200, 200, 200), 2)
            cv2.putText(frame, status, (frame.shape[1] - 220, 44),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.9, status_color, 2)
            cv2.putText(frame, "SPACE start/pause   R undo 25   Q save+quit",
                        (12, frame.shape[0] - 16),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.55, (220, 220, 220), 1)

            cv2.imshow("FSL Numbers - Collection", frame)

            key = cv2.waitKey(1) & 0xFF
            if key == ord(" "):
                recording = not recording
            elif key in (ord("r"), ord("R")):
                del buffer[-25:]
            elif key in (ord("q"), ord("Q"), 27):
                break

            if target_samples and count >= target_samples:
                recording = False

    cap.release()
    cv2.destroyAllWindows()

    if not buffer:
        print("No samples captured — nothing written.")
        return

    with open(CSV_PATH, "a", newline="") as f:
        writer = csv.writer(f)
        if write_header:
            writer.writerow(["label"] + [f"f{i}" for i in range(63)])
        for vec in buffer:
            writer.writerow([label] + [f"{v:.6f}" for v in vec])

    print(f"Wrote {len(buffer)} samples for label '{label}' -> {CSV_PATH}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Collect FSL number landmarks.")
    parser.add_argument("--label", required=True, choices=VALID_LABELS,
                        help="Digit 0-9.")
    parser.add_argument("--samples", type=int, default=300,
                        help="Target sample count for this session (default 300).")
    parser.add_argument("--camera", type=int, default=0,
                        help="Webcam index (default 0).")
    args = parser.parse_args()

    print(f"""
  Collecting: {args.label}   target: {args.samples} samples

  Tips for a model that actually generalizes:
    - Rotate your hand slightly through the session (±20°), don't hold it frozen.
    - Move nearer/farther from the camera.
    - Change your background and lighting between sessions.
    - Record a session with your other hand too, if learners may sign either way.

  Press SPACE to start.
""")
    collect(args.label, args.samples, args.camera)


if __name__ == "__main__":
    main()
