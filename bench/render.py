from functools import lru_cache

from PIL import Image


@lru_cache(maxsize=8)
def font_blob(path):
    return open(path, 'rb').read()


def shaped_text(image, position, text, font_path, size, fill):
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
