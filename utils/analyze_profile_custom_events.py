#!/usr/bin/env python3
import glob
import os
import re
import sys
from statistics import mean

"""
Parses KISS ProfileManager CSVs that embed CUSTOM_EVENT rows.
Looks for lines like:
  <ts>,CUSTOM_EVENT,<EVENT>,key:value,key:value,...,custom_event
Supported metrics:
- Throttling: counts RELOAD_THROTTLED events, computes throttle ratio
- Search durations: from SEARCH_PERFORMANCE duration:NNms (p50/p95/p99)
- Action durations: from PERFORMANCE_SNAPSHOT context:ACTION_END:<X> duration:NNms
- Memory snapshots: from PERFORMANCE_SNAPSHOT memory_mb:XX
Usage:
  python utils/analyze_profile_custom_events.py <path_to_csv_dir>
"""

DUR_RE = re.compile(r"duration:(\d+)ms")
MEM_RE = re.compile(r"memory_mb:([0-9]+(?:\.[0-9]+)?)")
CTX_RE = re.compile(r"context:([^,]+)")


def iter_lines(path):
    files = sorted(glob.glob(os.path.join(path, '*.csv')))
    for fp in files:
        try:
            with open(fp, 'r', encoding='utf-8', errors='ignore') as f:
                for line in f:
                    yield line.strip()
        except Exception as e:
            print(f"Failed to read {fp}: {e}")


def percentile(values, p):
    """Compute p-th percentile (0-100)"""
    if not values:
        return None
    sorted_vals = sorted(values)
    idx = (p / 100.0) * (len(sorted_vals) - 1)
    lower = int(idx)
    upper = lower + 1
    if upper >= len(sorted_vals):
        return sorted_vals[-1]
    frac = idx - lower
    return sorted_vals[lower] * (1 - frac) + sorted_vals[upper] * frac


def parse(path):
    reload_throttled = 0
    reload_requested = 0  # Phase 2 S4: Track RELOAD_REQUESTED for throttle ratio
    search_first = []  # SEARCH_PERFORMANCE
    search_action = []  # ACTION_END:SEARCH...
    reload_action = []  # ACTION_END:RELOAD
    memory_vals = []

    for ln in iter_lines(path):
        if not ln or ln.startswith('timestamp'):
            continue
        if ',CUSTOM_EVENT,' not in ln:
            # ignore periodic sample rows for this analyzer
            continue
        # Split only the first 3 commas to avoid breaking kv pairs
        parts = ln.split(',', 3)
        if len(parts) < 3:
            continue
        # parts[0] = timestamp, parts[1] = 'CUSTOM_EVENT', parts[2] = EVENT
        event = parts[2].strip().upper()
        tail = parts[3] if len(parts) > 3 else ''

        # Phase 2 S4: Count RELOAD_REQUESTED events
        if 'RELOAD_REQUESTED' in event or 'RELOAD_REQUESTED' in tail:
            reload_requested += 1
            continue

        if 'RELOAD_THROTTLED' in event or 'RELOAD_THROTTLED' in tail:
            reload_throttled += 1
            continue

        if event == 'SEARCH_PERFORMANCE':
            m = DUR_RE.search(tail)
            if m:
                search_first.append(int(m.group(1)))
            continue

        if event == 'PERFORMANCE_SNAPSHOT':
            # capture context and duration and memory if present
            cm = CTX_RE.search(tail)
            dm = DUR_RE.search(tail)
            mm = MEM_RE.search(tail)
            if mm:
                try:
                    memory_vals.append(float(mm.group(1)))
                except Exception:
                    pass
            ctx = cm.group(1) if cm else ''
            if dm:
                dur = int(dm.group(1))
                if 'ACTION_END:RELOAD' in ctx:
                    reload_action.append(dur)
                elif 'ACTION_END:SEARCH' in ctx or 'ACTION_END:SEARCH_EMPTY' in ctx:
                    search_action.append(dur)
            continue

    return {
        'reload_throttled': reload_throttled,
        'reload_requested': reload_requested,
        'search_first': search_first,
        'search_action': search_action,
        'reload_action': reload_action,
        'memory_vals': memory_vals,
    }


def summarize(res):
    def stat_with_percentiles(vals, label=""):
        if not vals:
            return '-'
        p50 = percentile(vals, 50)
        p95 = percentile(vals, 95)
        p99 = percentile(vals, 99)
        return f"{label}p50={p50:.0f}ms p95={p95:.0f}ms p99={p99:.0f}ms | avg {mean(vals):.0f}ms | min {min(vals)} | max {max(vals)} | n={len(vals)}"

    def stat(vals):
        if not vals:
            return '-'
        return f"avg {mean(vals):.0f}ms | min {min(vals)} | max {max(vals)} | n={len(vals)}"

    print('=== Throttling ===')
    throttle_ratio = res['reload_requested']
    if throttle_ratio > 0:
        ratio = (res['reload_throttled'] / throttle_ratio) * 100
        print(f"RELOAD_REQUESTED: {res['reload_requested']}")
        print(f"RELOAD_THROTTLED: {res['reload_throttled']}")
        print(f"Throttle Ratio: {ratio:.1f}%")
    else:
        print(f"RELOAD_REQUESTED: 0 (no reload attempts)")
        print(f"RELOAD_THROTTLED: {res['reload_throttled']}")

    print('\n=== Search (first, SEARCH_PERFORMANCE) with Percentiles ===')
    print(stat_with_percentiles(res['search_first'], ""))

    print('\n=== Search (action end) ===')
    print(stat(res['search_action']))

    print('\n=== Reload (action end) ===')
    print(stat(res['reload_action']))

    print('\n=== Memory (PERFORMANCE_SNAPSHOT memory_mb) ===')
    if res['memory_vals']:
        mv = res['memory_vals']
        print(f"first {mv[0]:.2f}MB | avg {mean(mv):.2f}MB | max {max(mv):.2f}MB | n={len(mv)}")
    else:
        print('-')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python utils/analyze_profile_custom_events.py <path_to_csv_dir>')
        sys.exit(1)
    res = parse(sys.argv[1])
    summarize(res)

