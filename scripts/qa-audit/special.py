import sys
from lib import *
import walk
def run(lid, i0, script, wait):
    t0=time.time(); start("--es","lesson",lid,"--ei","step",str(i0),"--es","midi",script)
    time.sleep(wait)
    lines=log(); open(os.path.join(walk.WALK,f"{lid}-{i0}.log"),"w",encoding="utf8").write("\n".join(lines))
    return lines
def show(lines):
    for x in lines:
        if any(k in x for k in ("lesson ","ended","pedal","midi:","input","report","Report","passed","dynamics","Dynamics","skip")) and "keyRect" not in x:
            print(x[6:][:230])
if __name__=="__main__":
    which=sys.argv[1]
    if which=="pedal":
        for lid,i0,chords in (("pedal",2,[(0,[60,64,67]),(4000,[60,64,69]),(8000,[60,65,69]),(12000,[59,62,67])]),("t2-pedal-progression",3,[(0,[60,64,67]),(4000,[59,62,67]),(8000,[60,64,69]),(12000,[60,65,69])])):
            P=8000; items=[]
            for s,ms in chords:
                for m in ms: items.append((P+s+20,m,80))
            sc=midi_str(items)
            ped=",".join(f"{P+s+100}:p1,{P+s+3700}:p0" for s,_ in chords)
            lines=run(lid,i0,sc+","+ped,45); print("==",lid); show(lines)
    if which=="dyn":
        for lid,i0 in (("dynamics",2),("t2-shaping",3)):
            s=lesson(lid)['steps'][i0]
            gs=[(n[1],n[0]) for n in s['notes']]
            items=[]
            for k,(t,m) in enumerate(gs): items.append((2000+t+220,m,40 if k<5 else 100))
            lines=run(lid,i0,midi_str(items),40); print("==",lid); show(lines)
