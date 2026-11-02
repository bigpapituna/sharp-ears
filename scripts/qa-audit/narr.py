import os, json, subprocess, collections
from lib import *
base=r"C:\dev\piano-wt\qa-audit\app\src\afterdark\assets\coach"
voices=["male_us","male_irish","female_us","female_au"]
cat=[l.split("\t",1) for l in open(os.path.join(OUT,"catalog.tsv"),encoding="utf8").read().split("\n")]
import hashlib
def key(t): return hashlib.sha1(" ".join(t.split()).encode()).hexdigest()[:16]
lessonlines={}  # line -> (lesson, step)
for l in LESSONS:
    for i,s in enumerate(l['steps']):
        for t in s['lines']: lessonlines.setdefault(t,(l['id'],i))
    lessonlines.setdefault(l['next'],(l['id'],'next'))
res={}
for v in voices:
    files=set(os.listdir(os.path.join(base,v,"course")))
    sizes={f:os.path.getsize(os.path.join(base,v,"course",f)) for f in files}
    miss={'None':[], 'w':[], 'm':[]}
    for who in miss:
        for t,(lid,si) in lessonlines.items():
            k=key(t)
            cands=([f"{k}_{who}.ogg"] if who!='None' else [])+[f"{k}.ogg"]
            if not any(c in files for c in cands): miss[who].append((lid,si,t))
    allmiss={'None':0,'w':0,'m':0}
    for who in allmiss:
        for k,t in cat:
            cands=([f"{k}_{who}.ogg"] if who!='None' else [])+[f"{k}.ogg"]
            if not any(c in files for c in cands): allmiss[who]+=1
    bad=[f for f,sz in sizes.items() if f.endswith('.ogg') and sz<2000]
    bad_hdr=[]
    for f in files:
        if not f.endswith('.ogg'): continue
        with open(os.path.join(base,v,"course",f),'rb') as fh:
            if fh.read(4)!=b'OggS': bad_hdr.append(f)
    res[v]=dict(files=len(files),lesson_lines=len(lessonlines),missing_lesson={k:len(x) for k,x in miss.items()},missing_all=allmiss,tiny=bad,badhdr=bad_hdr,detail=miss)
for v in voices:
    r=res[v]; print(v,r['files'],r['lesson_lines'],r['missing_lesson'],r['missing_all'],'tiny',len(r['tiny']),'badhdr',len(r['badhdr']))
json.dump(res,open(os.path.join(OUT,"narr.json"),"w"),indent=1)
for v in voices:
    for who in ('None','w','m'):
        for x in res[v]['detail'][who][:6]: print(v,who,x)
