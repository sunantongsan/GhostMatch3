# Ser Joy 0.46.0

Added 100 questions (IDs joke-23 to joke-122), preserving the first 22 and their answer ordering. Four short familiar wordplay premises were adapted into new Ser Joy scenarios: joke-44 (fire exit), joke-53 (frying pot), joke-62 (sleeping medicine), joke-109 (I see you). Inspiration: https://www.wongnai.com/articles/funny-q-a-collection (accessed 2026-11-01). The other 96 scenes and all distractor choices were authored for this game. This is not a copied compilation of 100 internet jokes.

The data patch is version 2. Existing version 43+ clients can download the questions; new artwork and code effects require app 46. A completed 22-question save resumes at question 23. The manifest is SHA-256 verified and the first 22 stable IDs remain unchanged.

Artwork generated with built-in image generation, using the existing original character sheet as identity reference. Final assets live in app/src/main/assets/ser-joy-fullbody.webp and ser-joy-neighborhood.webp. The source transparent sprite sheet is 1254 square; row sampling bounds are [0,420], [420,825], [825,1254]. Four columns and three rows retain the animation mapping: think, tease, congratulate.

Prompt summary: Same messy-haired, red-nosed man in a yellow shirt with a large belly, ragged navy shorts, skinny hairy legs and blue flip-flops; 12 full-body poses, transparent 4-column/3-row sheet, every foot visible. Background: portrait original Thai neighborhood comic stage, cream center, sunglasses cat, knotted lamp, smiling clouds, banana peel on a throne, tiny ghost, no text or UI. The full-body image is fit inside its view rather than cropped.

Match effects follow resolved clear snapshots: colored expanding rings, seven star particles per removed ghost, special horizontal/vertical beams or blast rings, and a combo callout. At most 80 short-lived bursts are retained. Effects stop scheduling frames while backgrounded and never change match rules, rewards, turn costs, or input locking.
