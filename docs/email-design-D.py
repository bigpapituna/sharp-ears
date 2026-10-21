import base64, os
os.chdir(os.path.dirname(os.path.abspath(__file__)))
def img(n): return "data:image/png;base64," + base64.b64encode(open(f"{n}.png", "rb").read()).decode()
P = dict(dancing=img('pup_dancing'), sticker=img('pup_sticker'), crown=img('pup_crown'), clap=img('pup_clapping'),
         think=img('pup_thinking'), base=img('pup_wear_base'), fly=img('pup_flying'))
CSS = '''<style>body{margin:0;background:#F3E9D8;font-family:'Nunito Sans',Arial,sans-serif;color:#2B2117}h1,h2,h3,summary,.n{font-family:Nunito,Arial,sans-serif}
.wrap{max-width:600px;margin:0 auto;padding:16px}.card{background:#FFF8EC;border-radius:22px;padding:20px;box-shadow:0 6px 0 #E6D3B3;margin-bottom:14px}
.btn{display:block;background:#C8622E;color:#fff;text-decoration:none;border-radius:999px;padding:13px 18px;font-family:Nunito,Arial;font-weight:900;font-size:16px;text-align:center;box-shadow:0 4px 0 #8F421B;margin:10px 0}
.btn.alt{background:#5B8DD6;box-shadow:0 4px 0 #3B6AAE}.chip{display:inline-block;background:#fff;border:2px solid #E6D3B3;border-radius:999px;padding:7px 12px;margin:4px 4px 0 0;font-weight:600;font-size:14px}
.paws span{display:inline-block;width:30px;height:30px;border-radius:50%;background:#EFE2C8;margin:3px;text-align:center;line-height:30px;font-size:16px}.paws span.on{background:#E8B23A}
.keys{display:flex;gap:2px;margin:8px 0}.keys div{flex:1;height:44px;background:#fff;border:1px solid #D9C8A6;border-radius:0 0 6px 6px;text-align:center;font-size:11px;line-height:70px;color:#8A7657}
.keys .m1{background:#FFE9A8}.keys .m2{background:#FFC46B}.keys .m3{background:#F08A5D;color:#fff}
.bubble{background:#FFE7B3;border-radius:18px;padding:13px 16px;font-size:15px}.small{font-size:13px;color:#7A6A52}
.stat{display:inline-block;width:31%;background:#fff;border-radius:16px;padding:10px 4px;text-align:center;margin:1%;box-sizing:border-box;vertical-align:top}.stat b{display:block;font-family:Nunito;font-size:22px;color:#C8622E}.stat i{font-style:normal;font-size:12px;color:#7A6A52}
details{background:#fff;border:2px solid #E6D3B3;border-radius:16px;padding:0 14px;margin:10px 0}details[open]{background:#FFFDF6}
summary{cursor:pointer;list-style:none;padding:13px 0;font-weight:900;font-size:16px;display:flex;justify-content:space-between;align-items:center}summary::-webkit-details-marker{display:none}
summary:after{content:"+";font-size:22px;color:#C8622E}details[open] summary:after{content:"\\2013"}
details .body{padding:0 0 14px;font-size:15px}table{width:100%;border-collapse:collapse;font-size:14px}td{padding:7px 0;border-bottom:1px solid #F0E4CC;vertical-align:top}td:last-child{text-align:right;white-space:nowrap}
.bar{height:10px;background:#EFE2C8;border-radius:5px;overflow:hidden;margin:4px 0}.bar i{display:block;height:100%;background:#5B8DD6}
.line{display:flex;align-items:flex-end;gap:6px;height:48px}.line b{flex:1;background:#E8B23A;border-radius:4px 4px 0 0}
</style>'''
H = f'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Mia's week from the pup</title>
<link href="https://fonts.googleapis.com/css2?family=Nunito:wght@700;900&family=Nunito+Sans:wght@400;600&display=swap" rel="stylesheet">{CSS}</head><body><div class="wrap">

<div class="card" style="border:3px dashed #E6D3B3;position:relative">
<div style="position:absolute;right:16px;top:12px;background:#fff;border:2px solid #C8622E;border-radius:8px;padding:5px 8px;font-family:Nunito;font-weight:900;color:#C8622E;transform:rotate(6deg)">WEEK 42</div>
<img src="{P['dancing']}" width="140" style="display:block;margin:-6px 0 0 -8px">
<h1 style="font-size:26px;line-height:1.15;margin:6px 0 10px">Mia kept at Row, Row, Row Your Boat until it worked, and played six days this week.</h1>
<div><span class="chip">78 minutes</span><span class="chip">6 of 7 days</span><span class="chip">4 new lessons</span><span class="chip">3 stickers</span></div>
<div class="paws" style="margin:10px 0 2px"><span class="on">&#x1F43E;</span><span class="on">&#x1F43E;</span><span class="on">&#x1F43E;</span><span>&#x1F43E;</span><span class="on">&#x1F43E;</span><span class="on">&#x1F43E;</span><span class="on">&#x1F43E;</span></div>
<p class="small" style="margin:2px 0 0">Thu Fri Sat Sun Mon Tue Wed. A paw is a day with practice; the goal is five minutes.</p>
</div>

<div class="card">
<h2 style="font-size:20px;margin:0 0 8px">In one minute</h2>
<p style="margin:6px 0"><b>What happened.</b> Six sessions, most of them after dinner, about 13 minutes each. Four new lessons at the pond; three of them earned three stars first time.</p>
<p style="margin:6px 0"><b>The hard bit.</b> Copy the frogs: three tries on the step where the pup plays four notes and Mia plays them back. New skills take a few days to settle, and the app lets a step go on purpose so nobody stays stuck.</p>
<p style="margin:6px 0"><b>Getting better.</b> Slips fell from 19 last week to 13, and most are now near misses, the key next door.</p>
<div style="display:flex;align-items:center;gap:10px;margin-top:12px"><img src="{P['sticker']}" width="64"><div class="bubble"><b>Say this at home:</b> "I noticed you kept going on Row, Row, Row Your Boat. That's sticking power."</div></div>
</div>

<div class="card">
<h2 style="font-size:20px;margin:0 0 4px">What I'd do next</h2>
<p class="small" style="margin:0 0 6px">Each button opens the app on that action, behind the grown-ups' gate.</p>
<p style="margin:8px 0 2px"><b>1. Make Copy the frogs tomorrow's first stone.</b> <span class="small">Why: it is the one lesson she left at one star, and a fresh day usually does it.</span></p>
<a class="btn" href="#">Set as next</a>
<p style="margin:8px 0 2px"><b>2. Sit in for one duet on Copy the frogs, you on the left hand.</b> <span class="small">Why: the hard step is listening then copying; a second pair of hands keeps the beat while she finds the keys.</span></p>
<a class="btn alt" href="#">Practise together</a>
<p style="margin:8px 0 2px"><b>3. Keep the evening slot.</b> <span class="small">Why: five of six sessions were after dinner and all of them reached the goal. Nothing to tap, just keep doing it.</span></p>
</div>

<div class="card" style="padding:14px 20px">
<h2 style="font-size:20px;margin:6px 0 2px">The details, if you want them</h2>
<p class="small" style="margin:0 0 8px">Tap a section to open it.</p>

<details open><summary>Lessons this week</summary><div class="body">
<table>
<tr><td>Drum on a lily pad<br><span class="small">Rhythm: clap, then C on every beat</span></td><td>&#x2B50;&#x2B50;&#x2B50; &middot; 1 try</td></tr>
<tr style="background:#FFF3D6"><td>Copy the frogs<br><span class="small">Listening: copy four notes</span></td><td>&#x2B50;&#x2606;&#x2606; &middot; 3 tries, let go</td></tr>
<tr><td>Row, Row, Row Your Boat<br><span class="small">Song, right hand</span></td><td>&#x2B50;&#x2B50;&#x2B50; &middot; came back, 2nd go</td></tr>
<tr><td>Ta and ti-ti<br><span class="small">Rhythm words</span></td><td>&#x2B50;&#x2B50;&#x2606; &middot; 1 try</td></tr>
<tr><td>Twinkle (replayed for stars)</td><td>&#x2B50;&#x2B50;&#x2B50;</td></tr>
</table>
<p class="small" style="margin:8px 0 0">"Let go" means the pup moved on after three tries; the lesson stays open and counts as one star until it is replayed.</p>
</div></details>

<details><summary>Keys and fingers</summary><div class="body">
<p class="small" style="margin:0 0 6px">Which keys slipped, over 412 notes played. Yellow is a near miss (the key next door), orange is further away.</p>
<div class="keys"><div>C</div><div class="m1">D</div><div class="m3">E</div><div>F</div><div class="m2">G</div><div>A</div><div>B</div><div>C</div></div>
<div><span class="stat"><b>11</b><i>near misses</i></span><span class="stat"><b>2</b><i>far misses</i></span><span class="stat"><b>3%</b><i>of notes slipped</i></span></div>
<p style="margin:8px 0 4px"><b>Most common slip:</b> E when D was wanted (7 times). That is the index and middle fingers swapping, the most common slip at this stage; the treehouse finger walks are built for it.</p>
<p style="margin:4px 0"><b>Skipped notes:</b> 4 (expected, never played). <b>Hesitation:</b> about 1.1 s to find a key on average, 2.4 s on G, which she still looks for.</p>
<p style="margin:4px 0"><b>Hands:</b> right hand 97% clean, left hand 88% clean (the left-paw lessons are new this week).</p>
</div></details>

<details><summary>Rhythm and listening</summary><div class="body">
<p style="margin:0 0 4px"><b>Timing:</b> notes landed 44 ms from the beat on average (last week 61 ms). Under 50 ms is steady for this age.</p>
<p style="margin:4px 0"><b>Clapping:</b> 14 of 16 claps on the beat in the drum lesson.</p>
<p style="margin:4px 0"><b>Ear games:</b> 5 of 6 "higher or lower" right; the copy game is the one that needs time.</p>
<p style="margin:4px 0"><b>Hints:</b> the pup pointed at a key 3 times and played it for her once. Last week: 9 and 4.</p>
</div></details>

<details><summary>Habits and trends</summary><div class="body">
<p class="small" style="margin:0 0 4px">Minutes per week, last four weeks</p>
<div class="line"><b style="height:20%"></b><b style="height:25%"></b><b style="height:45%"></b><b style="height:100%"></b></div>
<p class="small" style="margin:2px 0 10px">19 &middot; 22 &middot; 38 &middot; 78 minutes</p>
<p style="margin:4px 0"><b>When:</b> after dinner 5 times, after school once. <b>Session length:</b> about 13 minutes, up from 9.</p>
<p style="margin:4px 0"><b>Play vs lessons:</b> 70% lessons, 20% songs, 10% the Jam.</p>
<div class="bar"><i style="width:70%"></i></div>
<p style="margin:4px 0"><b>Room:</b> the app heard talking or TV twice and paused the yellow keys; a quieter spot helps.</p>
</div></details>

<details><summary>Readiness and what changed</summary><div class="body">
<p style="margin:0 0 4px"><b>Reading letters:</b> not yet; the keys show animals. The app will say when the letter lessons are ready (usually after the hall).</p>
<p style="margin:4px 0"><b>Next page:</b> 7 of 12 pond stones done. The concert hall opens after the pond concert.</p>
<p style="margin:4px 0"><b>You did:</b> set Twinkle as next on Tuesday (she played it Wednesday). Bedtime after the goal: off.</p>
</div></details>
</div>

<div class="card" style="text-align:center">
<img src="{P['crown']}" width="110">
<h3 style="margin:6px 0 2px">Gold star at home</h3>
<p style="margin:0">Mia came back to Row, Row, Row Your Boat after it was hard, and finished it. Tell her you saw that.</p>
<p class="small" style="margin-top:12px">Signed, the pup</p>
</div>
<p class="small" style="text-align:center">Sent by Sharp Ears from Roy's phone for Mia. Written by AI from the week's numbers; no audio or recordings leave the phone. Change the day, the time, or stop these in the app's grown-ups corner. The full report is attached as a PDF for mail apps that cannot open the sections.</p>
</div></body></html>'''
open('email-D-mix.html', 'w', encoding='utf-8').write(H)
print('ok')
