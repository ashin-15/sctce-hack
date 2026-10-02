"""Validate research artifacts; this is not a temporal-engine acceptance test."""

import copy
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

REPORT = Path("research/temporal-harassment-patterns.md")
SCHEMA = Path("data/sakshi-event-schema.json")
ARTIFACT = Path(".lavish/sakshi-temporal-patterns.html")


def main():
    report = REPORT.read_text()
    schema = json.loads(SCHEMA.read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    blocks = re.findall(r"```json\n(.*?)\n```", report, re.S)
    assert len(blocks) == 1, "Expected one complete synthetic event example"
    example = json.loads(blocks[0])
    validator.validate(example)

    # Each mutation exercises a safety-relevant contract, rather than a count.
    variants = {}
    changed = copy.deepcopy(example)
    changed["observed_at"] = "bad-date"
    variants["date-time format"] = changed
    changed = copy.deepcopy(example)
    changed["categories"][0]["confidence"].update(
        semantics="uncalibrated_bounded_score", value=0.9
    )
    variants["manual tag cannot invent probability"] = changed
    changed = copy.deepcopy(example)
    changed["retention"].update(mode="encrypted_candidate", expires_at=None)
    variants["candidate needs expiry"] = changed
    changed = copy.deepcopy(example)
    changed["deduplication"]["status"] = "same_representation"
    variants["duplicate needs canonical target"] = changed
    changed = copy.deepcopy(example)
    changed["timestamp"]["basis"] = "unknown"
    variants["unknown time cannot have known bounds"] = changed
    changed = copy.deepcopy(example)
    changed["categories"][0].update(
        basis="classifier_suggestion",
        confidence={
            "value": 0.9,
            "semantics": "calibrated_probability",
            "calibration_version": None,
        },
    )
    variants["calibrated probability needs calibration version"] = changed
    for name, value in variants.items():
        assert list(validator.iter_errors(value)), name

    files = [REPORT, SCHEMA, ARTIFACT]
    for path in files:
        assert not re.search("[\u2013\u2014]|TODO|FIXME|Saxshi", path.read_text()), path
    result = {
        "checked_at": datetime.now(timezone.utc).isoformat(),
        "schema": "Draft 2020-12 with date-time FormatChecker",
        "schema_valid": True,
        "synthetic_example_errors": 0,
        "malformed_variants_rejected": list(variants),
        "style_checks": "passed",
        "sha256": {
            str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in files
        },
        "limits": [
            "Does not check cross-record/application invariants in section 6.4",
            "Fixture all-zero hash is not verified source integrity",
            "Does not establish temporal-engine or Android acceptance",
        ],
    }
    Path("research/verification/temporal-patterns-validation.json").write_text(
        json.dumps(result, indent=2) + "\n"
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
