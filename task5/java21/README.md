
# Task 5 — Java 17 CS-RAR

## Algorithm

**Conflict-Saturation Resource-Aware Repacking (CS-RAR)**

The implementation contains:

- SLA/resource static eligibility
- sound infeasibility certificates
- DSATUR-style constrained-task ordering
- Task 2 quadratic SLA-boundary penalty
- resource-aware slot selection
- bounded one-task repacking
- independent F1/F2/F3 verification
- required JSON output

No OR-Tools, PuLP, CPLEX, Gurobi, Z3, SAT solver, NetworkX, or other
optimization solver is used.

The JSON parser and serializer are implemented using only the Java standard
library, so there are no third-party dependencies.

## Java version

Java 17+.

Check:

```bash
java -version
javac -version
```

## Compile

```bash
javac -d out src/Main.java test/Task5Test.java
```

## Run required tests

```bash
java -ea -cp out Task5Test
```

Expected:

```text
All 4 Task 5 tests passed.
```

## Run solver

```bash
java -cp out Main input.json -o output.json
```

If `-o` is omitted, output is written to:

```text
output.json
```

## Input

The implementation follows the Section 5 generator structure:

```json
{
  "tasks": ["T0", "T1"],
  "conflicts": [["T0", "T1"]],
  "resources": [[4, 16, 0, 3], [2, 8, 0, 2]],
  "capacities": [[32, 128, 8, 6], [32, 128, 8, 6]],
  "windows": [[0, 1], [0, 1]],
  "weights": [5, 4],
  "K": 2
}
```

Slots are zero-based: `0 ... K-1`.

## Output

```json
{
  "assignment": {"T0": 0, "T1": 1},
  "penalty": 4.0,
  "runtime_ms": 1,
  "feasible": true,
  "violation_reason": ""
}
```

For infeasible/could-not-construct cases, `feasible` is false and
`violation_reason` explains the certificate or construction failure.

## Required tests

1. All-conflict graph where chromatic number > K
2. Zero-capacity slot
3. Tight SLA window
4. Single-task instance

All four are included in `Task5Test.java`.

## Important correctness point

A greedy construction failure is not automatically labeled as mathematical
global infeasibility. Global infeasibility is reported only for sound
certificates such as an oversized conflict clique, impossible individual
eligibility, or total resource demand exceeding total capacity. This is
necessary because the underlying feasibility problem is NP-hard.
