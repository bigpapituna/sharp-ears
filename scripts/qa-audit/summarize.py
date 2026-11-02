from analyze import *
import glob
done=[l['id'] for l in LESSONS if glob.glob(WALK+'/%s-*.log'%l['id'])]
phone_sessions=[]
for lid in done:
    seen,out=analyze_lesson(lid)
    for k,v in sorted(seen.items()):
        iss=[i for i in v['issues'] if 'phone TTS' not in i[1] and 'not voiced as recorded' not in i[1]]
        ph=[i for i in v['issues'] if 'phone TTS' in i[1]]
        if ph: phone_sessions.append((lid,k,v['kind'],len(ph)))
        if iss: print(lid,k,v['kind'],iss)
print('phone-voiced steps:',phone_sessions)
print(len(done),'lessons')
