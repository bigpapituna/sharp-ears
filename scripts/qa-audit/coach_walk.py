import sys, os, time
from lib import *
import walk, plant
CW = os.path.join(OUT, "coach"); os.makedirs(CW, exist_ok=True)
walk.WALK = CW
VOICES = ["male_us", "male_irish", "female_us", "female_au"]
SESS = [("sit", 0), ("together", 0), ("first-melody", 0), ("first-melody", 4), ("rep-eine", 0)]
prog = open(os.path.join(OUT, "coach-progress.txt"), "a")
for v in VOICES:
    plant.plant_coach("progress", v, "w")
    for lid, i0 in SESS:
        # tag the log by voice
        s = walk.Session(lid, i0)
        r = s.run()
        src = os.path.join(CW, f"{lid}-{i0}.log"); dst = os.path.join(CW, f"{v}-{lid}-{i0}.log")
        if os.path.exists(dst): os.remove(dst)
        os.rename(src, dst)
        prog.write(f"{v} {lid} {i0}: {r}\n"); prog.flush()
plant.clear_coach("progress")
prog.write("ALL DONE\n"); prog.flush()
