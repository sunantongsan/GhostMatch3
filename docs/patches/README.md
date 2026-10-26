# Adventure content patches (app 0.37.0+)

Welcome screen checks `adventure-manifest.json` over HTTPS before enabling entry.
Only JSON level balance data is downloaded; native code, permissions, SDKs and artwork still require an app release.

Payload schema 2 maps level IDs 1–120 to `moves` (1–100), `goal` (1–500) and `reward` (0–10000 gold). Rescue stages retain their built-in eight extra turns. Omitted stages use bundled defaults.

For each update create a new `adventure-N.json`, increment its version and the manifest version, calculate the exact file SHA-256 and set `file`, `sha256` and `minAppVersion` in the manifest. Publish through GitHub Pages. Do not alter legacy `manifest.json`: apps 0.35/0.36 use that separate schema.

Downloads are size/time limited and validated before atomic installation. Failure keeps the last good data and permits offline play. Active saved runs retain their original moves limit, target and reward, so a patch only changes newly started runs.

Artwork: `app/src/main/res/drawable-nodpi/ghost_wai_welcome.png`, generated with built-in imagegen. Prompt: friendly pearly ghost doing Thai wai, modern polished 3D fantasy ghost kingdom at purple twilight, gold fireflies, portrait composition with quiet top for title and lower area for native loading controls; no text/UI/watermark.
