import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { launchBrowser } from '../e2e/support.js';

// README screenshots from a running Ruko (the deploy image or spring-boot:run with a PWA build):
//   RUKO_URL=http://localhost:8080 npm run screenshots
// Phone-sized (390 × 844 at 2×), light mode, demo messages only. Writes docs/screenshots/*.png.

const BASE = (process.env.RUKO_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
const OUT = fileURLToPath(new URL('../../docs/screenshots/', import.meta.url));

const HINDI = 'गारंटी के साथ हर महीने 20% मुनाफा, कोई नुकसान नहीं। VIP ग्रुप की फीस ₹4999 इस UPI पर भेजें: laxmi.trade@ybl। '
  + 'हम SEBI registered हैं, Reg No INH000012345। सिर्फ आज!';
const ENGLISH = 'Guaranteed 5% daily returns on your investment! Join our VIP group for just Rs 4999. '
  + 'Pay to vipprofits@okaxis and send screenshot. Only 10 seats left today.';

mkdirSync(OUT, { recursive: true });
const browser = await launchBrowser();

async function phone(lang) {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, colorScheme: 'light', serviceWorkers: 'allow',
  });
  await context.addInitScript((value) => localStorage.setItem('ruko.lang', value), lang);
  const page = await context.newPage();
  await page.goto(BASE);
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.evaluate(() => document.fonts.ready);
  return { context, page };
}

async function shot(page, name) {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${OUT}${name}.png` });
  console.log(`docs/screenshots/${name}.png`);
}

async function check(page, text, button) {
  await page.locator('textarea').fill(text);
  await page.getByRole('button', { name: button, exact: true }).click();
  await page.waitForURL('**/result');
}

{
  const { context, page } = await phone('hi');
  await page.locator('textarea').fill(HINDI);
  await shot(page, 'home');
  await check(page, HINDI, 'जाँचें');
  await shot(page, 'result');
  await page.locator('h2', { hasText: 'ख़तरे के निशान' }).evaluate((heading) => {
    window.scrollTo(0, heading.getBoundingClientRect().top + window.scrollY - 110);
  });
  await shot(page, 'flags');
  await page.goto(`${BASE}/recovery`);
  await shot(page, 'recovery');
  await page.goto(`${BASE}/journal`);
  await shot(page, 'journal');
  await page.goto(`${BASE}/how-ruko-decides`);
  await page.locator('h2').first().waitFor();
  await shot(page, 'how-ruko-decides');

  await context.setOffline(true);
  await page.goto(BASE);
  await check(page, HINDI, 'जाँचें');
  await page.locator('.notice').first().waitFor();
  await shot(page, 'offline');
  await context.close();
}

{
  const { context, page } = await phone('en');
  await check(page, ENGLISH, 'Check');
  await shot(page, 'result-en');
  await context.close();
}

await browser.close();
