package com.manus.cubemaster.solver;

import com.manus.cubemaster.CubeState;
import com.manus.cubemaster.solver.cfop.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 真实 ZZ 分阶段规划器。
 *
 * <p>EOLine 由完整棱朝向与 DF/DB 定位的联合目标生成；F2L 与 OCLL 限制为 R/L/U。
 * 最后的 PLL 使用标准最后层排列算法表（必要时含 F/B/R/L/U）并验证完整复原，绝不以 Kociemba
 * 完整解的重命名步骤替代。</p>
 */
public final class ZzSolver {
    private static final int MAX_STAGE_MOVES = 80;
    private static final int MAX_TOTAL_MOVES = 260;
    /** 剪枝表已在后台准备；实际阶段搜索须早于界面 12 秒兜底返回。 */
    private static final long PLANNING_BUDGET_MS = 7_000L;
    private static final String[] TOP_TURNS = {"", "U", "U2", "U'"};

    private ZzSolver() { }

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

    /** 在用户计算前于后台建立 ZZ 各阶段共用的剪枝表。 */
    public static void warmUp() {
        eolineGoal();
        for (int pair = 0; pair <= 4; pair++) f2lGoal(pair);
        ocllGoal();
    }

    public static Result solve(String currentFacelets) {
        String validation = SolverFacade.validate(currentFacelets);
        if (validation != null) throw new IllegalArgumentException(validation);
        if (SolverFacade.isSolved(currentFacelets)) return new Result(Collections.emptyList(), Collections.emptyList());

        CubeState verifier = new CubeState(currentFacelets);
        CubieCube current = new FaceCube(currentFacelets).toCubieCube();
        List<LayerByLayerSolver.Stage> stages = new ArrayList<>();
        List<String> allMoves = new ArrayList<>();
        long deadline = System.nanoTime() + PLANNING_BUDGET_MS * 1_000_000L;

        StageSearch.Goal eoline = eolineGoal();
        current = append(current, verifier, stages, allMoves, deadline, eoline,
                StageSearch.ALL_OUTER_MOVES, 9,
                "ZZ EOLine", "实际定向全部十二条棱，并让 DF、DB 两条底层棱归位形成 Line。");

        current = append(current, verifier, stages, allMoves, deadline, f2lGoal(0),
                StageSearch.RLU_MOVES, 10,
                "ZZ-F2L 底层十字", "EOLine 后只使用 R/L/U，建立底层十字并保持已完成的棱定向。");
        for (int pair = 1; pair <= 4; pair++) {
            current = append(current, verifier, stages, allMoves, deadline, f2lGoal(pair),
                    StageSearch.RLU_MOVES, 13,
                    "ZZ-F2L 第 " + pair + " 对", "在仅 R/L/U 的受限动作集中插入第 " + pair + " 组角棱对，同时保持前序 F2L 槽。 ");
        }

        StageSearch.Goal ocll = ocllGoal();
        current = append(current, verifier, stages, allMoves, deadline, ocll,
                StageSearch.RLU_MOVES, 13,
                "ZZ OCLL", "保持完整 F2L，仅定向最后层四个角块，不提前承担最后层排列。");

        StageSearch.Goal pll = pllGoal();
        List<String> pllMoves = findPllCase(verifier.facelets());
        if (pllMoves != null) {
            appendKnown(current, verifier, stages, allMoves, pll, pllMoves,
                    "ZZ PLL", "保持 EOLine、F2L 与 OCLL 成果，使用标准 PLL 算法表排列最后层并完成整颗魔方复原。");
        } else {
            append(current, verifier, stages, allMoves, deadline, pll,
                    StageSearch.ALL_OUTER_MOVES, 18,
                    "ZZ PLL", "未匹配标准 PLL 表时，使用独立最后层搜索排列顶层角块与棱块并完成整颗魔方复原。");
        }

        if (!CubeState.SOLVED.equals(verifier.facelets())) throw new IllegalStateException("ZZ PLL 未完成整颗魔方复原");
        return new Result(stages, allMoves);
    }

    private static StageSearch.Goal eolineGoal() {
        return StageSearch.goal(
                StageSearch.allEdgeOrientation(StageSearch.ALL_OUTER_MOVES),
                StageSearch.edges(new int[]{5, 7}, StageSearch.ALL_OUTER_MOVES));
    }

    private static StageSearch.Goal f2lGoal(int pairs) {
        int[] cornerOrder = {4, 5, 6, 7};
        int[] middleEdgeOrder = {8, 9, 10, 11};
        int[] corners = new int[pairs];
        int[] middleEdges = new int[pairs];
        System.arraycopy(cornerOrder, 0, corners, 0, pairs);
        System.arraycopy(middleEdgeOrder, 0, middleEdges, 0, pairs);
        return StageSearch.goal(
                StageSearch.edges(new int[]{4, 5, 6, 7}, StageSearch.RLU_MOVES),
                StageSearch.corners(corners, StageSearch.RLU_MOVES),
                StageSearch.edges(middleEdges, StageSearch.RLU_MOVES));
    }

    private static StageSearch.Goal ocllGoal() {
        return StageSearch.goal(
                StageSearch.edges(new int[]{4, 5, 6, 7}, StageSearch.RLU_MOVES),
                StageSearch.corners(new int[]{4, 5, 6, 7}, StageSearch.RLU_MOVES),
                StageSearch.edges(new int[]{8, 9, 10, 11}, StageSearch.RLU_MOVES),
                StageSearch.allCornerOrientation(StageSearch.RLU_MOVES));
    }

    private static StageSearch.Goal pllGoal() {
        return StageSearch.goal(
                StageSearch.edges(new int[]{4, 5, 6, 7}, StageSearch.RLU_MOVES),
                StageSearch.corners(new int[]{4, 5, 6, 7}, StageSearch.RLU_MOVES),
                StageSearch.edges(new int[]{8, 9, 10, 11}, StageSearch.RLU_MOVES),
                StageSearch.edges(new int[]{0, 1, 2, 3}, StageSearch.RLU_MOVES),
                StageSearch.corners(new int[]{0, 1, 2, 3}, StageSearch.RLU_MOVES));
    }

    /**
     * OCLL 后仅有标准 PLL 排列。以 CFOP MIT 内核中已收录的 PLL 表为案例库，枚举 U 对齐与 y 旋转；
     * 每个候选都直接在主 CubeState 验证，因而这是 ZZ 的最后层阶段算法，不是完整两阶段解的回放。
     */
    private static List<String> findPllCase(String facelets) {
        for (String raw : Constants.PLL_Algs) {
            if (raw == null || raw.trim().isEmpty()) continue;
            for (int rotation = 0; rotation < 4; rotation++) {
                List<String> body = rotateY(raw, rotation);
                for (String before : TOP_TURNS) {
                    for (String after : TOP_TURNS) {
                        List<String> candidate = new ArrayList<>();
                        if (!before.isEmpty()) candidate.add(before);
                        candidate.addAll(body);
                        if (!after.isEmpty()) candidate.add(after);
                        CubeState trial = new CubeState(facelets);
                        trial.applyMoves(candidate);
                        if (CubeState.SOLVED.equals(trial.facelets())) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static List<String> rotateY(String raw, int turns) {
        List<String> out = new ArrayList<>();
        for (String token : raw.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            char face = token.charAt(0);
            for (int i = 0; i < turns; i++) {
                switch (face) {
                    case 'F': face = 'R'; break;
                    case 'R': face = 'B'; break;
                    case 'B': face = 'L'; break;
                    case 'L': face = 'F'; break;
                    default: break;
                }
            }
            out.add(face + token.substring(1));
        }
        return out;
    }

    private static CubieCube appendKnown(CubieCube current, CubeState verifier, List<LayerByLayerSolver.Stage> stages,
                                          List<String> allMoves, StageSearch.Goal goal, List<String> moves,
                                          String title, String detail) {
        if (moves.size() > MAX_STAGE_MOVES || allMoves.size() + moves.size() > MAX_TOTAL_MOVES) {
            throw new IllegalStateException("ZZ 动作异常过长，已安全停止计算");
        }
        CubieCube next = StageSearch.apply(current, moves);
        if (!goal.isSolved(StageSearch.Snapshot.from(next))) throw new IllegalStateException("ZZ 阶段状态验证失败：" + title);
        verifier.applyMoves(moves);
        if (!CubeState.SOLVED.equals(verifier.facelets())) throw new IllegalStateException("ZZ 主状态验证失败：" + title);
        stages.add(new LayerByLayerSolver.Stage(title, detail, moves));
        allMoves.addAll(moves);
        return next;
    }

    private static CubieCube append(CubieCube current, CubeState verifier, List<LayerByLayerSolver.Stage> stages,
                                    List<String> allMoves, long deadline, StageSearch.Goal goal, int[] allowedMoves,
                                    int maxDepth, String title, String detail) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0L) throw new IllegalStateException("ZZ 阶段规划超过 7 秒安全预算");
        List<String> moves = StageSearch.solve(current, goal, allowedMoves, maxDepth,
                Math.max(1L, remainingNanos / 1_000_000L));
        if (moves == null) throw new IllegalStateException("ZZ 阶段未在限定搜索深度内完成：" + title);
        if (moves.size() > MAX_STAGE_MOVES || allMoves.size() + moves.size() > MAX_TOTAL_MOVES) {
            throw new IllegalStateException("ZZ 动作异常过长，已安全停止计算");
        }
        CubieCube next = StageSearch.apply(current, moves);
        if (!goal.isSolved(StageSearch.Snapshot.from(next))) throw new IllegalStateException("ZZ 阶段状态验证失败：" + title);
        verifier.applyMoves(moves);
        CubieCube verifierCube = new FaceCube(verifier.facelets()).toCubieCube();
        if (!goal.isSolved(StageSearch.Snapshot.from(verifierCube))) {
            throw new IllegalStateException("ZZ 主状态验证失败：" + title);
        }
        stages.add(new LayerByLayerSolver.Stage(title, detail, moves));
        allMoves.addAll(moves);
        return next;
    }
}
