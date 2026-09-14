const fs = require('fs');
const xml = fs.readFileSync('D:/学习区/.toolchain/repository2-1.xml', 'utf8');
const WANT = ['platforms;android-35', 'build-tools;35.0.0'];
for (const pkg of WANT) {
  console.log('==== ' + pkg);
  let from = 0;
  while (true) {
    const key = 'path="' + pkg + '"';
    const i = xml.indexOf(key, from);
    if (i < 0) break;
    const seg = xml.slice(i, i + 80000);
    const j = seg.indexOf('</remotePackage>');
    const block = seg.slice(0, j > 0 ? j : seg.length);
    const re = /<url>([^<]+)<\/url>/g;
    let m;
    while ((m = re.exec(block))) console.log('   url:', m[1]);
    from = i + 1;
  }
}
