WEIGHTS = {'accuracy': .35, 'latency': .20, 'peak_ram': .15, 'size': .10, 'integration_effort': .10, 'battery_thermal': .05, 'licence_openness': .05}
COSTS = {'latency', 'peak_ram', 'size', 'integration_effort', 'battery_thermal'}


def gate(candidate):
    if candidate.get('offline') is False:
        return 'disqualified: not fully offline'
    if candidate.get('licence_commercial') is False:
        return 'disqualified: licence blocks intended use'
    if candidate.get('peak_ram') is not None and candidate['peak_ram'] > 2_500_000_000:
        return 'disqualified: stage peak RAM above 2.5 GB'
    if candidate.get('offline') is not True or candidate.get('licence_commercial') is not True:
        return 'pending: offline/licence hard gate unknown'
    missing = [k for k in WEIGHTS if candidate.get(k) is None]
    return 'pending: missing ' + ', '.join(missing) if missing else 'eligible'


def score_slot(candidates):
    eligible = [c for c in candidates if gate(c) == 'eligible']
    results = []
    for candidate in candidates:
        status = gate(candidate)
        if status != 'eligible':
            results.append({**candidate, 'status': status, 'score': None})
            continue
        normalized = {}
        for metric, weight in WEIGHTS.items():
            low = min(c[metric] for c in eligible)
            high = max(c[metric] for c in eligible)
            value = 1. if high == low else (candidate[metric] - low) / (high - low)
            normalized[metric] = 1. - value if metric in COSTS and high != low else value
        results.append({**candidate, 'status': status, 'normalized': normalized,
                        'score': sum(WEIGHTS[m] * normalized[m] for m in WEIGHTS)})
    return sorted(results, key=lambda c: -(c['score'] if c['score'] is not None else -1))


def pareto(candidates):
    return [c for c in candidates if not any(o['accuracy'] >= c['accuracy'] and o['latency'] <= c['latency'] and
        (o['accuracy'] > c['accuracy'] or o['latency'] < c['latency']) for o in candidates)]
