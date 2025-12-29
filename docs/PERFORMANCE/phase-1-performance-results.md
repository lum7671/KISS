# Phase 1.3 Performance Results (Initial)

Period: 2025-12-15 16:00 → 2025-12-16 09:00  
Source: logs/manual_pull_20251216_094250/kiss_profile_logs (≈3.3 MB, 5 CSV files)

---

## Summary

- RELOAD_THROTTLED: 3 events (initial dataset; needs more onResume cycles for robust ratio)
- Search (first, SEARCH_PERFORMANCE): avg 3 ms | min 0 | max 51 | n=1531
- Search (action end): avg 3 ms | min 0 | max 64 | n=2661
- Reload (action end): avg 2 ms | min 0 | max 14 | n=111
- Memory (PERFORMANCE_SNAPSHOT): first 8.40 MB | avg 21.45 MB | max 243.10 MB | n=8318

Notes:
- Dataset is dominated by lightweight actions; lazy-init overhead and throttling opportunities look sparse in this slice. For a solid throttling evaluation, we should include more Home resume cycles (background↔foreground) during busy usage windows.
- Cold start metrics are not emitted as a dedicated `COLD_START` event in these CSVs; we can infer via `PERFORMANCE_SNAPSHOT context:STARTUP_*` if available in other logs, or we’ll add a targeted probe.

---

## Detail

### Throttling (onResume)
- Counted via CUSTOM_EVENT lines containing `RELOAD_THROTTLED`.
- Observed: 3 occurrences.
- Action durations for completed reloads (ACTION_END:RELOAD): avg 2 ms (n=111)

Actionable next: Capture a session with 20+ background→foreground cycles to compute the skip ratio = throttled / (throttled + reloads).

### Search
- First search (SEARCH_PERFORMANCE): avg 3 ms (n=1531)
- Subsequent (ACTION_END:SEARCH*): avg 3 ms (n=2661)

Interpretation: Searches are generally very fast in this window; lazy-init overhead is not prominent here (likely already warmed, or no contacts/shortcuts heavy queries in the window).

### Memory
- Snapshot (PERFORMANCE_SNAPSHOT memory_mb): first 8.40 MB | avg 21.45 MB | max 243.10 MB | n=8318

Interpretation: Max indicates transient peaks; average is low—consistent with the app idling much of the time. For startup deltas, we’ll parse startup-tagged snapshots in a focused startup run.

---

## Next Steps

1) On-device capture focused on onResume cycles (10–20 repeats) and first search after install:
```
bash scripts/collect_profile_logs_noninteractive.sh 180
```
2) Share the generated archive under `logs/profile_YYYYMMDD_HHMMSS.zip` or re-run the analyzer against the new folder.
3) I’ll merge the results and compute:
- Throttling skip ratio, with confidence interval
- Lazy-init overhead for first search vs subsequent
- Startup memory/time deltas if startup-tagged events are present

---

Generated: 2025-12-16
