import hashlib,json,pathlib
p=pathlib.Path(__file__).resolve().parents[1]
raw=(p/'app/src/main/assets/ser-joy-content.json').read_bytes();doc=json.loads(raw);jokes=doc['jokes']
old=json.loads((p/'docs/patches/ser-joy-v1.json').read_bytes())['jokes']
assert len(jokes)==122 and jokes[:22]==old, 'append 100 without changing existing progression'
assert len({j['question'] for j in jokes})==122, 'unique questions'
for n,j in enumerate(jokes,1):
 assert j['id']==f'joke-{n}' and len(j['choices'])==3 and 0<=j['answer']<3
 assert len({c['label'] for c in j['choices']})==3
 assert 0<len(j['question'])<=300 and 0<len(j['punchline'])<=500
 for c in j['choices']:assert 0<len(c['icon'])<=24 and 0<len(c['label'])<=100
m=json.loads((p/'docs/patches/ser-joy-manifest.json').read_text())
assert m['version']==doc['version']==2 and m['sha256']==hashlib.sha256(raw).hexdigest()
assert (p/'docs/patches'/m['file']).read_bytes()==raw and len(raw)<262144
print('PASS: 122 unique questions, stable old IDs, three choices, patch checksum and size')
