#!/usr/bin/env node
// Offline source oracle only. No app bootstrap, UniApp API, BLE, network or credentials.
import fs from 'node:fs';
import crypto from 'node:crypto';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const [sourcePath, outputPath] = process.argv.slice(2);
assert(sourcePath && outputPath, 'Usage: node script.mjs <legacy-app-service.js> <evidence.json>');
const source = fs.readFileSync(sourcePath, 'utf8');
const sha256 = crypto.createHash('sha256').update(source).digest('hex');
assert.equal(sha256, '1451bddc95735fdc86b071aef98d9d5b88add12b0b3612c66f666faca7572ddc', 'Legacy source changed; re-audit before execution');
const names = ['setBleConPwd', 'ten2Hex', 'hexStringToArrayBuffer'];
const locations = [];
const coffeeStart = source.indexOf('          setBleConPwd: function');
const scaleStart = source.indexOf('          ScaleBleWrite: function', coffeeStart);
assert(coffeeStart >= 0 && scaleStart > coffeeStart, 'Coffee/scale boundary missing');
const coffeeSource = source.slice(coffeeStart, scaleStart);
const methods = names.map(name => {
  const matches = [...coffeeSource.matchAll(new RegExp(`          ${name}: function \\([^]*?\\n          },`, 'g'))];
  assert.equal(matches.length, 1, `Ambiguous function ${name}`);
  const match = matches[0];
  locations.push({symbol: name, characterOffset: coffeeStart + match.index, line: source.slice(0, coffeeStart + match.index).split('\n').length});
  return match[0].trim().slice(0, -1);
});
const data = {pwd: []};
let writes = 0;
const context = vm.createContext({data, getApp: () => ({globalData: data}), e: () => {}});
// Compile and execute just these three fixed-source methods. Never execute the bundle.
vm.runInContext(`var candidate = ({${methods.join(',')}});`, context, {timeout: 1000});
context.verify = (hex, length) => {
  const actual = new Uint8Array(context.candidate.hexStringToArrayBuffer(hex, length));
  assert.equal(length, 9);
  assert.equal(hex.length, 16); // Original encoder emits eight bytes; allocation supplies the ninth zero.
  assert.deepEqual([...actual], [11, 6, ...data.pwd.map(Number), 0]);
  writes++;
};
vm.runInContext(`
  candidate.BleWrite = verify;
  for (var n = 0; n < 1000000; n++) {
    var digits = String(n).padStart(6, '0').split('');
    data.pwd = digits;
    candidate.setBleConPwd();
    data.pwd = digits.map(Number);
    candidate.setBleConPwd();
  }
`, context, {timeout: 120000});
assert.equal(writes, 2000000);
const evidence = {
  source: 'hoyi-project/app/assets/apps/__UNI__7D80DAB/www/app-service.js', sha256,
  kind: 'offline execution of three fixed-source functions, never app/BLE execution', locations,
  checks: writes, sixDigitPasswords: 1000000, representations: ['six one-character strings', 'six numeric digits'],
  encodedHexBytes: 8, requestedBufferBytes: 9, resultingBufferBytes: 9, trailingByte: 0,
  wireShape: '0B 06 d0 d1 d2 d3 d4 d5 00; each digit is numeric 0..9, not ASCII',
  symbolicOccurrencesInAppService: [...source.matchAll(/setBleConPwd/g)].length,
  limitations: [
    'Only well-formed six decimal digits tested; no claim of original input validation',
    'Nine-byte candidate and zero padding established, not device acceptance or command semantics',
    'Symbol search does not exclude dynamic, remote or other-version invocation',
    'No captured modification, readback, new/old password reconnect or failed-operation recovery',
    'Native encoder/API/dispatch remains absent and outbound guard continues rejecting opcode 0x0B'
  ]
};
fs.writeFileSync(outputPath, JSON.stringify(evidence, null, 2) + '\n');
console.log(`LEGACY_PASSWORD_CANDIDATE_CHECKS_PASSED checks=${writes} encodedBytes=8 bufferBytes=9 trailingZero=true noBle=true`);
