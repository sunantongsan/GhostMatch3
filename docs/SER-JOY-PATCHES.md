# Ser Joy content updates

The welcome screen checks `https://sunantongsan.github.io/GhostMatch3/patches/ser-joy-manifest.json` before enabling Enter Game. Network failure uses the last validated cache or bundled version 1. App code, animation and ad settings still update through Google Play.

To release a data patch:
1. Copy `ser-joy-v1.json` to a new numbered file in `docs/patches/` and increase its `version`.
2. Keep existing `joke-N` IDs and ordering; append new questions only at the end. Existing questions may receive wording corrections. Each question requires exactly three choices and an answer index from 0 to 2.
3. Set the manifest version, file, SHA-256 of the exact UTF-8 file bytes and minimum app version (43 or higher if the schema needs newer code).
4. Commit the content and manifest together to main. Verify the published GitHub Pages manifest and file checksum.
5. Launch an installed test build and confirm the welcome screen reports the new content version. Repeat offline to confirm the cached update remains playable.

Limits: schema 1, 22–500 questions, 256 KiB per response, HTTPS fixed host, no redirects, bounded connection/read times and atomic cache replacement. Corrupt or incompatible patches never replace usable content. Patches cannot remove or reorder questions, preserving saved progress and pending reward/break gates. An older manifest cannot downgrade a cached version; publish a higher version for rollback corrections.

## Curated replacement v3 (app 47)
The owner replaced all questions after 22 with 20 supplied jokes, for 42 total.
This is bundled in app 0.47.0 because older clients reject shrinking patches.
The manifest requires app 47; v1/v2 files are retained only as historical releases.
App 47 ignores older cached v2 in favor of bundled v3. On first entry it preserves
saves within the first 22 questions, and resets later saves to question 23 with
obsolete reward/break state cleared. A persistent marker prevents repeated resets.
Future online updates still enforce the no-shrinking rule.
