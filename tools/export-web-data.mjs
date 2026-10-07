// Exporta do PWA os dados que o app Android reaproveita (catalogo estatico, biografias de pilotos, titulos,
// campeoes de F1 e fichas de circuito) para `app/src/main/assets/`. Roda com o tsx do proprio PWA:
//
//   cd ../pitstophub && npx tsx ../pitstophub-android/tools/export-web-data.mjs
//
// (a pasta do PWA e o diretorio atual; passe outra como primeiro argumento se precisar)
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const pwa = resolve(process.argv[2] ?? process.cwd());
const out = resolve(import.meta.dirname, '../app/src/main/assets');
mkdirSync(out, { recursive: true });
const load = (file) => import(pathToFileURL(join(pwa, 'src', file)).href);

const { MOTORSPORT_DATA } = await load('types.ts');
const { getDriverBio } = await load('driver-bios.ts');
const { getDriverTitles } = await load('driver-titles.ts');
const { F1_CHAMPIONS } = await load('f1-champions.ts');
const { TEAM_BIOS } = await load('team-bios.ts');
const { getRaceCircuitInfo } = await load('circuit-info.ts');

// --- catalogo estatico (categorias, equipes, pilotos, calendario, regras) e biografias de equipes --------
writeFileSync(join(out, 'motorsport_data.json'), JSON.stringify({ categories: MOTORSPORT_DATA, teamBios: TEAM_BIOS }));

// --- biografias e titulos por piloto ("categoria:piloto") -------------------------------------------
const bios = {};
const titles = {};
const people = [
  ...MOTORSPORT_DATA.flatMap((category) => category.drivers.map((driver) => [category.id, driver.id])),
  ...F1_CHAMPIONS.map((champion) => ['f1', champion.id]),
];
for (const [categoryId, driverId] of people) {
  const key = `${categoryId}:${driverId}`;
  const bio = getDriverBio(categoryId, driverId);
  if (bio) bios[key] = bio;
  const driverTitles = getDriverTitles(categoryId, driverId);
  if (driverTitles?.length) titles[key] = driverTitles;
}
writeFileSync(join(out, 'driver_extras.json'), JSON.stringify({ bios, titles }));

// --- campeoes de F1 --------------------------------------------------------------------------------
writeFileSync(join(out, 'f1_champions.json'), JSON.stringify(F1_CHAMPIONS));

// --- fichas de circuito -----------------------------------------------------------------------------
// Cada circuito do PWA e escolhido por uma funcao `isXRace` que procura palavras no texto da corrida;
// aqui as palavras viram dados (keywords / exclude) e a ficha vem de getRaceCircuitInfo.
const source = readFileSync(join(pwa, 'src/circuit-info.ts'), 'utf8');
const circuits = [];
const order = [...source.matchAll(/\{ match: (is\w+Race), info: (\w+) \}/g)].map((m) => ({ fn: m[1], info: m[2] }));
for (const { fn, info } of order) {
  const pattern = new RegExp(`function ${fn}\\(race: Race\\): boolean \\{([\\s\\S]*?)\\r?\\n\\}`);
  const body = source.match(pattern)?.[1] ?? '';
  const literals = [...body.matchAll(/(!?)haystack\.includes\('([^']+)'\)/g)];
  const keywords = literals.filter((m) => !m[1]).map((m) => m[2]);
  const exclude = literals.filter((m) => m[1]).map((m) => m[2]);
  if (keywords.length === 0) throw new Error(`Sem palavras-chave para ${fn}`);
  const probe = getRaceCircuitInfo({ id: 'probe', name: '', location: '', date: '2026-01-01', circuit: keywords[0], status: 'upcoming' });
  if (!probe) throw new Error(`Sem ficha para ${fn}`);
  circuits.push({ id: info.replace(/_CIRCUIT_INFO$/, '').toLowerCase(), keywords, exclude, info: probe });
}
if (new Set(circuits.map((c) => c.info.trackImage)).size !== circuits.length) throw new Error('Palavras-chave de circuitos se sobrepoem.');
writeFileSync(join(out, 'circuits.json'), JSON.stringify(circuits));

console.log(`bios=${Object.keys(bios).length} titulos=${Object.keys(titles).length} campeoes=${F1_CHAMPIONS.length} circuitos=${circuits.length}`);
