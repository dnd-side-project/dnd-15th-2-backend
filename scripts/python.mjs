#!/usr/bin/env node

import { spawnSync } from "node:child_process";

const argumentsToPython = process.argv.slice(2);
if (argumentsToPython.length === 0) {
  console.error("Usage: node scripts/python.mjs <script> [arguments...]");
  process.exit(2);
}

const candidates = [
  process.env.MIRI_PYTHON,
  process.env.PYTHON,
  "python3",
  "python",
].filter(Boolean);

for (const candidate of [...new Set(candidates)]) {
  const result = spawnSync(candidate, argumentsToPython, {
    cwd: process.cwd(),
    stdio: "inherit",
  });
  if (result.error?.code === "ENOENT") {
    continue;
  }
  if (result.error) {
    console.error(`Unable to start ${candidate}: ${result.error.message}`);
    process.exit(1);
  }
  process.exit(result.status ?? 1);
}

console.error(
  "Python 3 was not found. Set MIRI_PYTHON or install Python with the Brewfile.",
);
process.exit(127);
