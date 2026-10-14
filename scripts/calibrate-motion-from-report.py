"""Scan motion thresholds from a completed evaluation report without network requests."""

import argparse
import hashlib
import json
import math
from pathlib import Path


VEHICLE_MODES = {"CAR", "PUBLIC_TRANSPORT", "UNKNOWN"}


def possible_prediction(record, thresholds, baseline_index):
    baseline = record["sensorCalibrationPredictions"][baseline_index]["predictedMode"]
    if not record.get("sensorCalibrationCompleted") or thresholds is None:
        return {baseline}
    features = record["sensorFeatures"]
    std = features["accelMagnitudeStd"]
    count = thresholds["minAccelSamples"]
    if features["accelSampleCount"] < count or not math.isfinite(std) or std > thresholds["steadyAccelStd"]:
        return {baseline}
    gyro_limit = thresholds.get("steadyGyroMean")
    gyro = features.get("gyroMagnitudeMean")
    if (gyro_limit is not None and features.get("gyroSampleCount", 0) >= count
            and gyro is not None and math.isfinite(gyro) and gyro >= 0 and gyro > gyro_limit):
        return {baseline}
    if baseline == "WALKING":
        return {"UNKNOWN"}
    if baseline != "CYCLING":
        return {baseline}

    # Reuse this run's observed vehicle branch, never infer its output from ground truth.
    known = {p["predictedMode"] for p in record["sensorCalibrationPredictions"]
             if p["predictedMode"] in VEHICLE_MODES}
    if record["predictedMode"] in {"CAR", "PUBLIC_TRANSPORT"}:
        known.add(record["predictedMode"])
    hint = record.get("activityHint")
    if hint and hint["confidence"] >= 60 and hint["type"] == "IN_VEHICLE":
        known.add(record["predictedMode"])
    if len(known) > 1:
        raise ValueError(f"Inconsistent vehicle evidence for {record['sampleId']}")
    # Full transit routes were not saved. Preserve uncertainty if this branch was never run.
    return known or VEHICLE_MODES


def score(records, thresholds, baseline_index):
    correct = 0
    ambiguous = 0
    by_mode = {}
    for record in records:
        outcomes = possible_prediction(record, thresholds, baseline_index)
        truth = record["trueMode"]
        if len(outcomes) > 1:
            ambiguous += 1
            if truth in outcomes:
                raise ValueError(f"Cannot determine accuracy for {record['sampleId']} without saved transit evidence")
        hit = outcomes == {truth}
        correct += hit
        mode = by_mode.setdefault(truth, {"totalSamples": 0, "correctSamples": 0})
        mode["totalSamples"] += 1
        mode["correctSamples"] += hit
    for mode in by_mode.values():
        mode["accuracy"] = mode["correctSamples"] / mode["totalSamples"]
    return {"totalSamples": len(records), "correctSamples": correct,
            "accuracy": correct / len(records), "perMode": by_mode,
            "unresolvedVehiclePredictions": ambiguous}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--std-candidates", nargs="+", type=float, default=[.2, .4, .6, .8, 1, 1.5, 2, 3])
    parser.add_argument("--gyro-mean-candidates", nargs="+", type=float, default=[.1, .2, .3, .4, .5, .6, 1])
    parser.add_argument("--sample-candidates", nargs="+", type=int, default=[250, 500, 1000])
    args = parser.parse_args()
    for value in args.std_candidates + args.gyro_mean_candidates + args.sample_candidates:
        if not math.isfinite(value) or value <= 0:
            parser.error("All candidates must be finite and positive")
    if args.report.resolve() == args.output.resolve():
        parser.error("Output must not overwrite the input report")
    raw = args.report.read_bytes()
    report = json.loads(raw.decode("utf-8-sig"))
    if "sourceReportContent" in report:
        raw = report["sourceReportContent"].encode("utf-8")
        if hashlib.sha256(raw).hexdigest() != report["sourceSha256"]:
            parser.error("Embedded source report SHA-256 does not match")
        report = json.loads(raw.decode("utf-8-sig"))
    calibration = report["sensorCalibration"]
    records = report["records"]
    if (report.get("schemaVersion") != 6 or not report.get("completed") or not records
            or len(records) != report["plannedSamples"] or not calibration.get("activityHintsMasked")
            or not calibration.get("selectionAvailable") or not all(r["runtimeSuccess"] for r in records)):
        parser.error("A completed schema-6 forced-sensor evaluation without runtime errors is required")
    baseline_index = next(i for i, c in enumerate(calibration["candidates"]) if c["id"] == "speed_only")

    # Check every saved prediction before trusting the offline implementation.
    checked = 0
    for index, candidate in enumerate(calibration["candidates"]):
        for record in records:
            saved = record["sensorCalibrationPredictions"][index]
            outcomes = possible_prediction(record, candidate["thresholds"], baseline_index)
            if outcomes != {saved["predictedMode"]} or saved["correct"] != (saved["predictedMode"] == record["trueMode"]):
                raise ValueError(f"Offline prediction differs for {record['sampleId']}, {candidate['id']}")
            checked += 1
        if score(records, candidate["thresholds"], baseline_index)["correctSamples"] != candidate["summary"]["correctSamples"]:
            raise ValueError(f"Incorrect saved summary for {candidate['id']}")

    thresholds = [None] + [
        {"steadyAccelStd": std, "minAccelSamples": count, "steadyGyroMean": gyro}
        for std in sorted(set(args.std_candidates))
        for count in sorted(set(args.sample_candidates), reverse=True)
        for gyro in [None] + sorted(set(args.gyro_mean_candidates))
    ]
    candidates = [{"id": "speed_only" if i == 0 else f"motion_{i}", "thresholds": t,
                   "summary": score(records, t, baseline_index)} for i, t in enumerate(thresholds)]
    best = max(candidates, key=lambda c: c["summary"]["correctSamples"])
    result = {
        "sourceReport": str(args.report.resolve()), "sourceSha256": hashlib.sha256(raw).hexdigest(),
        "sourceReportContent": raw.decode("utf-8"),
        "datasetSha256": report["datasetSha256"], "activityHintsMasked": True,
        "selectionMetric": "overall accuracy on all evaluated samples; no train/validation split",
        "tieBreak": "Prefer speed_only, then lower steadyAccelStd, then higher minAccelSamples, then gyro disabled, then lower steadyGyroMean",
        "verifiedSavedPredictions": checked,
        "method": "Reuse this run's features and observed vehicle outputs. No API calls or raw sensor replay.",
        "limitation": "Unobserved vehicle details may be ambiguous. Accuracy is exact only when all possible outputs have the same correctness; otherwise this script fails.",
        "candidates": candidates, "bestCandidateId": best["id"], "bestThresholds": best["thresholds"],
        "bestAccuracy": best["summary"]["accuracy"], "bestPerMode": best["summary"]["perMode"],
        "tiedBestCandidateIds": [c["id"] for c in candidates if c["summary"]["correctSamples"] == best["summary"]["correctSamples"]],
        "bestPredictions": [{"sampleId": r["sampleId"], "trueMode": r["trueMode"],
                             "possibleModes": sorted(possible_prediction(r, best["thresholds"], baseline_index)),
                             "baselineMode": r["sensorCalibrationPredictions"][baseline_index]["predictedMode"]}
                            for r in records],
    }
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Verified {checked} saved predictions; scanned {len(candidates)} candidates.")
    print(f"Best: {best['thresholds']}; {best['summary']['correctSamples']}/{len(records)} = {result['bestAccuracy']:.2%}")
    print(f"Saved: {args.output.resolve()}")


if __name__ == "__main__":
    main()
