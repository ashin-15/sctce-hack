import argparse
import hashlib
import json
import platform
import resource
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
import tokenizers
from tokenizers import Tokenizer

parser = argparse.ArgumentParser()
parser.add_argument("model_dir")
args = parser.parse_args()
root = Path(args.model_dir)
model_file = root / "model_optimized_quantized.onnx"
config = json.loads((root / "config.json").read_text())
start = time.perf_counter()
tok = Tokenizer.from_file(str(root / "tokenizer.json"))
tok.enable_truncation(max_length=256)
tok.enable_padding(pad_id=config["pad_token_id"], pad_token="<pad>" if config["model_type"] == "roberta" else "[PAD]")
options = ort.SessionOptions()
options.intra_op_num_threads = 4
options.inter_op_num_threads = 1
session = ort.InferenceSession(str(model_file), sess_options=options, providers=["CPUExecutionProvider"])
load_ms = (time.perf_counter() - start) * 1000
texts = [
    "Thank you for your help.",
    "I am angry about the broken delivery, please fix it.",
    "I will kill you tonight.",
    "You know where I live.",
    "Do not threaten me again.",
    "I will wait outside your home every night until you answer me.",
    "നീ എവിടെയാണ് താമസിക്കുന്നത് എന്ന് എനിക്കറിയാം.",
    "nee evideya thamasikkunnath enikkariyam",
]
rows = []
for text in texts:
    enc = tok.encode(text)
    data = {
        "input_ids": np.array([enc.ids], dtype=np.int64),
        "attention_mask": np.array([enc.attention_mask], dtype=np.int64),
        "token_type_ids": np.array([enc.type_ids], dtype=np.int64),
    }
    feed = {i.name: data[i.name] for i in session.get_inputs()}
    start = time.perf_counter()
    logits = session.run(None, feed)[0]
    elapsed = (time.perf_counter() - start) * 1000
    assert logits.shape == (1, len(config["id2label"]))
    assert np.isfinite(logits).all()
    probs = 1 / (1 + np.exp(-logits[0]))
    rows.append({"synthetic_text": text, "tokens": len(enc.ids), "unknown_tokens": enc.ids.count(100) if config["model_type"] == "bert" else None, "first_inference_ms": round(elapsed, 3), "raw_scores_not_calibrated": {config["id2label"][str(i)]: round(float(p), 5) for i, p in enumerate(probs)}})

bench = {}
for count in [32, 128, 256]:
    enc = tok.encode("Please stop contacting me. " * 100)
    ids = enc.ids[:count]
    feed = {"input_ids": np.array([ids], dtype=np.int64), "attention_mask": np.ones((1, count), dtype=np.int64), "token_type_ids": np.zeros((1, count), dtype=np.int64)}
    feed = {i.name: feed[i.name] for i in session.get_inputs()}
    for _ in range(5):
        session.run(None, feed)
    times = []
    for _ in range(30):
        start = time.perf_counter()
        session.run(None, feed)
        times.append((time.perf_counter() - start) * 1000)
    bench[str(count)] = {"p50_ms": round(float(np.percentile(times, 50)), 3), "p95_ms": round(float(np.percentile(times, 95)), 3), "runs": 30, "warmups": 5, "scope": "forward_only_synthetic_repeated_token_input_not_quality"}

print(json.dumps({"platform": platform.platform(), "machine": platform.machine(), "python": platform.python_version(), "onnxruntime": ort.__version__, "tokenizers": tokenizers.__version__, "numpy": np.__version__, "model_dir": str(root), "model_bytes": model_file.stat().st_size, "model_sha256": hashlib.sha256(model_file.read_bytes()).hexdigest(), "load_ms_file_cache_uncontrolled": round(load_ms, 3), "process_peak_rss_kib_linux": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss, "threads": {"intra_op": 4, "inter_op": 1}, "inputs": [{"name": i.name, "type": i.type, "shape": i.shape} for i in session.get_inputs()], "outputs": [{"name": o.name, "type": o.type, "shape": o.shape} for o in session.get_outputs()], "bench": bench, "rows": rows, "limitations": ["No Android execution", "No Laya weights or forward pass", "No calibration", "No representative labelled dataset", "Laptop timings are not phone estimates", "No controlled cold-start, energy, or isolated model RAM measurement"]}, ensure_ascii=False, indent=2))
