"""Generate the Pirkko models, icons, loot tables, translations and invisible head skins."""
import colorsys
import json
from pathlib import Path
from PIL import Image
MODULE = Path(__file__).resolve().parents[1]
SOURCE = MODULE/'tools/source'
ASSETS = MODULE/'src/main/resources/assets/pirkko'
DATA = MODULE/'src/main/resources/data/pirkko'
# The animation of moredyes' RGB colour (colors.json): the same 24 keyframes, blended by the client at the same
# slow drift, so an RGB Pirkko and RGB wool change colour together.
RAINBOW = ['#FF3333', '#FF6633', '#FF9933', '#FFCC33', '#FFFF33', '#CCFF33', '#99FF33', '#66FF33',
           '#33FF33', '#33FF66', '#33FF99', '#33FFCC', '#33FFFF', '#33CCFF', '#3399FF', '#3366FF',
           '#3333FF', '#6633FF', '#9933FF', '#CC33FF', '#FF33FF', '#FF33CC', '#FF3399', '#FF3366']
RAINBOW_FRAMES = len(RAINBOW)
# How the figure shows in an item frame (the "fixed" display context): turned so its feet are on the surface the
# frame hangs on, whether that is a wall, a floor or a ceiling, with its arms across the frame. Rotation in degrees;
# the translation is out of the frame by half the model's 16 px, which puts its feet on the frame instead of in it.
FRAME_DISPLAY = {'rotation': [-90, 90, 0], 'translation': [0, 0, -8], 'scale': [1, 1, 1]}
RAINBOW_ANIMATION = {'animation': {'interpolate': True, 'frametime': 150}}
# The icon's wave only reads as a wave if it moves at a visible speed: one turn takes 24 x 4 ticks (4.8 s).
ICON_ANIMATION = {'animation': {'interpolate': True, 'frametime': 4}}
def rainbow_icon():
    """
    The red sprite with a full turn of hue spread from its top to its bottom, sliding down by one step per frame,
    so a wave of colour runs down the icon. One frame per step, stacked vertically.
    """
    base = Image.open(SOURCE/'sprites/red.png').convert('RGBA')
    strip = Image.new('RGBA',(16,16*RAINBOW_FRAMES))
    for i in range(RAINBOW_FRAMES):
        frame = base.copy()
        pixels = frame.load()
        for x in range(16):
            for y in range(16):
                r,g,b,a = pixels[x,y]
                h,s,v = colorsys.rgb_to_hsv(r/255,g/255,b/255)
                pixels[x,y] = (*(round(c*255) for c in colorsys.hsv_to_rgb((h+y/16-i/RAINBOW_FRAMES)%1,s,v)),a)
        strip.paste(frame,(0,16*i))
    return strip

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent="\t")+'\n')

palette = {'laserviolett':'#CC99FF', 'cerise':'#E83D84'}
palette.update(json.loads((SOURCE/'dye-colors.json').read_text()))
names = list(palette)+['rainbow']
labels = {lang: json.loads((ASSETS/'lang'/f'{lang}.json').read_text()) for lang in ('en_us','sv_se')}
for lang in labels:
    labels[lang] = {k: v for k, v in labels[lang].items() if not k.startswith(('block.pirkko.', 'item.pirkko.'))}

for name in names:
    key='pirkko_'+name
    model=json.loads((SOURCE/'pirkko.json').read_text())
    model['textures']={'0':'pirkko:block/'+key,'particle':'pirkko:block/'+key}
    write(ASSETS/'models/block'/f'{key}.json',model)
    write(ASSETS/'items'/f'{key}.json',{'model':{'type':'minecraft:model','model':'pirkko:block/'+key}})
    if name == 'rainbow':
        # One 32x32 frame per hue, stacked vertically and interpolated by the client.
        frames = [Image.new('RGB',(32,32),color) for color in RAINBOW]
        strip = Image.new('RGB',(32,32*RAINBOW_FRAMES))
        for i, frame in enumerate(frames): strip.paste(frame,(0,32*i))
        strip.save(ASSETS/'textures/block'/f'{key}.png')
        write(ASSETS/'textures/block'/f'{key}.png.mcmeta',RAINBOW_ANIMATION)
    else:
        Image.new('RGB',(32,32),palette[name]).save(ASSETS/'textures/block'/f'{key}.png')
    # Inventory icon, the head item's model; the 3D model above is what the placed head's display shows.
    sprite = Image.open(SOURCE/'sprites'/f'{name}.png') if name != 'rainbow' else rainbow_icon()
    (ASSETS/'textures/item').mkdir(parents=True, exist_ok=True)
    sprite.save(ASSETS/'textures/item'/f'head_{name}.png')
    if name == 'rainbow':
        write(ASSETS/'textures/item'/'head_rainbow.png.mcmeta',ICON_ANIMATION)
    write(ASSETS/'models/item'/f'head_{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'pirkko:item/head_{name}'}})
    write(ASSETS/'models/item'/f'frame_{name}.json',{'parent':f'pirkko:block/{key}','display':{'fixed':FRAME_DISPLAY}})
    # The flat icon everywhere but in item frames, which show the figure itself.
    write(ASSETS/'items'/f'head_{name}.json',{'model':{'type':'minecraft:select','property':'minecraft:display_context',
        'cases':[{'when':'fixed','model':{'type':'minecraft:model','model':f'pirkko:item/frame_{name}'}}],
        'fallback':{'type':'minecraft:model','model':f'pirkko:item/head_{name}'}}})
    write(DATA/'loot_table/blocks'/f'{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'pirkko:'+key}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    # Placed heads use this fully transparent skin, so only the display entity inside is visible.
    (ASSETS/'textures/entity').mkdir(parents=True, exist_ok=True)
    Image.new('RGBA',(64,64),(0,0,0,0)).save(ASSETS/'textures/entity'/f'{key}.png')
    for lang in labels:
        # The id stays rainbow, but players are told RGB, like moredyes' RGB colour.
        display_name = 'RGB' if name == 'rainbow' else 'Laserviolet' if name == 'laserviolett' and lang != 'sv_se' else name.replace('_',' ').title()
        labels[lang]['item.pirkko.'+key]=display_name+' Pirkko'
for lang, entries in labels.items():
    write(ASSETS/'lang'/f'{lang}.json',entries)
print(f'Generated {len(names)} Pirkko variants')
