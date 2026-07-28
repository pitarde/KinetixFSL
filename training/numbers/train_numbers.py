"""
train_numbers.py — trains the FSL Numbers (0-9) static classifier.

Input : data/numbers_landmarks.csv   (from collect_numbers.py)
Output: out/fsl_numbers.tflite
        out/fsl_numbers_labels.txt
        out/confusion_matrix.png
        out/training_curves.png
        out/metrics.txt

Architecture mirrors the alphabet static model: a small Dense net over the
63-dim normalized landmark vector. Small on purpose — it must run per-frame
on a mid-range phone inside the CameraX analyzer loop.

Usage
-----
    python train_numbers.py
    python train_numbers.py --epochs 200 --augment 6
"""

import argparse
import os

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import train_test_split

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(HERE, "data", "numbers_landmarks.csv")
OUT_DIR = os.path.join(HERE, "out")

SEED = 42
np.random.seed(SEED)
tf.random.set_seed(SEED)


# ── Augmentation ────────────────────────────────────────────────────

def mirror_hand(X: np.ndarray) -> np.ndarray:
    """Flip left↔right by negating the x coordinate of every landmark.

    In normalized space the wrist is at the origin and scale is 1, so
    negating x is the exact geometric reflection. A right-hand "3"
    becomes a left-hand "3" with the same label — which is correct,
    because the sign is the same regardless of which hand performs it.
    """
    mirrored = X.reshape(-1, 21, 3).copy()
    mirrored[:, :, 0] *= -1.0  # negate x
    return mirrored.reshape(-1, 63).astype(np.float32)


def augment(X: np.ndarray, y: np.ndarray, copies: int) -> tuple[np.ndarray, np.ndarray]:
    """Expand the dataset with jittered copies.

    Four perturbations, all applied in the same normalized space the app
    produces at inference time:
      - Hand mirror (x-flip) : so training with one hand covers both hands
      - Gaussian noise       : landmark detection jitter
      - isotropic scaling    : residual hand-size variation the /||L9|| step misses
      - small 3D rotation    : hand tilt relative to the camera

    The mirror is applied FIRST to a copy of the original data, then every
    jitter copy is generated from BOTH the original and the mirrored set.
    This means every augmented sample exists in both left- and right-hand form.
    """
    if copies <= 0:
        # Still apply the mirror even with zero jitter copies,
        # so both hands are always covered.
        X_mirror = mirror_hand(X)
        return np.concatenate([X, X_mirror]), np.concatenate([y, y])

    # Mirror: negate the x coordinate of every landmark.
    # In normalized space (wrist at origin, scale-invariant), this is
    # exactly the transformation between a left and right hand.
    X_mirror = mirror_hand(X)
    X_both = np.concatenate([X, X_mirror])
    y_both = np.concatenate([y, y])

    Xs, ys = [X_both], [y_both]
    n = X_both.shape[0]

    for _ in range(copies):
        pts = X_both.reshape(n, 21, 3).copy()

        # Gaussian noise (per-point)
        pts += np.random.normal(0.0, 0.015, pts.shape).astype(np.float32)

        # Isotropic scale, ±8%
        scale = np.random.uniform(0.92, 1.08, (n, 1, 1)).astype(np.float32)
        pts *= scale

        # Small rotation about each axis, ±12°
        for axis in range(3):
            theta = np.random.uniform(-np.pi / 15, np.pi / 15, n).astype(np.float32)
            c, s = np.cos(theta), np.sin(theta)
            R = np.zeros((n, 3, 3), dtype=np.float32)
            a, b = [i for i in range(3) if i != axis]
            R[:, axis, axis] = 1.0
            R[:, a, a] = c
            R[:, a, b] = -s
            R[:, b, a] = s
            R[:, b, b] = c
            pts = np.einsum("nij,nkj->nki", R, pts)

        Xs.append(pts.reshape(n, 63).astype(np.float32))
        ys.append(y_both)

    return np.concatenate(Xs), np.concatenate(ys)


# ── Runtime-compatibility guard ─────────────────────────────────────

# Highest builtin-op version the Android app's TFLite runtime can parse.
# The app pins org.tensorflow:tensorflow-lite 2.16.1 in
# gradle/libs.versions.toml. The shipped fsl_alphabet.tflite uses
# FULLY_CONNECTED v9, which that runtime handles; v12 (emitted by
# dynamic-range quantization on TF >= 2.19) crashes it.
MAX_SUPPORTED_OP_VERSION = 9


def check_opcode_versions(tflite_path: str) -> None:
    """Fail loudly if the exported model uses ops the phone can't run.

    A model that converts fine on the desktop can still be unloadable on
    the device. Catching it here beats discovering it as a blank camera
    screen with a generic error message.
    """
    try:
        import tflite  # pip install tflite
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
            + "\nThe app would show 'This module's recognition model isn't\n"
              "available yet'. Remove converter.optimizations (quantization\n"
              "is what bumps the op version), or raise the tflite version in\n"
              "gradle/libs.versions.toml to match.\n"
        )

    print(f"  All ops <= v{MAX_SUPPORTED_OP_VERSION} — loadable by the app.")


# ── Model ───────────────────────────────────────────────────────────

def build_model(num_classes: int) -> tf.keras.Model:
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(63,), name="landmarks"),
        tf.keras.layers.Dense(128, activation="relu"),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(64, activation="relu"),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(32, activation="relu"),
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
    fig, ax = plt.subplots(figsize=(7, 6))
    im = ax.imshow(cm, cmap="Greens")
    ax.set_xticks(range(len(labels)), labels)
    ax.set_yticks(range(len(labels)), labels)
    ax.set_xlabel("Predicted")
    ax.set_ylabel("True")
    ax.set_title("FSL Numbers — Confusion Matrix (test set)")
    thresh = cm.max() / 2 if cm.max() else 0
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            if cm[i, j]:
                ax.text(j, i, str(cm[i, j]), ha="center", va="center",
                        fontsize=8,
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
    parser = argparse.ArgumentParser(description="Train the FSL numbers classifier.")
    parser.add_argument("--epochs", type=int, default=150)
    parser.add_argument("--batch", type=int, default=64)
    parser.add_argument("--augment", type=int, default=5,
                        help="Augmented copies per original sample (0 disables).")
    parser.add_argument("--csv", default=CSV_PATH)
    args = parser.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)

    if not os.path.exists(args.csv):
        raise SystemExit(f"No dataset at {args.csv}. Run collect_numbers.py first.")

    df = pd.read_csv(args.csv)
    df["label"] = df["label"].astype(str)

    print("\nSamples per label:")
    print(df["label"].value_counts().sort_index().to_string())

    thin = df["label"].value_counts()
    thin = thin[thin < 150]
    if not thin.empty:
        print(f"\n  WARNING: under 150 raw samples for: {list(thin.index)}")
        print("  Augmentation will not rescue a class that was barely recorded.\n")

    # Labels sorted so the .txt row order matches the model's output index.
    labels = sorted(df["label"].unique())
    label_to_idx = {lab: i for i, lab in enumerate(labels)}

    X = df[[f"f{i}" for i in range(63)]].to_numpy(dtype=np.float32)
    y = df["label"].map(label_to_idx).to_numpy(dtype=np.int32)

    # Split BEFORE augmenting — otherwise jittered twins of a training sample
    # leak into test and the reported accuracy is fiction.
    X_train, X_tmp, y_train, y_tmp = train_test_split(
        X, y, test_size=0.30, random_state=SEED, stratify=y)
    X_val, X_test, y_val, y_test = train_test_split(
        X_tmp, y_tmp, test_size=0.50, random_state=SEED, stratify=y_tmp)

    X_train, y_train = augment(X_train, y_train, args.augment)
    print(f"\nTrain {len(X_train)} (after augment) | Val {len(X_val)} | Test {len(X_test)}")

    model = build_model(len(labels))
    model.summary()

    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=args.epochs,
        batch_size=args.batch,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(
                monitor="val_accuracy", patience=25, restore_best_weights=True),
            tf.keras.callbacks.ReduceLROnPlateau(
                monitor="val_loss", factor=0.5, patience=10, min_lr=1e-5),
        ],
        verbose=2,
    )

    # ── Evaluation ──
    test_loss, test_acc = model.evaluate(X_test, y_test, verbose=0)
    y_pred = model.predict(X_test, verbose=0).argmax(axis=1)
    report = classification_report(y_test, y_pred, target_names=labels, digits=4)
    cm = confusion_matrix(y_test, y_pred)

    print(f"\nTest accuracy: {test_acc:.4f}   loss: {test_loss:.4f}\n")
    print(report)

    plot_confusion(cm, labels, os.path.join(OUT_DIR, "confusion_matrix.png"))
    plot_curves(history, os.path.join(OUT_DIR, "training_curves.png"))

    with open(os.path.join(OUT_DIR, "metrics.txt"), "w") as f:
        f.write(f"Test accuracy: {test_acc:.4f}\nTest loss: {test_loss:.4f}\n\n")
        f.write(report + "\n\nConfusion matrix (rows=true, cols=pred)\n")
        f.write("labels: " + ", ".join(labels) + "\n")
        f.write(np.array2string(cm))

    # Keep the Keras model so the .tflite can be re-converted later
    # (different opset, different settings) without retraining.
    keras_path = os.path.join(OUT_DIR, "fsl_numbers.keras")
    model.save(keras_path)

    # ── Export ──
    #
    # DO NOT enable converter.optimizations here.
    #
    # Dynamic-range quantization on TF >= 2.19 emits FULLY_CONNECTED
    # *version 12*, which the app's TFLite runtime (2.16.1, see
    # gradle/libs.versions.toml) cannot parse. It fails at Interpreter
    # construction with:
    #     Didn't find op for builtin opcode 'FULLY_CONNECTED' version '12'
    # and the practice screen just reports the model as unavailable.
    #
    # The model is ~25 KB as plain float32 anyway, so quantization buys
    # nothing here. The guard below catches it if anyone re-enables it.
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    tflite_path = os.path.join(OUT_DIR, "fsl_numbers.tflite")
    with open(tflite_path, "wb") as f:
        f.write(tflite_model)

    check_opcode_versions(tflite_path)

    labels_path = os.path.join(OUT_DIR, "fsl_numbers_labels.txt")
    with open(labels_path, "w", encoding="utf-8") as f:
        f.write("\n".join(labels))

    # ── Sanity check: the exported .tflite must agree with Keras ──
    interp = tf.lite.Interpreter(model_content=tflite_model)
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    outp = interp.get_output_details()[0]

    mismatches = 0
    probe = X_test[:200]
    keras_pred = model.predict(probe, verbose=0).argmax(axis=1)
    for i, vec in enumerate(probe):
        interp.set_tensor(inp["index"], vec.reshape(1, 63).astype(np.float32))
        interp.invoke()
        if interp.get_tensor(outp["index"])[0].argmax() != keras_pred[i]:
            mismatches += 1

    print(f"\nTFLite parity check: {len(probe) - mismatches}/{len(probe)} agree with Keras")
    if mismatches:
        print("  NOTE: float32 conversion should be near-exact. Any sizeable")
        print("  disagreement here means something is wrong with the export.")

    print(f"""
Done.
  {tflite_path}   ({os.path.getsize(tflite_path) / 1024:.1f} KB)
  {labels_path}

Copy both into app/src/main/assets/ :
  copy out\\fsl_numbers.tflite        ..\\..\\app\\src\\main\\assets\\
  copy out\\fsl_numbers_labels.txt    ..\\..\\app\\src\\main\\assets\\
""")


if __name__ == "__main__":
    main()
