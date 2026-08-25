#!/usr/bin/env python3
"""
CS-RAR: Conflict-Saturation Resource-Aware Repacking.

Task 5 implementation for the ScoreMe credit-pipeline scheduling assignment.
Only the Python standard library is used.
"""

from __future__ import annotations

import argparse
import json
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

EPS = 1e-9
LAMBDA_SLA = 1.0
BALANCE_WEIGHT = 0.02
FRAGMENTATION_WEIGHT = 0.01


@dataclass(frozen=True)
class Task:
    """Immutable task model; separating input data from solver state avoids accidental mutation."""

    task_id: str
    resource: tuple[float, ...]
    lower: int
    upper: int
    weight: float


class Instance:
    """Normalized scheduling instance used by the heuristic for constant-format internal access."""

    def __init__(
        self,
        tasks: list[Task],
        conflicts: list[tuple[str, str]],
        capacities: list[tuple[float, ...]],
        k: int,
    ) -> None:
        self.tasks = tasks
        self.task_by_id = {task.task_id: task for task in tasks}
        self.conflicts = conflicts
        self.capacities = capacities
        self.k = k
        self.d = len(capacities[0]) if capacities else (
            len(tasks[0].resource) if tasks else 4
        )

        self.neighbors: dict[str, set[str]] = {
            task.task_id: set() for task in tasks
        }
        for a, b in conflicts:
            self.neighbors[a].add(b)
            self.neighbors[b].add(a)


class CSRAR:
    """
    Conflict-Saturation Resource-Aware Repacking solver.

    The construction phase follows a DSATUR-style constrained-task ordering,
    then chooses a slot using the Task 2 penalty plus small resource-balance
    and fragmentation tie-break terms. If a task becomes blocked, at most
    one already assigned blocking task is moved once.
    """

    def __init__(self, instance: Instance, lambda_sla: float = LAMBDA_SLA) -> None:
        self.ins = instance
        self.lambda_sla = lambda_sla
        self.tasks = instance.tasks
        self.task_by_id = instance.task_by_id
        self.k = instance.k
        self.d = instance.d
        self.neighbors = instance.neighbors

        self.eligible: dict[str, list[int]] = {}
        self.assignment: dict[str, int] = {}
        self.load: list[list[float]] = [
            [0.0 for _ in range(self.d)] for _ in range(self.k)
        ]

    def build_static_eligibility(self) -> None:
        """Remove slots that violate a task's SLA or individual resource fit before graph search."""
        for task in self.tasks:
            candidates = []
            for slot in range(self.k):
                if (
                    task.lower <= slot <= task.upper
                    and self._fits_vector(
                        task.resource, self.ins.capacities[slot]
                    )
                ):
                    candidates.append(slot)
            self.eligible[task.task_id] = candidates

    @staticmethod
    def _fits_vector(
        requirement: tuple[float, ...],
        capacity: tuple[float, ...],
    ) -> bool:
        """Check every resource dimension because CPU, RAM, GPU, and network are all hard constraints."""
        return all(
            req <= cap + EPS
            for req, cap in zip(requirement, capacity)
        )

    def certified_infeasibility(self) -> str | None:
        """
        Return only mathematically sound infeasibility certificates.

        A failed greedy construction is deliberately not treated as proof of
        global infeasibility because the decision problem is NP-hard.
        """
        for task in self.tasks:
            if not self.eligible[task.task_id]:
                return (
                    f"task {task.task_id} has no "
                    "SLA/resource-compatible slot"
                )

        total_requirement = [
            sum(task.resource[d] for task in self.tasks)
            for d in range(self.d)
        ]
        total_capacity = [
            sum(self.ins.capacities[s][d] for s in range(self.k))
            for d in range(self.d)
        ]

        for d, (required, available) in enumerate(
            zip(total_requirement, total_capacity)
        ):
            if required > available + EPS:
                return (
                    "total demand exceeds total capacity in "
                    f"resource dimension {d}"
                )

        clique = self._find_greedy_clique_certificate()
        if len(clique) > self.k:
            return (
                f"conflict clique of size {len(clique)} "
                f"exceeds K={self.k}"
            )

        return None

    def _find_greedy_clique_certificate(self) -> list[str]:
        """
        Build a polynomial greedy clique certificate.

        The certificate is sufficient when it produces a clique larger than K;
        failure to find one is not used as an infeasibility claim.
        """
        ids = sorted(
            self.task_by_id,
            key=lambda tid: len(self.neighbors[tid]),
            reverse=True,
        )

        clique: list[str] = []
        for task_id in ids:
            if all(
                task_id in self.neighbors[other]
                for other in clique
            ):
                clique.append(task_id)
        return clique

    def legal_slots(self, task_id: str) -> list[int]:
        """Filter static candidates by current conflicts and residual slot capacity."""
        task = self.task_by_id[task_id]
        legal = []

        for slot in self.eligible[task_id]:
            if any(
                self.assignment.get(neighbor) == slot
                for neighbor in self.neighbors[task_id]
            ):
                continue

            if not self._fits_after(task.resource, slot):
                continue

            legal.append(slot)

        return legal

    def _fits_after(
        self,
        requirement: tuple[float, ...],
        slot: int,
    ) -> bool:
        """Check residual capacity immediately before committing a placement, guaranteeing F2."""
        return all(
            self.load[slot][d] + requirement[d]
            <= self.ins.capacities[slot][d] + EPS
            for d in range(self.d)
        )

    def _saturation(self, task_id: str) -> int:
        """Count distinct colors/slots already used by assigned neighbors, following DSATUR."""
        return len({
            self.assignment[neighbor]
            for neighbor in self.neighbors[task_id]
            if neighbor in self.assignment
        })

    def _window_pressure(self, task: Task) -> float:
        """Give narrow SLA windows higher urgency because they provide fewer repair opportunities."""
        width = max(1, task.upper - task.lower + 1)
        return 1.0 - width / max(1, self.k)

    def _resource_pressure(self, task: Task) -> float:
        """Use the tightest resource ratio across currently eligible slots to expose hard-to-pack tasks."""
        ratios = []
        for slot in self.eligible[task.task_id]:
            ratios.append(
                max(
                    task.resource[d]
                    / max(self.ins.capacities[slot][d], EPS)
                    for d in range(self.d)
                )
            )
        return max(ratios, default=1.0)

    def _urgency_score(self, task: Task) -> float:
        """Combine SLA, saturation, resource, degree, and business priority pressures without replacing DSATUR."""
        saturation = self._saturation(task.task_id)
        degree = len(self.neighbors[task.task_id])

        saturation_ratio = saturation / max(1, degree)
        degree_ratio = degree / max(1, len(self.tasks) - 1)

        max_weight = max(
            (other.weight for other in self.tasks),
            default=1.0,
        )
        priority_ratio = task.weight / max(max_weight, EPS)

        return (
            4.0 * self._window_pressure(task)
            + 3.0 * saturation_ratio
            + 2.0 * self._resource_pressure(task)
            + degree_ratio
            + priority_ratio
        )

    def choose_task(
        self,
        unassigned: set[str],
    ) -> tuple[str, list[int]] | None:
        """Select by fewest legal slots, then saturation, degree, domain urgency, and priority."""
        candidates: list[tuple[tuple[Any, ...], str, list[int]]] = []

        for task_id in unassigned:
            task = self.task_by_id[task_id]
            legal = self.legal_slots(task_id)

            key = (
                len(legal),
                -self._saturation(task_id),
                -len(self.neighbors[task_id]),
                -self._urgency_score(task),
                -task.weight,
                task_id,
            )
            candidates.append((key, task_id, legal))

        if not candidates:
            return None

        candidates.sort(key=lambda item: item[0])
        _, task_id, legal = candidates[0]
        return task_id, legal

    def _sla_risk(self, task: Task, slot: int) -> float:
        """Apply the quadratic normalized SLA-boundary risk defined in Task 2."""
        if task.upper <= task.lower:
            return 0.0

        position = (
            (slot - task.lower)
            / (task.upper - task.lower)
        )
        position = min(1.0, max(0.0, position))
        return position * position

    def _incremental_penalty(self, task: Task, slot: int) -> float:
        """Compute the objective contribution added by assigning one task to one slot."""
        return (
            task.weight * slot
            + self.lambda_sla
            * task.weight
            * self._sla_risk(task, slot)
        )

    def _slot_score(
        self,
        task: Task,
        slot: int,
    ) -> tuple[float, int, float, float]:
        """Minimize incremental penalty while preferring balanced and less-fragmented resource usage."""
        utilizations = [
            (
                self.load[slot][d] + task.resource[d]
            ) / max(self.ins.capacities[slot][d], EPS)
            for d in range(self.d)
        ]

        imbalance = sum(value * value for value in utilizations)
        fragmentation = sum(
            max(0.0, 1.0 - value) ** 2
            for value in utilizations
        )

        score = (
            self._incremental_penalty(task, slot)
            + BALANCE_WEIGHT * imbalance
            + FRAGMENTATION_WEIGHT * fragmentation
        )

        return (
            score,
            slot,
            max(utilizations, default=0.0),
            sum(utilizations),
        )

    def choose_slot(
        self,
        task: Task,
        legal_slots: list[int],
    ) -> int:
        """Choose the minimum-scored legal slot with earlier slots as the deterministic tie-break."""
        return min(
            legal_slots,
            key=lambda slot: self._slot_score(task, slot),
        )

    def _place(self, task_id: str, slot: int) -> None:
        """Commit a placement and update resource loads incrementally."""
        task = self.task_by_id[task_id]
        self.assignment[task_id] = slot

        for d in range(self.d):
            self.load[slot][d] += task.resource[d]

    def _unplace(self, task_id: str) -> None:
        """Undo a placement exactly so one-task repacking can test an alternative slot."""
        slot = self.assignment.pop(task_id)
        task = self.task_by_id[task_id]

        for d in range(self.d):
            self.load[slot][d] -= task.resource[d]

    def try_one_repack(self, blocked_task_id: str) -> bool:
        """Move at most one blocking task once, bounding repair cost while preserving feasibility checks."""
        blockers = [
            neighbor
            for neighbor in self.neighbors[blocked_task_id]
            if (
                neighbor in self.assignment
                and self.assignment[neighbor]
                in self.eligible[blocked_task_id]
            )
        ]

        blockers.sort(
            key=lambda neighbor: (
                len(self.legal_slots(neighbor)),
                -self._saturation(neighbor),
                -len(self.neighbors[neighbor]),
                neighbor,
            )
        )

        for blocker in blockers:
            old_slot = self.assignment[blocker]
            alternatives = [
                slot
                for slot in self.eligible[blocker]
                if slot != old_slot
            ]

            alternatives = [
                slot
                for slot in alternatives
                if (
                    self._fits_after(
                        self.task_by_id[blocker].resource,
                        slot,
                    )
                    and all(
                        self.assignment.get(neighbor) != slot
                        for neighbor in self.neighbors[blocker]
                        if neighbor != blocked_task_id
                    )
                )
            ]

            alternatives.sort(
                key=lambda slot: self._slot_score(
                    self.task_by_id[blocker],
                    slot,
                )
            )

            for new_slot in alternatives:
                self._unplace(blocker)

                # Re-check after removing the blocker because residual
                # capacity and conflict state have changed.
                can_move = (
                    new_slot in self.legal_slots(blocker)
                    and all(
                        self.assignment.get(neighbor) != new_slot
                        for neighbor in self.neighbors[blocker]
                    )
                )

                if can_move:
                    self._place(blocker, new_slot)

                    if self.legal_slots(blocked_task_id):
                        return True

                # Restore the exact previous state if the repair did not help.
                if blocker not in self.assignment:
                    self._place(blocker, old_slot)

        return False

    def verify(self) -> str | None:
        """Independently verify F1, F2, and F3 before returning feasible=true."""
        for a, b in self.ins.conflicts:
            if self.assignment.get(a) == self.assignment.get(b):
                return (
                    f"conflict violation: {a} and {b} "
                    f"share slot {self.assignment[a]}"
                )

        for slot in range(self.k):
            for d in range(self.d):
                used = sum(
                    self.task_by_id[task_id].resource[d]
                    for task_id, assigned_slot in self.assignment.items()
                    if assigned_slot == slot
                )

                if used > self.ins.capacities[slot][d] + EPS:
                    return (
                        f"capacity violation: slot {slot}, "
                        f"resource {d}, used={used}, "
                        f"capacity={self.ins.capacities[slot][d]}"
                    )

        for task in self.tasks:
            slot = self.assignment.get(task.task_id)

            if slot is None:
                return f"unassigned task: {task.task_id}"

            if not task.lower <= slot <= task.upper:
                return (
                    f"SLA violation: {task.task_id} assigned to {slot}, "
                    f"allowed [{task.lower},{task.upper}]"
                )

        return None

    def penalty(self) -> float:
        """Recompute the full Task 2 penalty from scratch for reliable output reporting."""
        return float(sum(
            self._incremental_penalty(
                task,
                self.assignment[task.task_id],
            )
            for task in self.tasks
        ))

    def solve(self) -> tuple[dict[str, int], float, bool, str]:
        """Run certificates, greedy construction, bounded repacking, and independent final verification."""
        self.build_static_eligibility()

        certificate = self.certified_infeasibility()
        if certificate:
            return {}, 0.0, False, certificate

        unassigned = {task.task_id for task in self.tasks}

        while unassigned:
            chosen = self.choose_task(unassigned)

            if chosen is None:
                return (
                    dict(self.assignment),
                    self.penalty() if self.assignment else 0.0,
                    False,
                    "no unassigned task remains selectable",
                )

            task_id, legal = chosen

            if not legal:
                if not self.try_one_repack(task_id):
                    return (
                        dict(self.assignment),
                        self.penalty() if self.assignment else 0.0,
                        False,
                        (
                            "construction failed: no legal slot for "
                            f"{task_id} after one-task repacking"
                        ),
                    )

                legal = self.legal_slots(task_id)

            if not legal:
                return (
                    dict(self.assignment),
                    self.penalty() if self.assignment else 0.0,
                    False,
                    (
                        "construction failed: "
                        f"{task_id} remains blocked after repacking"
                    ),
                )

            slot = self.choose_slot(
                self.task_by_id[task_id],
                legal,
            )
            self._place(task_id, slot)
            unassigned.remove(task_id)

        violation = self.verify()

        if violation:
            return (
                dict(self.assignment),
                self.penalty(),
                False,
                violation,
            )

        return (
            dict(self.assignment),
            self.penalty(),
            True,
            "",
        )


def load_instance(path: str | Path) -> Instance:
    """Parse the exact Section 5 generator structure and validate all dimensions and IDs."""
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)

    required = [
        "tasks",
        "conflicts",
        "resources",
        "capacities",
        "windows",
        "weights",
        "K",
    ]
    missing = [key for key in required if key not in data]

    if missing:
        raise ValueError(f"missing required input keys: {missing}")

    raw_tasks = data["tasks"]
    resources = data["resources"]
    windows = data["windows"]
    weights = data["weights"]
    capacities = data["capacities"]
    k = int(data["K"])

    n = len(raw_tasks)

    if not (
        len(resources) == len(windows) == len(weights) == n
    ):
        raise ValueError(
            "tasks/resources/windows/weights lengths do not match"
        )

    if len(capacities) != k:
        raise ValueError("capacities length must equal K")

    if not capacities:
        raise ValueError("capacities cannot be empty")

    dimension = len(capacities[0])

    if any(len(cap) != dimension for cap in capacities):
        raise ValueError("capacity vectors have inconsistent dimensions")

    if any(len(req) != dimension for req in resources):
        raise ValueError("resource vectors have inconsistent dimensions")

    task_ids = [str(task_id) for task_id in raw_tasks]

    if len(set(task_ids)) != n:
        raise ValueError("task IDs must be unique")

    tasks: list[Task] = []

    for index, task_id in enumerate(task_ids):
        lower, upper = map(int, windows[index])

        # The provided generator uses zero-based slots [0, K-1].
        if lower < 0 or upper >= k or lower > upper:
            raise ValueError(
                f"invalid SLA window for {task_id}: {windows[index]}"
            )

        requirement = tuple(
            float(value) for value in resources[index]
        )

        if any(value < 0 for value in requirement):
            raise ValueError(
                f"negative resource requirement for {task_id}"
            )

        tasks.append(
            Task(
                task_id=task_id,
                resource=requirement,
                lower=lower,
                upper=upper,
                weight=float(weights[index]),
            )
        )

    valid_ids = set(task_ids)
    conflicts: list[tuple[str, str]] = []

    for pair in data["conflicts"]:
        if len(pair) != 2:
            raise ValueError(f"invalid conflict pair: {pair}")

        a, b = str(pair[0]), str(pair[1])

        if a not in valid_ids or b not in valid_ids:
            raise ValueError(
                f"conflict references unknown task: {pair}"
            )

        conflicts.append((a, b))

    normalized_capacities = [
        tuple(float(value) for value in capacity)
        for capacity in capacities
    ]

    return Instance(
        tasks=tasks,
        conflicts=conflicts,
        capacities=normalized_capacities,
        k=k,
    )


def run_file(
    input_path: str,
    output_path: str,
) -> None:
    """Run one JSON instance and emit exactly the five required output fields."""
    start_ns = time.perf_counter_ns()

    try:
        instance = load_instance(input_path)
        solver = CSRAR(instance)

        assignment, penalty, feasible, reason = solver.solve()

    except Exception as exc:
        assignment = {}
        penalty = 0.0
        feasible = False
        reason = f"input/solver error: {exc}"

    runtime_ms = int(
        (time.perf_counter_ns() - start_ns) / 1_000_000
    )

    result = {
        "assignment": assignment,
        "penalty": float(penalty),
        "runtime_ms": runtime_ms,
        "feasible": bool(feasible),
        "violation_reason": reason if not feasible else "",
    }

    with open(output_path, "w", encoding="utf-8") as handle:
        json.dump(
            result,
            handle,
            indent=2,
            sort_keys=True,
        )


def main() -> None:
    """Expose the required command-line interface for evaluator execution."""
    parser = argparse.ArgumentParser(
        description="CS-RAR credit pipeline scheduler"
    )
    parser.add_argument(
        "input",
        help="input JSON generated by Section 5",
    )
    parser.add_argument(
        "-o",
        "--output",
        default="output.json",
        help="output JSON path",
    )

    args = parser.parse_args()
    run_file(args.input, args.output)


if __name__ == "__main__":
    main()
