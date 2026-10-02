import hashlib
import json
import math
import unicodedata
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

samples = [
    ('insult', 'You are an idiot.'),
    ('spacing', 'You are an i d i o t.'),
    ('leet', 'You are an 1d10t.'),
    ('punctuation', 'You are an i.d.i.o.t.'),
    ('zero_width_latin', 'You are an i\u200bd\u200bi\u200bo\u200bt.'),
    ('compatibility', 'You are an ｉｄｉｏｔ.'),
    ('quotation', 'He wrote "you are an idiot" to me. I am reporting it.'),
    ('negation', 'You are not an idiot.'),
    ('benign_anger', 'I am angry about the broken delivery, please fix it.'),
    ('explicit_harm', 'I will kill you tonight.'),
    ('polite_control_candidate', 'Please do not talk to your friends. I need to approve everyone you meet.'),
    ('conditional_exposure', 'Pay me or I will share your private photos.'),
    ('sarcasm_ambiguous', 'Great. Just what I needed.'),
    ('context_ambiguous', 'Fine.'),
    ('gratitude', 'Thank you for your help.'),
    ('manglish_a', 'nee ivide vannal nokkikko'),
    ('code_mix_a', "Don't tell anyone da"),
    ('hinglish_a', 'tum dekh lena'),
    ('manglish_b', 'nee entha ingane samsarikkunne?'),
    ('malayalam', 'നീ എവിടെയാണ് താമസിക്കുന്നത് എന്ന് എനിക്കറിയാം.'),
]

def sigmoid(x):
    return 1 / (1 + np.exp(-x))

def corn_distribution(logits):
    q = np.cumprod(sigmoid(np.asarray(logits)))
    return np.concatenate(([1-q[0]], q[:-1]-q[1:], [q[-1]]))

p = corn_distribution([0.1, 1.2, -0.3, 2.0])
assert np.isclose(p.sum(), 1) and (p >= 0).all()
assert 0 <= np.dot(np.arange(5), p)/4 <= 1
alpha = -math.expm1(-60/300)
assert 0 < alpha < 1
assert -math.expm1(0) == 0
original = samples[5][1]
derived = unicodedata.normalize('NFKC', original)
assert original != derived and original == samples[5][1]
assert 'idiot' in derived
assert abs((1-alpha)*0.2 + alpha*0.8 - 0.3087615481532109) < 1e-9

all_results = {}
for name, directory in [('behaviour', '/tmp/sakshi-minilm-tox'), ('emotion', '/tmp/sakshi-minilm-emotion')]:
    root = Path(directory)
    cfg = json.loads((root/'config.json').read_text())
    tok = Tokenizer.from_file(str(root/'tokenizer.json'))
    tok.enable_truncation(max_length=256)
    tok.no_padding()
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(str(root/'model_optimized_quantized.onnx'), sess_options=options, providers=['CPUExecutionProvider'])
    rows = []
    for fixture_id, text in samples:
        views = [('original', text)]
        if fixture_id == 'compatibility':
            views.append(('NFKC_diagnostic_not_production_default', unicodedata.normalize('NFKC', text)))
        for view, current in views:
            enc = tok.encode(current)
            data = {'input_ids': np.asarray([enc.ids], dtype=np.int64), 'attention_mask': np.asarray([enc.attention_mask], dtype=np.int64), 'token_type_ids': np.asarray([enc.type_ids], dtype=np.int64)}
            output = session.run(None, {i.name:data[i.name] for i in session.get_inputs()})[0]
            assert output.shape == (1, len(cfg['id2label'])) and np.isfinite(output).all()
            scores = sigmoid(output[0])
            indices = np.argsort(scores)[::-1][:3]
            selected = {cfg['id2label'][str(i)]:round(float(scores[i]), 6) for i in indices}
            if name == 'behaviour':
                selected['threat'] = round(float(scores[3]), 6)
            rows.append({'fixture':fixture_id, 'synthetic_text':text, 'view':view, 'original_sha256':hashlib.sha256(text.encode()).hexdigest(), 'tokens':len(enc.ids), 'unk':enc.ids.count(100) if name == 'behaviour' else None, 'raw_sigmoid_not_calibrated':selected})
    all_results[name] = rows
print(json.dumps({'fixture_count':len(samples), 'inference_rows_per_model':len(rows), 'runtime':ort.__version__, 'padding':'disabled', 'math_normalization_assertions':'passed', 'scope':'Linux x86 CPU synthetic diagnostics, no labeled quality/calibration/device measurements'}))
for model, model_rows in all_results.items():
    for row in model_rows:
        print(model, row['fixture'], row['view'], 'tokens', row['tokens'], 'unk', row['unk'], json.dumps(row['raw_sigmoid_not_calibrated']))
