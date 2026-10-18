// Checks that the theme colours used together stay readable (WCAG AA: 4.5:1 for text), in the
// light and the dark theme. Run with `npm run contrast`; fails when a pair falls short.
const fs = require('fs');
const path = require('path');

const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'styles.scss'), 'utf8');

/** The colour variables declared in one block of styles.scss. */
function block(selector) {
  const start = css.indexOf(selector + ' {');
  const end = css.indexOf('\n}', start);
  const vars = {};
  for (const m of css.slice(start, end).matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})/g)) {
    vars[m[1]] = m[2];
  }
  return vars;
}

function luminance(hex) {
  const [r, g, b] = [1, 3, 5]
    .map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function ratio(a, b) {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}

/** Text colour, then the surface it is written on. */
const PAIRS = [
  ['ink', 'bg'], ['ink', 'panel'], ['ink-soft', 'panel'], ['ink-soft', 'bg'], ['ink-soft', 'muted-bg'],
  ['on-brand', 'brand'], ['brand-ink', 'panel'], ['brand-ink', 'bg'], ['brand-ink', 'muted-bg'],
  ['brand-ink', 'brand-soft'], ['brand-dark', 'brand-soft'],
  ['green', 'panel'], ['green', 'green-soft'], ['amber', 'panel'], ['amber', 'bg'], ['amber', 'amber-soft'],
  ['red', 'panel'], ['red', 'red-soft'], ['blue', 'panel'], ['blue', 'blue-soft'],
  ['chrome-ink', 'chrome-bg'], ['chrome-ink-soft', 'chrome-bg'], ['chrome-accent', 'chrome-bg'],
];

const light = block(':root');
const dark = { ...light, ...block(":root[data-theme='dark']") };
let failures = 0;
for (const [name, theme] of [['light', light], ['dark', dark]]) {
  for (const [text, surface] of PAIRS) {
    if (!theme[text] || !theme[surface]) {
      failures++;
      console.log(`${name}: --${theme[text] ? surface : text} is not declared`);
      continue;
    }
    const value = ratio(theme[text], theme[surface]);
    if (value < 4.5) {
      failures++;
      console.log(`${name}: --${text} on --${surface} is ${value.toFixed(2)}:1, below 4.5:1`);
    }
  }
}
console.log(failures ? `${failures} pair(s) too pale.` : `All ${PAIRS.length * 2} pairs readable.`);
process.exit(failures ? 1 : 0);
