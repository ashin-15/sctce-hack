import re
import unicodedata

import numpy as np
from sklearn.metrics import precision_recall_fscore_support


def normalized(text):
    return ' '.join(unicodedata.normalize('NFC', text).casefold().split())


def edit_distance(a, b):
    previous = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        current = [i]
        for j, y in enumerate(b, 1):
            current.append(min(current[-1] + 1, previous[j] + 1, previous[j - 1] + (x != y)))
        previous = current
    return previous[-1]


def error_rates(references, predictions):
    pairs = [(normalized(a), normalized(b)) for a, b in zip(references, predictions)]
    return {'cer': sum(edit_distance(a, b) for a, b in pairs) / max(1, sum(len(a) for a, _ in pairs)),
            'wer': sum(edit_distance(a.split(), b.split()) for a, b in pairs) / max(1, sum(len(a.split()) for a, _ in pairs))}


def threshold_for_target(y, p, target, mode):
    options = []
    for threshold in sorted(set([0., 1.000001] + list(map(float, p)))):
        pred = p >= threshold
        tp = int(np.sum(pred & (y == 1)))
        precision = tp / max(1, int(pred.sum()))
        recall = tp / max(1, int(y.sum()))
        if (precision if mode == 'precision' else recall) >= target and pred.any():
            options.append((recall if mode == 'precision' else precision, threshold))
    return max(options)[1] if options else None


def ece(y, p, bins=10):
    y, p = np.asarray(y).ravel(), np.asarray(p).ravel()
    error = 0.
    for i in range(bins):
        mask = (p >= i / bins) & ((p < (i + 1) / bins) if i < bins - 1 else (p <= 1))
        if mask.any():
            error += mask.mean() * abs(y[mask].mean() - p[mask].mean())
    return float(error)


def classifier_metrics(y, p, labels, thresholds, operating, subset=None):
    y, p = np.asarray(y), np.asarray(p)
    pred = p >= np.asarray(thresholds)
    precision, recall, f1, support = precision_recall_fscore_support(y, pred, average=None, zero_division=0)
    result = {'macro_f1': float(f1.mean()), 'ece': ece(y, p),
              'flag_precision': float(precision_recall_fscore_support(y.any(axis=1), pred.any(axis=1), average='binary', zero_division=0)[0]),
              'flag_recall': float(precision_recall_fscore_support(y.any(axis=1), pred.any(axis=1), average='binary', zero_division=0)[1])}
    for j, label in enumerate(labels):
        result.update({f'{label}.precision': float(precision[j]), f'{label}.recall': float(recall[j]), f'{label}.f1': float(f1[j]), f'{label}.support': int(support[j])})
        for mode, target in [('precision', .9), ('recall', .95)]:
            threshold = operating[j][mode]
            if threshold is None:
                result[f'{label}.{mode}_target_{target}_attainable_val'] = 0
                continue
            z = p[:, j] >= threshold
            tp = int(np.sum(z & (y[:, j] == 1)))
            result[f'{label}.precision_at_val_{mode}_{target}'] = tp / max(1, int(z.sum()))
            result[f'{label}.recall_at_val_{mode}_{target}'] = tp / max(1, int(y[:, j].sum()))
    if subset is not None:
        for challenge in ['veiled_threat', 'victim_distress', 'friendly_joking', 'misspelling']:
            mask = np.array([r['challenge'] == challenge for r in subset])
            if mask.any():
                result[f'{challenge}.flag_rate'] = float(pred[mask].any(axis=1).mean())
                result[f'{challenge}.support'] = int(mask.sum())
    return result


def quote_faithfulness(source, quotes):
    nonempty = [q for q in quotes if q]
    return {'hallucinated_quote_rate': sum(q not in source for q in nonempty) / max(1, len(nonempty)),
            'quote_count': len(nonempty)}


def field_f1(reference, predicted):
    def pairs(fields):
        return {(k, str(v)) for k, value in fields.items() for v in (value if isinstance(value, list) else [value])}
    gold, got = pairs(reference), pairs(predicted)
    tp = len(gold & got)
    return 2 * tp / max(1, len(gold) + len(got))


def grouping_metrics(gold, predicted):
    tp = fp = fn = 0
    for i in range(len(gold)):
        for j in range(i):
            same_gold, same_pred = gold[i] == gold[j], predicted[i] == predicted[j]
            tp += same_gold and same_pred
            fp += not same_gold and same_pred
            fn += same_gold and not same_pred
    return {'grouping_precision': tp / max(1, tp + fp), 'grouping_recall': tp / max(1, tp + fn)}
