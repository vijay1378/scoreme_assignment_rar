# Task 7 — Design Journal

> **Personalization required before submission.** The assignment explicitly asks for your own words and personal observations. Replace any generic wording below with what you actually experienced while implementing and testing the algorithm.

## 1. Hardest design decision

The hardest design decision was the task-selection and repair strategy. I had to choose between a simple degree-first greedy order, a DSATUR-style constrained-task order, and deeper local repair. I chose DSATUR-style ordering because the conflict structure behaves like graph coloring, while saturation captures the current state better than static degree alone. I limited repair to one blocking task because unrestricted repacking could become expensive and undermine the polynomial-time heuristic objective.

## 2. Empirical failure

The Medium-50 instance (n=50, K=8, density=0.25, seed=10) became blocked at T42 after one-task repacking. Medium-100 similarly blocked at T3, and Stress-200-K20 blocked at T63. These are construction failures, not proofs of global infeasibility. With an additional week, I would add bounded two- or three-task neighborhood repair plus look-ahead to avoid decisions that leave a highly constrained task with no legal slot.

## 3. Production application

A natural ScoreMe production match is an OCR GPU cluster. OCR jobs compete for CPU, RAM, GPU and network resources and can have processing deadlines. Tasks that conflict because of shared GPU resources or other execution constraints can be modeled as graph edges. CS-RAR could prioritize constrained OCR jobs, filter GPU slots by resource/SLA feasibility, score candidates by delay and resource balance, and perform bounded repacking when a job becomes blocked.

## 4. What surprised me

The most important surprise was that problem size alone does not determine heuristic difficulty. The n=8 case had a worse empirical ratio than the n=10 and n=12 cases. The experiments also made the distinction between mathematical infeasibility and heuristic construction failure much clearer: getting stuck does not automatically mean no solution exists.
