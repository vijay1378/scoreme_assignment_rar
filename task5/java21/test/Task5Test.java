
import java.util.*;

/**
 * Dependency-free unit tests for the four Task 5 required scenarios.
 * Run with: java -ea Task5Test
 */
public class Task5Test {

    static Main.Instance instance(
            String[] ids,
            String[][] conflicts,
            double[][] resources,
            double[][] capacities,
            int[][] windows,
            double[] weights,
            int k) {

        List<Main.Task> tasks = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            tasks.add(new Main.Task(
                    ids[i], resources[i], windows[i][0], windows[i][1], weights[i]));
        }

        List<String[]> edges = new ArrayList<>();
        for (String[] edge : conflicts) edges.add(edge);

        return new Main.Instance(tasks, edges, capacities, k);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** K+1 mutually conflicting tasks form a certificate of infeasibility. */
    static void allConflictGraph() {
        int k = 3;
        String[] ids = {"T0","T1","T2","T3"};
        List<String[]> edges = new ArrayList<>();

        for (int i = 0; i < ids.length; i++)
            for (int j = i + 1; j < ids.length; j++)
                edges.add(new String[]{ids[i], ids[j]});

        Main.Instance in = instance(
                ids,
                edges.toArray(new String[0][]),
                new double[][]{{1,1,0,0},{1,1,0,0},{1,1,0,0},{1,1,0,0}},
                new double[][]{{10,10,10,10},{10,10,10,10},{10,10,10,10}},
                new int[][]{{0,2},{0,2},{0,2},{0,2}},
                new double[]{1,1,1,1},
                k);

        Main.Result r = new Main.Solver(in).solve();
        check(!r.feasible, "all-conflict graph should be infeasible");
        check(r.violationReason.contains("clique"), "expected clique certificate");
    }

    /** A zero-capacity first slot must be skipped for a positive-demand task. */
    static void zeroCapacitySlot() {
        Main.Instance in = instance(
                new String[]{"T0"},
                new String[][]{},
                new double[][]{{1,1,1,1}},
                new double[][]{{0,0,0,0},{2,2,2,2}},
                new int[][]{{0,1}},
                new double[]{1},
                2);

        Main.Result r = new Main.Solver(in).solve();
        check(r.feasible, r.violationReason);
        check(r.assignment.get("T0") == 1, "task should use slot 1");
    }

    /** A one-slot SLA must remain fixed despite a conflict with another task. */
    static void tightSlaWindow() {
        Main.Instance in = instance(
                new String[]{"T0","T1"},
                new String[][]{{"T0","T1"}},
                new double[][]{{1,1,0,0},{1,1,0,0}},
                new double[][]{{10,10,10,10},{10,10,10,10}},
                new int[][]{{0,0},{0,1}},
                new double[]{5,1},
                2);

        Main.Result r = new Main.Solver(in).solve();
        check(r.feasible, r.violationReason);
        check(r.assignment.get("T0") == 0, "tight-window task must be in slot 0");
        check(r.assignment.get("T1") == 1, "conflicting task must move to slot 1");
    }

    /** One task and one slot is the minimal feasible case. */
    static void singleTask() {
        Main.Instance in = instance(
                new String[]{"T0"},
                new String[][]{},
                new double[][]{{1,1,1,1}},
                new double[][]{{2,2,2,2}},
                new int[][]{{0,0}},
                new double[]{3},
                1);

        Main.Result r = new Main.Solver(in).solve();
        check(r.feasible, r.violationReason);
        check(r.assignment.get("T0") == 0, "single task must use slot 0");
        check(Math.abs(r.penalty) < 1e-9, "penalty should be zero");
    }

    public static void main(String[] args) {
        allConflictGraph();
        zeroCapacitySlot();
        tightSlaWindow();
        singleTask();
        System.out.println("All 4 Task 5 tests passed.");
    }
}
