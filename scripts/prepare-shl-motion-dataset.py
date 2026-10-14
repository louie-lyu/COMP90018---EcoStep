"""Add only accelerometer/gyroscope rows belonging to existing evaluated SHL journeys.

Uses only Python's standard library. Original downloads and the GPS-only backup are retained.
The existing classifier expects integer millisecond timestamps and Android-compatible DEFLATE.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import time
import zipfile


def digest_file(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def copy_entry(source, target, name):
    digest = hashlib.sha256()
    with source.open(name) as incoming, target.open(name, 'w', force_zip64=True) as outgoing:
        for chunk in iter(lambda: incoming.read(1024 * 1024), b''):
            digest.update(chunk)
            outgoing.write(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-dir', type=Path, default=Path(__file__).resolve().parents[1] /
                        'app/src/androidTest/java/com/ecostep/app/evaluation/data')
    parser.add_argument('--segments-report', type=Path, required=True,
                        help='Existing full SHL evaluation JSON identifying the original recordings.')
    args = parser.parse_args()
    root = args.data_dir.resolve(strict=True)
    target = root / 'SHL-preview-evaluation.zip'
    backup = root / 'SHL-preview-evaluation.gps-only.zip'
    building = root / 'SHL-preview-evaluation.with-motion.building.zip'
    # Every created/replaced file is a direct child of the explicitly selected data directory.
    assert all(path.resolve().parent == root for path in (target, backup, building))
    if building.exists():
        raise RuntimeError(f'An unfinished output already exists: {building}')
    with zipfile.ZipFile(target) as current:
        has_motion = any(name.endswith('/Hand_Motion.txt') for name in current.namelist())
    baseline = backup if has_motion else target
    with zipfile.ZipFile(baseline) as base:
        motion_names = sorted(name.removesuffix('Label.txt') + 'Hand_Motion.txt'
                              for name in base.namelist() if name.endswith('/Label.txt'))
    sources = {}
    for path in sorted(root.glob('SHLDataset_preview_v1_part*.zip')):
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                if info.filename in motion_names:
                    if info.filename in sources:
                        raise RuntimeError(f'Duplicate source: {info.filename}')
                    sources[info.filename] = (path, info)
    # Reuse an already normalized local archive when available; raw downloads remain untouched.
    if has_motion:
        with zipfile.ZipFile(target) as current:
            for info in current.infolist():
                if info.filename in motion_names:
                    sources[info.filename] = (target, info)
    missing = set(motion_names) - sources.keys()
    if missing:
        raise RuntimeError(f'Missing motion files in source archives: {sorted(missing)}')
    base_hash = digest_file(baseline)
    segments_report = json.loads(args.segments_report.read_text(encoding='utf-8-sig'))
    if segments_report.get('datasetSha256') != base_hash:
        raise RuntimeError('The segments report does not match the GPS-only archive SHA-256.')
    records = segments_report['records']
    if not segments_report.get('completed') or len(records) != segments_report.get('plannedSamples'):
        raise RuntimeError('A complete evaluation report is required; no extra journeys will be selected.')
    intervals = {name: [] for name in motion_names}
    sample_ids = set()
    for record in records:
        if not record.get('recordingAvailable') or record['sampleId'] in sample_ids:
            raise RuntimeError('Report contains unavailable or duplicate recordings.')
        sample_ids.add(record['sampleId'])
        user, date, _ = record['sampleId'].split('_', 2)
        matching = [name for name in motion_names if f'/{user}/{date}/' in name]
        if len(matching) != 1:
            raise RuntimeError(f'Cannot locate original recording {record["sampleId"]}')
        start, end = record['startTimeMillis'], record['endTimeMillis']
        if not isinstance(start, int) or not isinstance(end, int) or end <= start:
            raise RuntimeError('Invalid recording boundaries.')
        intervals[matching[0]].append((start, end))
    for ranges in intervals.values():
        ranges.sort()
        if any(left[1] > right[0] for left, right in zip(ranges, ranges[1:])):
            raise RuntimeError('Overlapping recording boundaries.')
    if backup.exists():
        if digest_file(backup) != base_hash:
            raise RuntimeError('Existing GPS-only backup differs; refusing to overwrite it.')
    else:
        shutil.copy2(target, backup)

    expected_hashes = {}
    motion_records = []
    timestamp_pattern = re.compile(rb'^(\d+)(?:\.(0+))?$')
    started = time.monotonic()
    with zipfile.ZipFile(backup) as base, zipfile.ZipFile(
        building, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True,
    ) as output:
        manifest_bytes = base.read('manifest.json')
        manifest = json.loads(manifest_bytes)
        for info in base.infolist():
            if info.filename != 'manifest.json':
                expected_hashes[info.filename] = copy_entry(base, output, info.filename)
        output.writestr('manifest.gps-only.json', manifest_bytes)
        expected_hashes['manifest.gps-only.json'] = hashlib.sha256(manifest_bytes).hexdigest()
        for index, name in enumerate(motion_names, 1):
            source_path, info = sources[name]
            print(f'[{index}/{len(motion_names)}] Converting {name} ({info.file_size / 1e6:.0f} MB)', flush=True)
            raw_hash = hashlib.sha256()
            normalized_hash = hashlib.sha256()
            rows = valid = normalized = scanned = 0
            first = last = previous = None
            ranges = intervals[name]
            range_index = 0
            buffer = bytearray()
            with zipfile.ZipFile(source_path) as source, source.open(name) as incoming, output.open(
                name, 'w', force_zip64=True,
            ) as outgoing:
                for line in incoming:
                    raw_hash.update(line)
                    if not line.strip():
                        continue
                    fields = line.split(None, 7)
                    if len(fields) < 7:
                        raise RuntimeError(f'{name}: expected at least 7 columns')
                    match = timestamp_pattern.fullmatch(fields[0])
                    if match is None:
                        raise RuntimeError(f'{name}: non-integral millisecond timestamp {fields[0]!r}')
                    timestamp = int(match[1])
                    if previous is not None and timestamp <= previous:
                        raise RuntimeError(f'{name}: non-increasing timestamps')
                    previous = timestamp
                    scanned += 1
                    while range_index < len(ranges) and timestamp >= ranges[range_index][1]:
                        range_index += 1
                    if range_index == len(ranges) or timestamp < ranges[range_index][0]:
                        continue
                    if first is None:
                        first = timestamp
                    last = timestamp
                    values = [float(value) for value in fields[1:7]]
                    valid += all(math.isfinite(value) for value in values)
                    rows += 1
                    # Keep original six numeric tokens (including NaN), at the original 100 Hz.
                    converted = b' '.join([match[1]] + fields[1:7]) + b'\n'
                    normalized += match[1] != fields[0]
                    buffer.extend(converted)
                    if len(buffer) >= 1024 * 1024:
                        normalized_hash.update(buffer)
                        outgoing.write(buffer)
                        buffer.clear()
                    if rows % 1_000_000 == 0:
                        print(f'  {rows:,} rows; {time.monotonic() - started:.0f}s elapsed', flush=True)
                if buffer:
                    normalized_hash.update(buffer)
                    outgoing.write(buffer)
            if not valid:
                raise RuntimeError(f'No finite accelerometer/gyroscope rows in {name}')
            record = dict(path=name, sourceArchive=source_path.name, sourceZipCrc32=f'{info.CRC:08x}',
                          sourceSha256=raw_hash.hexdigest(), sha256=normalized_hash.hexdigest(),
                          rows=rows, sourceRowsScanned=scanned, intervals=len(ranges),
                          finiteAccelAndGyroRows=valid, normalizedTimestamps=normalized,
                          firstTimestampMillis=first, lastTimestampMillis=last)
            motion_records.append(record)
            expected_hashes[name] = record['sha256']
            print(f'  Finished: {rows:,} rows, {valid:,} finite accel/gyro rows', flush=True)
        manifest['selection'] += f'; only time/accelerometer/gyroscope columns within the {len(records)} original evaluated recordings.'
        manifest['originalContentsPreserved'] = False
        manifest['compression'] = 'DEFLATE for Android java.util.zip.ZipFile compatibility'
        manifest['motionExtension'] = dict(
            baseArchiveSha256=base_hash, baseManifest='manifest.gps-only.json',
            timestampNormalization='Remove only an all-zero fractional part; exact integer milliseconds, no rounding.',
            sensorValues='Original accelerometer XYZ and gyroscope XYZ numeric tokens retained at 100 Hz, including NaN; no resampling or imputation.',
            selection='Only [start,end) intervals in the matching original evaluation report; no new journeys.',
            segmentsReport=args.segments_report.name, segmentsReportSha256=digest_file(args.segments_report),
            recordingCount=len(records), recordings=[{k: r[k] for k in ('sampleId', 'startTimeMillis', 'endTimeMillis')} for r in records],
            existingFiles='Existing GPS, Activity, labels, documentation and other entries unchanged byte-for-byte.',
            files=motion_records,
        )
        manifest.setdefault('files', []).extend(motion_records)
        new_manifest = json.dumps(manifest, ensure_ascii=False, indent=2).encode('utf-8')
        output.writestr('manifest.json', new_manifest)
        expected_hashes['manifest.json'] = hashlib.sha256(new_manifest).hexdigest()

    print('Verifying every output entry against its expected SHA-256 and ZIP CRC...', flush=True)
    with zipfile.ZipFile(building) as archive:
        assert len(archive.infolist()) == len(expected_hashes)
        for info in archive.infolist():
            assert info.compress_type == zipfile.ZIP_DEFLATED
            digest = hashlib.sha256()
            with archive.open(info) as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
            if digest.hexdigest() != expected_hashes[info.filename]:
                raise RuntimeError(f'Output verification failed: {info.filename}')
        assert all(name in archive.namelist() for name in motion_names)
    # Promote only the fully verified new archive. The verified original remains in backup.
    os.replace(building, target)
    report = dict(output=str(target), bytes=target.stat().st_size, sha256=digest_file(target),
                  backup=str(backup), backupSha256=base_hash, motionFiles=motion_records,
                  verifiedEntries=len(expected_hashes), recordingCount=len(records),
                  elapsedSeconds=round(time.monotonic() - started, 1))
    report_path = root / 'SHL-preview-evaluation.preparation.json'
    report_path.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({key: value for key, value in report.items() if key != 'motionFiles'}, indent=2), flush=True)


if __name__ == '__main__':
    main()
