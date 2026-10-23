// Tests des règles de la base sur l'émulateur local (projet « demo-crimping », jamais la production).
// Les outils et dépendances sont hors du dépôt : $CRIMPING_TOOLS, par défaut ~/dev-tools/crimping-pro
//   node run-rules-tests.mjs                                  → règles actuelles (database.rules.json)
//   node run-rules-tests.mjs --rules <fichier> --test <fichier> → autre jeu de règles et de tests
import { spawn } from 'node:child_process';
import { copyFileSync, existsSync } from 'node:fs';
import { homedir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const arg = name => {
  const i = process.argv.indexOf(name);
  return i > 0 ? process.argv[i + 1] : undefined;
};
const rulesFile = resolve(here, arg('--rules') || '../database.rules.json');
const testFile = resolve(here, arg('--test') || 'rules.test.mjs');

const tools = process.env.CRIMPING_TOOLS || join(homedir(), 'dev-tools', 'crimping-pro');
const runtime = join(tools, 'security-tests');
const java = join(tools, 'jdk-21', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
const jar = join(tools, 'firebase-database-emulator-4.11.2.jar');
for (const p of [java, jar, join(runtime, 'node_modules'), rulesFile, testFile]) {
  if (!existsSync(p)) {
    console.error('Introuvable : ' + p);
    process.exit(2);
  }
}

// Le fichier de test est copié à côté de ses dépendances (hors dépôt)
copyFileSync(testFile, join(runtime, basename(testFile)));

const emulator = spawn(java, ['-Duser.language=en', '-jar', jar, '--host', '127.0.0.1', '--port', '9000'],
  { stdio: 'ignore' });
const stop = () => { try { emulator.kill(); } catch { /* déjà arrêté */ } };
process.on('exit', stop);

let ok = false;
for (let i = 0; i < 120 && !ok; i++) {
  try {
    ok = (await fetch('http://127.0.0.1:9000/.json?ns=demo-crimping')).ok;
  } catch { /* pas encore prêt */ }
  if (!ok) await new Promise(r => setTimeout(r, 500));
}
if (!ok) {
  console.error("L'émulateur ne répond pas.");
  process.exit(3);
}

const run = spawn(process.execPath, ['--test', basename(testFile)], {
  cwd: runtime, stdio: 'inherit', env: { ...process.env, RULES_FILE: rulesFile }
});
run.on('exit', code => { stop(); process.exit(code ?? 1); });
