# Temporal-patterns research completion

Completed 2 October 2026. These receipts validate research artifacts and their
synthetic demonstration. They do not establish an implemented temporal engine,
Android compatibility, detection accuracy, calibration, battery cost or product
acceptance. Android evidence acquisition remains the first engineering milestone.
The engine design and pending engineering tests remain in report sections 14 and
13.4 after removal of the completed handoff.

## Schema and synthetic example

Run from the repository root with previously cached dependencies:

```sh
uv run --no-project --offline --python 3.12 --with 'jsonschema[format]' \
  python research/verification/temporal-patterns-validate.py
```

`temporal-patterns-validation.json` records schema validity, the complete report
example with date-time format checking, six rejected malformed variants, style
checks and SHA-256 digests. The script does not implement the cross-record
invariants in section 6.4. In particular, the all-zero example hash remains a
fixture placeholder, and the 09:20 review cannot be used by a 09:10 analysis.
No model download or inference is involved.

## Browser verification

The in-app browser execution tool was unavailable. The standalone Chromium
check opens only the local HTML in a disposable profile:

```sh
node research/verification/temporal-patterns-browser-check.mjs
```

Use `CHROMIUM_PATH` for another Chromium executable. Node must support the
built-in WebSocket API. The sandbox blocked Chromium's startup socket during this
run; execution outside that sandbox succeeded. The disposable browser uses
`--no-sandbox`; this is a local artifact check, not a browsing configuration.

`temporal-patterns-browser.json` records 1440px/390px checks in light/dark themes:
21 replay combinations per viewport/theme (84 total), counts/spans, boundary
qualifiers, search including empty results, theme/disclosure controls, navigation
targets, duplicate IDs, page/SVG bounds, runtime exceptions and remote requests.
A suggested "recurrence" search initially returned no rows; the boundary-state
row now includes that term. Full-page and header/replay screenshots are stored
in temporary paths for visual inspection; they do not survive cleanup/reboot.
Wide tables remain scrollable inside their containers at phone width.

## Primary-source figure check

Live checks on 2 October 2026 confirmed the figures flagged by the handoff:

- [Graphically Speaking v1](https://arxiv.org/html/2504.01902v1), tables 1-2:
  GAT 0.7624 and the three context baselines; ten-run means and 95% CIs.
- [TGBully v2](https://arxiv.org/html/2011.00449v2), tables 2-3 and section 5.2:
  Instagram micro F1 80.97 and Vine 69.35; five-run means and standard deviations.
- [CGA evaluation](https://arxiv.org/html/2507.19470), table 1:
  the report's CRAFT/BERT/Gemma2/Mistral accuracy, F1, FPR and horizon columns match.
- [DCAP institution abstract](https://pure.hud.ac.uk/en/publications/a-hybrid-neural-symbolic-approach-for-the-longitudinal-profiling-/):
  0.85 macro F1 in comparative experiments and, separately, 92.8% target-review
  reduction on the simulated 8,451-message case. No full replication was performed.

This was a targeted numerical cross-check, not a new systematic literature review.
The remaining source-register entries retain the original investigation's scope.
None of these external figures is a Sakshi benchmark.

## Repository regression suite

```sh
uv run --no-project --offline --python 3.12 \
  --with scikit-learn==1.7.2 --with numpy==2.2.6 \
  --with pycryptodome==3.23.0 --with psutil==7.0.0 \
  python -m unittest discover -s bench/tests -v
```

Result: 20 tests run, 19 passed, one optional faster-whisper decoder test skipped
because its runtime is absent. Dependency files and measured benchmark results
were not changed. No private evidence, model binaries, audio or keys were added.
