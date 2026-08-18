package com.manus.cubemaster.solver;

import com.manus.cubemaster.CubeState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 独立的经典棱先（Edges First）与角先（Corners First）规划器。
 *
 * <p>棱先先以外层搜索完成全部 12 条棱，随后仅使用严格保持棱块不变的纯角宏完成 8 个角；
 * 角先则先以完整 2×2 角块坐标完成 8 个角，随后仅使用严格保持角块不变的纯棱宏完成 12 条棱。
 * 这两条路线不会调用或重命名 Kociemba 完整解。</p>
 */
public final class PieceFirstSolver {
    private static final long PLANNING_BUDGET_MS = 10_000L;
    private static final int MAX_STAGE_MOVES = 260;
    private static final int MAX_TOTAL_MOVES = 650;
    private static final int MACRO_SEARCH_DEPTH = 5;
    private static final int MAX_OUTER_RETRY_DEPTH = 30;
    private static final int[][] EDGE_GROUPS = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {9, 10, 11}
    };
    private static final int[][] CORNER_GROUPS = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7}
    };
    private static final String[] OUTER_MOVES = {
            "U", "U2", "U'", "R", "R2", "R'", "F", "F2", "F'",
            "D", "D2", "D'", "L", "L2", "L'", "B", "B2", "B'"
    };
    private static final String[] QUARTER_OUTER_MOVES = {
            "U", "R", "F", "D", "L", "B", "U'", "R'", "F'", "D'", "L'", "B'"
    };

    private static volatile List<Macro> pureCornerMacros;
    private static volatile List<Macro> pureEdgeMacros;
    private static final StageListener NO_STAGE_LISTENER = (title, detail, stageIndex, stageCount) -> { };

    private PieceFirstSolver() { }

    /** 后台预组合纯角、纯棱宏，避免把首次初始化成本计入用户点击后的 12 秒计算窗口。 */
    public static void warmUp() {
        pureCornerMacros();
        pureEdgeMacros();
    }

    /** 从后台求解线程回调当前实际阶段；调用方负责切回 UI 线程。 */
    public interface StageListener {
        void onStageStarted(String title, String detail, int stageIndex, int stageCount);
    }

    public static final class Result {
        private final List<LayerByLayerSolver.Stage> stages;
        private final List<String> moves;

        Result(List<LayerByLayerSolver.Stage> stages, List<String> moves) {
            this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
            this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
        }

        public List<LayerByLayerSolver.Stage> stages() { return stages; }
        public List<String> moves() { return moves; }
    }

    /** 经典棱先：所有棱先完成，所有角后完成。 */
    public static Result solveEdgesFirst(String currentFacelets) {
        return solveEdgesFirst(currentFacelets, NO_STAGE_LISTENER);
    }

    public static Result solveEdgesFirst(String currentFacelets, StageListener listener) {
        return solve(currentFacelets, true, listener == null ? NO_STAGE_LISTENER : listener);
    }

    /** 经典角先：所有角先完成，所有棱后完成。 */
    public static Result solveCornersFirst(String currentFacelets) {
        return solveCornersFirst(currentFacelets, NO_STAGE_LISTENER);
    }

    public static Result solveCornersFirst(String currentFacelets, StageListener listener) {
        return solve(currentFacelets, false, listener == null ? NO_STAGE_LISTENER : listener);
    }

    private static Result solve(String currentFacelets, boolean edgesFirst, StageListener listener) {
        String validation = SolverFacade.validate(currentFacelets);
        if (validation != null) throw new IllegalArgumentException(validation);
        if (SolverFacade.isSolved(currentFacelets)) return new Result(Collections.emptyList(), Collections.emptyList());

        CubeState verifier = new CubeState(currentFacelets);
        CubieCube current = new FaceCube(currentFacelets).toCubieCube();
        List<LayerByLayerSolver.Stage> stages = new ArrayList<>();
        List<String> allMoves = new ArrayList<>();
        long deadlineNanos = System.nanoTime() + PLANNING_BUDGET_MS * 1_000_000L;

        if (edgesFirst) {
            current = solveEdgeFirstOuterStages(current, verifier, stages, allMoves, deadlineNanos, listener);
            requireAllEdges(current, "棱先主阶段验证失败：全部棱未完成");
            current = solveCornerMacroStages(current, verifier, stages, allMoves, deadlineNanos, true, listener);
            requireAllEdges(current, "棱先纯角阶段破坏了已完成棱");
            requireAllCorners(current, "棱先收尾未完成全部角");
        } else {
            current = solveAllCorners(current, verifier, stages, allMoves, deadlineNanos, listener);
            requireAllCorners(current, "角先主阶段验证失败：全部角未完成");
            current = solveEdgeMacroStages(current, verifier, stages, allMoves, deadlineNanos, listener);
            requireAllCorners(current, "角先纯棱阶段破坏了已完成角");
            requireAllEdges(current, "角先收尾未完成全部棱");
        }

        if (!CubeState.SOLVED.equals(verifier.facelets())) {
            throw new IllegalStateException((edgesFirst ? "棱先" : "角先") + "最终主状态未完整复原");
        }
        return new Result(stages, allMoves);
    }

    private static CubieCube solveEdgeFirstOuterStages(CubieCube current, CubeState verifier,
                                                        List<LayerByLayerSolver.Stage> stages,
                                                        List<String> allMoves, long deadlineNanos,
                                                        StageListener listener) {
        for (int stage = 0; stage < EDGE_GROUPS.length; stage++) {
            StageSearch.DistanceOracle[] goals = new StageSearch.DistanceOracle[stage + 1];
            for (int group = 0; group <= stage; group++) {
                goals[group] = StageSearch.edges(EDGE_GROUPS[group], StageSearch.ALL_OUTER_MOVES);
            }
            String title = "棱先 · 棱组 " + (stage + 1) + "/4";
            String detail = stage == 0
                    ? "实际开始棱先路线：先定位并定向第一组棱块。"
                    : stage == 3
                    ? "在保持前三组棱的前提下完成最后一组；此阶段结束后 12 条棱全部复原。"
                    : "保持此前已复原棱组，继续定位并定向下一组棱块。";
            listener.onStageStarted(title, detail, stage + 1, 8);
            current = appendOuter(current, verifier, stages, allMoves, deadlineNanos,
                    StageSearch.goal(goals), 14 + stage * 3, title, detail);
        }
        return current;
    }

    private static CubieCube solveAllCorners(CubieCube current, CubeState verifier,
                                              List<LayerByLayerSolver.Stage> stages,
                                              List<String> allMoves, long deadlineNanos,
                                              StageListener listener) {
        StageSearch.Goal goal = StageSearch.goal(
                StageSearch.allCornerPermutation(StageSearch.ALL_OUTER_MOVES),
                StageSearch.allCornerOrientation(StageSearch.ALL_OUTER_MOVES));
        String title = "角先 · 完整八角";
        String detail = "实际完成并验证全部 8 个角块的位置与朝向；后续纯棱阶段不会移动这些角块。";
        listener.onStageStarted(title, detail, 1, 5);
        return appendOuter(current, verifier, stages, allMoves, deadlineNanos, goal, 14, title, detail);
    }

    private static CubieCube solveCornerMacroStages(CubieCube current, CubeState verifier,
                                                     List<LayerByLayerSolver.Stage> stages,
                                                     List<String> allMoves, long deadlineNanos,
                                                     boolean preserveEdges, StageListener listener) {
        List<Macro> macros = pureCornerMacros();
        int[] cumulative = new int[0];
        for (int stage = 0; stage < CORNER_GROUPS.length; stage++) {
            cumulative = append(cumulative, CORNER_GROUPS[stage]);
            String title = "棱先 · 角组 " + (stage + 1) + "/4";
            String detail = stage == 3
                    ? "仅使用已验证的纯角宏完成最后角组；全程保持 12 条棱不变，完成整颗魔方。"
                    : "仅使用保持全部棱不变的纯角宏，逐组完成角块。";
            listener.onStageStarted(title, detail, stage + 5, 8);
            current = appendMacro(current, verifier, stages, allMoves, deadlineNanos, macros,
                    cumulative, true, preserveEdges, title, detail);
        }
        return current;
    }

    private static CubieCube solveEdgeMacroStages(CubieCube current, CubeState verifier,
                                                   List<LayerByLayerSolver.Stage> stages,
                                                   List<String> allMoves, long deadlineNanos,
                                                   StageListener listener) {
        List<Macro> macros = pureEdgeMacros();
        int[] cumulative = new int[0];
        for (int stage = 0; stage < EDGE_GROUPS.length; stage++) {
            cumulative = append(cumulative, EDGE_GROUPS[stage]);
            String title = "角先 · 棱组 " + (stage + 1) + "/4";
            String detail = stage == 3
                    ? "仅使用已验证的纯棱宏完成最后棱组；全程保持 8 个角不变，完成整颗魔方。"
                    : "仅使用保持全部角不变的纯棱宏，逐组完成棱块。";
            listener.onStageStarted(title, detail, stage + 2, 5);
            current = appendMacro(current, verifier, stages, allMoves, deadlineNanos, macros,
                    cumulative, false, true, title, detail);
        }
        return current;
    }

    private static CubieCube appendOuter(CubieCube current, CubeState verifier,
                                          List<LayerByLayerSolver.Stage> stages, List<String> allMoves,
                                          long deadlineNanos, StageSearch.Goal goal, int maxDepth,
                                          String title, String detail) {
        List<String> moves = StageSearch.solve(current, goal, StageSearch.ALL_OUTER_MOVES, maxDepth,
                remainingMs(deadlineNanos));
        // 初始深度主要保证常见状态的响应速度；若已完整迭代仍无路径，则在同一总预算内加深一次。
        if (moves == null && maxDepth < MAX_OUTER_RETRY_DEPTH) {
            moves = StageSearch.solve(current, goal, StageSearch.ALL_OUTER_MOVES,
                    Math.min(MAX_OUTER_RETRY_DEPTH, maxDepth + 6), remainingMs(deadlineNanos));
        }
        if (moves == null) throw new IllegalStateException(title + "未在限定时间和搜索深度内完成");
        CubieCube next = StageSearch.apply(current, moves);
        if (!goal.isSolved(StageSearch.Snapshot.from(next))) throw new IllegalStateException(title + "阶段目标验证失败");
        appendVerified(verifier, stages, allMoves, moves, title, detail);
        return next;
    }

    private static CubieCube appendMacro(CubieCube current, CubeState verifier,
                                          List<LayerByLayerSolver.Stage> stages, List<String> allMoves,
                                          long deadlineNanos, List<Macro> macros, int[] pieces,
                                          boolean corners, boolean preserveOtherType,
                                          String title, String detail) {
        List<String> moves = solveMacroProjection(current, pieces, corners, macros, deadlineNanos);
        if (moves == null) throw new IllegalStateException(title + "未在限定时间内找到保持约束的宏路径");
        CubieCube next = StageSearch.apply(current, moves);
        if (!piecesSolved(next, pieces, corners)) throw new IllegalStateException(title + "阶段目标验证失败");
        if (preserveOtherType) {
            if (corners) requireAllEdges(next, title + "破坏了已完成棱");
            else requireAllCorners(next, title + "破坏了已完成角");
        }
        appendVerified(verifier, stages, allMoves, moves, title, detail);
        return next;
    }

    private static void appendVerified(CubeState verifier, List<LayerByLayerSolver.Stage> stages,
                                       List<String> allMoves, List<String> moves, String title, String detail) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        if (moves.size() > MAX_STAGE_MOVES || allMoves.size() + moves.size() > MAX_TOTAL_MOVES) {
            throw new IllegalStateException(title + "动作异常过长，已安全停止计算");
        }
        verifier.applyMoves(moves);
        stages.add(new LayerByLayerSolver.Stage(title, detail, moves));
        allMoves.addAll(moves);
    }

    private static List<String> solveMacroProjection(CubieCube source, int[] pieces, boolean corners,
                                                      List<Macro> macros, long deadlineNanos) {
        String startKey = projectionKey(source, pieces, corners);
        CubieCube solved = new CubieCube();
        String goalKey = projectionKey(solved, pieces, corners);
        if (startKey.equals(goalKey)) return new ArrayList<>();

        Map<String, Node> fromStart = new HashMap<>();
        Map<String, Node> fromGoal = new HashMap<>();
        Map<String, CubieCube> startFrontier = new HashMap<>();
        Map<String, CubieCube> goalFrontier = new HashMap<>();
        fromStart.put(startKey, Node.root());
        fromGoal.put(goalKey, Node.root());
        startFrontier.put(startKey, source);
        goalFrontier.put(goalKey, solved);
        int startDepth = 0;
        int goalDepth = 0;

        while (!startFrontier.isEmpty() && !goalFrontier.isEmpty()
                && startDepth + goalDepth < MACRO_SEARCH_DEPTH) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            if (System.nanoTime() >= deadlineNanos) return null;
            boolean expandStart = startFrontier.size() <= goalFrontier.size();
            Map<String, CubieCube> frontier = expandStart ? startFrontier : goalFrontier;
            Map<String, Node> visited = expandStart ? fromStart : fromGoal;
            Map<String, Node> otherVisited = expandStart ? fromGoal : fromStart;
            Map<String, CubieCube> nextFrontier = new HashMap<>();
            for (Map.Entry<String, CubieCube> entry : frontier.entrySet()) {
                for (int macroIndex = 0; macroIndex < macros.size(); macroIndex++) {
                    CubieCube next = applyMacro(entry.getValue(), macros.get(macroIndex));
                    String nextKey = projectionKey(next, pieces, corners);
                    if (visited.containsKey(nextKey)) continue;
                    visited.put(nextKey, new Node(entry.getKey(), macroIndex));
                    if (otherVisited.containsKey(nextKey)) return reconstruct(nextKey, fromStart, fromGoal, macros);
                    nextFrontier.put(nextKey, next);
                    if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                    if (System.nanoTime() >= deadlineNanos) return null;
                }
            }
            if (expandStart) {
                startFrontier = nextFrontier;
                startDepth++;
            } else {
                goalFrontier = nextFrontier;
                goalDepth++;
            }
        }
        return null;
    }

    /** 纯宏已在预热期合成为单个 CubieCube 变换，搜索期不再逐个展开其中的 10~30 个面转。 */
    private static CubieCube applyMacro(CubieCube source, Macro macro) {
        CubieCube out = new CubieCube();
        out.cp = source.cp.clone();
        out.co = source.co.clone();
        out.ep = source.ep.clone();
        out.eo = source.eo.clone();
        out.cornerMultiply(macro.transform);
        out.edgeMultiply(macro.transform);
        return out;
    }

    private static List<String> reconstruct(String meeting, Map<String, Node> fromStart,
                                            Map<String, Node> fromGoal, List<Macro> macros) {
        ArrayDeque<Integer> left = new ArrayDeque<>();
        for (String key = meeting; fromStart.get(key).parent != null; key = fromStart.get(key).parent) {
            left.addFirst(fromStart.get(key).macroIndex);
        }
        List<String> out = new ArrayList<>();
        for (int macroIndex : left) out.addAll(macros.get(macroIndex).moves);
        for (String key = meeting; fromGoal.get(key).parent != null; key = fromGoal.get(key).parent) {
            out.addAll(macros.get(fromGoal.get(key).macroIndex).inverseMoves);
        }
        return out;
    }

    private static List<Macro> pureCornerMacros() {
        List<Macro> cached = pureCornerMacros;
        if (cached != null) return cached;
        synchronized (PieceFirstSolver.class) {
            if (pureCornerMacros == null) pureCornerMacros = Collections.unmodifiableList(buildMacros(true));
            return pureCornerMacros;
        }
    }

    private static List<Macro> pureEdgeMacros() {
        List<Macro> cached = pureEdgeMacros;
        if (cached != null) return cached;
        synchronized (PieceFirstSolver.class) {
            if (pureEdgeMacros == null) pureEdgeMacros = Collections.unmodifiableList(buildMacros(false));
            return pureEdgeMacros;
        }
    }

    /** @param corners true 为保持棱、改变角的宏；false 为保持角、改变棱的宏。 */
    private static List<Macro> buildMacros(boolean corners) {
        String[] bases = corners
                ? new String[]{
                "R' U L U' R U L' U'",
                "L U L' U L U2 L' R' U' R U' R' U2 R",
                "R' U2 R U R' U R L U2 L' U' L U' L'"
        }
                : new String[]{
                "M' R U' M U R'",
                "M2 U M2 U2 M2 U M2",
                "M2 U M2 U M' U2 M2 U2 M' U2"
        };
        List<List<String>> setups = new ArrayList<>();
        setups.add(Collections.emptyList());
        for (String outer : OUTER_MOVES) setups.add(CubeState.parseMoves(outer));
        for (String first : QUARTER_OUTER_MOVES) {
            for (String second : QUARTER_OUTER_MOVES) {
                if (first.charAt(0) != second.charAt(0)) setups.add(CubeState.parseMoves(first + " " + second));
            }
        }

        LinkedHashMap<String, Macro> unique = new LinkedHashMap<>();
        for (List<String> setup : setups) {
            for (String base : bases) {
                for (List<String> variant : Arrays.asList(CubeState.parseMoves(base), inverse(CubeState.parseMoves(base)))) {
                    List<String> moves = new ArrayList<>(setup);
                    moves.addAll(variant);
                    moves.addAll(inverse(setup));
                    Macro macro = Macro.create(moves, corners);
                    if (macro != null) unique.put(macro.signature, macro);
                }
            }
        }
        if (!corners) {
            // [A, M] 与 [A, M′] 均天然保持角块；补足纯棱宏覆盖度。
            for (String outer : OUTER_MOVES) {
                List<String> setup = CubeState.parseMoves(outer);
                for (String slice : new String[]{"M", "M'"}) {
                    List<String> moves = new ArrayList<>(setup);
                    moves.add(slice);
                    moves.addAll(inverse(setup));
                    moves.add(slice.endsWith("'") ? "M" : "M'");
                    Macro macro = Macro.create(moves, false);
                    if (macro != null) unique.put(macro.signature, macro);
                }
            }
        }
        if (unique.isEmpty()) throw new IllegalStateException("纯" + (corners ? "角" : "棱") + "宏初始化失败");
        return new ArrayList<>(unique.values());
    }

    private static List<String> inverse(List<String> source) {
        List<String> out = new ArrayList<>();
        for (int index = source.size() - 1; index >= 0; index--) {
            String move = source.get(index);
            out.add(move.endsWith("2") ? move : move.endsWith("'") ? move.substring(0, move.length() - 1) : move + "'");
        }
        return out;
    }

    private static int[] append(int[] current, int[] added) {
        int[] out = Arrays.copyOf(current, current.length + added.length);
        System.arraycopy(added, 0, out, current.length, added.length);
        return out;
    }

    private static long remainingMs(long deadlineNanos) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        long remaining = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        if (remaining <= 0L) throw new IllegalStateException("棱先/角先规划超过 10 秒安全预算");
        return remaining;
    }

    private static boolean piecesSolved(CubieCube cube, int[] pieces, boolean corners) {
        if (corners) {
            for (int piece : pieces) {
                if (cube.cp[piece].ordinal() != piece || cube.co[piece] != 0) return false;
            }
        } else {
            for (int piece : pieces) {
                if (cube.ep[piece].ordinal() != piece || cube.eo[piece] != 0) return false;
            }
        }
        return true;
    }

    private static void requireAllCorners(CubieCube cube, String error) {
        if (!piecesSolved(cube, new int[]{0, 1, 2, 3, 4, 5, 6, 7}, true)) throw new IllegalStateException(error);
    }

    private static void requireAllEdges(CubieCube cube, String error) {
        if (!piecesSolved(cube, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, false)) throw new IllegalStateException(error);
    }

    private static String projectionKey(CubieCube cube, int[] pieces, boolean corners) {
        StringBuilder out = new StringBuilder(pieces.length * 2);
        if (corners) {
            for (int piece : pieces) {
                for (int position = 0; position < 8; position++) {
                    if (cube.cp[position].ordinal() == piece) {
                        out.append((char) ('A' + position)).append((char) ('a' + cube.co[position]));
                        break;
                    }
                }
            }
        } else {
            for (int piece : pieces) {
                for (int position = 0; position < 12; position++) {
                    if (cube.ep[position].ordinal() == piece) {
                        out.append((char) ('A' + position)).append((char) ('a' + cube.eo[position]));
                        break;
                    }
                }
            }
        }
        return out.toString();
    }

    private static boolean standardCenters(String facelets) {
        return facelets.charAt(4) == 'U' && facelets.charAt(13) == 'R' && facelets.charAt(22) == 'F'
                && facelets.charAt(31) == 'D' && facelets.charAt(40) == 'L' && facelets.charAt(49) == 'B';
    }

    private static final class Node {
        final String parent;
        final int macroIndex;

        Node(String parent, int macroIndex) {
            this.parent = parent;
            this.macroIndex = macroIndex;
        }

        static Node root() { return new Node(null, -1); }
    }

    private static final class Macro {
        final List<String> moves;
        final List<String> inverseMoves;
        final String signature;
        final CubieCube transform;

        Macro(List<String> moves, List<String> inverseMoves, String signature, CubieCube transform) {
            this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
            this.inverseMoves = Collections.unmodifiableList(new ArrayList<>(inverseMoves));
            this.signature = signature;
            this.transform = transform;
        }

        static Macro create(List<String> moves, boolean changesCorners) {
            CubeState state = new CubeState();
            state.applyMoves(moves);
            if (!standardCenters(state.facelets())) return null;
            CubieCube cube = new FaceCube(state.facelets()).toCubieCube();
            if (changesCorners) {
                if (!piecesSolved(cube, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, false)) return null;
            } else {
                if (!piecesSolved(cube, new int[]{0, 1, 2, 3, 4, 5, 6, 7}, true)) return null;
            }
            String signature = projectionKey(cube,
                    changesCorners ? new int[]{0, 1, 2, 3, 4, 5, 6, 7} : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                    changesCorners);
            return new Macro(moves, inverse(moves), signature, cube);
        }
    }
}
