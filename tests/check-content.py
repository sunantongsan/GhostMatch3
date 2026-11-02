import hashlib,json,pathlib
p=pathlib.Path(__file__).resolve().parents[1]
raw=(p/'app/src/main/assets/ser-joy-content.json').read_bytes();doc=json.loads(raw);jokes=doc['jokes']
old=json.loads((p/'docs/patches/ser-joy-v1.json').read_bytes())['jokes']
assert len(jokes)==155 and all(a['question']==b['question'] for a,b in zip(jokes[:22],old)), 'replace tail with 20 supplied jokes; preserve first 22'
assert len({j['question'] for j in jokes})==155, 'unique questions'
for n,j in enumerate(jokes,1):
 assert j['id']==f'joke-{n+(1 if n>=51 else 0)}' and len(j['choices'])==3 and 0<=j['answer']<3
 assert len({c['label'] for c in j['choices']})==3
 assert 0<len(j['question'])<=300 and 0<len(j['punchline'])<=500
 for c in j['choices']:assert 0<len(c['icon'])<=24 and 0<len(c['label'])<=100
m=json.loads((p/'docs/patches/ser-joy-manifest.json').read_text())
assert m['version']==doc['version']==6 and m['sha256']==hashlib.sha256(raw).hexdigest()
assert (p/'docs/patches'/m['file']).read_bytes()==raw and len(raw)<262144
print('PASS: 155 unique questions, stable old IDs, three choices, patch checksum and size')

assert m['minAppVersion']==51
assert json.loads((p/'app/src/main/assets/ser-joy-jokes.json').read_text())==jokes

previous=json.loads((p/'docs/patches/ser-joy-v2.json').read_text())['jokes'][22:]
for a,b in zip(jokes[55:],previous):
 assert a['question']==b['question'] and a['punchline']==b['punchline'] and a['answer']==b['answer']
scenes=json.loads((p/'app/src/main/assets/story/scenes.json').read_text())['scenes']
assert len(scenes)==len(jokes)
for i,s in enumerate(scenes):
 assert s['id']==jokes[i]['id'] and 0<=s['room']<16 and len(s['objects'])==3 and len(s['tags'])==3
 for obj in [s['target']]+s['objects']:assert -17<=obj<352 and not 218<=obj<224
for n in ['special','motion']+[f'rooms-{i}' for i in range(4)]+[f'objects-{i}' for i in range(11)]:assert (p/f'app/src/main/assets/story/{n}.webp').stat().st_size>1000
assert [c['label'] for c in jokes[23]['choices']]==['ค้อน','ระเบิด','ไฟ'] and jokes[23]['answer']==2
print('PASS: all 155 story scenes, old tail preserved, art references and door choices')

bounds=json.loads((p/'app/src/main/assets/story/bounds.json').read_text())
for name,rects in bounds.items():
 assert len(rects)==(16 if name in ['special.webp','motion.webp'] else 32)
 for x,y,r,b in rects:assert 0<=x<r and 0<=y<b
print('PASS: bounded sprite crops and HD background atlases')

assert len(jokes[42:55])==13
for j in jokes[42:55]:
 assert not any(t in j["question"]+j["punchline"] for t in ["ขุนแผน","นัท","นกถูกเหยียบ","เย็นที่สุด","กทม."])

assert all(len(s["labels"])==3 and all(s["labels"]) for s in scenes)
assert scenes[26]["labels"][2]=="ไฟแช็ก" and scenes[26]["objects"][2]==-17
assert scenes[27]["labels"][0]=="เงินบาท"

# All question strings remain as supplied, no scene-level paraphrases.
prior=json.loads((p/'docs/patches/ser-joy-v5.json').read_text())['jokes']
prior=[j for j in prior if j['id']!='joke-51']
assert all(j['id']!='joke-51' and 'พม่าแหกคุก' not in j['punchline'] for j in jokes)
for j,prev,s in zip(jokes,prior,scenes):
 assert j['question']==prev['question'] and 'prompt' not in s
 assert j['answer']==prev['answer'], 'stable scoring index'
 assert s['labels']==[c['label'] for c in j['choices']]
 assert isinstance(s['showTargetBeforeAnswer'],bool)
 if s['showTargetBeforeAnswer']: assert s['target'] not in s['objects'], 'no enlarged choice before answering'
 if len(set(s['objects']))<3: assert len(s.get('inscriptions',[]))==3, 'shared art needs distinct inscriptions'
 assert not any(x in s['objects'] for x in [111,263,276,287,229]), 'no clue-bearing bookworm/crowned fish/money germ/phone in water'
assert scenes[30]['objects']==[288,289,290] and scenes[30]['labels']==['หนอน','งู','ปลวก']
assert 'หนอนหนังสือ' in jokes[30]['punchline'] and 'อันติ๊ดเดียว' in jokes[34]['punchline']
assert scenes[31]['objects']==[333,-14,59]
print('PASS: original questions, neutral choices, setup subjects and post-answer explanations')
