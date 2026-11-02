import hashlib,json,pathlib
p=pathlib.Path(__file__).resolve().parents[1]
raw=(p/'app/src/main/assets/ser-joy-content.json').read_bytes();doc=json.loads(raw);jokes=doc['jokes']
old=json.loads((p/'docs/patches/ser-joy-v1.json').read_bytes())['jokes']
assert len(jokes)==142 and jokes[:22]==old, 'replace tail with 20 supplied jokes; preserve first 22'
assert len({j['question'] for j in jokes})==142, 'unique questions'
for n,j in enumerate(jokes,1):
 assert j['id']==f'joke-{n}' and len(j['choices'])==3 and 0<=j['answer']<3
 assert len({c['label'] for c in j['choices']})==3
 assert 0<len(j['question'])<=300 and 0<len(j['punchline'])<=500
 for c in j['choices']:assert 0<len(c['icon'])<=24 and 0<len(c['label'])<=100
m=json.loads((p/'docs/patches/ser-joy-manifest.json').read_text())
assert m['version']==doc['version']==4 and m['sha256']==hashlib.sha256(raw).hexdigest()
assert (p/'docs/patches'/m['file']).read_bytes()==raw and len(raw)<262144
print('PASS: 142 unique questions, stable old IDs, three choices, patch checksum and size')

assert m['minAppVersion']==43
assert json.loads((p/'app/src/main/assets/ser-joy-jokes.json').read_text())==jokes

previous=json.loads((p/'docs/patches/ser-joy-v2.json').read_text())['jokes'][22:]
for a,b in zip(jokes[42:],previous):
 assert {k:v for k,v in a.items() if k!='id'}=={k:v for k,v in b.items() if k!='id'}
scenes=json.loads((p/'app/src/main/assets/story/scenes.json').read_text())['scenes']
assert len(scenes)==len(jokes)
for i,s in enumerate(scenes):
 assert s['id']==jokes[i]['id'] and 0<=s['room']<16 and len(s['objects'])==3 and len(s['tags'])==3
 for obj in [s['target']]+s['objects']:assert -16<=obj<256 and not 218<=obj<224
for n in ['rooms','special','motion']+[f'objects-{i}' for i in range(8)]:assert (p/f'app/src/main/assets/story/{n}.webp').stat().st_size>1000
assert [c['label'] for c in jokes[23]['choices']]==['ค้อน','ระเบิด','ไฟ'] and jokes[23]['answer']==2
print('PASS: all 142 story scenes, old tail preserved, art references and door choices')
