package com.manus.cubemaster.solver;

import com.manus.cubemaster.CubeState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 真实 Roux 分阶段规划器。
 *
 * <p>First Block、Second Block、CMLL 与 LSE 都由 StageSearch 对各自的块不变量直接规划；
 * 不读取或改名 Kociemba 的完整解。LSE 仅允许 M/U 动作，并在结束时复核六条剩余棱和顶层四角。</p>
 */
public final class RouxSolver {
    private static final int MAX_STAGE_MOVES = 80;
    private static final int MAX_TOTAL_MOVES = 220;
    /** 剪枝表已在后台准备；实际阶段搜索须早于界面 12 秒兜底返回。 */
    private static final long PLANNING_BUDGET_MS = 7_000L;

    private RouxSolver() { }

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

    /** 在用户计算前于后台建立本方法所需的所有受约束剪枝表。 */
    public static void warmUp() {
        firstBlockGoal();
        secondBlockGoal();
        cmllGoal();
        lseGoal();
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

        StageSearch.Goal firstBlock = firstBlockGoal();
        current = append(current, verifier, stages, allMoves, deadline, firstBlock,
                StageSearch.ALL_OUTER_MOVES, 14,
                "Roux First Block", "实际完成左侧 1×2×3 块：两个底角与三条相邻棱均归位并定向。");

        StageSearch.Goal secondBlock = secondBlockGoal();
        current = append(current, verifier, stages, allMoves, deadline, secondBlock,
                StageSearch.ALL_OUTER_MOVES, 17,
                "Roux Second Block", "保留左块，实际完成右侧 1×2×3 块；此时仅顶层四角和最后六棱未完成。");

        StageSearch.Goal cmll = cmllGoal();
        current = append(current, verifier, stages, allMoves, deadline, cmll,
                StageSearch.RLU_MOVES, 16,
                "Roux CMLL", "在不破坏两个块的前提下，定向并排列最后层四个角块。");

        StageSearch.Goal lse = lseGoal();
        append(current, verifier, stages, allMoves, deadline, lse,
                StageSearch.MU_MOVES, 18,
                "Roux LSE", "只使用 M/U 切片完成最后六条棱；结束后验证整颗魔方复原。");

        if (!CubeState.SOLVED.equals(verifier.facelets())) throw new IllegalStateException("Roux LSE 未完成整颗魔方复原");
        return new Result(stages, allMoves);
    }

    private static StageSearch.Goal firstBlockGoal() {
        return StageSearch.goal(StageSearch.block(new int[]{6, 9, 10}, new int[]{5, 6}, StageSearch.ALL_OUTER_MOVES));
    }

    private static StageSearch.Goal secondBlockGoal() {
        return StageSearch.goal(
                StageSearch.block(new int[]{6, 9, 10}, new int[]{5, 6}, StageSearch.ALL_OUTER_MOVES),
                StageSearch.block(new int[]{4, 8, 11}, new int[]{4, 7}, StageSearch.ALL_OUTER_MOVES));
    }

    private static StageSearch.Goal cmllGoal() {
        return StageSearch.goal(
                StageSearch.block(new int[]{6, 9, 10}, new int[]{5, 6}, StageSearch.RLU_MOVES),
                StageSearch.block(new int[]{4, 8, 11}, new int[]{4, 7}, StageSearch.RLU_MOVES),
                StageSearch.corners(new int[]{0, 1, 2, 3}, StageSearch.RLU_MOVES));
    }

    private static StageSearch.Goal lseGoal() {
        return StageSearch.goal(
                StageSearch.edges(new int[]{0, 1, 2}, StageSearch.MU_MOVES),
                StageSearch.edges(new int[]{3, 5, 7}, StageSearch.MU_MOVES),
                StageSearch.corners(new int[]{0, 1, 2, 3}, StageSearch.MU_MOVES));
    }

    private static CubieCube append(CubieCube current, CubeState verifier, List<LayerByLayerSolver.Stage> stages,
                                    List<String> allMoves, long deadline, StageSearch.Goal goal, int[] allowedMoves,
                                    int maxDepth, String title, String detail) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0L) throw new IllegalStateException("Roux 阶段规划超过 10 秒安全预算");
        List<String> moves = StageSearch.solve(current, goal, allowedMoves, maxDepth,
                Math.max(1L, remainingNanos / 1_000_000L));
        if (moves == null) throw new IllegalStateException("Roux 阶段未在限定搜索深度内完成：" + title);
        if (moves.size() > MAX_STAGE_MOVES || allMoves.size() + moves.size() > MAX_TOTAL_MOVES) {
            throw new IllegalStateException("Roux 动作异常过长，已安全停止计算");
        }
        CubieCube next = StageSearch.apply(current, moves);
        if (!goal.isSolved(StageSearch.Snapshot.from(next))) throw new IllegalStateException("Roux 阶段状态验证失败：" + title);
        verifier.applyMoves(moves);
        CubieCube verifierCube = new FaceCube(verifier.facelets()).toCubieCube();
        if (!goal.isSolved(StageSearch.Snapshot.from(verifierCube))) {
            throw new IllegalStateException("Roux 主状态验证失败：" + title);
        }
        stages.add(new LayerByLayerSolver.Stage(title, detail, moves));
        allMoves.addAll(moves);
        return next;
    }
}
