#!/usr/bin/env python3
"""Print FrameTimingMetric percentiles (P50/P90/P95/P99) from macrobenchmark JSON output.

One file:   tools/frame-percentiles.py after/*-benchmarkData.json
Two files:  tools/frame-percentiles.py before.json after.json   (adds a delta table)

The JSON is what `connectedBenchmarkAndroidTest` leaves next to the Perfetto traces, or what
`adb pull /sdcard/Android/media/<test app id>/additional_test_output/` returns.
"""
import json
import sys

PERCENTILES = ("P50", "P90", "P95", "P99")
METRICS = ("frameDurationCpuMs", "frameOverrunMs")


def load(path):
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle)
    result = {}
    for bench in data.get("benchmarks", []):
        sampled = bench.get("sampledMetrics", {})
        row = {}
        for metric in METRICS:
            stats = sampled.get(metric)
            if stats:
                row[metric] = {p: stats.get(p) for p in PERCENTILES}
        frames = bench.get("metrics", {}).get("frameCount")
        if frames:
            row["frameCount"] = frames.get("median")
        result[bench["name"]] = row
    return result


def fmt(value):
    return "n/a" if value is None else "%.2f" % value


def print_one(label, table):
    print(label)
    for name, row in sorted(table.items()):
        print("  " + name + ("  frames(median)=%s" % fmt(row.get("frameCount")) if "frameCount" in row else ""))
        for metric in METRICS:
            if metric in row:
                cells = "  ".join("%s=%s" % (p, fmt(row[metric][p])) for p in PERCENTILES)
                print("    %-18s %s" % (metric, cells))


def print_delta(before, after):
    print("\ndelta (after - before; negative is faster)")
    for name in sorted(set(before) & set(after)):
        print("  " + name)
        for metric in METRICS:
            if metric in before[name] and metric in after[name]:
                cells = []
                for p in PERCENTILES:
                    b, a = before[name][metric][p], after[name][metric][p]
                    cells.append("%s=%s" % (p, "n/a" if b is None or a is None else "%+.2f" % (a - b)))
                print("    %-18s %s" % (metric, "  ".join(cells)))


def main(argv):
    if len(argv) not in (1, 2):
        print(__doc__)
        return 2
    tables = [load(path) for path in argv]
    print_one("before" if len(argv) == 2 else argv[0], tables[0])
    if len(argv) == 2:
        print_one("after", tables[1])
        print_delta(tables[0], tables[1])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
