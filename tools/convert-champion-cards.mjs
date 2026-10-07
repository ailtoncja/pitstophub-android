// Converte as cartas de campeoes do PWA (PNG de ~3 MB) para WebP leve dentro do app.
//
//   cd ../pitstophub && node ../pitstophub-android/tools/convert-champion-cards.mjs
//
// Usa o `sharp` que ja e dependencia de desenvolvimento do PWA.
import { readdirSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join, resolve, basename } from 'node:path';

const pwa = resolve(process.argv[2] ?? process.cwd());
const sharp = createRequire(join(pwa, 'package.json'))('sharp');
const source = join(pwa, 'public/champions');
const target = resolve(import.meta.dirname, '../app/src/main/res/drawable-nodpi');
mkdirSync(target, { recursive: true });

for (const file of readdirSync(source).filter((name) => name.endsWith('.png'))) {
  const id = basename(file, '.png');
  const output = join(target, `champion_${id}.webp`);
  const info = await sharp(join(source, file)).resize({ width: 720, withoutEnlargement: true }).webp({ quality: 82 }).toFile(output);
  console.log(`${file} -> champion_${id}.webp (${Math.round(info.size / 1024)} KB, ${info.width}x${info.height})`);
}
