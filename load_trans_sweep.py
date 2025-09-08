#!/usr/bin/env python3
"""
Load transitivity sweep CSV and build a NumPy array:

trans: shape = (num_targetC, repeats)

Default CSV path matches TargetTransitivityApp output:
  java-project/output/edge-triangle/z=6/N=1000/transitivity-sweep.csv

Usage examples:
  python load_trans_sweep.py
  python load_trans_sweep.py --csv path/to/transitivity-sweep.csv --save npy_out/trans.npy
"""
import argparse
import csv
import os
from collections import defaultdict, OrderedDict

import numpy as np


def load_trans_matrix(csv_path: str):
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"CSV not found: {csv_path}")

    # Preserve first-seen order of targetC values
    order = OrderedDict()
    groups = defaultdict(list)  # targetC -> list of (repeat_idx?, trans)

    with open(csv_path, newline="") as f:
        reader = csv.DictReader(f)
        required = {"target_transitivity", "trans"}
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"CSV missing columns: {sorted(missing)}")

        has_repeat = "repeat" in reader.fieldnames
        for row in reader:
            try:
                target_c = float(row["target_transitivity"])  # input target
                trans = float(row["trans"])                   # measured value
            except Exception as e:
                raise ValueError(f"Failed to parse row: {row}") from e

            if target_c not in order:
                order[target_c] = len(order)

            if has_repeat:
                try:
                    rep = int(row["repeat"])  # 0..repeats-1 expected
                except Exception:
                    rep = None
                groups[target_c].append((rep, trans))
            else:
                groups[target_c].append((None, trans))

    # Determine repeats and build matrix
    targets = list(order.keys())
    counts = [len(groups[t]) for t in targets]
    if not counts:
        raise ValueError("No data rows in CSV.")
    repeats = max(counts)
    if any(c != repeats for c in counts):
        raise ValueError(f"Unequal repeat counts per targetC: {counts}")

    trans_mat = np.full((len(targets), repeats), np.nan, dtype=float)

    for ti, tval in enumerate(targets):
        items = groups[tval]
        # If repeat indices exist and cover 0..repeats-1, place by index; else fill in order
        by_idx = [x for x in items if x[0] is not None]
        if len(by_idx) == repeats and sorted(r for r, _ in by_idx) == list(range(repeats)):
            for r, v in by_idx:
                trans_mat[ti, r] = v
        else:
            for r, (_, v) in enumerate(items):
                trans_mat[ti, r] = v

    return np.array(targets, dtype=float), trans_mat


def main():
    ap = argparse.ArgumentParser(description="Load transitivity sweep CSV into a matrix.")
    ap.add_argument(
        "--csv",
        default=os.path.join(
            "java-project",
            "output",
            "edge-triangle",
            "z=6",
            "N=1000",
            "transitivity-sweep.csv",
        ),
        help="Path to transitivity-sweep.csv",
    )
    ap.add_argument("--save", default=None, help="Optional .npy path to save the trans matrix")
    args = ap.parse_args()

    targets, trans = load_trans_matrix(args.csv)
    print(f"Loaded: {args.csv}")
    print(f"targets shape: {targets.shape}; repeats: {trans.shape[1]}")
    print(f"trans shape: {trans.shape}")
    print(f"targetC min..max: {targets.min():.4f} .. {targets.max():.4f}")
    print(f"trans sample (first row): {trans[0] if len(trans) else '[]'}")

    if args.save:
        os.makedirs(os.path.dirname(args.save), exist_ok=True)
        np.save(args.save, trans)
        print(f"Saved trans matrix to: {args.save}")


if __name__ == "__main__":
    main()

