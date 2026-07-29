"""
train_category1.py — trains the Greetings & Courtesies dynamic classifier.

Input : data/greetings_sequences.npz   (from collect_landmarks_category1.py)
Output: out/fsl_greetings.tflite
        out/fsl_greetings_labels.txt
        out/fsl_greetings.keras
        out/greetings_confusion_matrix.png
        out/greetings_training_curves.png
        out/greetings_metrics.txt

Architecture matches the existing fsl_dynamic model: a small 1D-CNN over the
time axis. Input is (SEQUENCE_LENGTH, 126) — 30 frames x two-hand features.

Usage
-----
    python train_category1.py
    python train_category1.py --epochs 250 --augment 8
"""

import argparse
import os

import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import train_test_split

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
NPZ_PATH = os.path.join(HERE, "data", "greetings_sequences.npz")
OUT_DIR = os.path.join(HERE, "out")

SEQUENCE_LENGTH = 30
SINGLE_HAND_FEATURES = 63
TWO_HAND_FEATURES = 126

SEED = 42
np.random.seed(SEED)
tf.random.set_seed(SEED)


# ── Feature-layout helpers ──────────────────────────────────────────
#
# Each frame is 126 floats = 2 hand blocks of 63.
# Within a block, index 0 is the RAW wrist position in 0..1 image space;
# indices 1..20 are wrist-relative, scale-normalized coordinates.
# The two obey different mirror rules, which is why this is spelled out.

def _blocks(X: np.ndarray) -> np.ndarray:
    """(N, T, 126) -> (N, T, 2, 21, 3) view-shaped copy."""
    n, t, _ = X.shape
    return X.reshape(n, t, 2, 21, 3)


def hand_present(X: np.ndarray) -> np.ndarray:
    """(N, T, 126) -> (N, T, 2) bool. A hand block of all zeros = absent."""
    b = _blocks(X)
    return np.abs(b).sum(axis=(3, 4)) > 0


def mirror_sequences(X: np.ndarray) -> np.ndarray:
    """Left<->right mirror of whole sequences.

    Three things have to happen together, or the mirrored sample is wrong:

      1. relative coords (landmarks 1..20)  x -> -x
         They are centred on the wrist, so reflection is a sign flip.

      2. raw wrist position (landmark 0)    x -> 1 - x
         It lives in 0..1 image space, so reflection is about x = 0.5,
         NOT a sign flip. Getting this wrong teaches the model that
         left-handed signers stand outside the frame.

      3. the two hand slots swap
         Slot order is "leftmost wrist first", so mirroring the image
         swaps which hand is leftmost. Only valid when both hands are
         present — a lone hand stays in slot 0 (the collector always
         puts a single hand there, whatever side it is on).

    Absent hands stay all-zero and are never touched; running 1-x over a
    zeroed block would fabricate a hand at x = 1.
    """
    b = _blocks(X).copy()
    present = hand_present(X)                      # (N, T, 2)

    # 1 + 2: reflect coordinates, per hand block, only where present.
    for slot in range(2):
        m = present[:, :, slot]                    # (N, T)
        if not m.any():
            continue
        b[:, :, slot, 1:, 0][m] *= -1.0            # relative x -> -x
        b[:, :, slot, 0, 0][m] = 1.0 - b[:, :, slot, 0, 0][m]  # wrist x -> 1-x

    # 3: swap slots only on frames where both hands are present.
    both = present[:, :, 0] & present[:, :, 1]     # (N, T)
    if both.any():
        slot0 = b[:, :, 0].copy()
        b[:, :, 0][both] = b[:, :, 1][both]
        b[:, :, 1][both] = slot0[both]

    n, t = X.shape[0], X.shape[1]
    return b.reshape(n, t, TWO_HAND_FEATURES).astype(np.float32)


# ── Augmentation ────────────────────────────────────────────────────

def jitter(X: np.ndarray) -> np.ndarray:
    """Per-sequence perturbations that preserve the sign's identity."""
    b = _blocks(X).copy()
    present = hand_present(X)
    n = X.shape[0]

    for slot in range(2):
        m = present[:, :, slot]
        if not m.any():
            continue

        # Landmark jitter on the relative coords only.
        rel = b[:, :, slot, 1:, :]
        noise = np.random.normal(0.0, 0.015, rel.shape).astype(np.float32)
        rel += noise * m[:, :, None, None]

        # Hand-size wobble, constant per sequence (a person doesn't
        # change hand size mid-sign).
        scale = np.random.uniform(0.92, 1.08, (n, 1, 1, 1)).astype(np.float32)
        rel *= scale
        b[:, :, slot, 1:, :] = rel

        # Whole-sign translation: shift the wrist track, same offset for
        # every frame, so the trajectory shape survives but its position
        # in frame varies. Keeps the model from memorising "wave happens
        # in the top-left corner".
        dx = np.random.uniform(-0.06, 0.06, (n, 1)).astype(np.float32)
        dy = np.random.uniform(-0.06, 0.06, (n, 1)).astype(np.float32)
        b[:, :, slot, 0, 0] += dx * m
        b[:, :, slot, 0, 1] += dy * m

    n_, t_ = X.shape[0], X.shape[1]
    out = b.reshape(n_, t_, TWO_HAND_FEATURES).astype(np.float32)
    np.clip(out, -20.0, 20.0, out=out)
    return out


def time_warp(X: np.ndarray) -> np.ndarray:
    """Resample each sequence to a random speed, then pad/truncate back.

    Signers vary a lot in tempo, and the app classifies early on
    zero-padded partial buffers, so the model must tolerate both a
    faster sign and a sequence that ends in padding.
    """
    n, t, f = X.shape
    out = np.zeros_like(X)
    present_any = hand_present(X).any(axis=2)      # (N, T)

    for i in range(n):
        real_len = int(present_any[i].sum())
        if real_len < 4:
            out[i] = X[i]
            continue

        factor = np.random.uniform(0.75, 1.30)
        new_len = int(np.clip(round(real_len * factor), 4, t))

        src = np.linspace(0, real_len - 1, new_len)
        lo = np.floor(src).astype(int)
        hi = np.minimum(lo + 1, real_len - 1)
        w = (src - lo).astype(np.float32)[:, None]

        resampled = X[i, lo] * (1 - w) + X[i, hi] * w
        out[i, :new_len] = resampled
        # remaining frames stay zero — same padding the app produces

    return out.astype(np.float32)


def augment(X: np.ndarray, y: np.ndarray, copies: int) -> tuple[np.ndarray, np.ndarray]:
    """Mirror every sequence, then add jittered/time-warped copies of both."""
    X_both = np.concatenate([X, mirror_sequences(X)])
    y_both = np.concatenate([y, y])

    if copies <= 0:
        return X_both, y_both

    Xs, ys = [X_both], [y_both]
    for _ in range(copies):
        Xs.append(jitter(time_warp(X_both)))
        ys.append(y_both)

    return np.concatenate(Xs), np.concatenate(ys)


# ── Runtime-compatibility guard ─────────────────────────────────────

# The app pins org.tensorflow:tensorflow-lite 2.16.1. Anything newer than
# v9 (what dynamic-range quantization emits on TF >= 2.19) fails to load
# with "Didn't find op for builtin opcode ... version 12".
MAX_SUPPORTED_OP_VERSION = 9


def check_opcode_versions(tflite_path: str) -> None:
    try:
        import tflite
    except ImportError:
        print("\n  (skipping opcode check — `pip install tflite` to enable it)")
        return

    names = {v: k for k, v in vars(tflite.BuiltinOperator).items()
             if isinstance(v, int)}
    model = tflite.Model.GetRootAsModel(open(tflite_path, "rb").read(), 0)

    print("\nOperator versions in the exported model:")
    bad = []
    for i in range(model.OperatorCodesLength()):
        oc = model.OperatorCodes(i)
        op_name = names.get(oc.BuiltinCode(), str(oc.BuiltinCode()))
        version = oc.Version()
        flag = "" if version <= MAX_SUPPORTED_OP_VERSION else "  <-- TOO NEW"
        print(f"    {op_name} v{version}{flag}")
        if version > MAX_SUPPORTED_OP_VERSION:
            bad.append((op_name, version))

    if bad:
        raise SystemExit(
            "\nERROR: this model cannot load on the app's TFLite runtime.\n"
            + "".join(f"  {n} v{v} > max supported v{MAX_SUPPORTED_OP_VERSION}\n"
                      for n, v in bad)
            + "\nRemove converter.optimizations, or raise the tflite version\n"
              "in gradle/libs.versions.toml to match.\n"
        )

    print(f"  All ops <= v{MAX_SUPPORTED_OP_VERSION} — loadable by the app.")


# ── Model ───────────────────────────────────────────────────────────

def build_model(num_classes: int) -> tf.keras.Model:
    """1D-CNN over time — same family as the existing fsl_dynamic model.

    Convolutions run along the frame axis, so the network learns short
    motion motifs and then pools them. Kept small: it runs every third
    frame inside the CameraX analyzer loop on a mid-range phone.
    """
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(SEQUENCE_LENGTH, TWO_HAND_FEATURES),
                              name="sequence"),

        tf.keras.layers.Conv1D(96, 5, padding="same", activation="relu"),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.MaxPooling1D(2),

        tf.keras.layers.Conv1D(128, 3, padding="same", activation="relu"),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.MaxPooling1D(2),

        tf.keras.layers.Conv1D(128, 3, padding="same", activation="relu"),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.GlobalAveragePooling1D(),

        tf.keras.layers.Dropout(0.4),
        tf.keras.layers.Dense(64, activation="relu"),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(num_classes, activation="softmax", name="probs"),
    ])
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model


# ── Plots ───────────────────────────────────────────────────────────

def plot_confusion(cm: np.ndarray, labels: list[str], path: str) -> None:
    fig, ax = plt.subplots(figsize=(6.5, 5.5))
    im = ax.imshow(cm, cmap="Greens")
    ax.set_xticks(range(len(labels)), labels, rotation=30, ha="right")
    ax.set_yticks(range(len(labels)), labels)
    ax.set_xlabel("Predicted")
    ax.set_ylabel("True")
    ax.set_title("FSL Greetings — Confusion Matrix (test set)")
    thresh = cm.max() / 2 if cm.max() else 0
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            if cm[i, j]:
                ax.text(j, i, str(cm[i, j]), ha="center", va="center",
                        fontsize=9,
                        color="white" if cm[i, j] > thresh else "black")
    fig.colorbar(im)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


def plot_curves(history, path: str) -> None:
    fig, (a1, a2) = plt.subplots(1, 2, figsize=(11, 4))
    a1.plot(history.history["accuracy"], label="train")
    a1.plot(history.history["val_accuracy"], label="val")
    a1.set_title("Accuracy"); a1.set_xlabel("epoch"); a1.legend(); a1.grid(alpha=.3)
    a2.plot(history.history["loss"], label="train")
    a2.plot(history.history["val_loss"], label="val")
    a2.set_title("Loss"); a2.set_xlabel("epoch"); a2.legend(); a2.grid(alpha=.3)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


# ── Main ────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Train the FSL Greetings dynamic classifier.")
    parser.add_argument("--epochs", type=int, default=200)
    parser.add_argument("--batch", type=int, default=32)
    parser.add_argument("--augment", type=int, default=6,
                        help="Augmented copies per (original+mirrored) sample.")
    parser.add_argument("--npz", default=NPZ_PATH)
    args = parser.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)

    if not os.path.exists(args.npz):
        raise SystemExit(
            f"No dataset at {args.npz}.\n"
            "Run collect_landmarks_category1.py first."
        )

    d = np.load(args.npz, allow_pickle=True)
    X, y_raw = d["X"].astype(np.float32), d["y"]

    if X.shape[1:] != (SEQUENCE_LENGTH, TWO_HAND_FEATURES):
        raise SystemExit(
            f"Expected sequences of shape ({SEQUENCE_LENGTH}, {TWO_HAND_FEATURES}), "
            f"got {X.shape[1:]}. The collector and trainer are out of sync."
        )

    labels = sorted({str(v) for v in y_raw})
    label_to_idx = {lab: i for i, lab in enumerate(labels)}
    y = np.array([label_to_idx[str(v)] for v in y_raw], dtype=np.int32)

    print("\nTakes per sign:")
    for lab in labels:
        print(f"  {lab:<10} {int((y == label_to_idx[lab]).sum())}")

    counts = np.bincount(y, minlength=len(labels))
    thin = [labels[i] for i, c in enumerate(counts) if c < 20]
    if thin:
        print(f"\n  WARNING: under 20 takes for {thin}.")
        print("  Motion has far more variation than a static pose —")
        print("  augmentation cannot invent takes you never recorded.\n")

    if counts.min() < 3:
        raise SystemExit(
            "At least 3 takes per sign are needed to make a stratified split."
        )

    # Split BEFORE augmenting, so mirrored/jittered twins of a training
    # take cannot leak into test and inflate the reported accuracy.
    X_train, X_tmp, y_train, y_tmp = train_test_split(
        X, y, test_size=0.30, random_state=SEED, stratify=y)
    X_val, X_test, y_val, y_test = train_test_split(
        X_tmp, y_tmp, test_size=0.50, random_state=SEED, stratify=y_tmp)

    # Val and test get the mirror too (no jitter), so the reported score
    # reflects both-handed use rather than only the hand you recorded with.
    X_val = np.concatenate([X_val, mirror_sequences(X_val)])
    y_val = np.concatenate([y_val, y_val])

    n_test_orig = len(X_test)
    X_test = np.concatenate([X_test, mirror_sequences(X_test)])
    y_test = np.concatenate([y_test, y_test])
    test_is_mirrored = np.concatenate([
        np.zeros(n_test_orig, bool), np.ones(n_test_orig, bool)])

    X_train, y_train = augment(X_train, y_train, args.augment)

    print(f"\nTrain {len(X_train)} (after mirror+augment) | "
          f"Val {len(X_val)} | Test {len(X_test)}")

    model = build_model(len(labels))
    model.summary()

    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=args.epochs,
        batch_size=args.batch,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(
                monitor="val_accuracy", patience=30, restore_best_weights=True),
            tf.keras.callbacks.ReduceLROnPlateau(
                monitor="val_loss", factor=0.5, patience=12, min_lr=1e-5),
        ],
        verbose=2,
    )

    # ── Evaluation ──
    test_loss, test_acc = model.evaluate(X_test, y_test, verbose=0)
    y_pred = model.predict(X_test, verbose=0).argmax(axis=1)
    report = classification_report(y_test, y_pred, target_names=labels,
                                   digits=4, zero_division=0)
    cm = confusion_matrix(y_test, y_pred)

    as_recorded = (y_pred[~test_is_mirrored] == y_test[~test_is_mirrored]).mean()
    mirrored = (y_pred[test_is_mirrored] == y_test[test_is_mirrored]).mean()

    print(f"\nTest accuracy: {test_acc:.4f}   loss: {test_loss:.4f}")
    print(f"  as recorded (your hand): {as_recorded:.4f}")
    print(f"  mirrored (other hand)  : {mirrored:.4f}")
    if abs(as_recorded - mirrored) > 0.10:
        print("  WARNING: large gap between hands. The mirror augmentation")
        print("  is not carrying — check that both hands stay in frame.")
    print()
    print(report)

    plot_confusion(cm, labels,
                   os.path.join(OUT_DIR, "greetings_confusion_matrix.png"))
    plot_curves(history,
                os.path.join(OUT_DIR, "greetings_training_curves.png"))

    with open(os.path.join(OUT_DIR, "greetings_metrics.txt"), "w",
              encoding="utf-8") as f:
        f.write(f"Test accuracy: {test_acc:.4f}\nTest loss: {test_loss:.4f}\n")
        f.write(f"As-recorded hand: {as_recorded:.4f}\n")
        f.write(f"Mirrored hand:    {mirrored:.4f}\n\n")
        f.write(report + "\n\nConfusion matrix (rows=true, cols=pred)\n")
        f.write("labels: " + ", ".join(labels) + "\n")
        f.write(np.array2string(cm))

    keras_path = os.path.join(OUT_DIR, "fsl_greetings.keras")
    model.save(keras_path)

    # ── Export ──
    # No converter.optimizations — see check_opcode_versions above.
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    tflite_path = os.path.join(OUT_DIR, "fsl_greetings.tflite")
    with open(tflite_path, "wb") as f:
        f.write(tflite_model)

    labels_path = os.path.join(OUT_DIR, "fsl_greetings_labels.txt")
    with open(labels_path, "w", encoding="utf-8") as f:
        f.write("\n".join(labels))

    check_opcode_versions(tflite_path)

    # ── Sanity check: exported model must agree with Keras ──
    interp = tf.lite.Interpreter(model_content=tflite_model)
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    outp = interp.get_output_details()[0]

    print(f"\nTFLite input shape : {inp['shape']}  (app expects "
          f"[1 {SEQUENCE_LENGTH} {TWO_HAND_FEATURES}])")
    print(f"TFLite output shape: {outp['shape']}  ({len(labels)} classes)")

    probe = X_test[:100]
    keras_pred = model.predict(probe, verbose=0).argmax(axis=1)
    mismatches = 0
    for i, seq in enumerate(probe):
        interp.set_tensor(
            inp["index"],
            seq.reshape(1, SEQUENCE_LENGTH, TWO_HAND_FEATURES).astype(np.float32))
        interp.invoke()
        if interp.get_tensor(outp["index"])[0].argmax() != keras_pred[i]:
            mismatches += 1
    print(f"TFLite parity check: {len(probe) - mismatches}/{len(probe)} "
          f"agree with Keras")

    # ── Partial-buffer check ──
    # The app classifies from 15 frames onward with the rest zero-padded.
    # A model that only works on full 30-frame windows will feel broken.
    print("\nAccuracy on zero-padded partial buffers (what the app actually sees):")
    for k in (15, 20, 25, 30):
        partial = X_test.copy()
        partial[:, k:, :] = 0.0
        acc = (model.predict(partial, verbose=0).argmax(axis=1) == y_test).mean()
        print(f"  first {k:>2} frames: {acc:.4f}")

    print(f"""
Done.
  {tflite_path}   ({os.path.getsize(tflite_path) / 1024:.1f} KB)
  {labels_path}

Copy both into the app:
  copy out\\fsl_greetings.tflite       C:\\Users\\pitar\\Documents\\Kinetix-FSL\\app\\src\\main\\assets\\
  copy out\\fsl_greetings_labels.txt   C:\\Users\\pitar\\Documents\\Kinetix-FSL\\app\\src\\main\\assets\\
""")


if __name__ == "__main__":
    main()
