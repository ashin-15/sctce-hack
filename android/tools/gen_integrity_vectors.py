"""Generate integrity test vectors from the Python reference in bench/adapters.py.

Run from the repository root.
The system python3 lacks scikit-learn (imported by bench), so use:
    uv run --no-project --with numpy --with scikit-learn python android/tools/gen_integrity_vectors.py
"""
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path.cwd()))

from bench.adapters import chain, merkle  # noqa: E402

COUNTS = [0, 1, 2, 3, 4, 5, 8, 1000]
EXPLICIT = [[], [""], ["00"], ["e0b4b8e0b4be", "f09f9882"]]
OUTPUT = Path('android/testfixtures/integrity-vectors.json')


def entries_for(count):
    return [('sakshi synthetic entry ' + str(i)).encode('utf-8') for i in range(count)]


def main():
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    generated = []
    for count in COUNTS:
        entries = entries_for(count)
        generated.append({'count': count, 'chain_hex': chain(entries).hex(), 'merkle_hex': merkle(entries).hex()})
    explicit = []
    for hex_entries in EXPLICIT:
        entries = [bytes.fromhex(h) for h in hex_entries]
        explicit.append({'entries_hex': hex_entries, 'chain_hex': chain(entries).hex(), 'merkle_hex': merkle(entries).hex()})
    document = {
        'generated_from': 'bench/adapters.py chain, merkle',
        'bench_git_commit': commit,
        'entry_rule': "utf8('sakshi synthetic entry ' + decimal index)",
        'synthetic': True,
        'generated': generated,
        'explicit': explicit,
    }
    OUTPUT.write_text(json.dumps(document, indent=2, sort_keys=True, ensure_ascii=False) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
