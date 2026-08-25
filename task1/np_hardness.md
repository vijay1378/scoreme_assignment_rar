# Task 1 — NP-Hardness of the Scheduling Problem

## Reduction source

Reduce from **Graph k-Coloring**, which is NP-complete when k is part of the input.

## Construction

Given a graph G=(V,E) and integer k, create one scheduling task t_v for every vertex v and k processing slots. For every edge (u,v), add a conflict between t_u and t_v. Give every task the same resource requirement vector q and every slot the same capacity vector C, choosing q <= C so that resource feasibility is automatic. Give every task the common SLA window [0,k-1]. Set all weights to 1.

The construction is polynomial: it creates |V| tasks, |E| conflict pairs, k slots, and constant-size resource/window data per task/slot.

## Completeness

If G has a proper k-coloring c:V->{0,...,k-1}, assign scheduling task t_v to slot c(v). For every graph edge (u,v), c(u) != c(v), so the conflict constraint is satisfied. Resource demand fits in every slot by construction, and every slot is inside every task's SLA window. Hence the scheduling instance is feasible.

## Feasibility-preserving direction

If the scheduling instance has a feasible assignment sigma, define c(v)=sigma(t_v). Because every graph edge was converted into a scheduling conflict, feasibility requires sigma(t_u) != sigma(t_v) for every (u,v) in E. Therefore c is a proper k-coloring of G.

Thus G is k-colorable iff the constructed scheduling instance is feasible. The reduction is polynomial, so the decision version of the scheduling problem is NP-hard.

## Note on the compound constraints

The reduction preserves all three constraint families: conflict avoidance carries the graph-coloring structure, while resource vectors/capacities and SLA windows are deliberately constructed as non-binding but valid constraints. This establishes hardness of the full problem even when those two families are easy. A stronger reduction could make resource and SLA constraints encode additional combinatorial structure, but that is not necessary to establish NP-hardness of the general problem.
