import hashlib
import json
import random
import textwrap
from pathlib import Path

SEED = 1729
LABELS = ['insult', 'threat', 'sexual_harassment', 'caste_religious_slur', 'doxxing', 'coercive_control']
LANGUAGES = ['en', 'hi', 'hinglish', 'ml', 'manglish', 'mixed']
SCENARIOS = [
    ('insult', ['You are worthless.', 'तुम बेकार हो।', 'Tum bekaar ho.', 'നീ ഒന്നിനും കൊള്ളില്ല.', 'Nee onninum kollilla.', 'Tum are worthless.']),
    ('insult', ['Nobody wants an idiot like you here.', 'तुम जैसे मूर्ख को यहाँ कोई नहीं चाहता।', 'Tum jaise bewakoof ko yahan koi nahi chahta.', 'നിന്നെപ്പോലെ ഒരു വിഡ്ഢിയെ ഇവിടെ ആരും ആഗ്രഹിക്കുന്നില്ല.', 'Ninnepole oru viddhiye ivide aarum aagrahikkunnilla.', 'Nobody wants tum jaise idiot here.']),
    ('threat', ['I will hurt you after class.', 'कक्षा के बाद मैं तुम्हें चोट पहुँचाऊँगा।', 'Class ke baad main tumhe chot pahunchaunga.', 'ക്ലാസ് കഴിഞ്ഞാൽ ഞാൻ നിന്നെ ഉപദ്രവിക്കും.', 'Class kazhinjal njan ninne upadravikkum.', 'Class ke baad I will hurt you.']),
    ('threat', ['Keep talking; the walk home may not be safe.', 'बोलते रहो; घर जाने का रास्ता सुरक्षित नहीं होगा।', 'Bolte raho; ghar ka rasta safe nahi hoga.', 'സംസാരിച്ചുകൊണ്ടിരിക്ക്; വീട്ടിലേക്കുള്ള വഴി സുരക്ഷിതമാകില്ല.', 'Samsarichondirikk; veettilekkulla vazhi safe aakilla.', 'Keep talking; ghar ka rasta safe nahi hoga.']),
    ('sexual_harassment', ['Send nude photos even though you said no.', 'तुमने मना किया फिर भी नग्न तस्वीरें भेजो।', 'Tumne mana kiya phir bhi nude photos bhejo.', 'വേണ്ടെന്ന് പറഞ്ഞാലും നഗ്ന ചിത്രങ്ങൾ അയയ്ക്കൂ.', 'Vendennu paranjalum nude photos ayakku.', 'Tumne mana kiya but send nude photos.']),
    ('sexual_harassment', ['I will keep sending sexual messages after you asked me to stop.', 'तुम्हारे मना करने के बाद भी मैं अश्लील संदेश भेजूँगा।', 'Mana karne ke baad bhi ashleel messages bhejunga.', 'നിർത്താൻ പറഞ്ഞാലും ഞാൻ അശ്ലീല സന്ദേശങ്ങൾ അയയ്ക്കും.', 'Nirthan paranjalum njan ashleela messages ayakkum.', 'Stop bola phir bhi sexual messages bhejunga.']),
    ('caste_religious_slur', ['People of your caste are dirty and should be excluded.', 'तुम्हारी जाति के लोग गंदे हैं और उन्हें बाहर करना चाहिए।', 'Tumhari jaati ke log gande hain aur unhe bahar karo.', 'നിന്റെ ജാതിയിലുള്ളവർ വൃത്തികെട്ടവരാണ്; അവരെ ഒഴിവാക്കണം.', 'Ninte jaathiyilullavar vrithikettavaranu; avare ozhivakkanam.', 'Your jaati people are dirty; exclude them.']),
    ('caste_religious_slur', ['Your religion makes you inferior; you do not belong here.', 'तुम्हारा धर्म तुम्हें नीचा बनाता है; तुम्हारी यहाँ जगह नहीं।', 'Tumhara dharm tumhe neecha banata hai; yahan jagah nahi.', 'നിന്റെ മതം നിന്നെ താഴ്ന്നവനാക്കുന്നു; ഇവിടെ നിനക്ക് ഇടമില്ല.', 'Ninte matham ninne thaazhnnavanakkunnu; ivide idamilla.', 'Tumhara religion makes you inferior.']),
    ('doxxing', ['I will publish your private address without permission.', 'मैं तुम्हारा निजी पता बिना अनुमति प्रकाशित करूँगा।', 'Main tumhara private address bina permission publish karunga.', 'അനുമതിയില്ലാതെ നിന്റെ സ്വകാര്യ വിലാസം പരസ്യമാക്കും.', 'Anumathiyillathe ninte private address publish cheyyum.', 'Your private address bina permission publish karunga.']),
    ('coercive_control', ['Share your password and stop meeting your friends or I will punish you.', 'पासवर्ड दो और दोस्तों से मिलना बंद करो वरना मैं सज़ा दूँगा।', 'Password do aur doston se milna band karo warna saza dunga.', 'പാസ്‌വേഡ് തരൂ; സുഹൃത്തുക്കളെ കാണുന്നത് നിർത്തൂ, അല്ലെങ്കിൽ ശിക്ഷിക്കും.', 'Password tharu; friendsine kaanunnathu nirthu allenkil shikshikkum.', 'Password do and stop seeing friends warna punishment.']),
    ('none', ['I feel scared and cannot sleep after what happened.', 'जो हुआ उसके बाद मुझे डर लगता है और नींद नहीं आती।', 'Jo hua uske baad darr lagta hai aur neend nahi aati.', 'സംഭവിച്ചതിന് ശേഷം എനിക്ക് പേടിയാണ്; ഉറങ്ങാൻ കഴിയുന്നില്ല.', 'Sambhavichathinu shesham enikku pedi aanu; urangaan pattunnilla.', 'I feel scared aur neend nahi aati.']),
    ('none', ['Nice one, genius! We both laughed at our silly game.', 'वाह, जीनियस! हम दोनों अपने मज़ेदार खेल पर हँसे।', 'Wah genius! Hum dono apne funny game par hanse.', 'കൊള്ളാം, ജീനിയസ്! ഞങ്ങൾ രണ്ടുപേരും കളിയിൽ ചിരിച്ചു.', 'Kollam genius! Njangal randuperum gameil chirichu.', 'Nice one genius! Hum dono laughed at our game.']),
    ('none', ['Please do not share my address; I want privacy.', 'कृपया मेरा पता साझा मत करो; मुझे निजता चाहिए।', 'Please mera address share mat karo; privacy chahiye.', 'എന്റെ വിലാസം പങ്കിടരുത്; എനിക്ക് സ്വകാര്യത വേണം.', 'Ente address share cheyyaruthu; privacy venam.', 'Please mera address mat share karo.']),
    ('threat+doxxing', ['I will hurt you and publish your private address.', 'मैं तुम्हें चोट पहुँचाऊँगा और तुम्हारा निजी पता प्रकाशित करूँगा।', 'Main tumhe chot pahunchaunga aur private address publish karunga.', 'നിന്നെ ഉപദ്രവിക്കും; നിന്റെ സ്വകാര്യ വിലാസം പരസ്യമാക്കും.', 'Ninne upadravikkum; ninte private address publish cheyyum.', 'I will hurt you aur private address publish karunga.']),
]


def dump(path, value):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def jsonl(path, rows):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(''.join(json.dumps(r, ensure_ascii=False) + '\n' for r in rows), encoding='utf-8')


def load(path):
    return [json.loads(line) for line in Path(path).read_text(encoding='utf-8').splitlines() if line]


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def generate_text(root):
    rng = random.Random(SEED)
    groups = [(s, l) for s in range(len(SCENARIOS)) for l in range(len(LANGUAGES))]
    for attempt in range(10000):
        rng.shuffle(groups)
        split_groups = {'train': groups[:58], 'val': groups[58:71], 'test': groups[71:]}
        if all({LANGUAGES[l] for _, l in items} == set(LANGUAGES) and
               {label for s, _ in items for label in SCENARIOS[s][0].split('+')} >= set(LABELS)
               for items in split_groups.values()):
            break
    else:
        raise ValueError('Could not construct balanced group split')
    rows = []
    for split, items in split_groups.items():
        for index, (s, l) in enumerate(sorted(items)):
            label, sentences = SCENARIOS[s]
            base = sentences[l]
            variants = [base, base + '!', base + ' ...', base + ' 😟', base.replace(' ', '  '), base + '\n', base.replace('you', 'u').replace('tum', 'tm').replace('ninte', 'nint') + '?']
            if split == 'train' and index < 14:
                variants.append(base + ' !!')
            if split != 'train' and index == len(items) - 1:
                variants = variants[:6]
            for v, text in enumerate(variants):
                rows.append({'id': f't{s:02d}-{l}-{v}', 'group_id': f'g{s:02d}-{l}',
                             'parallel_family': s, 'text': text, 'language': LANGUAGES[l],
                             'labels': [] if label == 'none' else label.split('+'),
                             'none': label == 'none', 'split': split, 'synthetic': True,
                             'challenge': 'victim_distress' if s == 10 else 'friendly_joking' if s == 11 else 'veiled_threat' if s == 3 else 'misspelling' if v == 6 else 'standard',
                             'sender': f'user_{s % 5:02d}', 'timestamp': f'2025-01-{1+s:02d}T10:{l*5+v:02d}:00Z'})
    jsonl(root / 'text.jsonl', rows)
    return rows


def generate_extraction(root, rows):
    threads = []
    for i in range(100):
        row = rows[(i * 13) % len(rows)]
        platform = ['whatsapp', 'instagram'][i % 2]
        threads.append({'id': f'case-{i:03d}', 'split': row['split'], 'language': row['language'],
                        'case_id': f'case-{i//4:03d}', 'source_message_ids': [row['id']],
                        'text': row['text'], 'sender': row['sender'], 'timestamp': row['timestamp'],
                        'platform': platform, 'fields': {'date': row['timestamp'][:10],
                        'platform': platform, 'sender': row['sender'],
                        'threat_type': row['labels'], 'quote': row['text']}, 'synthetic': True})
    jsonl(root / 'extraction.jsonl', threads)


def font_paths():
    candidates = [Path('C:/Windows/Fonts/Nirmala.ttc'), Path('C:/Windows/Fonts/segoeui.ttf'),
                  Path('/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf')]
    return [p for p in candidates if p.exists()]


def generate_screenshots(root, rows):
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
    from .render import font_runs, shaped_text
    fonts = font_paths()
    if not fonts:
        raise RuntimeError('Supply fonts supporting Latin, Devanagari and Malayalam')
    indic_font = fonts[0]
    rng = random.Random(SEED)
    items = []
    selected = []
    for split, count in [('train', 84), ('val', 24), ('test', 60)]:
        subset = [r for r in rows if r['split'] == split]
        selected.extend(subset[j * len(subset) // count] for j in range(count))
    rng.shuffle(selected)
    for i, row in enumerate(selected):
        width = [720, 900, 1080][i % 3]
        height = int(width * 1.5)
        dark = bool(i % 2)
        fg, bg = ('#eeeeee', '#101820') if dark else ('#101010', '#faf7f0')
        font_path = fonts[i % len(fonts)] if row['language'] in ['en', 'hinglish', 'manglish'] else indic_font
        font = ImageFont.truetype(str(font_path), size=width // 26)
        image = Image.new('RGB', (width, height), bg)
        draw = ImageDraw.Draw(image)
        platform = ['whatsapp', 'instagram'][(i // 2) % 2]
        draw.rectangle((0, 0, width, 90), fill='#075e54' if platform == 'whatsapp' else '#402060')
        shaped_text(image, (25, 25), platform + ' | ' + row['sender'], font_path, width // 26, 'white')
        draw.rounded_rectangle((20, 130, width - 20, height - 160), radius=25, fill='#254035' if dark else '#e0efd8')
        lines = textwrap.wrap(row['text'], width=24 if row['language'] in ['hi', 'ml', 'mixed'] else 34)
        y = 160
        for line in lines:
            shaped_text(image, (40, y), line, font_path, width // 26, fg)
            y += int(width / 12)
        stamp = row['timestamp'][11:16]
        shaped_text(image, (40, height - 220), row['sender'] + ' ' + stamp, font_path, width // 26, fg)
        angle = [0, -1, 1][i % 3]
        blur = [0, .35, .7][(i // 3) % 3]
        image = image.rotate(angle, fillcolor=bg).filter(ImageFilter.GaussianBlur(blur))
        path = root / 'screenshots' / f'screen-{i:03d}.jpg'
        path.parent.mkdir(parents=True, exist_ok=True)
        quality = [50, 75, 95][i % 3]
        image.save(path, quality=quality)
        ocr_text = platform + ' | ' + row['sender'] + '\n' + '\n'.join(lines) + '\n' + row['sender'] + ' ' + stamp
        used_fonts = sorted({p for p, _ in font_runs(font_path, ocr_text)})
        items.append({'id': path.stem, 'path': path.as_posix(), 'sha256': digest(path),
                      'message_id': row['id'], 'split': row['split'], 'language': row['language'],
                      'text': row['text'].strip(), 'ocr_text': ocr_text,
                      'fonts_used': used_fonts, 'font_hashes': {p: digest(p) for p in used_fonts},
                      'render_backend': 'HarfBuzz/FreeType with glyph-checked monochrome font fallback',
                      'sender': row['sender'], 'timestamp': stamp, 'platform': platform, 'dark': dark,
                      'font': str(font_path), 'font_sha256': digest(font_path), 'width': width, 'height': height,
                      'jpeg_quality': quality, 'blur': blur, 'rotation': angle, 'synthetic': True,
                      'render_review': 'pending_native_speaker_and_visual_review'})
    jsonl(root / 'screenshots.jsonl', items)
    return items


def validate(root):
    rows = load(root / 'text.jsonl')
    assert len(rows) >= 400
    assert len({r['id'] for r in rows}) == len(rows)
    assert {r['language'] for r in rows} == set(LANGUAGES)
    groups = {}
    for row in rows:
        groups.setdefault(row['group_id'], set()).add(row['split'])
        assert set(row['labels']) <= set(LABELS)
        assert row['none'] == (not row['labels'])
    assert all(len(splits) == 1 for splits in groups.values())
    for split in ['train', 'val', 'test']:
        assert {r['language'] for r in rows if r['split'] == split} == set(LANGUAGES)
    threads = load(root / 'extraction.jsonl')
    assert len(threads) == 100
    assert all(t['fields']['quote'] in t['text'] for t in threads)
    screenshots = load(root / 'screenshots.jsonl')
    assert len(screenshots) >= 150
    assert {s['split'] for s in screenshots} == {'train', 'val', 'test'}
    assert {s['language'] for s in screenshots if s['split'] == 'test'} == set(LANGUAGES)
    assert all(digest(s['path']) == s['sha256'] for s in screenshots)
    from .render import missing_glyphs
    assert all(not missing_glyphs(s.get('fonts_used', [s['font']]), s['ocr_text']) for s in screenshots)
    return {'text': len(rows), 'screenshots': len(screenshots), 'extraction': len(threads),
            'splits': {s: sum(r['split'] == s for r in rows) for s in ['train', 'val', 'test']},
            'split_note': '600 messages: 420/90/90 (70/15/15), all variants grouped. Parallel translations span splits: residual semantic-template leakage, synthetic sanity checks only.',
            'labels_review': 'pending_native_speaker_review', 'audio': 'run explicit public-audio preparation'}


def build(root=Path('data')):
    rows = generate_text(root)
    generate_extraction(root, rows)
    generate_screenshots(root, rows)
    result = validate(root)
    dump(root / 'manifest.json', {'seed': SEED, 'source': 'synthetic authored fixtures; no victim data',
                                 'validation': result, 'files': {p.name: digest(p) for p in sorted(root.glob('*.jsonl'))}})
    print(json.dumps(result, ensure_ascii=False, indent=2))
