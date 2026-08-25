import unittest

from run import CSRAR, Instance, Task


def make_instance(
    tasks,
    conflicts,
    resources,
    capacities,
    windows,
    weights,
    k,
):
    """Build normalized test instances directly so tests focus on solver behavior rather than JSON parsing."""
    return Instance(
        tasks=[
            Task(
                str(tasks[i]),
                tuple(float(x) for x in resources[i]),
                int(windows[i][0]),
                int(windows[i][1]),
                float(weights[i]),
            )
            for i in range(len(tasks))
        ],
        conflicts=[
            (str(a), str(b))
            for a, b in conflicts
        ],
        capacities=[
            tuple(float(x) for x in capacity)
            for capacity in capacities
        ],
        k=k,
    )


class TestCSRAR(unittest.TestCase):
    def test_all_conflict_graph_chromatic_number_gt_k(self):
        """A K+1 clique must be rejected by the explicit conflict-clique certificate."""
        k = 3
        tasks = [f"T{i}" for i in range(4)]
        conflicts = [
            (tasks[i], tasks[j])
            for i in range(4)
            for j in range(i + 1, 4)
        ]

        instance = make_instance(
            tasks=tasks,
            conflicts=conflicts,
            resources=[[1, 1, 0, 0] for _ in tasks],
            capacities=[[10, 10, 10, 10] for _ in range(k)],
            windows=[[0, k - 1] for _ in tasks],
            weights=[1] * len(tasks),
            k=k,
        )

        _, _, feasible, reason = CSRAR(instance).solve()

        self.assertFalse(feasible)
        self.assertIn("clique", reason)

    def test_zero_capacity_slot(self):
        """A positive-demand task must skip a zero-capacity slot and use another legal slot."""
        instance = make_instance(
            tasks=["T0"],
            conflicts=[],
            resources=[[1, 1, 1, 1]],
            capacities=[
                [0, 0, 0, 0],
                [2, 2, 2, 2],
            ],
            windows=[[0, 1]],
            weights=[1],
            k=2,
        )

        assignment, _, feasible, reason = CSRAR(instance).solve()

        self.assertTrue(feasible, reason)
        self.assertEqual(assignment["T0"], 1)

    def test_tight_sla_windows(self):
        """A one-slot SLA must be respected even when the task participates in a conflict."""
        instance = make_instance(
            tasks=["T0", "T1"],
            conflicts=[("T0", "T1")],
            resources=[
                [1, 1, 0, 0],
                [1, 1, 0, 0],
            ],
            capacities=[
                [10, 10, 10, 10],
                [10, 10, 10, 10],
            ],
            windows=[
                [0, 0],
                [0, 1],
            ],
            weights=[5, 1],
            k=2,
        )

        assignment, _, feasible, reason = CSRAR(instance).solve()

        self.assertTrue(feasible, reason)
        self.assertEqual(assignment["T0"], 0)
        self.assertEqual(assignment["T1"], 1)

    def test_single_task_instance(self):
        """A single task with one legal slot is the minimal deterministic feasible case."""
        instance = make_instance(
            tasks=["T0"],
            conflicts=[],
            resources=[[1, 1, 1, 1]],
            capacities=[[2, 2, 2, 2]],
            windows=[[0, 0]],
            weights=[3],
            k=1,
        )

        assignment, penalty, feasible, reason = CSRAR(instance).solve()

        self.assertTrue(feasible, reason)
        self.assertEqual(assignment, {"T0": 0})
        self.assertAlmostEqual(penalty, 0.0)


if __name__ == "__main__":
    unittest.main()
