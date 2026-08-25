
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Task 5 implementation of Conflict-Saturation Resource-Aware Repacking (CS-RAR).
 *
 * Java 17+, standard library only. No optimization, SAT, or graph-coloring
 * solver is used. Input follows the Section 5 zero-based slot convention.
 */
public class Main {

    private static final double EPS = 1e-9;
    private static final double LAMBDA_SLA = 1.0;
    private static final double BALANCE_WEIGHT = 0.02;
    private static final double FRAGMENTATION_WEIGHT = 0.01;

    static final class Task {
        /** Immutable task data keeps the solver state separate from input data. */
        final String id;
        final double[] resource;
        final int lower;
        final int upper;
        final double weight;

        Task(String id, double[] resource, int lower, int upper, double weight) {
            this.id = id;
            this.resource = resource;
            this.lower = lower;
            this.upper = upper;
            this.weight = weight;
        }
    }

    static final class Instance {
        /** Normalized instance provides constant-format access to tasks and capacities. */
        final List<Task> tasks;
        final List<String[]> conflicts;
        final double[][] capacities;
        final int k;
        final int dimensions;
        final Map<String, Task> byId = new LinkedHashMap<>();
        final Map<String, Set<String>> neighbors = new LinkedHashMap<>();

        Instance(List<Task> tasks, List<String[]> conflicts,
                 double[][] capacities, int k) {
            this.tasks = tasks;
            this.conflicts = conflicts;
            this.capacities = capacities;
            this.k = k;
            this.dimensions = capacities.length == 0
                    ? (tasks.isEmpty() ? 4 : tasks.get(0).resource.length)
                    : capacities[0].length;

            for (Task t : tasks) {
                byId.put(t.id, t);
                neighbors.put(t.id, new LinkedHashSet<>());
            }
            for (String[] edge : conflicts) {
                neighbors.get(edge[0]).add(edge[1]);
                neighbors.get(edge[1]).add(edge[0]);
            }
        }
    }

    static final class Solver {
        /** CS-RAR combines constrained-task ordering, penalty-aware slot choice, and bounded repacking. */
        final Instance in;
        final Map<String, List<Integer>> eligible = new LinkedHashMap<>();
        final Map<String, Integer> assignment = new LinkedHashMap<>();
        final double[][] load;

        Solver(Instance in) {
            this.in = in;
            this.load = new double[in.k][in.dimensions];
        }

        /** Build static eligibility from SLA windows and individual resource feasibility. */
        void buildEligibility() {
            for (Task t : in.tasks) {
                List<Integer> slots = new ArrayList<>();
                for (int s = 0; s < in.k; s++) {
                    if (t.lower <= s && s <= t.upper
                            && fits(t.resource, in.capacities[s])) {
                        slots.add(s);
                    }
                }
                eligible.put(t.id, slots);
            }
        }

        /** Return only sound polynomial certificates of global infeasibility. */
        String certifiedInfeasibility() {
            for (Task t : in.tasks) {
                if (eligible.get(t.id).isEmpty()) {
                    return "task " + t.id + " has no SLA/resource-compatible slot";
                }
            }

            for (int d = 0; d < in.dimensions; d++) {
                double demand = 0, capacity = 0;
                for (Task t : in.tasks) demand += t.resource[d];
                for (int s = 0; s < in.k; s++) capacity += in.capacities[s][d];
                if (demand > capacity + EPS) {
                    return "total demand exceeds total capacity in resource dimension " + d;
                }
            }

            List<String> clique = greedyCliqueCertificate();
            if (clique.size() > in.k) {
                return "conflict clique of size " + clique.size()
                        + " exceeds K=" + in.k;
            }
            return null;
        }

        /**
         * Greedily constructs a clique certificate. Failure to find a large clique
         * is never interpreted as proof of infeasibility.
         */
        List<String> greedyCliqueCertificate() {
            List<String> ids = new ArrayList<>(in.byId.keySet());
            ids.sort((a, b) -> Integer.compare(
                    in.neighbors.get(b).size(), in.neighbors.get(a).size()));

            List<String> clique = new ArrayList<>();
            for (String id : ids) {
                boolean joins = true;
                for (String other : clique) {
                    if (!in.neighbors.get(id).contains(other)) {
                        joins = false;
                        break;
                    }
                }
                if (joins) clique.add(id);
            }
            return clique;
        }

        /** Check residual resource capacity after adding a task to a slot. */
        boolean fitsAfter(Task t, int slot) {
            for (int d = 0; d < in.dimensions; d++) {
                if (load[slot][d] + t.resource[d] > in.capacities[slot][d] + EPS)
                    return false;
            }
            return true;
        }

        /** Check a resource vector against a capacity vector. */
        static boolean fits(double[] req, double[] cap) {
            for (int d = 0; d < req.length; d++) {
                if (req[d] > cap[d] + EPS) return false;
            }
            return true;
        }

        /** Return slots satisfying current conflicts and residual resource capacity. */
        List<Integer> legalSlots(String id) {
            Task t = in.byId.get(id);
            List<Integer> result = new ArrayList<>();
            for (int s : eligible.get(id)) {
                boolean conflict = false;
                for (String n : in.neighbors.get(id)) {
                    if (Objects.equals(assignment.get(n), s)) {
                        conflict = true;
                        break;
                    }
                }
                if (!conflict && fitsAfter(t, s)) result.add(s);
            }
            return result;
        }

        /** Count distinct slots already occupied by assigned neighbors, as in DSATUR. */
        int saturation(String id) {
            Set<Integer> colors = new HashSet<>();
            for (String n : in.neighbors.get(id)) {
                Integer s = assignment.get(n);
                if (s != null) colors.add(s);
            }
            return colors.size();
        }

        /** Normalize narrow SLA windows into a domain-pressure score. */
        double windowPressure(Task t) {
            int width = Math.max(1, t.upper - t.lower + 1);
            return 1.0 - ((double) width / Math.max(1, in.k));
        }

        /** Measure the tightest resource ratio among a task's statically eligible slots. */
        double resourcePressure(Task t) {
            double best = 1.0;
            for (int s : eligible.get(t.id)) {
                double ratio = 0;
                for (int d = 0; d < in.dimensions; d++) {
                    ratio = Math.max(ratio,
                            t.resource[d] / Math.max(in.capacities[s][d], EPS));
                }
                best = Math.max(best, ratio);
            }
            return best;
        }

        /** Combine SLA, saturation, resource, degree, and business priority pressure. */
        double urgency(Task t) {
            int degree = in.neighbors.get(t.id).size();
            double satRatio = (double) saturation(t.id) / Math.max(1, degree);
            double degreeRatio = (double) degree / Math.max(1, in.tasks.size() - 1);

            double maxWeight = 1;
            for (Task x : in.tasks) maxWeight = Math.max(maxWeight, x.weight);
            double priorityRatio = t.weight / maxWeight;

            return 4 * windowPressure(t)
                    + 3 * satRatio
                    + 2 * resourcePressure(t)
                    + degreeRatio
                    + priorityRatio;
        }

        /**
         * Select the most constrained unassigned task. Fewest legal slots is
         * primary; saturation, degree, urgency and weight resolve ties.
         */
        String chooseTask(Set<String> unassigned) {
            String best = null;
            int bestDomain = Integer.MAX_VALUE;
            int bestSat = -1, bestDegree = -1;
            double bestUrgency = Double.NEGATIVE_INFINITY, bestWeight = Double.NEGATIVE_INFINITY;

            for (String id : unassigned) {
                Task t = in.byId.get(id);
                int domain = legalSlots(id).size();
                int sat = saturation(id);
                int degree = in.neighbors.get(id).size();
                double urg = urgency(t);

                boolean better =
                        domain < bestDomain
                        || (domain == bestDomain && sat > bestSat)
                        || (domain == bestDomain && sat == bestSat && degree > bestDegree)
                        || (domain == bestDomain && sat == bestSat && degree == bestDegree
                            && urg > bestUrgency)
                        || (domain == bestDomain && sat == bestSat && degree == bestDegree
                            && Double.compare(urg, bestUrgency) == 0 && t.weight > bestWeight)
                        || (domain == bestDomain && sat == bestSat && degree == bestDegree
                            && Double.compare(urg, bestUrgency) == 0
                            && Double.compare(t.weight, bestWeight) == 0
                            && id.compareTo(best == null ? "" : best) < 0);

                if (best == null || better) {
                    best = id;
                    bestDomain = domain;
                    bestSat = sat;
                    bestDegree = degree;
                    bestUrgency = urg;
                    bestWeight = t.weight;
                }
            }
            return best;
        }

        /** Calculate the quadratic normalized SLA-boundary risk from Task 2. */
        double slaRisk(Task t, int slot) {
            if (t.upper <= t.lower) return 0;
            double q = (double)(slot - t.lower) / (t.upper - t.lower);
            q = Math.max(0, Math.min(1, q));
            return q * q;
        }

        /** Calculate the incremental Task 2 penalty for one task-slot choice. */
        double incrementalPenalty(Task t, int slot) {
            return t.weight * slot + LAMBDA_SLA * t.weight * slaRisk(t, slot);
        }

        /**
         * Score a legal slot by Task 2 penalty plus tiny deterministic packing
         * tie-break terms, without changing hard feasibility.
         */
        double slotScore(Task t, int slot) {
            double imbalance = 0, fragmentation = 0;
            for (int d = 0; d < in.dimensions; d++) {
                double u = (load[slot][d] + t.resource[d])
                        / Math.max(in.capacities[slot][d], EPS);
                imbalance += u * u;
                double free = Math.max(0, 1 - u);
                fragmentation += free * free;
            }
            return incrementalPenalty(t, slot)
                    + BALANCE_WEIGHT * imbalance
                    + FRAGMENTATION_WEIGHT * fragmentation;
        }

        /** Choose the minimum-scored legal slot with the slot number as tie-break. */
        int chooseSlot(Task t, List<Integer> legal) {
            int best = legal.get(0);
            double score = slotScore(t, best);
            for (int s : legal) {
                double candidate = slotScore(t, s);
                if (candidate < score - EPS
                        || (Math.abs(candidate - score) <= EPS && s < best)) {
                    best = s;
                    score = candidate;
                }
            }
            return best;
        }

        /** Commit a placement and increment each resource load. */
        void place(String id, int slot) {
            Task t = in.byId.get(id);
            assignment.put(id, slot);
            for (int d = 0; d < in.dimensions; d++) load[slot][d] += t.resource[d];
        }

        /** Undo a placement exactly, allowing bounded repacking. */
        void unplace(String id) {
            int slot = assignment.remove(id);
            Task t = in.byId.get(id);
            for (int d = 0; d < in.dimensions; d++) load[slot][d] -= t.resource[d];
        }

        /**
         * Move at most one blocking task once. Bounding the repair keeps the
         * heuristic polynomial and prevents unbounded local-search behavior.
         */
        boolean tryOneRepack(String blockedId) {
            List<String> blockers = new ArrayList<>();
            for (String n : in.neighbors.get(blockedId)) {
                if (assignment.containsKey(n)) blockers.add(n);
            }

            blockers.sort((a, b) -> {
                int c = Integer.compare(legalSlots(a).size(), legalSlots(b).size());
                if (c != 0) return c;
                c = Integer.compare(saturation(b), saturation(a));
                if (c != 0) return c;
                c = Integer.compare(in.neighbors.get(b).size(), in.neighbors.get(a).size());
                if (c != 0) return c;
                return a.compareTo(b);
            });

            for (String blocker : blockers) {
                int old = assignment.get(blocker);
                Task bt = in.byId.get(blocker);
                List<Integer> alternatives = new ArrayList<>();
                for (int s : eligible.get(blocker)) {
                    if (s == old || !fitsAfter(bt, s)) continue;
                    boolean conflict = false;
                    for (String n : in.neighbors.get(blocker)) {
                        if (!n.equals(blockedId) && Objects.equals(assignment.get(n), s)) {
                            conflict = true;
                            break;
                        }
                    }
                    if (!conflict) alternatives.add(s);
                }

                alternatives.sort(Comparator.comparingDouble(s -> slotScore(bt, s)));

                for (int newSlot : alternatives) {
                    unplace(blocker);
                    if (legalSlots(blocker).contains(newSlot)) {
                        place(blocker, newSlot);
                        if (!legalSlots(blockedId).isEmpty()) return true;
                        unplace(blocker);
                    }
                    place(blocker, old);
                }
            }
            return false;
        }

        /**
         * Independently verify all hard constraints before declaring feasible.
         * This prevents implementation bugs from leaking invalid assignments.
         */
        String verify() {
            for (String[] edge : in.conflicts) {
                Integer a = assignment.get(edge[0]);
                Integer b = assignment.get(edge[1]);
                if (a != null && a.equals(b)) {
                    return "conflict violation: " + edge[0] + " and " + edge[1]
                            + " share slot " + a;
                }
            }

            for (int s = 0; s < in.k; s++) {
                for (int d = 0; d < in.dimensions; d++) {
                    double used = 0;
                    for (Task t : in.tasks) {
                        if (Objects.equals(assignment.get(t.id), s))
                            used += t.resource[d];
                    }
                    if (used > in.capacities[s][d] + EPS) {
                        return "capacity violation: slot " + s
                                + ", resource " + d
                                + ", used=" + used
                                + ", capacity=" + in.capacities[s][d];
                    }
                }
            }

            for (Task t : in.tasks) {
                Integer s = assignment.get(t.id);
                if (s == null) return "unassigned task: " + t.id;
                if (s < t.lower || s > t.upper) {
                    return "SLA violation: " + t.id + " assigned to " + s
                            + ", allowed [" + t.lower + "," + t.upper + "]";
                }
            }
            return null;
        }

        /** Recompute the complete Task 2 objective from the final assignment. */
        double penalty() {
            double total = 0;
            for (Task t : in.tasks) {
                Integer s = assignment.get(t.id);
                if (s != null) total += incrementalPenalty(t, s);
            }
            return total;
        }

        /** Execute certificates, construction, bounded repair, and final verification. */
        Result solve() {
            buildEligibility();

            String certificate = certifiedInfeasibility();
            if (certificate != null)
                return new Result(new LinkedHashMap<>(), 0, false, certificate);

            Set<String> unassigned = new LinkedHashSet<>(in.byId.keySet());

            while (!unassigned.isEmpty()) {
                String id = chooseTask(unassigned);
                List<Integer> legal = legalSlots(id);

                if (legal.isEmpty() && !tryOneRepack(id)) {
                    return new Result(new LinkedHashMap<>(assignment), penalty(), false,
                            "construction failed: no legal slot for " + id
                                    + " after one-task repacking");
                }

                legal = legalSlots(id);
                if (legal.isEmpty()) {
                    return new Result(new LinkedHashMap<>(assignment), penalty(), false,
                            "construction failed: " + id + " remains blocked after repacking");
                }

                place(id, chooseSlot(in.byId.get(id), legal));
                unassigned.remove(id);
            }

            String violation = verify();
            if (violation != null)
                return new Result(new LinkedHashMap<>(assignment), penalty(), false, violation);

            return new Result(new LinkedHashMap<>(assignment), penalty(), true, "");
        }
    }

    static final class Result {
        /** Immutable solver result mirrors the five required output fields. */
        final Map<String, Integer> assignment;
        final double penalty;
        final boolean feasible;
        final String violationReason;

        Result(Map<String, Integer> assignment, double penalty,
               boolean feasible, String violationReason) {
            this.assignment = assignment;
            this.penalty = penalty;
            this.feasible = feasible;
            this.violationReason = violationReason;
        }
    }

    /**
     * Minimal JSON parser supporting the generator's objects, arrays, strings,
     * numbers, booleans and null. It avoids adding a third-party dependency.
     */
    static final class Json {
        final String text;
        int p = 0;

        Json(String text) { this.text = text; }

        static Object parse(String text) {
            return new Json(text).value();
        }

        void ws() {
            while (p < text.length() && Character.isWhitespace(text.charAt(p))) p++;
        }

        Object value() {
            ws();
            if (p >= text.length()) throw error("unexpected end");
            char c = text.charAt(p);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') { literal("true"); return Boolean.TRUE; }
            if (c == 'f') { literal("false"); return Boolean.FALSE; }
            if (c == 'n') { literal("null"); return null; }
            return number();
        }

        Map<String, Object> object() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (peek('}')) { p++; return m; }

            while (true) {
                ws();
                String key = string();
                ws(); expect(':');
                m.put(key, value());
                ws();
                if (peek('}')) { p++; return m; }
                expect(',');
            }
        }

        List<Object> array() {
            expect('[');
            List<Object> a = new ArrayList<>();
            ws();
            if (peek(']')) { p++; return a; }

            while (true) {
                a.add(value());
                ws();
                if (peek(']')) { p++; return a; }
                expect(',');
            }
        }

        String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (p < text.length()) {
                char c = text.charAt(p++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }

                if (p >= text.length()) throw error("bad escape");
                char e = text.charAt(p++);
                switch (e) {
                    case '"', '\\', '/' -> b.append(e);
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        if (p + 4 > text.length()) throw error("bad unicode escape");
                        b.append((char) Integer.parseInt(text.substring(p, p + 4), 16));
                        p += 4;
                    }
                    default -> throw error("unknown escape");
                }
            }
            throw error("unterminated string");
        }

        Number number() {
            int start = p;
            if (peek('-')) p++;
            while (p < text.length() && Character.isDigit(text.charAt(p))) p++;
            if (peek('.')) {
                p++;
                while (p < text.length() && Character.isDigit(text.charAt(p))) p++;
            }
            if (peek('e') || peek('E')) {
                p++;
                if (peek('+') || peek('-')) p++;
                while (p < text.length() && Character.isDigit(text.charAt(p))) p++;
            }
            String s = text.substring(start, p);
            try {
                double d = Double.parseDouble(s);
                return d;
            } catch (NumberFormatException e) {
                throw error("invalid number: " + s);
            }
        }

        void literal(String s) {
            if (!text.startsWith(s, p)) throw error("expected " + s);
            p += s.length();
        }

        boolean peek(char c) { return p < text.length() && text.charAt(p) == c; }

        void expect(char c) {
            ws();
            if (!peek(c)) throw error("expected '" + c + "'");
            p++;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + p);
        }

        static String quote(String s) {
            StringBuilder b = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    case '\b' -> b.append("\\b");
                    case '\f' -> b.append("\\f");
                    default -> {
                        if (c < 32) b.append(String.format("\\u%04x", (int)c));
                        else b.append(c);
                    }
                }
            }
            return b.append('"').toString();
        }

        static String write(Object o) {
            if (o == null) return "null";
            if (o instanceof String s) return quote(s);
            if (o instanceof Boolean || o instanceof Number) return o.toString();
            if (o instanceof Map<?, ?> map) {
                StringBuilder b = new StringBuilder("{");
                boolean first = true;
                for (var e : map.entrySet()) {
                    if (!first) b.append(',');
                    first = false;
                    b.append(quote(String.valueOf(e.getKey())))
                            .append(':').append(write(e.getValue()));
                }
                return b.append('}').toString();
            }
            if (o instanceof Iterable<?> it) {
                StringBuilder b = new StringBuilder("[");
                boolean first = true;
                for (Object x : it) {
                    if (!first) b.append(',');
                    first = false;
                    b.append(write(x));
                }
                return b.append(']').toString();
            }
            throw new IllegalArgumentException("unsupported JSON value");
        }

        static String pretty(Object o) {
            // The evaluator accepts JSON whitespace freely; compact JSON avoids
            // maintaining a second serializer implementation.
            return write(o);
        }
    }

    static final class Loader {
        /** Convert the Section 5 JSON object into the normalized Instance model. */
        static Instance load(Path path) throws IOException {
            String text = Files.readString(path);
            Object root = Json.parse(text);
            if (!(root instanceof Map<?, ?> raw))
                throw new IllegalArgumentException("input JSON must be an object");

            Map<String, Object> data = new LinkedHashMap<>();
            for (var e : raw.entrySet()) data.put(String.valueOf(e.getKey()), e.getValue());

            String[] required = {
                    "tasks", "conflicts", "resources", "capacities",
                    "windows", "weights", "K"
            };
            for (String key : required)
                if (!data.containsKey(key))
                    throw new IllegalArgumentException("missing required input key: " + key);

            List<Object> rawTasks = list(data.get("tasks"));
            List<Object> rawResources = list(data.get("resources"));
            List<Object> rawWindows = list(data.get("windows"));
            List<Object> rawWeights = list(data.get("weights"));
            List<Object> rawCapacities = list(data.get("capacities"));
            int k = integer(data.get("K"));

            int n = rawTasks.size();
            if (rawResources.size() != n || rawWindows.size() != n || rawWeights.size() != n)
                throw new IllegalArgumentException("tasks/resources/windows/weights lengths do not match");
            if (rawCapacities.size() != k)
                throw new IllegalArgumentException("capacities length must equal K");
            if (k <= 0) throw new IllegalArgumentException("K must be positive");

            double[][] capacities = new double[k][];
            int dimensions = -1;
            for (int s = 0; s < k; s++) {
                capacities[s] = doubles(list(rawCapacities.get(s)));
                if (dimensions < 0) dimensions = capacities[s].length;
                if (capacities[s].length != dimensions)
                    throw new IllegalArgumentException("capacity vectors have inconsistent dimensions");
            }

            List<Task> tasks = new ArrayList<>();
            Set<String> ids = new HashSet<>();

            for (int i = 0; i < n; i++) {
                String id = String.valueOf(rawTasks.get(i));
                if (!ids.add(id)) throw new IllegalArgumentException("duplicate task ID: " + id);

                double[] req = doubles(list(rawResources.get(i)));
                if (req.length != dimensions)
                    throw new IllegalArgumentException("resource vector dimension mismatch for " + id);

                List<Object> window = list(rawWindows.get(i));
                if (window.size() != 2)
                    throw new IllegalArgumentException("invalid SLA window for " + id);

                int lower = integer(window.get(0));
                int upper = integer(window.get(1));
                if (lower < 0 || upper >= k || lower > upper)
                    throw new IllegalArgumentException("invalid SLA window for " + id);

                tasks.add(new Task(id, req, lower, upper, number(rawWeights.get(i))));
            }

            List<String[]> conflicts = new ArrayList<>();
            for (Object edgeObj : list(data.get("conflicts"))) {
                List<Object> edge = list(edgeObj);
                if (edge.size() != 2) throw new IllegalArgumentException("invalid conflict pair");
                String a = String.valueOf(edge.get(0));
                String b = String.valueOf(edge.get(1));
                if (!ids.contains(a) || !ids.contains(b))
                    throw new IllegalArgumentException("conflict references unknown task");
                conflicts.add(new String[]{a, b});
            }

            return new Instance(tasks, conflicts, capacities, k);
        }

        static List<Object> list(Object o) {
            if (!(o instanceof List<?> l)) throw new IllegalArgumentException("expected JSON array");
            return new ArrayList<>(l);
        }

        static int integer(Object o) {
            return (int)Math.round(number(o));
        }

        static double number(Object o) {
            if (!(o instanceof Number n)) throw new IllegalArgumentException("expected JSON number");
            return n.doubleValue();
        }

        static double[] doubles(List<Object> values) {
            double[] r = new double[values.size()];
            for (int i = 0; i < r.length; i++) r[i] = number(values.get(i));
            return r;
        }
    }

    /** Write exactly the required output fields in JSON. */
    static void writeResult(Path output, Result r, long runtimeMs) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assignment", r.assignment);
        out.put("penalty", r.penalty);
        out.put("runtime_ms", runtimeMs);
        out.put("feasible", r.feasible);
        out.put("violation_reason", r.feasible ? "" : r.violationReason);
        Files.writeString(output, Json.pretty(out));
    }

    /** Main command-line entry point: java Main input.json -o output.json */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <input.json> [-o output.json]");
            System.exit(2);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of("output.json");

        for (int i = 1; i + 1 < args.length; i++) {
            if (args[i].equals("-o") || args[i].equals("--output"))
                output = Path.of(args[++i]);
        }

        long start = System.nanoTime();
        Result result;

        try {
            Instance instance = Loader.load(input);
            result = new Solver(instance).solve();
        } catch (Exception e) {
            result = new Result(
                    new LinkedHashMap<>(), 0, false,
                    "input/solver error: " + e.getMessage()
            );
        }

        long runtimeMs = (System.nanoTime() - start) / 1_000_000;

        try {
            writeResult(output, result, runtimeMs);
        } catch (IOException e) {
            System.err.println("Unable to write output: " + e.getMessage());
            System.exit(1);
        }
    }
}
