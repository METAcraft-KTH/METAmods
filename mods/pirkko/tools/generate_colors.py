"""Generate the uploaded Pirkko model in custom and Minecraft dye colors."""
import json
from pathlib import Path
from PIL import Image
MODULE = Path(__file__).resolve().parents[1]
SOURCE = MODULE/'tools/source'
RES = MODULE/'src/main/resources'
ASSETS = RES/'assets/pirkko'

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2)+'\n')

palette = {'laserviolett':'#CC99FF', 'cerise':'#E83D84'}
palette.update(json.loads((SOURCE/'dye-colors.json').read_text()))
for name,color in palette.items():
    key='pirkko_'+name
    model=json.loads((SOURCE/'pirkko.json').read_text())
    model['textures']={'0':'pirkko:block/'+key,'particle':'pirkko:block/'+key}
    write(ASSETS/'models/block'/f'{key}.json',model)
    Image.new('RGB',(32,32),color).save(ASSETS/'textures/block'/f'{key}.png')
    write(ASSETS/'items'/f'{key}.json',{'model':{'type':'minecraft:model','model':'pirkko:block/'+key}})
    write(ASSETS/'blockstates'/f'{key}.json',{'variants':{'':{'model':'pirkko:block/'+key}}})
    write(RES/'data/pirkko/loot_table/blocks'/f'{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'pirkko:'+key}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for lang in ('en_us','sv_se'):
        path=ASSETS/'lang'/f'{lang}.json'; labels=json.loads(path.read_text())
        display_name = 'Laserviolet' if name == 'laserviolett' and lang != 'sv_se' else name.replace('_',' ').title()
        labels['block.pirkko.'+key]=display_name+' Pirkko'
        write(path,labels)
print(f'Generated {len(palette)} colored Pirkkos')
