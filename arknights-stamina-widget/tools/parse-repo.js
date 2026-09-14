const fs = require('fs');
const xml = fs.readFileSync('D:/学习区/.toolchain/repository2-1.xml', 'utf8');
const WANT = ['platforms;android-35', 'build-tools;35.0.0'];
for (const pkg of WANT) {
  const key = 'path="' + pkg + '"';
  const i = xml.indexOf(key);
  if (i < 0) { console.log('NOT FOUND', pkg); continue; }
  const seg = xml.slice(i, i + 60000);
  const j = seg.indexOf('</remotePackage>');
  const block = seg.slice(0, j > 0 ? j : seg.length);
  const re = /<url>([^<]+)<\/url>/g;
  let m, u = [];
  while ((m = re.exec(block))) u.push(m[1]);
  const win = u.filter(x => x.includes('-windows'));
  const any = u.find(x => x.endsWith('.zip'));
  console.log(pkg, '=>', win.length ? win[win.length - 1] : any);
}
