# Task 6 — Empirical Analysis and Benchmarking

## 1. Requirements

The assignment requires the algorithm to be run on 9 specified instances:
3 small instances for comparison with brute-force optimal, 3 medium instances,
and 3 stress instances. It also requires a structured results table, at least
two charts (penalty vs `n` and runtime vs `n`), empirical approximation ratios
for the small instances, and an explanation of every anomaly/failure.

## 2. Benchmark suite

| Group | n | K | Density | Seed |
|---|---:|---:|---:|---:|
| Small | 8 | 3 | 0.30 | 1 |
| Small | 10 | 4 | 0.40 | 2 |
| Small | 12 | 4 | 0.50 | 3 |
| Medium | 50 | 8 | 0.25 | 10 |
| Medium | 100 | 10 | 0.30 | 11 |
| Medium | 150 | 12 | 0.35 | 12 |
| Stress | 200 | 15 | 0.40 | 20 |
| Stress | 200 | 5 | 0.60 | 21 |
| Stress | 200 | 20 | 0.10 | 22 |

## 3. Results

| Instance | n | K | Density | Penalty | Runtime (ms) | Status | Brute-force optimum | Ratio |
|---|---:|---:|---:|---:|---:|---|---:|---:|
| small-8 | 8 | 3 | 0.30 | 47.242 | 17 | Feasible | 37.518 | 1.2592 |
| small-10 | 10 | 4 | 0.40 | 37.579 | 14 | Feasible | 37.579 | 1.0000 |
| small-12 | 12 | 4 | 0.50 | 79.839 | 12 | Feasible | 79.839 | 1.0000 |
| medium-50 | 50 | 8 | 0.25 | 277.475 | 50 | Infeasible | — | — |
| medium-100 | 100 | 10 | 0.30 | 166.531 | 54 | Infeasible | — | — |
| medium-150 | 150 | 12 | 0.35 | 0.000 | 54 | Infeasible | — | — |
| stress-200-K15 | 200 | 15 | 0.40 | 0.000 | 49 | Infeasible | — | — |
| stress-200-K5 | 200 | 5 | 0.60 | 0.000 | 75 | Infeasible | — | — |
| stress-200-K20 | 200 | 20 | 0.10 | 462.944 | 66 | Infeasible | — | — |

## 4. Small-instance analysis

**n=8, K=3, seed=1:** CS-RAR obtained **47.242**, while exhaustive search
obtained **37.518**. The empirical ratio is **1.2592**. This is the only
small case where the heuristic was not optimal.

**n=10, K=4, seed=2:** CS-RAR obtained **37.579**, matching the exhaustive
optimum **37.579** (floating-point equality), giving ratio **1.0000**.

**n=12, K=4, seed=3:** CS-RAR obtained **79.839**, matching the exhaustive
optimum **79.839**, giving ratio **1.0000**.

Thus, on the required small suite, CS-RAR was optimal on **2/3** cases and its
worst observed empirical ratio was **1.2592**.

## 5. Medium and stress analysis

- **n=50, K=8:** construction became blocked at `T42` after the allowed
  one-task repacking attempt. This is a heuristic construction failure, not
  a proof of global infeasibility.
- **n=100, K=10:** construction became blocked at `T3` after bounded
  repacking. Again, this is not a mathematical infeasibility certificate.
- **n=150, K=12:** the solver certified infeasibility because total demand
  exceeded total capacity in resource dimension 3.
- **n=200, K=15:** the same total-capacity certificate detected infeasibility
  in resource dimension 3.
- **n=200, K=5:** total demand exceeded total capacity in resource dimension 2,
  so infeasibility was certified.
- **n=200, K=20:** construction became blocked at `T63` after bounded repacking.
  This is a heuristic failure rather than a global infeasibility proof.

The distinction is important: the implementation deliberately does not label
a greedy construction failure as global infeasibility unless a sound
certificate exists.

## 6. Runtime

Measured Java solver runtimes were between **12 ms and 75 ms** for this suite.
The trend is broadly upward with problem size, but it is not strictly
monotonic because early infeasibility certificates can terminate the algorithm
before the full construction phase.

## 7. Penalty

For feasible instances, the penalty generally increases as the number of tasks
increases because the objective is a sum of task-level slot-delay and
quadratic SLA-boundary-risk terms. Infeasible instances are excluded from the
penalty-vs-`n` chart because they do not have a valid final assignment penalty.

## 8. Main findings

1. CS-RAR was optimal on **2 of 3** small benchmark instances.
2. Maximum observed small-instance ratio: **1.2592**.
3. Runtime remained in the millisecond range for all tested instances.
4. Large instances exposed both genuine resource infeasibility and heuristic
   construction failures.
5. The most visible heuristic limitation is the **one-task repacking bound**:
   some blocked states may require coordinated movement of multiple assigned
   tasks.

## 9. Input-format note

The supplied generator represents conflicts as integer pairs such as `(i,j)`,
while the Java implementation's JSON loader expects task IDs such as
`["Ti","Tj"]`. The benchmark harness normalized the generated pairs to task IDs
before invoking Java. This does not change the generated graph or algorithm;
it only reconciles the generator's conflict representation with the JSON
interface used by the implementation.

## 10. Files

- `task6_results.csv` — structured benchmark table
- `penalty_vs_n.png` — required penalty chart
- `runtime_vs_n.png` — required runtime chart
- `approx_ratio_small.png` — small-instance empirical ratio chart
