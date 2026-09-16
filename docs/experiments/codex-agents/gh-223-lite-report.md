# GH-223 lite comparison report

- status: `PASS` (approved lite evaluation scope; result commit draft approved by the user)
- issue_number: `223`; task_id: `GH-223-INSTRUCTION-ARCHITECTURE-PHASE2`; design_id: `HARNESS-DESIGN-GH-223-001`
- plan_id: `GH-223-TOKEN-COMPARISON-LITE-001`
- Evidence base commit: `87cd509`; model: `gpt-5.6-sol/high`; CLI: `0.153.4`

## Findings

B3 has the lowest equal-task mean input, uncached input, output, and elapsed time in these 24 observations. This is an observed average, not a universal winner: B0 uses the least input on L1, B3 on L2, and B2 on L3. B3’s L3 input and uncached input vary substantially between repetitions. B1 has the lowest first-input mean.

All 24 executor-reviewed answers meet the three predefined checklist items. The checklist establishes limited instruction-reporting coverage, not identical answer depth or implementation quality. The [final independent review](gh-223-lite-verification.md) passed.

## Execution and accounting

Exactly 24 comparison parent sessions completed; 0 invalid sessions, 0 retries, 0 children. Model process elapsed sums to 3806.055 seconds (63 minutes 26 seconds). Total input: 8,685,408, including 7,438,208 cached; uncached input: 1,247,200; output: 149,349. Input plus output: 8,834,757. Cache is already part of input; reasoning output is already part of output. These sums exclude pilot and coordinator/reviewer model usage and orchestration wall time.

The Task 9 rough input projection was 6.09–6.58 million; observed input was 8.69 million. Its average-based uncached projection was 1.42 million versus 1.25 million observed. Additional read/tool turns and cache variation explain why input totals alone are not a monetary cost estimate. No price-weighted cost is claimed.

The two valid pilots and one historical invalid pilot remain separate. There were 27 actual evaluation/pilot parent attempts across Task 9–10, of which exactly 24 belong to this comparison.

## Candidate means with equal task weights

Each task contributes one third; each candidate/task contains two observations. Metrics remain separate.

| Candidate | First input | Final input | Cached input | Uncached input | Output | Elapsed seconds |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| B0 | 23,567.0 | 436,539.3 | 378,944.0 | 57,595.3 | 6,227.8 | 168.1 |
| B1 | 20,612.3 | 429,737.8 | 376,938.7 | 52,799.2 | 6,845.7 | 166.9 |
| B2 | 20,809.0 | 334,519.5 | 284,458.7 | 50,060.8 | 6,117.8 | 169.9 |
| B3 | 20,970.0 | 246,771.3 | 199,360.0 | 47,411.3 | 5,700.2 | 129.5 |

### Mean differences

Percentage change = (candidate mean / reference mean − 1) × 100. Negative is a lower observed value. These are descriptive comparisons, not significance tests.

| Comparison | First input | Final input | Cached input | Uncached input | Output | Elapsed |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| B1 vs B0 | -12.5% | -1.6% | -0.5% | -8.3% | +9.9% | -0.7% |
| B2 vs B0 | -11.7% | -23.4% | -24.9% | -13.1% | -1.8% | +1.1% |
| B3 vs B0 | -11.0% | -43.5% | -47.4% | -17.7% | -8.5% | -23.0% |
| B2 vs B1 | +1.0% | -22.2% | -24.5% | -5.2% | -10.6% | +1.8% |
| B3 vs B2 | +0.8% | -26.2% | -29.9% | -5.3% | -6.8% | -23.8% |

## Task means and ranges

| Task / candidate | Input mean [min, max] | Uncached mean [min, max] | Output mean [min, max] | Elapsed mean [min, max] |
| --- | ---: | ---: | ---: | ---: |
| L1/B0 | 137,516.5 [123,581.0, 151,452.0] | 34,540.5 [33,085.0, 35,996.0] | 3,810.0 [3,132.0, 4,488.0] | 86.6 [77.0, 96.1] |
| L1/B1 | 154,569.5 [137,807.0, 171,332.0] | 36,425.5 [36,036.0, 36,815.0] | 4,218.5 [4,211.0, 4,226.0] | 99.4 [99.3, 99.5] |
| L1/B2 | 222,461.5 [203,483.0, 241,440.0] | 50,365.5 [31,392.0, 69,339.0] | 4,548.5 [3,648.0, 5,449.0] | 187.5 [79.5, 295.5] |
| L1/B3 | 239,927.0 [222,954.0, 256,900.0] | 36,407.0 [34,692.0, 38,122.0] | 5,547.0 [4,671.0, 6,423.0] | 125.8 [97.7, 153.8] |
| L2/B0 | 815,823.5 [528,983.0, 1,102,664.0] | 79,951.5 [70,615.0, 89,288.0] | 7,866.0 [7,069.0, 8,663.0] | 191.8 [164.9, 218.7] |
| L2/B1 | 564,186.5 [412,006.0, 716,367.0] | 75,098.5 [68,198.0, 81,999.0] | 8,010.0 [7,427.0, 8,593.0] | 206.2 [205.8, 206.7] |
| L2/B2 | 565,800.0 [560,743.0, 570,857.0] | 65,000.0 [57,959.0, 72,041.0] | 7,878.0 [7,592.0, 8,164.0] | 186.8 [184.9, 188.7] |
| L2/B3 | 184,676.0 [170,515.0, 198,837.0] | 31,524.0 [26,165.0, 36,883.0] | 4,987.5 [4,623.0, 5,352.0] | 110.2 [100.4, 120.0] |
| L3/B0 | 356,278.0 [336,717.0, 375,839.0] | 58,294.0 [50,637.0, 65,951.0] | 7,007.5 [6,513.0, 7,502.0] | 225.9 [172.5, 279.3] |
| L3/B1 | 570,457.5 [565,605.0, 575,310.0] | 46,873.5 [43,877.0, 49,870.0] | 8,308.5 [8,289.0, 8,328.0] | 195.2 [192.4, 197.9] |
| L3/B2 | 215,297.0 [214,449.0, 216,145.0] | 34,817.0 [34,641.0, 34,993.0] | 5,927.0 [5,344.0, 6,510.0] | 135.3 [126.4, 144.2] |
| L3/B3 | 315,711.0 [230,138.0, 401,284.0] | 74,303.0 [54,660.0, 93,946.0] | 6,566.0 [5,652.0, 7,480.0] | 152.4 [122.8, 181.9] |

### Task-specific input differences

| Task | B1 vs B0 | B2 vs B0 | B3 vs B0 | B2 vs B1 | B3 vs B2 |
| --- | ---: | ---: | ---: | ---: | ---: |
| L1 | +12.4% | +61.8% | +74.5% | +43.9% | +7.9% |
| L2 | -30.8% | -30.6% | -77.4% | +0.3% | -67.4% |
| L3 | +60.1% | -39.6% | -11.4% | -62.3% | +46.6% |

### Other task-specific metric differences

Each metric is compared independently; cached input changes do not alone imply a cost improvement.

| Task / comparison | First input | Cached input | Uncached input | Output | Elapsed |
| --- | ---: | ---: | ---: | ---: | ---: |
| L1 / B1 vs B0 | -12.6% | +14.7% | +5.5% | +10.7% | +14.8% |
| L1 / B2 vs B0 | -11.7% | +67.1% | +45.8% | +19.4% | +116.6% |
| L1 / B3 vs B0 | -11.0% | +97.6% | +5.4% | +45.6% | +45.3% |
| L1 / B2 vs B1 | +1.0% | +45.7% | +38.3% | +7.8% | +88.7% |
| L1 / B3 vs B2 | +0.8% | +18.3% | -27.7% | +22.0% | -32.9% |
| L2 / B1 vs B0 | -12.5% | -33.5% | -6.1% | +1.8% | +7.5% |
| L2 / B2 vs B0 | -11.7% | -31.9% | -18.7% | +0.2% | -2.6% |
| L2 / B3 vs B0 | -11.0% | -79.2% | -60.6% | -36.6% | -42.5% |
| L2 / B2 vs B1 | +0.9% | +2.4% | -13.4% | -1.6% | -9.4% |
| L2 / B3 vs B2 | +0.8% | -69.4% | -51.5% | -36.7% | -41.0% |
| L3 / B1 vs B0 | -12.5% | +75.7% | -19.6% | +18.6% | -13.6% |
| L3 / B2 vs B0 | -11.7% | -39.4% | -40.3% | -15.4% | -40.1% |
| L3 / B3 vs B0 | -11.0% | -19.0% | +27.5% | -6.3% | -32.6% |
| L3 / B2 vs B1 | +0.9% | -65.5% | -25.7% | -28.7% | -30.7% |
| L3 / B3 vs B2 | +0.8% | +33.8% | +113.4% | +10.8% | +12.6% |

## Both observations

First input/cache, full provenance, summary hashes, tool counts, and checklist judgments are retained in [the 24-run CSV](gh-223-lite-runs.csv). The following values are child-inclusive; all child counts are zero.

| Run | Input | Cached | Uncached | Output | Seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| r1-l1-b0 | 123,581.0 | 90,496.0 | 33,085.0 | 3,132.0 | 77.0 |
| r1-l1-b1 | 171,332.0 | 135,296.0 | 36,036.0 | 4,226.0 | 99.3 |
| r1-l1-b2 | 241,440.0 | 210,048.0 | 31,392.0 | 5,449.0 | 295.5 |
| r1-l1-b3 | 222,954.0 | 184,832.0 | 38,122.0 | 4,671.0 | 97.7 |
| r1-l2-b0 | 528,983.0 | 458,368.0 | 70,615.0 | 7,069.0 | 164.9 |
| r1-l2-b1 | 412,006.0 | 343,808.0 | 68,198.0 | 7,427.0 | 206.7 |
| r1-l2-b2 | 560,743.0 | 502,784.0 | 57,959.0 | 7,592.0 | 188.7 |
| r1-l2-b3 | 170,515.0 | 133,632.0 | 36,883.0 | 5,352.0 | 120.0 |
| r1-l3-b0 | 336,717.0 | 286,080.0 | 50,637.0 | 7,502.0 | 172.5 |
| r1-l3-b1 | 575,310.0 | 525,440.0 | 49,870.0 | 8,289.0 | 197.9 |
| r1-l3-b2 | 214,449.0 | 179,456.0 | 34,993.0 | 5,344.0 | 126.4 |
| r1-l3-b3 | 401,284.0 | 346,624.0 | 54,660.0 | 7,480.0 | 181.9 |
| r2-l1-b0 | 151,452.0 | 115,456.0 | 35,996.0 | 4,488.0 | 96.1 |
| r2-l1-b1 | 137,807.0 | 100,992.0 | 36,815.0 | 4,211.0 | 99.5 |
| r2-l1-b2 | 203,483.0 | 134,144.0 | 69,339.0 | 3,648.0 | 79.5 |
| r2-l1-b3 | 256,900.0 | 222,208.0 | 34,692.0 | 6,423.0 | 153.8 |
| r2-l2-b0 | 1,102,664.0 | 1,013,376.0 | 89,288.0 | 8,663.0 | 218.7 |
| r2-l2-b1 | 716,367.0 | 634,368.0 | 81,999.0 | 8,593.0 | 205.8 |
| r2-l2-b2 | 570,857.0 | 498,816.0 | 72,041.0 | 8,164.0 | 184.9 |
| r2-l2-b3 | 198,837.0 | 172,672.0 | 26,165.0 | 4,623.0 | 100.4 |
| r2-l3-b0 | 375,839.0 | 309,888.0 | 65,951.0 | 6,513.0 | 279.3 |
| r2-l3-b1 | 565,605.0 | 521,728.0 | 43,877.0 | 8,328.0 | 192.4 |
| r2-l3-b2 | 216,145.0 | 181,504.0 | 34,641.0 | 6,510.0 | 144.2 |
| r2-l3-b3 | 230,138.0 | 136,192.0 | 93,946.0 | 5,652.0 | 122.8 |

## Limits and provenance

- Only two repetitions per task/candidate. No statistical significance, safety probability, universal optimum, or actual implementation-quality claim. Equal task weights describe this three-task sample; a different workload mix may change the preferred candidate.
- L1 input ordering changes between repetitions for B0/B1 and B2/B3. L2 B0/B1 ordering changes; B1/B2 means differ by only about 0.3%. Treat these small or unstable differences as inconclusive. L3 input ordering is stable in this sample, but B3 has pronounced cache/uncached variation.
- L2 and L3 start at repository root and explicitly read nested guidance; they do not directly measure automatic nested-cwd loading. Date rolled from September 11 to 12 during repetition 2; the frozen manifest and runtime checks remained unchanged.
- Runtime fingerprint covers model/effort/CLI, base instructions, and the serialized dynamic supplement. Complete builtin/MCP/plugin catalogs remain unavailable. Child-role coverage remains unobserved because no evaluated session spawned children.
- Instruction-token attribution is auxiliary and UNAVAILABLE. Actual usage is measured; do not equate it to repository-instruction token size.
- Evaluator summaries preserve their original UNAVAILABLE answer fields. Separate executor judgments are bound to each immutable summary hash in the CSV; independent verification must inspect that evidence instead of treating the evaluator as an automatic semantic grader.
- Read-only GitHub lookup failure in r1-l1-b1 was disclosed in its answer. External-read behavior and variable read counts are retained as observed, not erased or retried.
- No actual Gradle/test/Terraform/AWS execution or mutation was observed in comparison sessions. Full safety/smoke evaluation and the 204-session plan remain paused, with original artifacts retained.
- Raw evidence remains outside repositories with restricted permissions; public artifacts contain logical run identifiers, hashes, and non-sensitive metrics only.
- Frozen environment file SHA-256: `c37190afd3ea0503e7ad2fc2afb7440412d4daab2748da622b8f655965b0e0c9`; order SHA-256: `2c0050d7f0247c7e010c89b99b4a05b5ed3d7611fffc2e5167828e2bc3f11308`.

## Verification status

Task 10 aggregate is COMPLETE with 24 runs and no problems. Final independent evidence review and the Task 12 repository validation bundle passed; details are recorded in the [verification report](gh-223-lite-verification.md). Final evidence commits require their own concrete draft approval; push/PR/merge remain outside this run.
