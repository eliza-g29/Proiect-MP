"""Measure frozen-model inference only, without HTTP/DB or model changes."""
import json
from pathlib import Path
import statistics
import time
from dataset import load_split
from model import VERSION, demo_response

if __name__ == "__main__":
    rows = load_split("test")
    samples = []
    for row in rows:
        started = time.perf_counter()
        demo_response(row["clinical_input_text"])
        samples.append((time.perf_counter() - started) * 1000)
    samples.sort()
    report = {"model": VERSION, "examples": len(samples), "unit": "milliseconds",
              "median": statistics.median(samples), "p95": samples[int(len(samples) * 0.95) - 1],
              "maximum": max(samples), "includes_http": False, "includes_database": False,
              "clinical_validation": False, "environment": "Current execution session; CPU inference"}
    path = Path(__file__).resolve().parent / "artifacts" / "inference_benchmark.json"
    path.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
