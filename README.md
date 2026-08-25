# ScoreMe Advanced Systems Design — CS-RAR

This repository organizes the work for Tasks 1–7 of the ScoreMe Advanced Systems Design assessment.

## Algorithm

**Conflict-Saturation Resource-Aware Repacking (CS-RAR)**

The implementation combines DSATUR-style task ordering, hard conflict/resource/SLA filtering, penalty-aware slot selection, bounded one-task repacking, and independent final verification.

The formal problem has four resource dimensions (CPU, RAM, GPU, Network), K processing slots, conflict graph G, resource requirements, per-slot capacities, priority weights, and SLA windows. The hard constraints are conflict avoidance, capacity, and SLA compliance.

## Repository

- `task1/` — NP-hardness reduction
- `task2/` — penalty function
- `task3/` — CS-RAR design and pseudocode
- `task4/` — feasibility/approximation analysis
- `task5/` — implementation and tests (Python + Java 21)
- `task6/` — benchmark report, CSV and charts
- `task7/` — design journal draft; personalize before submission

## Task 5 — Python

```bash
python run.py input.json -o output.json
```

Tests:

```bash
python -m unittest -v test_task5.py
```

## Task 5 — Java 21

```bash
javac -d out src/Main.java test/Task5Test.java
java -ea -cp out Task5Test
java -cp out Main input.json -o output.json
```

## Benchmark results

Task 6 reports empirical ratios of 1.2592, 1.0000 and 1.0000 for the required small instances (n=8,10,12). CS-RAR was optimal on 2/3 small cases. Larger benchmarks exposed both sound resource-capacity infeasibility and bounded-repair construction failures.

