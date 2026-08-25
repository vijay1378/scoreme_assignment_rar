# Task 3 — CS-RAR

## Name

**Conflict-Saturation Resource-Aware Repacking (CS-RAR)**

## Core idea

Treat the conflict structure like graph coloring, but combine DSATUR-style task ordering with hard resource/SLA filtering, penalty-aware slot selection, and bounded local repair.

## Pseudocode

```text
CS-RAR(I):
  1. Validate I.
  2. Build conflict adjacency lists.
  3. Compute each task's statically eligible slots using its SLA window
     and individual resource fit.
  4. If a task has no eligible slot, return a sound infeasibility certificate.
  5. If total demand exceeds total capacity in any resource dimension,
     return a sound infeasibility certificate.
  6. While unassigned tasks remain:
       a. Select the task with the fewest currently legal slots;
          break ties by saturation, degree, urgency, then weight.
       b. Filter its candidate slots by conflicts, remaining capacities,
          and SLA.
       c. If candidates exist, score each candidate using incremental
          penalty plus small balance/fragmentation terms and choose the best.
       d. If no candidate exists, try moving at most one blocking assigned task
          and retry the blocked task.
       e. If repair fails, return construction failure rather than claiming
          global infeasibility.
  7. Independently verify F1, F2 and F3.
  8. Compute the final penalty and return the assignment.
```

## Why this structure

- DSATUR-style ordering addresses the graph-coloring component by handling constrained tasks early.
- Static eligibility removes slots that can never work for a task.
- Hard filtering prevents conflicts, capacity violations, and SLA violations from being traded against penalty.
- Penalty-aware scoring addresses the optimization objective.
- One-task repacking provides limited recovery without turning the algorithm into unrestricted exponential search.
- Independent verification prevents a scoring bug from producing an invalid reported solution.
