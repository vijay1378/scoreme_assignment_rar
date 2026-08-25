# Task 5 — CS-RAR Implementation

## Algorithm

**Conflict-Saturation Resource-Aware Repacking (CS-RAR)**

The implementation follows the Task 3 design:

1. Static eligibility from SLA window + individual resource fit.
2. Sound infeasibility certificates:
   - task has no eligible slot,
   - total demand exceeds total capacity,
   - detected conflict clique is larger than `K`.
3. DSATUR-style task ordering:
   - fewest current legal slots,
   - highest saturation,
   - highest degree,
   - domain-aware urgency,
   - highest priority weight.
4. Slot selection minimizes the Task 2 penalty contribution plus small
   resource-balance/fragmentation terms.
5. If a task is blocked, at most one blocking task is repacked.
6. Every returned solution is independently verified for F1, F2 and F3.

A greedy construction failure is **not** called global infeasibility unless a
sound certificate exists.

## Input

The program accepts the exact JSON structure produced by the assignment's
Section 5 generator:

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

The provided generator uses zero-based slots `0..K-1`, so the implementation
preserves that convention.

## Output

The output JSON contains exactly:

```json
{
  "assignment": {"T0": 0, "T1": 1},
  "penalty": 4.0,
  "runtime_ms": 0,
  "feasible": true,
  "violation_reason": ""
}
```

When infeasible, `feasible` is `false` and `violation_reason` contains the
reason. `assignment` may contain the partial assignment when construction
fails after the initial certificates.

## Run

```bash
python run.py input.json -o output.json
```

## Tests

```bash
python -m unittest -v test_task5.py
```

The required Task 5 cases are included:

- all-conflict graph with chromatic number > K
- zero-capacity slot
- tight SLA window
- single-task instance

No forbidden optimization, SAT, graph-coloring, or solver library is used.
Only the Python standard library is required.

## Important submission note

The assignment document says "Implement ... in Java" in the section heading,
but the actual Task 5 requirement immediately permits **Python 3.10+** and says
Java 17+ requires prior written approval. This submission uses Python 3.10+.
