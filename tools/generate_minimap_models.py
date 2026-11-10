"""Build compressed surface meshes from the two PGM repositories; not a runtime dependency."""
import collections
from concurrent.futures import ThreadPoolExecutor
import gzip
import io
import json
import math
from pathlib import Path
import re
import struct
import sys
import time
import urllib.request
from urllib.parse import quote, urlsplit, urlunsplit
import zlib
import xml.etree.ElementTree as ET

import nbtlib
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/otl/maps"
CACHE = Path.home() / "AppData/Local/Temp/opencode/minimap-terrain"
REPORT = ROOT / "build/minimap-generation.json"
SOURCES = (("CommunityMaps", "master"), ("PublicMaps", "main"))
AIR = {"air", "cave_air", "void_air", "structure_void", "light", "barrier"}
DYES = dict(zip(("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"),
                (0xDCDCD2,0xDE7B2A,0xAF4AAA,0x5BA3C7,0xE3C13A,0x7FAE34,0xDA8FA0,0x4C4F50,0x94938B,0x2F8591,0x7E4AAD,0x424EA5,0x755137,0x587030,0xA23930,0x28282B)))
COLORS = (("amethyst",0x9B7DC4),("cherry_leaves",0xCD99B3),("leaves",0x537B39),("moss",0x5C773F),("grass",0x6F8B46),("fern",0x4F7B3E),("vine",0x4D7030),
          ("water",0x4177A8),("lava",0xE7711B),("snow",0xE1E8E8),("ice",0x93BCD1),("deepslate",0x48494D),("blackstone",0x36323B),("andesite",0x848682),("diorite",0xB7B5AC),
          ("granite",0x916D5C),("stone_brick",0x777A76),("cobblestone",0x6F736C),("stone",0x878881),("tuff",0x6F7367),("gravel",0x827A73),("dirt",0x7D5C3E),("podzol",0x705635),
          ("sandstone",0xCABB8B),("sand",0xD6CA9B),("brick",0x995947),("terracotta",0xA06850),("spruce",0x715431),("dark_oak",0x4C3924),("birch_log",0xC4C0A9),("birch",0xC7B67D),
          ("oak_log",0x715B39),("oak_wood",0x715B39),("oak",0xA38552),("jungle",0x9F744E),("wood",0x97794B),("planks",0xA38552),("iron",0xADADA2),("gold",0xDEB840),
          ("glass",0xADCECF),("lantern",0xDDA946),("torch",0xDCAB40),("chain",0x56595B),("bedrock",0x484848),("obsidian",0x2F263A),("quartz",0xDAD3C2),("bone",0xD3CDAE),("copper",0xB06F4D))
# Legacy 1.8 block IDs; metadata below restores common wood and wool variants.
LEGACY = {1:"stone",2:"grass_block",3:"dirt",4:"cobblestone",5:"oak_planks",6:"oak_sapling",7:"bedrock",8:"water",9:"water",10:"lava",11:"lava",12:"sand",13:"gravel",14:"gold_ore",15:"iron_ore",16:"coal_ore",17:"oak_log",18:"oak_leaves",20:"glass",24:"sandstone",31:"grass",35:"white_wool",41:"gold_block",42:"iron_block",43:"stone_slab",44:"stone_slab",45:"bricks",47:"bookshelf",48:"mossy_cobblestone",49:"obsidian",50:"torch",53:"oak_stairs",54:"chest",60:"farmland",67:"cobblestone_stairs",78:"snow",79:"ice",80:"snow_block",85:"oak_fence",87:"netherrack",88:"soul_sand",89:"glowstone",95:"white_stained_glass",96:"oak_trapdoor",98:"stone_bricks",101:"iron_bars",102:"glass_pane",107:"oak_fence_gate",108:"brick_stairs",109:"stone_brick_stairs",112:"nether_bricks",121:"end_stone",126:"oak_slab",128:"sandstone_stairs",133:"emerald_block",134:"spruce_stairs",135:"birch_stairs",136:"jungle_stairs",139:"cobblestone_wall",155:"quartz_block",156:"quartz_stairs",159:"white_terracotta",160:"white_stained_glass_pane",161:"acacia_leaves",162:"acacia_log",163:"acacia_stairs",164:"dark_oak_stairs",168:"prismarine",169:"sea_lantern",170:"hay_block",171:"white_carpet",172:"terracotta",173:"coal_block",174:"packed_ice",179:"red_sandstone",180:"red_sandstone_stairs",181:"red_sandstone_slab",182:"red_sandstone_slab",183:"spruce_fence_gate",188:"spruce_fence",189:"birch_fence",190:"jungle_fence",191:"dark_oak_fence",192:"acacia_fence"}


def color(name):
    for dye,rgb in DYES.items():
        if name.startswith(dye+"_") and any(s in name for s in ("wool","concrete","terracotta","glass","carpet")):
            return rgb
    return next((rgb for token,rgb in COLORS if token in name),0x918673)


def request(url):
    parts=urlsplit(url)
    url=urlunsplit((parts.scheme,parts.netloc,quote(parts.path,safe="/%"),parts.query,parts.fragment))
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={"User-Agent":"OTL-minimap-converter"}),timeout=60) as response:
                return response.read()
        except Exception:
            if attempt==2:
                raise
            time.sleep(attempt+1)


def cached(repo,branch,path):
    dest=CACHE/repo/branch/path
    if not dest.is_file():
        dest.parent.mkdir(parents=True,exist_ok=True)
        data=request(f"https://raw.githubusercontent.com/OvercastCommunity/{repo}/{branch}/{path}")
        part=dest.with_name(dest.name+".part")
        part.write_bytes(data)
        part.replace(dest)
    return dest


def unpack_states(palette,data):
    if data is None:
        return np.zeros(4096,dtype=np.uint16)
    bits=max(4,(len(palette)-1).bit_length())
    longs=np.asarray(data,dtype=np.int64).view(np.uint64)
    indices=np.arange(4096,dtype=np.uint64)
    per=64//bits
    if len(longs)==math.ceil(4096/per):
        values=longs[indices//per]>>((indices%per)*bits)
    else:
        positions=indices*bits
        word,shift=positions//64,positions%64
        values=longs[word]>>shift
        cross=shift+bits>64
        values[cross]|=longs[word[cross]+1]<<(64-shift[cross])
    values&=(1<<bits)-1
    if int(values.max())>=len(palette):
        raise ValueError("Invalid palette index")
    return values.astype(np.uint16)


def read_world(paths):
    sections=[]
    names=["air"]
    ids={"air":0}
    def material(name):
        name=name.removeprefix("minecraft:")
        if name in AIR:
            return 0
        if name not in ids:
            ids[name]=len(names);names.append(name)
        return ids[name]
    for path in paths:
        raw=path.read_bytes()
        for i in range(1024):
            start=int.from_bytes(raw[i*4:i*4+3],"big")*4096
            if not start:
                continue
            size=int.from_bytes(raw[start:start+4],"big")
            payload=raw[start+5:start+4+size]
            compression=raw[start+4]
            if compression==1:
                payload=gzip.decompress(payload)
            elif compression==2:
                payload=zlib.decompress(payload)
            elif compression!=3:
                raise ValueError(f"Unsupported compression {compression}")
            root=nbtlib.File.parse(io.BytesIO(payload))
            chunk=root.get("Level",root)
            for section in chunk.get("sections",chunk.get("Sections",[])):
                states=section.get("block_states",section)
                palette=states.get("palette",states.get("Palette"))
                if palette is not None:
                    mapping=np.array([material(str(e["Name"])) for e in palette],dtype=np.uint16)
                    if not mapping.any():
                        continue
                    blocks=mapping[unpack_states(palette,states.get("data",states.get("BlockStates")))]
                elif "Blocks" in section:
                    block_ids=np.asarray(section["Blocks"],dtype=np.int8).view(np.uint8).astype(np.uint16)
                    metadata=np.asarray(section.get("Data",np.zeros(2048,dtype=np.int8)),dtype=np.int8).view(np.uint8)
                    metadata=np.stack((metadata&15,metadata>>4),axis=1).reshape(-1)
                    if "Add" in section:
                        add=np.asarray(section["Add"],dtype=np.int8).view(np.uint8)
                        block_ids|=np.stack((add&15,add>>4),axis=1).reshape(-1).astype(np.uint16)<<8
                    packed=block_ids*16+metadata
                    blocks=np.zeros(4096,dtype=np.uint16)
                    for code in np.unique(packed):
                        block_id,data=divmod(int(code),16)
                        name=LEGACY.get(block_id,"air" if block_id==0 else f"legacy_{block_id}")
                        if block_id in (35,95,159,160,171):
                            suffix={35:"wool",95:"stained_glass",159:"terracotta",160:"stained_glass_pane",171:"carpet"}[block_id]
                            name=list(DYES)[data]+"_"+suffix
                        elif block_id in (5,17,18,126):
                            wood=("oak","spruce","birch","jungle","acacia","dark_oak")[min(data&7,5) if block_id in (5,126) else data&3]
                            name=wood+{5:"_planks",17:"_log",18:"_leaves",126:"_slab"}[block_id]
                        blocks[packed==code]=material(name)
                else:
                    continue
                if blocks.any():
                    sections.append((int(chunk["xPos"])*16,int(section["Y"])*16,int(chunk["zPos"])*16,blocks.reshape(16,16,16).transpose(2,0,1)))
    if not sections:
        raise ValueError("No supported terrain sections found")
    low=np.array([min(s[d] for s in sections) for d in range(3)])
    high=np.array([max(s[d]+16 for s in sections) for d in range(3)])
    if np.prod(high-low)>80_000_000:
        return None,names,low,sections
    grid=np.zeros(tuple(high-low),dtype=np.uint16)
    for x,y,z,blocks in sections:
        a,b,c=np.array([x,y,z])-low
        grid[a:a+16,b:b+16,c:c+16]=blocks
    occupied=[np.flatnonzero(np.any(grid,axis=tuple(a for a in range(3) if a!=d))) for d in range(3)]
    crop_low=np.array([axis[0] for axis in occupied]);crop_high=np.array([axis[-1]+1 for axis in occupied])
    grid=grid[tuple(slice(int(a),int(b)) for a,b in zip(crop_low,crop_high))]
    return grid,names,low+crop_low,None


def mesh(grid,names,padded=None):
    output=bytearray()
    if padded is None:
        padded=np.pad(grid,1)
    count=0
    for d in range(3):
        u,v=(d+1)%3,(d+2)%3
        for sign in (-1,1):
            for k in range(grid.shape[d]):
                section=np.take(grid,k,axis=d)
                neighbor_slice=[slice(1,-1)]*3;neighbor_slice[d]=k+1+sign
                mask=np.where((section!=0)&(padded[tuple(neighbor_slice)]==0),section,0)
                if [a for a in range(3) if a!=d]!=[u,v]:
                    mask=mask.T.copy()
                for a,b in np.argwhere(mask):
                    value=int(mask[a,b])
                    if not value:
                        continue
                    width=1
                    while b+width<mask.shape[1] and mask[a,b+width]==value:
                        width+=1
                    height=1
                    while a+height<mask.shape[0] and np.all(mask[a+height,b:b+width]==value):
                        height+=1
                    mask[a:a+height,b:b+width]=0
                    point=[0,0,0];point[d]=k+(sign>0);point[u]=int(a);point[v]=int(b)
                    output.extend(struct.pack(">HHHHHBBI",*point,int(height),int(width),d,int(sign>0),color(names[value])))
                    count+=1
    return output,count


def sparse_mesh(sections,names,origin):
    """Section-sized halo meshes avoid allocating empty space between distant islands."""
    lookup={tuple(s[:3]):s[3] for s in sections}
    record=np.dtype([("point",">u2",3),("size",">u2",2),("axis","u1"),("positive","u1"),("color",">u4")])
    output=bytearray();count=0
    for x,y,z,blocks in sections:
        halo=np.pad(blocks,1)
        for axis in range(3):
            for sign in (-1,1):
                adjacent=[x,y,z];adjacent[axis]+=sign*16
                neighbor=lookup.get(tuple(adjacent))
                if neighbor is not None:
                    edge=[slice(1,-1)]*3;edge[axis]=0 if sign<0 else -1
                    halo[tuple(edge)]=np.take(neighbor,15 if sign<0 else 0,axis=axis)
        data,quads=mesh(blocks,names,halo)
        offset=np.array([x,y,z])-origin
        if np.any(offset<0) or np.any(offset+16>65535):
            raise ValueError("Map exceeds compact mesh coordinate range")
        records=np.frombuffer(data,dtype=record).copy()
        records["point"]+=offset.astype(np.uint16)
        output.extend(records.tobytes());count+=quads
    return output,count


def self_test():
    grid=np.ones((2,1,1),dtype=np.uint16)
    data,count=mesh(grid,["air","stone"])
    assert count==6 and len(data)==96
    blocks=np.ones((16,16,16),dtype=np.uint16)
    sparse,count=sparse_mesh([(0,0,0,blocks),(16,0,0,blocks)],["air","stone"],np.array([0,0,0]))
    assert count==10 and len(sparse)==160  # The two touching section faces must be culled.
    for bits in (4,5,6,8,9):
        palette=list(range(1<<bits));values=np.arange(4096,dtype=np.uint64)%len(palette)
        per=64//bits;longs=np.zeros(math.ceil(4096/per),dtype=np.uint64)
        for i,value in enumerate(values):
            longs[i//per]|=value<<np.uint64((i%per)*bits)
        assert np.array_equal(unpack_states(palette,longs.view(np.int64)),values)
        dense=[0]*math.ceil(4096*bits/64)
        for i,value in enumerate(values):
            word,shift=divmod(i*bits,64);number=int(value)
            dense[word]|=(number<<shift)&((1<<64)-1)
            if shift+bits>64:
                dense[word+1]|=number>>(64-shift)
        assert np.array_equal(unpack_states(palette,np.array(dense,dtype=np.uint64).view(np.int64)),values)
    print("Converter self-check PASS",flush=True)


def validate_models():
    entries=json.loads((ASSETS/"minimap-index.json").read_text(encoding="utf-8"))
    root=ASSETS.resolve()
    referenced=set()
    for entry in entries:
        path=(ASSETS/entry["asset"]).resolve()
        if not path.is_relative_to(root) or not path.is_file() or path.name!="minimap.mesh.gz":
            raise ValueError(f"Invalid/missing indexed model: {entry}")
        referenced.add(path)
    files=list(ASSETS.rglob("minimap.mesh.gz"))
    record=np.dtype([("point",">u2",3),("size",">u2",2),("axis","u1"),("positive","u1"),("color",">u4")])
    total=0
    for path in files:
        with gzip.open(path,"rb") as stream:
            data=stream.read(21+2000000*16+1)
        if len(data)<21:
            raise ValueError(f"Truncated model: {path}")
        magic,version,x,y,z,count=struct.unpack_from(">4sBiiiI",data)
        if magic!=b"OTLM" or version!=1 or not 1<=count<=2000000 or len(data)!=21+count*16 or max(abs(x),abs(y),abs(z))>30000000:
            raise ValueError(f"Invalid model header/length: {path}")
        quads=np.frombuffer(data,offset=21,dtype=record)
        if np.any(quads["size"]==0) or np.any(quads["axis"]>2) or np.any(quads["positive"]>1):
            raise ValueError(f"Invalid geometry: {path}")
        total+=path.stat().st_size
    if not files or not referenced:
        raise ValueError("No minimap models found")
    print(f"MINIMAP_VALIDATION PASS: {len(files)} valid meshes; {len(referenced)} indexed meshes; {len(entries)} name/path entries; {total:,} compressed bytes",flush=True)


def main():
    self_test()
    if "--self-test" in sys.argv:
        return
    if "--validate" in sys.argv:
        validate_models();return
    jobs=[];trees={};missing=[];metadata_failures=[]
    for repo,branch in SOURCES:
        tree=json.loads(request(f"https://api.github.com/repos/OvercastCommunity/{repo}/git/trees/{branch}?recursive=1"))
        if tree.get("truncated"):
            raise ValueError(f"Truncated repository tree: {repo}")
        trees[repo]=tree["sha"]
        regions=collections.defaultdict(list)
        for entry in tree["tree"]:
            if re.search(r"/region/[^/]+\.mca$",entry["path"]):
                regions[entry["path"].rsplit("/region/",1)[0]].append(entry["path"])
        for entry in tree["tree"]:
            if not entry["path"].endswith("/map.xml"):
                continue
            directory=entry["path"][:-8]
            try:
                metadata=ET.fromstring(cached(repo,branch,directory+"/map.xml").read_bytes())
            except Exception as error:
                metadata_failures.append({"path":f"{repo}/{branch}/{directory}","error":str(error)});continue
            name=metadata.findtext("name") or directory.rsplit("/",1)[-1]
            variants=collections.defaultdict(set)
            has_default_world=False
            for variant in metadata.iter("variant"):
                world=variant.get("world")
                if not world:
                    continue
                world_dir=directory+"/"+world
                # World references are repository-relative data, never executable paths.
                if world_dir not in regions or any(p in (".","..") for p in world.split("/")):
                    continue
                suffix=(variant.text or "").strip()
                label=suffix if suffix and variant.get("override")=="true" else name+(" "+suffix if suffix else "")
                variants[world_dir].add(label)
                if variant.get("id")=="default":
                    has_default_world=True
            for world_dir,labels in variants.items():
                asset=world_dir if repo=="CommunityMaps" else f"{repo}/{branch}/{world_dir}"
                jobs.append((repo,branch,world_dir,asset,regions[world_dir],sorted(labels)))
            if has_default_world:
                continue
            region_dir=directory
            if region_dir not in regions:
                alternatives=[d for d in (directory+"/DIM-1",directory+"/DIM1") if d in regions]
                if directory.endswith("/region") and directory[:-7] in regions:
                    alternatives.append(directory[:-7])
                if len(alternatives)==1:
                    region_dir=alternatives[0]
            if region_dir not in regions:
                if not variants:
                    missing.append(f"{repo}/{branch}/{directory}")
                continue
            asset=directory if repo=="CommunityMaps" else f"{repo}/{branch}/{directory}"
            jobs.append((repo,branch,directory,asset,regions[region_dir],[name]))
    # Some maps expose the same world through multiple XML entries; build each output once.
    unique={}
    for job in jobs:
        key=job[:3]
        if key in unique:
            unique[key][-1].extend(name for name in job[-1] if name not in unique[key][-1])
        else:
            unique[key]=job
    jobs=list(unique.values())
    jobs.sort(key=lambda j:(j[2]!="ctw/allure",j[0],j[2]))
    results=[];index=[]
    REPORT.parent.mkdir(parents=True,exist_ok=True)
    pool=ThreadPoolExecutor(max_workers=8)
    for number,(repo,branch,directory,asset,regions,labels) in enumerate(jobs,1):
        key=f"{repo}/{branch}/{directory}"
        try:
            dest=ASSETS/asset/"minimap.mesh.gz"
            if not dest.exists():
                paths=list(pool.map(lambda path:cached(repo,branch,path),regions))
                grid,names,origin,sections=read_world(paths)
                data,count=sparse_mesh(sections,names,origin) if sections is not None else mesh(grid,names)
                if count>2000000:
                    raise ValueError("Model exceeds client two-million-quad safety limit")
                payload=struct.pack(">4sBiiiI",b"OTLM",1,*origin.tolist(),count)+data
                assert len(payload)==21+16*count
                compressed=gzip.compress(payload,compresslevel=9,mtime=0)
                dest.parent.mkdir(parents=True,exist_ok=True)
                part=dest.with_name(dest.name+".part");part.write_bytes(compressed);part.replace(dest)
            for name in labels:
                index.append({"name":name,"path":key,"asset":asset+"/minimap.mesh.gz"})
            results.append({"path":key,"bytes":dest.stat().st_size,"status":"ok"})
            print(f"[{number}/{len(jobs)}] {key}: {dest.stat().st_size:,} bytes",flush=True)
        except Exception as error:
            results.append({"path":key,"status":"failed","error":str(error)})
            print(f"[{number}/{len(jobs)}] FAILED {key}: {error}",flush=True)
        # Atomic checkpoints permit inspection/resume without putting half a mesh in a release.
        index_file=ASSETS/"minimap-index.json"
        part=index_file.with_suffix(".json.part");part.write_text(json.dumps(index,ensure_ascii=False),encoding="utf-8");part.replace(index_file)
        REPORT.write_text(json.dumps({"repository_trees":trees,"total":len(jobs),"processed":len(results),"missing_direct_region":missing,"metadata_failures":metadata_failures,"models":results,"model_bytes":sum(r.get("bytes",0) for r in results)},indent=2),encoding="utf-8")
    pool.shutdown()
    print(f"MINIMAP_GENERATION COMPLETE: {sum(r['status']=='ok' for r in results)}/{len(jobs)} models; {sum(r.get('bytes',0) for r in results):,} bytes; {len(missing)} entries without direct region files; {len(metadata_failures)} metadata failures",flush=True)


if __name__=="__main__":
    main()
