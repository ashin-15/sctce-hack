"""Generates bench-rule-labels.json from bench/adapters.py without importing it (numpy-free)."""
import ast, json, re, sys, textwrap
root = sys.argv[1]
src = open(f'{root}/bench/adapters.py', encoding='utf-8').read()
tree = ast.parse(src)
rules = None
identify_src = None
for node in ast.walk(tree):
    if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'RULES' for t in node.targets):
        rules = ast.literal_eval(node.value)
    if isinstance(node, ast.ClassDef) and node.name == 'Language':
        for item in node.body:
            if isinstance(item, ast.FunctionDef) and item.name == 'identify':
                item.decorator_list = []
                identify_src = ast.unparse(item)
ns = {'re': re}
exec(identify_src, ns)
identify = ns['identify']
rows = []
for line in open(f'{root}/data/text.jsonl', encoding='utf-8'):
    r = json.loads(line)
    t = r['text']
    labels = sorted(l for l, ks in rules.items() if any(k.casefold() in t.casefold() for k in ks))
    rows.append({'id': r['id'], 'split': r['split'], 'language': r['language'], 'gold': sorted(r['labels']),
                 'rule_labels': labels, 'identify': identify(t)})
out = {'generated_by': 'python3 android/tools/gen_rule_expectations.py <repo root> <output>; RULES read by ast from bench/adapters.py, Language.identify extracted by ast and exec-ed, any(k.casefold() in text.casefold()) replicated; fixtures are synthetic',
       'rules': rules, 'rows': rows}
json.dump(out, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
