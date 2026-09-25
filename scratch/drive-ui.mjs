// Drives the real Angular app against the real backend through CDP: logs in as each
// role, walks every menu entry, runs the full business flow, checks the routing guards
// and the *appHasRole display, and collects any console error.
import { mkdirSync, writeFileSync } from 'node:fs';

const BASE = process.env.APP_URL ?? 'http://localhost:4300';
const CDP = process.env.CDP_URL ?? 'http://localhost:9222';
const PASSWORD = process.env.APP_PASSWORD ?? 'vaha2026';
const OUT = new URL('./screens/', import.meta.url);
mkdirSync(OUT, { recursive: true });

const problems = [];
const pages = [];
const flow = [];

const list = await (await fetch(`${CDP}/json/list`)).json();
const target = list.find((t) => t.type === 'page');
if (!target) throw new Error('no page target');

const ws = new WebSocket(target.webSocketDebuggerUrl);
let nextId = 1;
const pending = new Map();

ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data);
  if (msg.id && pending.has(msg.id)) {
    const { resolve, reject } = pending.get(msg.id);
    pending.delete(msg.id);
    msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
    return;
  }
  if (msg.method === 'Runtime.exceptionThrown') {
    problems.push(`EXCEPTION ${msg.params.exceptionDetails.text} ${
      msg.params.exceptionDetails.exception?.description ?? ''}`);
  }
  if (msg.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(msg.params.type)) {
    problems.push(`CONSOLE.${msg.params.type} ${
      msg.params.args.map((a) => a.value ?? a.description).join(' ')}`);
  }
});

await new Promise((resolve, reject) => {
  ws.addEventListener('open', resolve, { once: true });
  ws.addEventListener('error', reject, { once: true });
});

const send = (method, params = {}) =>
  new Promise((resolve, reject) => {
    const id = nextId++;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const flowIfBad = (result, what) => {
  if (result !== 'typed') problems.push(`TYPE ${what}: ${result}`);
};

const evaluate = async (expression) => {
  const { result, exceptionDetails } = await send('Runtime.evaluate', {
    expression, returnByValue: true, awaitPromise: true,
  });
  if (exceptionDetails) {
    throw new Error(exceptionDetails.text + ' ' + (exceptionDetails.exception?.description ?? ''));
  }
  return result.value;
};

const shoot = async (name) => {
  const { data } = await send('Page.captureScreenshot', { format: 'png' });
  writeFileSync(new URL(`./${name}.png`, OUT), Buffer.from(data, 'base64'));
};

await send('Page.enable');
await send('Runtime.enable');
await send('Network.enable');
// Start from a clean browser: a leftover session cookie would make the
// anonymousOnlyGuard bounce us straight off the login page.
await send('Network.clearBrowserCookies');
await send('Emulation.setDeviceMetricsOverride', {
  width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false,
});

// Angular's ngModel listens to the native input event, so the value has to be set
// through the prototype setter and the event dispatched, not just assigned.
const TYPE_HELPER = `
  window.__waitFor = async (selector, timeoutMs = 10000) => {
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      if (document.querySelector(selector)) return true;
      await new Promise(r => setTimeout(r, 100));
    }
    return false;
  };
  window.__type = async (selector, value) => {
    if (!await window.__waitFor(selector)) return 'NOT FOUND: ' + selector;
    const el = document.querySelector(selector);
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    return 'typed';
  };
  window.__clickText = (text) => {
    const btn = [...document.querySelectorAll('button')].find(b => b.textContent.includes(text));
    if (!btn) return 'NOT FOUND: ' + text;
    if (btn.disabled) return 'DISABLED: ' + text;
    btn.click();
    return 'clicked';
  };
`;

const goto = async (path) => {
  await send('Page.navigate', { url: BASE + path });
  await sleep(3000);
  await evaluate(TYPE_HELPER);
};

const login = async (username) => {
  await send('Network.clearBrowserCookies');
  await goto('/login');
  flowIfBad(await evaluate(`__type('#username', ${JSON.stringify(username)})`), 'username');
  flowIfBad(await evaluate(`__type('#password', ${JSON.stringify(PASSWORD)})`), 'password');
  await evaluate(`document.querySelector('form').requestSubmit()`);
  await sleep(1800);
  return evaluate(`({ path: location.pathname, role: document.querySelector('.role-tag')?.textContent?.trim() })`);
};

const logout = async () => {
  await evaluate(`__clickText('Se déconnecter')`);
  await sleep(1500);
};

const snapshot = () => evaluate(`(() => ({
  url: location.pathname,
  title: document.querySelector('.topbar h1')?.textContent?.trim(),
  cards: document.querySelectorAll('.content .card').length,
  rows: document.querySelectorAll('.content tbody tr').length,
  kpis: [...document.querySelectorAll('.content .kpi')].map(k =>
    k.querySelector('.label')?.textContent.trim() + ' = ' + k.querySelector('.value')?.textContent.trim()),
  chars: document.querySelector('.content')?.textContent?.trim().length,
}))()`);

const ROLES = [
  { user: 'fatima', slug: 'caisse' },
  { user: 'joseph', slug: 'depot' },
  { user: 'nadia', slug: 'gestion' },
  { user: 'mparany', slug: 'admin' },
];

// ---------------------------------------------------------- 1. every screen
for (const role of ROLES) {
  const session = await login(role.user);
  flow.push(`login ${role.user} -> ${session.path} (${session.role})`);

  const menu = await evaluate(`[...document.querySelectorAll('nav.menu a')].map(a => a.textContent.trim())`);
  for (let i = 0; i < menu.length; i++) {
    await evaluate(`document.querySelectorAll('nav.menu a')[${i}].click()`);
    await sleep(1400);
    await evaluate(TYPE_HELPER);
    pages.push({ role: role.slug, menu: menu[i], ...(await snapshot()) });
    await shoot(`${role.slug}-${String(i + 1).padStart(2, '0')}`);
  }
  await logout();
}

// -------------------------------------------------- 2. guards and hidden UI
await login('fatima');
await goto('/admin/vue-ensemble');
flow.push(`guard: cashier -> /admin/vue-ensemble lands on ${await evaluate('location.pathname')}`);
await goto('/depot/caisse');
flow.push(`guard: cashier -> /depot/caisse lands on ${await evaluate('location.pathname')}`);

await goto('/caisse/versements');
flow.push(`*appHasRole: cashier sees "Confirmer réception" = ${
  await evaluate(`[...document.querySelectorAll('button')].some(b => b.textContent.includes('Confirmer réception'))`)}`);

await goto('/login');
flow.push(`guard: signed-in user asking for /login lands on ${await evaluate('location.pathname')}`);

// ------------------------------------------------------- 3. business flow
await goto('/caisse/nouvelle-vente');
await evaluate(`(() => { const c = document.querySelectorAll('.prod-card'); c[0].click(); c[0].click(); c[3].click(); })()`);
await sleep(600);
flow.push(`cart total = ${await evaluate(`document.querySelector('.total-row span:last-child').textContent`)}`);
await evaluate(`(() => {
  const sel = document.getElementById('payment-status');
  sel.selectedIndex = 1;                       // "Non payée (à régler au dépôt)"
  sel.dispatchEvent(new Event('change', { bubbles: true }));
})()`);
await sleep(400);
await evaluate(`__clickText('Valider la vente')`);
await sleep(2000);
const saleToast = await evaluate(`document.querySelector('.toast')?.textContent ?? 'NO TOAST'`);
flow.push(`sale toast: ${saleToast}`);
await shoot('flow-1-sale');

await logout();
await login('joseph');
await goto('/depot/remise');
flow.push(`deliver: ${await evaluate(`__clickText('Encaisser et remettre')`)}`);
await sleep(2000);
flow.push(`delivery toast: ${await evaluate(`document.querySelector('.toast')?.textContent ?? 'NO TOAST'`)}`);

await goto('/depot/caisse');
flow.push(`cash in hand = ${await evaluate(`document.querySelector('.kpi .value')?.textContent`)}`);
flow.push(`remit: ${await evaluate(`__clickText('à la caisse')`)}`);
await sleep(2000);
flow.push(`remit toast: ${await evaluate(`document.querySelector('.toast')?.textContent ?? 'NO TOAST'`)}`);
await shoot('flow-2-remittance');

await logout();
await login('fatima');
await goto('/caisse/versements');
flow.push(`confirm: ${await evaluate(`__clickText('Confirmer réception')`)}`);
await sleep(2000);
flow.push(`confirm toast: ${await evaluate(`document.querySelector('.toast')?.textContent ?? 'NO TOAST'`)}`);
await shoot('flow-3-confirmed');

await logout();
await login('nadia');
await goto('/gestion-depot/approvisionnement');
flow.push(`supply: ${await evaluate(`__clickText('Ajouter au stock')`)}`);
await sleep(2000);
flow.push(`supply toast: ${await evaluate(`document.querySelector('.toast')?.textContent ?? 'NO TOAST'`)}`);
await shoot('flow-4-supply');

await logout();
await login('mparany');
await goto('/admin/vue-ensemble');
flow.push(`admin CA = ${await evaluate(`document.querySelector('.content .kpi .value')?.textContent`)}`);
await shoot('flow-5-admin');

// -------------------------------------------------- 4. session expiry path
await evaluate(`fetch('/api/auth/logout', { method: 'POST', headers: { 'X-XSRF-TOKEN': document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1] } })`);
await sleep(800);
await evaluate(`document.querySelectorAll('nav.menu a')[1].click()`);
await sleep(2000);
flow.push(`after server-side logout, a navigation lands on ${await evaluate('location.pathname')}`);
await shoot('flow-6-session-expired');

console.log('=== PAGES ===');
for (const p of pages) {
  console.log(`${p.role.padEnd(8)} | ${String(p.menu).padEnd(26)} | ${String(p.title).padEnd(24)} | ` +
    `cards=${p.cards} rows=${String(p.rows).padStart(3)} kpis=${p.kpis.length} chars=${p.chars}`);
}
console.log('\n=== KPI SAMPLES ===');
for (const p of pages.filter((x) => x.kpis.length)) {
  console.log(`${p.role} / ${p.title}: ${p.kpis.join(' | ')}`);
}
console.log('\n=== FLOW ===');
flow.forEach((f) => console.log(' ', f));
console.log('\n=== PROBLEMS ===');
console.log(problems.length ? [...new Set(problems)].join('\n') : 'none');

ws.close();
