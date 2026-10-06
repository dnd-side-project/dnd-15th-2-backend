#!/usr/bin/env node

import { spawnSync } from "node:child_process";

const argumentsToPython = process.argv.slice(2);
if (argumentsToPython.length === 0) {
  console.error("Usage: node scripts/python.mjs <script> [arguments...]");
  process.exit(2);
}

// scripts/find-python.sh와 같은 순서다. Windows의 python3 실행 별칭(Store 안내)은 실행은 되지만
// Python이 아니므로, 존재 여부가 아니라 Python 3 코드를 실제로 실행하는지로 후보를 고른다.
const probe = "import sys; sys.exit(0 if sys.version_info[0] == 3 else 1)";
const runsPython3 = ([command, ...prefix]) => {
  const check = spawnSync(command, [...prefix, "-c", probe], { stdio: "ignore" });
  return !check.error && check.status === 0;
};

// 명시한 QELLO_PYTHON이 동작하지 않으면 다른 인터프리터로 넘어가지 않고 실패한다(find-python.sh와 같다).
if (process.env.QELLO_PYTHON && !runsPython3([process.env.QELLO_PYTHON])) {
  console.error("QELLO_PYTHON does not point to a working Python 3 interpreter.");
  process.exit(127);
}

const candidates = [
  [process.env.QELLO_PYTHON],
  [process.env.PYTHON],
  ["python3"],
  ["python"],
  ["py", "-3"],
].filter(([command]) => Boolean(command));

for (const [command, ...prefix] of candidates) {
  if (!runsPython3([command, ...prefix])) {
    continue;
  }
  const result = spawnSync(command, [...prefix, ...argumentsToPython], {
    cwd: process.cwd(),
    stdio: "inherit",
  });
  if (result.error) {
    console.error(`Unable to start ${command}: ${result.error.message}`);
    process.exit(1);
  }
  process.exit(result.status ?? 1);
}

console.error(
  "Python 3 was not found. Set QELLO_PYTHON or install Python 3 (macOS: Brewfile).",
);
process.exit(127);
