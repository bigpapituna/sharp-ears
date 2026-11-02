from analyze import *
import collections, json
levels = collections.OrderedDict()
for l in LESSONS: levels.setdefault(l["level"], []).append(l)
perf = []; allrows = {}
for lv, ls in levels.items():
    for l in ls:
        seen, out = analyze_lesson(l["id"])
        allrows[l["id"]] = seen
        for k, v in seen.items():
            for sev, msg_ in v["issues"]:
                if "right notes flagged WRONG" in msg_: perf.append((l["id"], k, v["kind"], msg_, (v["info"].get("ended") or "")[-70:]))
print("== levels")
for lv, ls in levels.items():
    steps = sum(len(l["steps"]) for l in ls)
    scripted = sum(1 for l in ls for k, v in allrows[l["id"]].items() if v["scripted"])
    passed = sum(1 for l in ls for k, v in allrows[l["id"]].items() if v["scripted"] and "passed=true" in (v["info"].get("ended") or ""))
    print(lv, len(ls), steps, scripted, passed)
print("== perform wrong")
for p in perf: print(p)
json.dump({k: {str(i): dict(kind=v["kind"], scripted=v["scripted"], issues=v["issues"], ended=v["info"].get("ended")) for i, v in s.items()} for k, s in allrows.items()}, open(os.path.join(OUT, "rows.json"), "w", encoding="utf8"))
