// 从 repository2-1.xml 解析并下载指定 SDK 组件（绕过损坏的 sdkmanager）
const https = require('https');
const fs = require('fs');
const path = require('path');

const BASE = 'https://dl.google.com/android/repository/';
const XML = BASE + 'repository2-1.xml';
const OUT = 'D:/学习区/.toolchain/';
const WANT = ['platforms;android-35', 'build-tools;35.0.0'];

function get(u) {
  return new Promise((resolve, reject) => {
    https.get(u, { timeout: 60000 }, (res) => {
      if (res.statusCode === 301 || res.statusCode === 302) {
        res.resume();
        return resolve(get(res.headers.location));
      }
      if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode + ' ' + u));
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    }).on('error', reject);
  });
}

function blockFor(xml, pkgPath) {
  const key = 'path="' + pkgPath + '"';
  const i = xml.indexOf(key);
  if (i < 0) return null;
  const seg = xml.slice(i, i + 40000);
  const j = seg.indexOf('</remotePackage>');
  return seg.slice(0, j > 0 ? j : seg.length);
}

function firstUrl(block, mustContain) {
  const re = /<url>([^<]+)<\/url>/g;
  let m;
  while ((m = re.exec(block))) {
    if (!mustContain || m[1].includes(mustContain)) return m[1];
  }
  return null;
}

function download(url, file) {
  return new Promise((resolve, reject) => {
    const out = path.resolve(OUT + file);
    const f = fs.createWriteStream(out);
    https.get(url, { timeout: 120000, headers: { 'User-Agent': 'Mozilla/5.0' } }, (res) => {
      if (res.statusCode !== 200) { res.resume(); return reject(new Error('HTTP ' + res.statusCode + ' ' + url)); }
      const total = parseInt(res.headers['content-length'] || '0', 10);
      let got = 0;
      res.on('data', (c) => { got += c.length; if (total) process.stdout.write('\r' + file + ' ' + Math.floor(got / 1048576) + '/' + Math.floor(total / 1048576) + 'MB   '); });
      f.on('finish', () => { console.log('\nDONE ' + file); resolve(out); });
      f.on('error', reject);
      res.pipe(f);
    }).on('error', reject);
  });
}

(async () => {
  console.log('fetching ' + XML);
  const xml = (await get(XML)).toString('utf8');
  for (const pkg of WANT) {
    const block = blockFor(xml, pkg);
    if (!block) { console.log('NOT FOUND ' + pkg); continue; }
    const url = firstUrl(block, pkg.startsWith('build-tools') ? '-windows.zip' : '.zip');
    if (!url) { console.log('NO URL for ' + pkg); continue; }
    const file = url.split('/').pop();
    console.log(pkg + ' -> ' + BASE + url + '  (' + file + ')');
    await download(BASE + url, file);
  }
  console.log('all done');
})().catch((e) => { console.error('FAIL', e.message); process.exit(1); });
