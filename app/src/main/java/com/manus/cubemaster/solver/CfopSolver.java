package com.manus.cubemaster.solver;

import com.manus.cubemaster.CubeState;
import com.manus.cubemaster.solver.cfop.Cross;
import com.manus.cubemaster.solver.cfop.F2L;
import com.manus.cubemaster.solver.cfop.FridrichSolver;
import com.manus.cubemaster.solver.cfop.OLL;
import com.manus.cubemaster.solver.cfop.PLL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 真正的 CFOP（Cross → F2L → OLL → PLL）规划器。
 *
 * <p>动作来自独立的 MIT 许可 CFOP 内核，而非 Kociemba 动作重命名。适配层将内核的
 * F/R/B/L/D/U 色面布局映射到应用固定的 URFDLB 三维主状态，并在每段结束后断言其 CFOP 目标。</p>
 */
public final class CfopSolver {
    private static final int MAX_STAGE_MOVES = 180;
    private static final int MAX_TOTAL_MOVES = 400;
    // 内核 F/R/B/L/D/U 六面分别映射到应用 F/L/B/R/D/U；每个值是内核格写入应用面的顺时针旋转数。
    private static final int[] CORE_TO_APP_FACE = {2, 4, 5, 1, 3, 0};
    private static final int[] CORE_TO_APP_ROTATION = {2, 2, 2, 2, 3, 1};

    private CfopSolver() { }

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

    private interface CoreStage { void apply(FridrichSolver core); }
    private interface Goal { boolean reached(String facelets); }

    /** 对当前完整合法状态执行真实 CFOP；白色 Cross 位于应用 U 面。 */
    public static Result solve(String currentFacelets) {
        String validation = SolverFacade.validate(currentFacelets);
        if (validation != null) throw new IllegalArgumentException(validation);
        if (SolverFacade.isSolved(currentFacelets)) return new Result(Collections.emptyList(), Collections.emptyList());

        FridrichSolver core = new FridrichSolver(toCoreFacelets(currentFacelets));
        CubeState verifier = new CubeState(currentFacelets);
        List<LayerByLayerSolver.Stage> stages = new ArrayList<>();
        List<String> allMoves = new ArrayList<>();

        appendStage(core, Cross::Solve, "CFOP Cross", "实际建立白色十字；四条底层棱同时与四个侧面中心色对应。", verifier, stages, allMoves, CfopSolver::isCrossSolved);
        appendStage(core, F2L::Solve, "CFOP F2L", "按角棱配对完成前两层；不是先底角、再中层棱的入门分层法。", verifier, stages, allMoves, CfopSolver::isF2lSolved);
        appendStage(core, OLL::Solve, "CFOP OLL", "保持前两层，定向最后一层，使 U 面九个面片全部朝上。", verifier, stages, allMoves, CfopSolver::isLastLayerOriented);
        appendStage(core, PLL::Solve, "CFOP PLL", "保持顶层朝向，排列最后一层并完成整个魔方。", verifier, stages, allMoves, CfopSolver::isSolved);

        if (core.ErrorCode != 0) throw new IllegalStateException("CFOP 内核未完成，错误码：" + core.ErrorCode);
        return new Result(stages, allMoves);
    }

    private static void appendStage(FridrichSolver core, CoreStage action, String title, String detail,
                                    CubeState verifier, List<LayerByLayerSolver.Stage> stages,
                                    List<String> allMoves, Goal goal) {
        int before = core.Solution.length();
        action.apply(core);
        if (core.ErrorCode != 0) throw new IllegalStateException(title + "阶段失败，错误码：" + core.ErrorCode);
        String raw = core.Solution.substring(before);
        List<String> moves = mapCoreMoves(raw);
        if (moves.size() > MAX_STAGE_MOVES || allMoves.size() + moves.size() > MAX_TOTAL_MOVES) {
            throw new IllegalStateException(title + "步骤异常过长，已安全停止计算。");
        }
        verifier.applyMoves(moves);
        if (!goal.reached(verifier.facelets())) {
            throw new IllegalStateException(title + "阶段目标未达成。");
        }
        stages.add(new LayerByLayerSolver.Stage(title, detail, moves));
        allMoves.addAll(moves);
    }

    private static List<String> mapCoreMoves(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String token : raw.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            char face = token.charAt(0);
            char mapped;
            switch (face) {
                case 'U': mapped = 'D'; break;
                case 'R': mapped = 'L'; break;
                case 'F': mapped = 'F'; break;
                case 'D': mapped = 'U'; break;
                case 'L': mapped = 'R'; break;
                case 'B': mapped = 'B'; break;
                default: throw new IllegalStateException("CFOP 内核输出了不支持的动作：" + token);
            }
            String suffix = token.length() > 1 ? token.substring(1) : "";
            if (!(suffix.isEmpty() || "2".equals(suffix) || "'".equals(suffix))) {
                throw new IllegalStateException("CFOP 内核动作记号异常：" + token);
            }
            out.add(mapped + suffix);
        }
        return out;
    }

    private static String toCoreFacelets(String current) {
        char[] out = new char[54];
        for (int coreFace = 0; coreFace < 6; coreFace++) {
            int appFace = CORE_TO_APP_FACE[coreFace];
            int rotation = CORE_TO_APP_ROTATION[coreFace];
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    char app = current.charAt(appFace * 9 + transformedIndex(rotation, row, col));
                    out[coreFace * 9 + row * 3 + col] = toCoreColor(app);
                }
            }
        }
        return new String(out);
    }

    private static int transformedIndex(int rotation, int row, int col) {
        int r = row;
        int c = col;
        switch (rotation) {
            case 1: r = col; c = 2 - row; break;
            case 2: r = 2 - row; c = 2 - col; break;
            case 3: r = 2 - col; c = row; break;
            default: break;
        }
        return r * 3 + c;
    }

    private static char toCoreColor(char app) {
        switch (app) {
            case 'U': return 'w';
            case 'R': return 'r';
            case 'F': return 'g';
            case 'D': return 'y';
            case 'L': return 'o';
            case 'B': return 'b';
            default: throw new IllegalArgumentException("CFOP 不能读取未知面片");
        }
    }

    private static boolean isCrossSolved(String s) {
        return s.charAt(1) == 'U' && s.charAt(3) == 'U' && s.charAt(5) == 'U' && s.charAt(7) == 'U'
                && s.charAt(10) == 'R' && s.charAt(19) == 'F' && s.charAt(37) == 'L' && s.charAt(46) == 'B';
    }

    private static boolean isF2lSolved(String s) {
        for (int i = 0; i < 9; i++) if (s.charAt(i) != 'U') return false;
        return rowMatches(s, 1, 0, 'R') && rowMatches(s, 1, 1, 'R')
                && rowMatches(s, 2, 0, 'F') && rowMatches(s, 2, 1, 'F')
                && rowMatches(s, 4, 0, 'L') && rowMatches(s, 4, 1, 'L')
                && rowMatches(s, 5, 0, 'B') && rowMatches(s, 5, 1, 'B');
    }

    private static boolean isLastLayerOriented(String s) {
        if (!isF2lSolved(s)) return false;
        for (int i = 27; i < 36; i++) if (s.charAt(i) != 'D') return false;
        return true;
    }

    private static boolean isSolved(String s) { return CubeState.SOLVED.equals(s); }

    private static boolean rowMatches(String s, int face, int row, char color) {
        int start = face * 9 + row * 3;
        return s.charAt(start) == color && s.charAt(start + 1) == color && s.charAt(start + 2) == color;
    }
}
