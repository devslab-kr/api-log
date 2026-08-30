import { createHash } from 'node:crypto';
import { access, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const guide = 'https://devslab.kr/brand/open-source/';
const socialImage = 'https://api-log.devslab.kr/assets/social-preview.png';
const read = (relative) => readFile(path.join(root, relative), 'utf8');
const file = (relative) => path.join(root, relative);

function expect(contents, expected, message) {
  if (!contents.includes(expected)) throw new Error(`${message}: expected ${JSON.stringify(expected)}`);
}

function reject(contents, pattern, message) {
  if (pattern.test(contents)) throw new Error(message);
}

async function sha256(relative) {
  return createHash('sha256').update(await readFile(file(relative))).digest('hex');
}

const expectedAssets = new Map([
  ['.github/assets/readme-header.png', 'fea39433ffb685add26050558b2758858610be982f893014a1eead2d5469fbcc'],
  ['.github/assets/social-preview.png', '3120931330d7604cda9ee4aa27d42e849e9f05f24cd8cb50b95e389d97a86842'],
  ['docs/assets/logo.svg', '94697c47e87ac17d9209b890da0993b9d85c04ddb2d91b27afc7cfc31dc0380d'],
  ['docs/assets/favicon.svg', '94697c47e87ac17d9209b890da0993b9d85c04ddb2d91b27afc7cfc31dc0380d'],
  ['docs/assets/social-preview.png', '3120931330d7604cda9ee4aa27d42e849e9f05f24cd8cb50b95e389d97a86842'],
]);

for (const [relative, expected] of expectedAssets) {
  await access(file(relative));
  if ((await sha256(relative)) !== expected) throw new Error(`${relative} must match the O09 v0.1.1 asset checksum`);
}

const glyph = await read('docs/assets/logo.svg');
expect(glyph, 'data-oss-project="O09"', 'O09 registry id');
expect(glyph, 'M5 8H13', 'O09 event-ledger input row');
expect(glyph, 'M15 6H27V12H15Z', 'O09 event-ledger record cell');
reject(glyph, /M\s*4\s*4\s*L\s*28\s*4/i, 'O09 must replace the former shared backend mark');
reject(glyph, /M7 6H21V20H7Z/i, 'O09 must remain distinct from O08');
reject(glyph, /M5 5H13V13H5Z/i, 'O09 must remain distinct from O10');

const [readme, readmeKo, mkdocs, index, indexKo, override, css] = await Promise.all([
  read('README.md'),
  read('README.ko.md'),
  read('mkdocs.yml'),
  read('docs/index.md'),
  read('docs/index.ko.md'),
  read('docs/overrides/main.html'),
  read('docs/stylesheets/extra.css'),
]);

for (const [relative, contents, endorsement] of [
  ['README.md', readme, 'Open source by DevsLab'],
  ['README.ko.md', readmeKo, 'DevsLab 오픈소스'],
]) {
  expect(contents, '.github/assets/readme-header.png', `${relative} O09 README header`);
  expect(contents, guide, `${relative} canonical OSS guide`);
  expect(contents, endorsement, `${relative} localized endorsement`);
}

for (const [relative, contents, endorsement] of [
  ['docs/index.md', index, 'Open source by DevsLab'],
  ['docs/index.ko.md', indexKo, 'DevsLab 오픈소스'],
]) {
  expect(contents, 'oss-project-intro', `${relative} O09 project introduction`);
  expect(contents, guide, `${relative} canonical OSS guide`);
  expect(contents, endorsement, `${relative} localized endorsement`);
}

expect(mkdocs, 'custom_dir: docs/overrides', 'MkDocs override source');
expect(mkdocs, 'favicon: assets/favicon.svg', 'MkDocs O09 favicon');
expect(mkdocs, 'stylesheets/extra.css', 'MkDocs atmosphere stylesheet');
expect(override, socialImage, 'source-only O09 social preview metadata');
expect(override, 'og:image:alt', 'Open Graph alt metadata');
expect(override, 'twitter:image:alt', 'Twitter alt metadata');
expect(css, '.oss-project-intro', 'O09 docs project introduction styling');
expect(css, 'data-atmosphere="project"', 'O09 project atmosphere');
const slateAtmosphere = `[data-md-color-scheme="slate"] .oss-project-intro[data-atmosphere="project"]::before {
  background: radial-gradient(ellipse at 18% 50%, rgb(34 211 238 / 0.10), transparent 68%);
}`;
expect(css, slateAtmosphere, 'Material slate O09 atmosphere at the 0.10 cyan opacity cap');
expect(css, 'pointer-events: none', 'O09 atmosphere ignores interaction');
expect(css, '@media (forced-colors: active), print', 'O09 print and forced-colors fallback');

console.log(`check:brand: verified O09 event-ledger identity, metadata, and ${expectedAssets.size} checksummed v0.1.1 assets`);
