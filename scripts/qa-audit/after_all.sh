#!/bin/bash
# 2.19.0: the fix round's emulator runs, one after another on emulator-5562 (pacing, B-02 launches, the held reaction, B-01
# steps, the whole Road). No builds meanwhile: the Ramp scripts drift when the host is busy.
cd "$(dirname "$0")"
export QA_COURSE='C:\dev\piano-wt\qa-audit\app\build\qa\course.json' PYTHONIOENCODING=utf8 QA_SCR='C:\dev\piano-wt\qa-merge\app\build\screens'
B='C:\dev\piano-wt\qa-merge\app\build'
rm -rf ../../app/build/qa/pace-after ../../app/build/qa/walk
python plant.py micoff
QA_OUT="$B\qa" python -u pace.py after > ../../app/build/pace-after.out 2>&1
QA_OUT="$B\qa" python -u b02.py 80 > ../../app/build/b02.out 2>&1
python -u hold_check.py emulator-5562 after > ../../app/build/hold.out 2>&1
python plant.py micoff
QA_OUT="$B\qa" python -u b01.py > ../../app/build/b01-run.txt 2>&1
python plant.py micoff
QA_OUT="$B\qa-full" python -u run_all.py > ../../app/build/run_all.out 2>&1
echo ALL DONE >> ../../app/build/run_all.out
