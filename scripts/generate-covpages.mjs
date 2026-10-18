import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';

const ROOT_DIR = process.cwd();

console.log('🚀 [1/4] Running Android Unit Tests & generating Jacoco Coverage...');
execSync('./gradlew :mucker-interceptor:createDebugUnitTestCoverageReport :mucker-mcp:createDebugUnitTestCoverageReport', {
  stdio: 'inherit',
  cwd: ROOT_DIR,
});

console.log('\n🎯 [2/4] Running Dart Unit Tests & collecting Coverage...');
execSync('dart test --coverage=coverage && dart run coverage:format_coverage --lcov --in=coverage --out=coverage/lcov.info --packages=.dart_tool/package_config.json --report-on=lib', {
  stdio: 'inherit',
  cwd: path.join(ROOT_DIR, 'dart/mucker'),
});

console.log('\n🔄 [3/4] Converting Jacoco XML to LCOV format...');
function jacocoToLcov(xmlPath, moduleDir) {
  if (!fs.existsSync(xmlPath)) {
    console.warn(`Warning: ${xmlPath} not found!`);
    return '';
  }
  const xml = fs.readFileSync(xmlPath, 'utf8');
  const packageRegex = /<package name=\"([^\"]+)\">([\s\S]*?)<\/package>/g;
  let pkgMatch;
  let out = '';

  while ((pkgMatch = packageRegex.exec(xml)) !== null) {
    const pkgName = pkgMatch[1];
    const pkgContent = pkgMatch[2];
    const sourceRegex = /<sourcefile name=\"([^\"]+)\">([\s\S]*?)<\/sourcefile>/g;
    let srcMatch;

    while ((srcMatch = sourceRegex.exec(pkgContent)) !== null) {
      const srcName = srcMatch[1];
      const srcContent = srcMatch[2];
      const relPath = moduleDir + '/src/main/java/' + pkgName + '/' + srcName;
      
      out += 'TN:\nSF:' + relPath + '\n';
      const lineRegex = /<line nr=\"(\d+)\"[^>]*?ci=\"(\d+)\"[^>]*?\/>/g;
      let lineMatch;
      let lf = 0;
      let lh = 0;

      while ((lineMatch = lineRegex.exec(srcContent)) !== null) {
        const nr = lineMatch[1];
        const ci = parseInt(lineMatch[2], 10);
        const hits = ci > 0 ? 1 : 0;
        out += 'DA:' + nr + ',' + hits + '\n';
        lf++;
        if (ci > 0) lh++;
      }

      out += 'LF:' + lf + '\nLH:' + lh + '\nend_of_record\n';
    }
  }
  return out;
}

const dartLcovPath = path.join(ROOT_DIR, 'dart/mucker/coverage/lcov.info');
const dartLcov = fs.existsSync(dartLcovPath) ? fs.readFileSync(dartLcovPath, 'utf8') : '';
const interceptorLcov = jacocoToLcov(
  path.join(ROOT_DIR, 'mucker-interceptor/build/reports/coverage/test/debug/report.xml'),
  'mucker-interceptor'
);
const mcpLcov = jacocoToLcov(
  path.join(ROOT_DIR, 'mucker-mcp/build/reports/coverage/test/debug/report.xml'),
  'mucker-mcp'
);

const combinedLcov = [dartLcov.trim(), interceptorLcov.trim(), mcpLcov.trim()]
  .filter(Boolean)
  .join('\n');

const coverageDir = path.join(ROOT_DIR, 'coverage');
fs.mkdirSync(coverageDir, { recursive: true });
fs.writeFileSync(path.join(coverageDir, 'lcov.info'), combinedLcov);
console.log(`Combined LCOV written to ${path.join(coverageDir, 'lcov.info')} (${combinedLcov.length} bytes)`);

console.log('\n📊 [4/4] Generating covpages static site with multi-module breakdown...');
const localCovpagesBin = '/Users/yongjhih/works/covpages/bin/covpages.js';
let covpagesCmd = 'npx covpages';
if (fs.existsSync(localCovpagesBin)) {
  covpagesCmd = `node ${localCovpagesBin}`;
}

execSync(
  `${covpagesCmd} generate --input coverage/lcov.info --output docs/covpages --title "Mucker Multi-Platform Test Coverage" --base-url "/mucker/covpages/" --mcp`,
  { stdio: 'inherit', cwd: ROOT_DIR }
);

console.log('\n✨ Coverage report successfully generated at docs/covpages/index.html');
