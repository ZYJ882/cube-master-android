package com.manus.cubemaster.solver;

import com.manus.cubemaster.CubeState;
import com.manus.cubemaster.solver.layerbylayer.LayerByLayerCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 真实入门层先法求解器。
 *
 * <p>该类调用一个独立的层先法内核，按“底层十字、首层角、中层棱、顶层十字、顶层朝向、顶层排列”
 * 的顺序实际改变状态。Kociemba 仅用于把任意合法的当前面片状态重放进层先法内核；不会把
 * Kociemba 返回的动作作为层先法输出。</p>
 */
public final class LayerByLayerSolver {
    private LayerByLayerSolver() { }

    public static final class Stage {
        private final String title;
        private final String detail;
        private final List<String> moves;

        Stage(String title, String detail, List<String> moves) {
            this.title = title;
            this.detail = detail;
            this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
        }

        public String title() { return title; }
        public String detail() { return detail; }
        public List<String> moves() { return moves; }
    }

    public static final class Result {
        private final List<Stage> stages;
        private final List<String> moves;

        Result(List<Stage> stages, List<String> moves) {
            this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
            this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
        }

        public List<Stage> stages() { return stages; }
        public List<String> moves() { return moves; }
    }

    /**
     * 对当前标准朝向下的完整合法状态执行真正的层先法。
     * 当前状态约定为 U=白、D=黄、F=绿；阶段完成后会立即以 CubeState 面片断言校验。
     */
    public static Result solve(String currentFacelets) {
        String validation = SolverFacade.validate(currentFacelets);
        if (validation != null) throw new IllegalArgumentException(validation);
        if (SolverFacade.isSolved(currentFacelets)) return new Result(Collections.emptyList(), Collections.emptyList());

        // 用已验证的任意状态搜索仅重建内核的输入状态；最终显示的动作来自下方独立 LBL 内核。
        List<String> inverseSolution = CubeState.parseMoves(SolverFacade.solve(currentFacelets));
        if (inverseSolution.isEmpty()) throw new IllegalStateException("无法重建层先法输入状态");
        List<String> scramble = inverseMoves(inverseSolution);

        LayerByLayerCore core = new LayerByLayerCore();
        core.performMoves(join(toReferenceMoves(scramble)));
        CubeState verifier = new CubeState(currentFacelets);
        List<Stage> stages = new ArrayList<>();
        List<String> allMoves = new ArrayList<>();
        char[] frame = {'U', 'R', 'F', 'D', 'L', 'B'}; // 内核当前面 -> 初始参考面；跨阶段持续保留

        appendStage(core.makeSunflower() + " " + core.makeWhiteCross(), "底层十字", "先建立白色十字，并让四个侧面棱块与中心色一致。", verifier, stages, allMoves, frame, LayerByLayerSolver::isCrossSolved);
        appendStage(core.finishWhiteLayer(), "完成首层", "插入并朝向四个白色角块；首层不会被后续中层操作破坏。", verifier, stages, allMoves, frame, LayerByLayerSolver::isFirstLayerSolved);
        appendStage(core.insertAllEdges(), "完成中层", "将四条非黄色棱块分别插入正确的中层槽位。", verifier, stages, allMoves, frame, LayerByLayerSolver::isSecondLayerSolved);
        appendStage(core.makeYellowCross(), "顶层十字", "仅定向顶层棱块，形成黄色十字。", verifier, stages, allMoves, frame, LayerByLayerSolver::isLastCrossSolved);
        appendStage(core.orientLastLayer(), "顶层朝向", "将四个顶层角块朝向正确，使整个顶面变黄。", verifier, stages, allMoves, frame, LayerByLayerSolver::isLastLayerOriented);
        appendStage(core.permuteLastLayer(), "顶层排列并复原", "排列顶层角块和棱块，完成整个魔方。", verifier, stages, allMoves, frame, LayerByLayerSolver::isSolved);

        return new Result(stages, allMoves);
    }

    private interface Goal { boolean reached(String facelets); }

    private static void appendStage(String referenceMoves, String title, String detail, CubeState verifier,
                                    List<Stage> stages, List<String> allMoves, char[] frame, Goal goal) {
        List<String> moves = toCurrentMoves(referenceMoves, frame);
        verifier.applyMoves(moves);
        if (!goal.reached(verifier.facelets())) {
            throw new IllegalStateException("层先法阶段未达成：" + title);
        }
        stages.add(new Stage(title, detail, moves));
        allMoves.addAll(moves);
    }

    /** 参考内核按黄顶白底工作；当前项目是白顶黄底，二者相差 z2。 */
    private static List<String> toReferenceMoves(List<String> currentMoves) {
        List<String> out = new ArrayList<>();
        for (String move : currentMoves) out.add(mapZ2Face(move));
        return out;
    }

    /**
     * 把参考内核的动作写回当前项目固定的三维坐标。
     * 内核中的 y 是“转动整颗魔方后重新抓取”的视角操作；它不应在主模型上产生物理面转。
     * 因此只更新坐标系，并把后续动作投影至转动前的固定面。
     */
    private static List<String> toCurrentMoves(String referenceMoves, char[] frame) {
        List<String> out = new ArrayList<>();
        if (referenceMoves == null || referenceMoves.trim().isEmpty()) return out;
        String[] tokens = referenceMoves.trim().split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            char face = token.charAt(0);
            String suffix = token.length() > 1 ? token.substring(1) : "";
            if (face == 'y') {
                int turns = "2".equals(suffix) ? 2 : "'".equals(suffix) ? 3 : 1;
                for (int turn = 0; turn < turns; turn++) rotateFrameY(frame);
            } else if ("URFDLB".indexOf(face) >= 0) {
                int index = "URFDLB".indexOf(face);
                out.add(mapZ2Face(frame[index] + suffix));
            }
        }
        return out;
    }

    /** 执行一次内核 y 视角重抓取：新 R 面看到旧 F 面。 */
    private static void rotateFrameY(char[] frame) {
        char oldR = frame[1];
        char oldF = frame[2];
        char oldL = frame[4];
        char oldB = frame[5];
        frame[1] = oldB;
        frame[2] = oldR;
        frame[4] = oldF;
        frame[5] = oldL;
    }

    private static String mapZ2Face(String move) {
        if (move == null || move.isEmpty()) return move;
        char face = move.charAt(0);
        char mapped;
        switch (face) {
            case 'U': mapped = 'D'; break;
            case 'D': mapped = 'U'; break;
            case 'R': mapped = 'L'; break;
            case 'L': mapped = 'R'; break;
            default: mapped = face; break;
        }
        return mapped + (move.length() > 1 ? move.substring(1) : "");
    }

    private static List<String> inverseMoves(List<String> moves) {
        List<String> out = new ArrayList<>();
        for (int i = moves.size() - 1; i >= 0; i--) {
            String move = moves.get(i);
            if (move.endsWith("2")) out.add(move);
            else if (move.endsWith("'")) out.add(move.substring(0, move.length() - 1));
            else out.add(move + "'");
        }
        return out;
    }

    private static String join(List<String> moves) {
        StringBuilder out = new StringBuilder();
        for (String move : moves) {
            if (out.length() > 0) out.append(' ');
            out.append(move);
        }
        return out.toString();
    }

    private static boolean isCrossSolved(String s) {
        return s.charAt(1) == 'U' && s.charAt(3) == 'U' && s.charAt(5) == 'U' && s.charAt(7) == 'U'
                && s.charAt(10) == 'R' && s.charAt(19) == 'F' && s.charAt(37) == 'L' && s.charAt(46) == 'B';
    }

    private static boolean isFirstLayerSolved(String s) {
        for (int i = 0; i < 9; i++) if (s.charAt(i) != 'U') return false;
        return rowMatches(s, 1, 0, 'R') && rowMatches(s, 2, 0, 'F')
                && rowMatches(s, 4, 0, 'L') && rowMatches(s, 5, 0, 'B');
    }

    private static boolean isSecondLayerSolved(String s) {
        return isFirstLayerSolved(s) && rowMatches(s, 1, 1, 'R') && rowMatches(s, 2, 1, 'F')
                && rowMatches(s, 4, 1, 'L') && rowMatches(s, 5, 1, 'B');
    }

    private static boolean isLastCrossSolved(String s) {
        return isSecondLayerSolved(s) && s.charAt(28) == 'D' && s.charAt(30) == 'D'
                && s.charAt(32) == 'D' && s.charAt(34) == 'D';
    }

    private static boolean isLastLayerOriented(String s) {
        if (!isSecondLayerSolved(s)) return false;
        for (int i = 27; i < 36; i++) if (s.charAt(i) != 'D') return false;
        return true;
    }

    private static boolean isSolved(String s) { return CubeState.SOLVED.equals(s); }

    private static boolean rowMatches(String s, int face, int row, char color) {
        int start = face * 9 + row * 3;
        return s.charAt(start) == color && s.charAt(start + 1) == color && s.charAt(start + 2) == color;
    }
}
