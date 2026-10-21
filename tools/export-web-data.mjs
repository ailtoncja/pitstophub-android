// Exporta do PWA os dados que o app Android reaproveita (catalogo estatico, biografias de pilotos, titulos,
// campeoes de F1, fichas e desenhos de circuito, fichas de rali do WRC) para `app/src/main/assets/`. Roda com o
// tsx do proprio PWA (e usa o sharp dele para converter os mapas):
//
//   cd ../pitstophub && npx tsx ../pitstophub-android/tools/export-web-data.mjs
//
// (a pasta do PWA e o diretorio atual; passe outra como primeiro argumento se precisar)
//
// O site e a fonte da verdade: nada aqui e digitado a mao. As regras de escolha do circuito (src/circuit-info.ts)
// viram dados (circuits.json) e o app aplica o mesmo algoritmo (data/CircuitRouting.kt). Antes de gravar, este
// script confere que o algoritmo em cima dos dados exportados da exatamente a mesma ficha que o site para todas as
// etapas do catalogo em todas as categorias, e grava o resultado esperado para o teste do app
// (app/src/test/resources/circuit_routing_expected.json).
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const pwa = resolve(process.argv[2] ?? process.cwd());
const root = resolve(import.meta.dirname, '..');
const out = join(root, 'app/src/main/assets');
const testResources = join(root, 'app/src/test/resources');
mkdirSync(out, { recursive: true });
mkdirSync(testResources, { recursive: true });
const load = (file) => import(pathToFileURL(join(pwa, 'src', file)).href);
const sharp = createRequire(join(pwa, 'package.json'))('sharp');

const { MOTORSPORT_DATA } = await load('types.ts');
const { getDriverBio } = await load('driver-bios.ts');
const { getDriverTitles } = await load('driver-titles.ts');
const { F1_CHAMPIONS } = await load('f1-champions.ts');
const { TEAM_BIOS } = await load('team-bios.ts');
const { getRaceCircuitInfo } = await load('circuit-info.ts');
const { DRAWN_CIRCUITS } = await load('circuit-drawings.ts');
const { RALLIES_2026 } = await load('rally/rallies-2026.ts');
const { RALLY_DATES_2026 } = await load('rally/dates.ts');

// --- catalogo estatico (categorias, equipes, pilotos, calendario, regras) e biografias de equipes --------
// Inclui os pares "cancelada <-> substituta" (replacedBy/replaces/relocated/changeSource) de cada etapa.
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

// --- circuitos ---------------------------------------------------------------------------------------
const source = readFileSync(join(pwa, 'src/circuit-info.ts'), 'utf8');

// Termos de busca: texto puro (includes) ou expressao regular (so a fonte; o site nao usa flags).
function splitTerms(list, where) {
  const terms = [];
  const patterns = [];
  for (const term of list) {
    if (typeof term === 'string') terms.push(term);
    else if (term instanceof RegExp) {
      if (term.flags) throw new Error(`${where}: regex com flags (${term}) nao suportada pelo app`);
      patterns.push(term.source);
    } else throw new Error(`${where}: termo desconhecido ${term}`);
  }
  return { terms, patterns };
}

// 1) Lugares com desenho do OpenStreetMap (VENUES): o literal do site e JS puro (strings, regex, objetos).
const venuesText = source.match(/const VENUES: Venue\[\] = (\[[\s\S]*?\n\]);/)?.[1];
if (!venuesText) throw new Error('VENUES nao encontrado em circuit-info.ts');
const VENUES = new Function(`return ${venuesText};`)();
const VENUE_KEYS = new Set(['terms', 'not', 'categories', 'layout', 'byCategory', 'byText', 'byEvent']);
const venues = VENUES.map((venue, index) => {
  for (const key of Object.keys(venue)) if (!VENUE_KEYS.has(key)) throw new Error(`VENUES[${index}]: campo novo "${key}" (atualize o app)`);
  return {
    ...splitTerms(venue.terms, `VENUES[${index}]`),
    not: venue.not ?? [],
    ...(venue.categories ? { categories: venue.categories } : {}),
    layout: venue.layout,
    byCategory: venue.byCategory ?? {},
    byText: (venue.byText ?? []).map((option) => ({ ...splitTerms(option.terms, `VENUES[${index}].byText`), layout: option.layout })),
    byEvent: venue.byEvent ?? [],
  };
});

const drawings = Object.fromEntries(
  Object.entries(DRAWN_CIRCUITS).map(([key, drawn]) => [
    key,
    {
      name: drawn.name,
      layout: drawn.layout,
      trackImage: drawn.trackImage,
      ...(drawn.lengthKm != null ? { lengthKm: drawn.lengthKm } : {}),
      ...(drawn.corners != null ? { corners: drawn.corners } : {}),
      ...(drawn.direction != null ? { direction: drawn.direction } : {}),
    },
  ]),
);

// 2) Fichas de GP (RACE_TEST_CIRCUITS): cada `isXRace` vira dados (termos, exclusoes, regex, pais, "a nao ser").
const order = [...source.matchAll(/\{ match: (is\w+Race), info: (\w+) \}/g)].map((m) => ({ fn: m[1], info: m[2] }));
const idOf = (info) => info.replace(/_CIRCUIT_INFO$/, '').toLowerCase().replace(/_/g, '');
const idByFn = Object.fromEntries(order.map(({ fn, info }) => [fn, idOf(info)]));
const gp = [];
for (const { fn, info } of order) {
  const pattern = new RegExp(`function ${fn}\\(race: Race\\): boolean \\{([\\s\\S]*?)\\r?\\n\\}`);
  const body = source.match(pattern)?.[1] ?? '';
  const literals = [...body.matchAll(/(!?)haystack\.includes\('([^']+)'\)/g)];
  const terms = literals.filter((m) => !m[1]).map((m) => m[2]);
  const not = literals.filter((m) => m[1]).map((m) => m[2]);
  const patterns = [...body.matchAll(/\/((?:\\.|[^/\\\n])+)\/([a-z]*)\.test\(haystack\)/g)].map((m) => {
    if (m[2]) throw new Error(`${fn}: regex com flags`);
    return m[1];
  });
  const unless = [...body.matchAll(/if \((is\w+Race)\(race\)\) return false/g)].map((m) => idByFn[m[1]]);
  const withCountry = /race\.country/.test(body);
  if (terms.length === 0 && patterns.length === 0) throw new Error(`Sem palavras-chave para ${fn}`);
  const probe = getRaceCircuitInfo({ id: 'probe', name: '', location: '', date: '2026-01-01', circuit: terms[0] ?? '', status: 'upcoming' }, 'f1');
  if (!probe || probe.laps == null) throw new Error(`Sem ficha de GP para ${fn} (a palavra "${terms[0]}" caiu em outro lugar)`);
  gp.push({ id: idOf(info), terms, patterns, not, unless, withCountry, info: probe });
}
if (new Set(gp.map((entry) => entry.info.trackImage)).size !== gp.length) throw new Error('Duas fichas de GP com o mesmo mapa.');

const circuits = { venues, drawings, gp };

// 3) O mesmo algoritmo que o app roda (data/CircuitRouting.kt), em cima dos dados acima.
const strip = (text) => text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const haystackOf = (race, withCountry) =>
  strip(`${race.circuit} ${race.location} ${race.enLocation ?? ''} ${race.name} ${race.enName ?? ''}${withCountry ? ` ${race.country ?? ''}` : ''}`);
const anyTerm = (haystack, set) => set.terms.some((term) => haystack.includes(term)) || set.patterns.some((p) => new RegExp(p).test(haystack));
const dayOf = (date) => Date.parse(`${date.slice(0, 10)}T00:00:00Z`) / 86_400_000;
const gpById = Object.fromEntries(gp.map((entry) => [entry.id, entry]));
const gpMatches = (entry, race) => {
  const haystack = haystackOf(race, entry.withCountry);
  return anyTerm(haystack, entry) && !entry.not.some((term) => haystack.includes(term)) && !entry.unless.some((id) => gpMatches(gpById[id], race));
};
const clean = (info) => Object.fromEntries(Object.entries(info).filter(([, value]) => value !== undefined));
function routeOf(race, categoryId) {
  if (categoryId === 'wrc') return null;
  const haystack = haystackOf(race, true);
  for (const venue of venues) {
    if (venue.categories && !(categoryId && venue.categories.includes(categoryId))) continue;
    if (!anyTerm(haystack, venue)) continue;
    if (venue.not.some((term) => haystack.includes(term))) continue;
    const event = venue.byEvent.find((e) => e.categoryId === categoryId && Math.abs(dayOf(e.date) - dayOf(race.date)) <= 2);
    const text = venue.byText.find((option) => anyTerm(haystack, option));
    const layout = event?.layout ?? text?.layout ?? ((categoryId && venue.byCategory[categoryId]) || venue.layout);
    const drawn = drawings[layout];
    if (!drawn) return null;
    return clean({ trackImage: drawn.trackImage, osm: true, lengthKm: drawn.lengthKm, corners: drawn.corners, direction: drawn.direction });
  }
  const entry = gp.find((candidate) => gpMatches(candidate, race));
  if (!entry) return null;
  if (!categoryId || categoryId === 'f1') return entry.info;
  const { trackImage, osm, lengthKm, corners, direction } = entry.info;
  return clean({ trackImage, osm, lengthKm, corners, direction });
}

// 4) Confere com o site: toda etapa do catalogo, em toda categoria (e sem categoria), mais variantes ao vivo.
const sorted = (value) => JSON.stringify(value, (key, v) => (v && typeof v === 'object' && !Array.isArray(v) ? Object.fromEntries(Object.entries(v).sort()) : v));
const allRaces = MOTORSPORT_DATA.flatMap((category) => category.calendar);
const variants = allRaces.flatMap((race) => [
  race,
  { ...race, id: `live-${race.id}`, name: race.enName ?? race.name, enName: undefined, location: race.enLocation ?? race.location, enLocation: undefined },
  { ...race, id: `bare-${race.id}`, circuit: '', name: race.location, enName: undefined },
]);
const categoryIds = [undefined, ...MOTORSPORT_DATA.map((category) => category.id)];
let checked = 0;
for (const race of variants) {
  for (const categoryId of categoryIds) {
    const site = getRaceCircuitInfo(race, categoryId);
    const mine = routeOf(race, categoryId);
    if (sorted(site) !== sorted(mine)) {
      throw new Error(`Algoritmo do app diverge do site em ${categoryId ?? '-'} / ${race.id}:\n site ${sorted(site)}\n app  ${sorted(mine)}`);
    }
    checked++;
  }
}
writeFileSync(join(out, 'circuits.json'), JSON.stringify(circuits));

// Resultado esperado de cada etapa do catalogo na propria categoria, para o teste do app.
const expected = MOTORSPORT_DATA.flatMap((category) =>
  category.calendar.map((race) => ({ categoryId: category.id, raceId: race.id, info: getRaceCircuitInfo(race, category.id) })),
);
writeFileSync(join(testResources, 'circuit_routing_expected.json'), `${JSON.stringify(expected, null, 1)}\n`);

// 5) Mapas: todo mapa citado (fichas de GP e desenhos) vira assets/circuits/<nome>.webp (960 px de largura).
const circuitDir = join(out, 'circuits');
rmSync(circuitDir, { recursive: true, force: true });
mkdirSync(circuitDir, { recursive: true });
const images = new Set([...gp.map((entry) => entry.info.trackImage), ...Object.values(drawings).map((drawn) => drawn.trackImage)]);
for (const image of images) {
  const file = join(pwa, 'public', image);
  if (!existsSync(file)) throw new Error(`Mapa ausente no site: ${image}`);
  const name = image.replace(/^\/circuits\//, '').replace(/\.png$/, '');
  await sharp(file).resize({ width: 960, withoutEnlargement: true }).webp({ quality: 90, alphaQuality: 100, effort: 6 }).toFile(join(circuitDir, `${name}.webp`));
}

// --- fichas de rali (WRC) ------------------------------------------------------------------------------
// Um arquivo por rali (com o mapa), lido so na pagina de uma etapa do WRC, e um indice leve de datas.
const rallyIndex = RALLIES_2026.map((rally) => ({ id: rally.id, start: rally.dates.start, end: rally.dates.end }));
if (sorted(rallyIndex) !== sorted(RALLY_DATES_2026)) throw new Error('RALLY_DATES_2026 nao bate com RALLIES_2026');
const rallyDir = join(out, 'rally');
rmSync(rallyDir, { recursive: true, force: true });
mkdirSync(rallyDir, { recursive: true });
writeFileSync(join(rallyDir, 'index.json'), JSON.stringify(RALLY_DATES_2026));
for (const rally of RALLIES_2026) writeFileSync(join(rallyDir, `${rally.id}.json`), JSON.stringify(rally));

console.log(
  `bios=${Object.keys(bios).length} titulos=${Object.keys(titles).length} campeoes=${F1_CHAMPIONS.length} ` +
    `gp=${gp.length} lugares=${venues.length} desenhos=${Object.keys(drawings).length} mapas=${images.size} ` +
    `rotas conferidas=${checked} ralis=${RALLIES_2026.length} (${readdirSync(rallyDir).length - 1} arquivos)`,
);
