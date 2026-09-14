// Minimal HTTPS downloader (Node built-in), follows redirects manually.
// Usage: node dl.js <url> <outFile>
const https = require('https');
const http = require('http');
const fs = require('fs');
const path = require('path');

const [url, out] = process.argv.slice(2);
if (!url || !out) { console.error('usage: node dl.js <url> <outFile>'); process.exit(2); }

function get(u, redirects) {
  return new Promise((resolve, reject) => {
    const mod = u.startsWith('https') ? https : http;
    const req = mod.get(u, { timeout: 120000, headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        if (redirects > 10) return reject(new Error('too many redirects'));
        res.resume();
        return resolve(get(new URL(res.headers.location, u).toString(), redirects + 1));
      }
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error('HTTP ' + res.statusCode + ' for ' + u));
      }
      resolve(res);
    });
    req.on('timeout', () => { req.destroy(new Error('timeout ' + u)); });
    req.on('error', reject);
  });
}

(async () => {
  const res = await get(url, 0);
  const total = parseInt(res.headers['content-length'] || '0', 10);
  fs.mkdirSync(path.dirname(path.resolve(out)), { recursive: true });
  const f = fs.createWriteStream(out);
  let got = 0;
  res.on('data', (c) => { got += c.length; if (total) process.stdout.write('\r' + url.split('/')[2] + ' ' + Math.floor(got / 1048576) + '/' + Math.floor(total / 1048576) + ' MB'); });
  await new Promise((resolve, reject) => { f.on('finish', resolve); f.on('error', reject); res.pipe(f); });
  console.log('\nDONE ' + out + ' bytes=' + got);
})().catch((e) => { console.error('FAIL ' + e.message); process.exit(1); });
