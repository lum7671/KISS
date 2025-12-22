#!/usr/bin/env python3
import csv
import glob
import os
import statistics as stats
import sys
from collections import defaultdict

"""
Quick analyzer for KISS ProfileManager CSV logs.
Usage:
  python utils/analyze_profile_logs.py logs/profile_YYYYMMDD_HHMMSS/profile_logs
Output: summary metrics printed to stdout.
Assumptions: CSV headers include at least: timestamp,event,provider,duration_ms,memory_mb
"""

def load_rows(path):
    rows = []
    files = sorted(glob.glob(os.path.join(path, '*.csv')))
    if not files:
        print(f"No CSV files found under: {path}")
        return rows
    for fp in files:
        try:
            with open(fp, newline='') as f:
                reader = csv.DictReader(f)
                for r in reader:
                    rows.append(r)
        except Exception as e:
            print(f"Failed to read {fp}: {e}")
    return rows


def intsafed(v, default=0):
    try:
        return int(float(v))
    except Exception:
        return default


def summarize(rows):
    durations = defaultdict(list)
    memory = []
    counts = defaultdict(int)

    for r in rows:
        event = r.get('event', '').strip().upper()
        dur = intsafed(r.get('duration_ms', '0'))
        mem = intsafed(r.get('memory_mb', '0'))
        provider = r.get('provider', '')

        counts[event] += 1
        if dur:
            durations[event].append(dur)
        if mem:
            memory.append(mem)

    def fmt_stats(vals):
        if not vals:
            return "-"
        mean = stats.mean(vals)
        med = stats.median(vals)
        sdev = stats.pstdev(vals) if len(vals) > 1 else 0
        return f"avg {mean:.0f}ms | med {med:.0f}ms | σ {sdev:.0f}ms | min {min(vals)} | max {max(vals)}"

    # Output
    print("=== Events (counts) ===")
    for k in sorted(counts.keys()):
        print(f"{k:24s}: {counts[k]}")

    print("\n=== Cold Start ===")
    print(fmt_stats(durations.get('COLD_START', [])))

    print("\n=== Throttling ===")
    throttled = counts.get('RELOAD_THROTTLED', 0)
    reloads = counts.get('RELOAD', 0)
    denom = throttled + reloads
    ratio = (throttled / denom * 100) if denom else 0
    print(f"skipped {throttled}/{denom} = {ratio:.1f}%")

    print("\n=== Lazy Search Overhead (first search) ===")
    print(fmt_stats(durations.get('SEARCH_LAZY_LOADING', [])))

    print("\n=== Subsequent Search ===")
    print(fmt_stats(durations.get('SEARCH', [])))

    print("\n=== Memory (MB) ===")
    if memory:
        print(f"first {memory[0]} | avg {stats.mean(memory):.0f} | max {max(memory)}")
    else:
        print("-")


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python utils/analyze_profile_logs.py <path_to_csv_dir>")
        sys.exit(1)
    rows = load_rows(sys.argv[1])
    if not rows:
        sys.exit(2)
    summarize(rows)
