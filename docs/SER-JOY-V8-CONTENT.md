# Content v8: repair confusing questions after level 70

The v7 importer adapted published riddles and wrote its own distractors and explanations. Several edits removed the pun's mechanism, exposed the answer in the prompt, or asked a different question from the one the answer addressed. Source URLs alone did not prevent these defects.

This revision repairs the existing set rather than generating a new batch. For example, level 82 now explains the approximate English sound in sea; level 109 distinguishes the speaker's ผม from hair; level 122 asks what happens to the snake instead of which object it bites; level 155 no longer gives away sesame seeds in the question. Explanations distinguish jokes from literal animal, language, and medical claims. Word answers use neutral icons instead of misleading substring-derived illustrations.

All 156 IDs, order, answer indices and the first 69 records remain stable. The revised answer labels remain under 32 characters. Existing save data, ad settings, reward gates, gameplay and audio are unaffected. The prior v7 release remains immutable. `tools/curate-v8.py` reproduces v8 from that release; the v7 curator is historical and must not be used to publish the current pack.

The source ledger inherits v7 provenance and records editorial corrections; it is not a claim that every wording is a direct quotation or that every source author was independently identified. Original source pages were consulted during review. Distractors and explanatory language are editorial.

Delivery is a data patch, version 8, minimum app code 53. Apps 53/54 can fetch it at startup; an older app still requires its Google Play update. The checksum is generated from exact file bytes. Bundled content also includes v8 for subsequent builds.

Validation: run `python3 tests/check-content.py`. This checks canonical answers, patch bytes/checksum, preservation of IDs/answer positions/first 69 records, bounded mobile labels, and focused regressions. Android device execution and public patch delivery must be reported separately; content checks alone do not prove a device installed the update.
