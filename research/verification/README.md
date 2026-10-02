# Sentiment/emotion handoff verification

These receipts support the completion of `HANDOFF.md`. They do not establish
Android compatibility, harassment detection quality, calibration or product acceptance.
All probe inputs are authored synthetic fixtures.

## Offline model diagnostics

With the previously provisioned `/tmp/sakshi-minilm-tox` and
`/tmp/sakshi-minilm-emotion` packs and cached dependencies:

```sh
uv run --no-project --offline \
  --with onnxruntime==1.22.1 --with tokenizers==0.22.0 --with numpy==2.3.3 \
  python research/probes/sakshi-emotion-probe.py
```

`sentiment-emotion-probe.json` records the current output, graph/config/tokenizer
digests and environment. Twenty fixtures plus one normalization view produce
21 rows per model. The script checks shape, finite logits, ordinal equations,
time-aware smoothing and original preservation. It does not calculate task accuracy.
Its inputs and models must remain separate from real evidence.

Missing packs require an explicit preparation download pinned to the revisions
in the report. Do not fetch models during inference. Temporary pack directories
and screenshots will not survive cleanup or reboot.

## Visual and interaction checks

The in-app browser control tool was unavailable during this run. The standalone
headless Chromium check uses a new temporary profile and opens only the local HTML.
Run from the repository root with Node supporting the built-in `WebSocket` API:

```sh
node research/verification/sentiment-emotion-browser-check.mjs
```

Set `CHROMIUM_PATH` to a Chromium executable if it is not on `PATH`.
`sentiment-emotion-browser.json` records 1440px/390px checks in dark/light themes,
all fixture options, filtering including empty results, navigation, disclosure
and theme controls, duplicate IDs, SVG text bounds, page overflow, runtime
exceptions and remote requests. Tables scroll inside their containers on narrow
screens. The receipt points to temporary full-page screenshots which were also
visually inspected. The test uses `--no-sandbox` in a disposable local browser
profile; it is not a production browsing configuration.

## Repository regression suite

The isolated completion environment uses Python 3.12 and the pinned dependencies
needed by the benchmark unit tests:

```sh
uv run --no-project --python 3.12 \
  --with scikit-learn==1.7.2 --with numpy==2.2.6 \
  --with pycryptodome==3.23.0 --with psutil==7.0.0 \
  python -m unittest discover -s bench/tests -v
```

This command may provision dependencies before tests. Optional faster-whisper
decoder coverage requires its separately pinned runtime/PyAV pack and is skipped
when that pack is absent. No dependency file, fixture corpus or benchmark result
was changed by this handoff completion.

`sentiment-emotion-validation.json` records the final report structure, citation
coverage, syntax/style and regression checks. Source-register entries distinguish
live primary-source checks from historical inspection and local diagnostics.
