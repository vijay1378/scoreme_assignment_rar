# Task 4 — Feasibility / Approximation Analysis

## Important limitation

The current CS-RAR implementation is a heuristic. It can stop with a message such as “construction failed: no legal slot ... after one-task repacking” even when a feasible assignment may exist. Therefore a general theorem of the form “if a feasible assignment exists, CS-RAR always finds it” is **not valid for this implementation**, and a universal approximation ratio is not established.

## What can be proved

### Returned-solution feasibility

Every candidate slot is filtered against the three hard constraints:

1. For every already assigned conflicting neighbor, the candidate slot must differ. Therefore F1 cannot be violated by a returned assignment.
2. A candidate is accepted only when adding the task keeps every resource dimension within the slot capacity. Therefore F2 cannot be violated.
3. Static eligibility requires l_i <= s <= u_i. Therefore F3 cannot be violated.

After construction, the implementation independently verifies F1, F2 and F3 before marking the result feasible. Consequently, **every assignment reported as feasible by the implementation satisfies the formal feasibility constraints**.

### Sound infeasibility certificates

The implementation returns global infeasibility only for certificates such as:

- a task has no statically eligible slot;
- total demand exceeds total capacity in a resource dimension;
- a detected conflict clique is larger than K.

These are necessary conditions for feasibility, so their violation is sufficient to prove infeasibility.

## Approximation quality

No worst-case alpha bound is claimed. The Task 6 experiments instead measure empirical approximation ratios on the small instances using brute-force optimal solutions. The observed ratios were 1.2592, 1.0000 and 1.0000 for n=8,10,12 respectively. These are empirical observations, not a proof of a global approximation guarantee.
