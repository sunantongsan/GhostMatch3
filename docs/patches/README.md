# Adventure content patches (app 0.37.0+)

Welcome screen checks `adventure-manifest.json` over HTTPS before enabling entry.
Only JSON level balance data is downloaded; native code, permissions, SDKs and artwork still require an app release.

Payload schema 2 maps level IDs 1–120 to `moves` (1–100), `goal` (1–500) and `reward` (0–10000 gold). Rescue stages retain their built-in eight extra turns. Omitted stages use bundled defaults.

For each update create a new `adventure-N.json`, increment its version and the manifest version, calculate the exact file SHA-256 and set `file`, `sha256` and `minAppVersion` in the manifest. Publish through GitHub Pages. Do not alter legacy `manifest.json`: apps 0.35/0.36 use that separate schema.

Downloads are size/time limited and validated before atomic installation. Failure keeps the last good data and permits offline play. Active saved runs retain their original moves limit, target and reward, so a patch only changes newly started runs.

Artwork: `app/src/main/res/drawable-nodpi/ghost_wai_welcome.png`, generated with built-in imagegen. Prompt: friendly pearly ghost doing Thai wai, modern polished 3D fantasy ghost kingdom at purple twilight, gold fireflies, portrait composition with quiet top for title and lower area for native loading controls; no text/UI/watermark.

## Board patches (app 0.42.0+, schema 4) — แพตช์ผังด่าน

`board-manifest.json` → `board-N.json`. ใช้เปลี่ยนผังกระดาน อุปสรรค ผียักษ์ และเป้าหมายสีที่สองได้โดยไม่ต้องอัปเดตแอป

```json
{"schema":4,"version":2,
 "layouts":[["..#..#..","........","..i..i..","........","rr....rr","........","..#..#..","........"]],
 "levels":{"12":{"layout":["Gg....Gg","gg....gg","........","........","..####..","........","........","........"],
                 "color":0,"color2":3,"goal2":6,"giantHp":4}}}
```

- `layouts` (ไม่บังคับ, 1–64 ผัง): ผังที่วนใช้ตั้งแต่ด่าน 4 (ด่าน 4 = ผังแรก, ด่าน 5 = ผังที่สอง …). ไม่ใส่ = ใช้ 12 ผังในแอป
  ในผังที่วนใช้ โลกแรก (ด่าน 1–10) เปลี่ยน `l`/`w` เป็นหิน/น้ำแข็ง และผียักษ์เริ่มที่ด่าน 8
- `levels` (ไม่บังคับ): กำหนดเฉพาะด่าน 1–120 — `layout` ใช้ตามที่เขียนทุกตัว, `color`/`color2` สีผี 0–5 (ต้องไม่ซ้ำกันถ้ามี goal2), `goal2` 0–200 (0 = ไม่มีเป้าหมายที่สอง), `giantHp` 1–9
- ช่อง: `.` ชิ้นผี, `#` หลุม, `r` หิน, `R` หินแข็ง (2 ชั้น), `i` น้ำแข็ง, `w` ใยแมงมุม, `l` หีบล็อก (2 ชั้น), ผียักษ์เขียน `Gg` บนแถวหนึ่งและ `gg` แถวถัดไปตรงกัน
- ทุกผังต้องมี 8 แถว แถวละ 8 ตัว และมี `.` อย่างน้อย 24 ช่อง

ขั้นตอนอัปเดต: สร้างไฟล์ใหม่ `board-N.json` (ห้ามแก้ไฟล์ที่เผยแพร่แล้ว) เพิ่ม `version`, แก้ `board-manifest.json` ให้ `version`, `file`, `sha256` ตรงกับไฟล์ใหม่ แล้ว merge เข้า main (GitHub Pages เผยแพร่โฟลเดอร์ /docs)
แอปตรวจ SHA-256 และตรวจทุกด่าน 1–120 ก่อนใช้งาน ถ้าไฟล์ผิดหรือออฟไลน์จะใช้ผังล่าสุดที่ถูกต้อง ด่านที่เล่นค้างอยู่ไม่ถูกเปลี่ยนรูปทรงกลางคัน
