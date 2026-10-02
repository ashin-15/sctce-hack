"""Reproduce the local inventory checks without changing or printing source text."""

import csv
import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

REPORT = Path("research/harassment-detection-datasets.md")
CSV = Path("data/labeled_data.csv")
SCHEMA = Path("data/sakshi-event-schema.json")
EXPECTED_HASH = "fcb8bc7c68120ae4af04a5b9acd58585513ede11e1548ebf36a5c2040b6f6281"


def main():
    original_hash = hashlib.sha256(CSV.read_bytes()).hexdigest()
    assert original_hash == EXPECTED_HASH, "Supplied CSV changed"
    with CSV.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        rows = list(reader)
        columns = reader.fieldnames
    assert columns == [
        "", "count", "hate_speech", "offensive_language", "neither", "class", "tweet"
    ], columns
    classes = dict(sorted(Counter(row["class"] for row in rows).items()))
    assert classes == {"0": 1430, "1": 19190, "2": 4163}, classes
    empty = sum(not row["tweet"].strip() for row in rows)
    duplicate_excess = len(rows) - len({row["tweet"] for row in rows})
    votes = [int(row["count"]) for row in rows]
    mismatches = sum(
        sum(int(row[key]) for key in ("hate_speech", "offensive_language", "neither"))
        != int(row["count"])
        for row in rows
    )
    assert (len(rows), empty, duplicate_excess, min(votes), max(votes), mismatches) == (
        24783, 0, 0, 3, 9, 0
    )

    report = REPORT.read_text()
    body, ledger = report.split("## Sources and verification ledger")
    ledger_keys = re.findall(r"\*\*(S\d{2}):\*\*", ledger)
    assert len(ledger_keys) == len(set(ledger_keys)) == 41
    references = set()
    for bracket in re.findall(r"\[([^\]\n]*S\d{2}[^\]\n]*)\]", body):
        for match in re.finditer(r"S(\d{2})(?:-S(\d{2}))?", bracket):
            references.update(
                f"S{number:02}"
                for number in range(int(match[1]), int(match[2] or match[1]) + 1)
            )
    assert references == set(ledger_keys), sorted(references - set(ledger_keys))
    for section in "ABCDEFG":
        assert re.search(rf"^# {section}\. ", report, re.M), section
    table_counts = {}
    expected_ids = {f"D{number:02}" for number in range(1, 40)}
    for section in ("A1", "A2", "A3"):
        table = report.split(f"## {section}.")[1].split("## ")[0]
        ids = re.findall(r"^\| (D\d{2}) \|", table, re.M)
        assert len(ids) == len(set(ids)) == 39 and set(ids) == expected_ids, section
        table_counts[section] = len(ids)

    schema = json.loads(SCHEMA.read_text())
    reconciliation = report.split("## E4.")[1].split("# F.")[0]
    mapped = re.findall(r"^\| `([^`]+)` \|", reconciliation, re.M)
    assert len(mapped) == len(set(mapped)) == 20
    assert set(mapped) == set(schema["required"])

    examples = re.findall(r"```json\n(.*?)\n```", report, re.S)
    assert len(examples) == 1
    example = json.loads(examples[0])
    events = {event["event_id"]: event for event in example["events"]}
    assert len(events) == len(example["events"])
    assert example["origin_kind"] == "human_authored_fiction"
    assert example["split"] == "diagnostic" and example["is_time_fabricated"]
    for annotation in example["annotations"]:
        assert annotation["as_of_event_id"] in events
        assert annotation["available_at"] is None
        for anchor in annotation.get("evidence_anchors", []):
            event = events[anchor["event_id"]]
            assert anchor["unit"] == "unicode_code_points"
            assert anchor["derivative_id"] == event["derivative_id"]
            assert 0 <= anchor["start"] < anchor["end"] <= len(event["text"])
            assert event["sequence_index"] <= events[annotation["as_of_event_id"]][
                "sequence_index"
            ]
    assert "\u2014" not in report and not re.search(r"TODO|FIXME|Saxshi", report)
    assert hashlib.sha256(CSV.read_bytes()).hexdigest() == original_hash
    result = {
        "checked_at": datetime.now(timezone.utc).isoformat(),
        "csv": {
            "path": str(CSV), "sha256": original_hash, "rows": len(rows),
            "columns": columns, "classes": classes,
            "class_percentages": {
                key: round(value / len(rows) * 100, 3) for key, value in classes.items()
            },
            "empty_texts": empty, "exact_duplicate_excess": duplicate_excess,
            "vote_range": [min(votes), max(votes)], "vote_sum_mismatches": mismatches,
            "unchanged": True,
        },
        "sections": list("ABCDEFG"),
        "source_keys_resolved": sorted(references),
        "dataset_rows_per_table": table_counts,
        "temporal_required_fields_covered": sorted(mapped),
        "fictional_example": "JSON parses; code-point anchors and prefix references pass",
        "report_sha256": hashlib.sha256(REPORT.read_bytes()).hexdigest(),
        "temporal_schema_sha256": hashlib.sha256(SCHEMA.read_bytes()).hexdigest(),
        "limits": [
            "Source-reported external counts were spot-checked, not independently re-counted",
            "Research envelope has no executable JSON Schema; example is not a temporal event",
            "Field coverage does not implement or validate an adapter",
            "No new corpus, training, inference or Android benchmark",
        ],
    }
    destination = Path("research/verification/datasets-validation.json")
    destination.write_text(json.dumps(result, indent=2) + "\n")
    print("Dataset inventory checks passed; CSV unchanged; receipt:", destination)


if __name__ == "__main__":
    main()
