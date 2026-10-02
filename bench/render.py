from functools import lru_cache
from pathlib import Path

from PIL import Image


@lru_cache(maxsize=8)
def font_blob(path):
    return Path(path).read_bytes()


@lru_cache(maxsize=8)
def character_map(font_path):
    from fontTools.ttLib import TTFont
    with TTFont(str(font_path), fontNumber=0) as font:
        return frozenset(font.getBestCmap())


def missing_glyphs(font_paths, text):
    paths = [font_paths] if isinstance(font_paths, (str, Path)) else font_paths
    coverage = set().union(*(character_map(str(path)) for path in paths))
    return sorted({ord(c) for c in text if not c.isspace() and ord(c) not in coverage})


def font_runs(font_path, text):
    candidates = [str(font_path)] + [str(p) for p in [Path('C:/Windows/Fonts/seguiemj.ttf'),
                  Path('C:/Windows/Fonts/Nirmala.ttc'),
                  Path('/usr/share/fonts/truetype/noto/NotoSansMalayalam-Regular.ttf')] if p.exists() and str(p) != str(font_path)]
    runs = []
    for character in text:
        chosen = next((p for p in candidates if character.isspace() or ord(character) in character_map(p)), None)
        if chosen is None:
            raise ValueError(f'No local font supports U+{ord(character):04X}; refusing incorrect screenshot ground truth')
        if runs and runs[-1][0] == chosen:
            runs[-1] = (chosen, runs[-1][1] + character)
        else:
            runs.append((chosen, character))
    return runs


def shaped_text(image, position, text, font_path, size, fill):
    x = position[0]
    for selected, run in font_runs(font_path, text):
        x = shape_run(image, (x, position[1]), run, selected, size, fill)


def shape_run(image, position, text, font_path, size, fill):
    import freetype
    import uharfbuzz as hb
    font = hb.Font(hb.Face(font_blob(str(font_path))))
    font.scale = (size * 64, size * 64)
    hb.ot_font_set_funcs(font)
    buffer = hb.Buffer()
    buffer.add_str(text)
    buffer.guess_segment_properties()
    hb.shape(font, buffer)
    face = freetype.Face(str(font_path))
    face.set_pixel_sizes(0, size)
    x, y = position[0], position[1] + size
    for glyph, offset in zip(buffer.glyph_infos, buffer.glyph_positions):
        face.load_glyph(glyph.codepoint, freetype.FT_LOAD_RENDER)
        bitmap = face.glyph.bitmap
        if bitmap.width and bitmap.rows:
            rows = []
            pixels = bytes(bitmap.buffer)
            for row in range(bitmap.rows):
                start = row * abs(bitmap.pitch)
                rows.append(pixels[start:start + bitmap.width])
            mask = Image.frombytes('L', (bitmap.width, bitmap.rows), b''.join(rows))
            origin = (round(x + offset.x_offset / 64 + face.glyph.bitmap_left), round(y - offset.y_offset / 64 - face.glyph.bitmap_top))
            image.paste(fill, origin, mask)
        x += offset.x_advance / 64
        y -= offset.y_advance / 64
    return x
