package com.manus.cubemaster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.manus.cubemaster.solver.CfopSolver;
import com.manus.cubemaster.solver.LayerByLayerSolver;
import com.manus.cubemaster.solver.PieceFirstSolver;
import com.manus.cubemaster.solver.RouxSolver;
import com.manus.cubemaster.solver.Search;
import com.manus.cubemaster.solver.SolverFacade;
import com.manus.cubemaster.solver.ZzSolver;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * 不依赖设备的关键回归测试。所有求解结果都按播放器相同的 CubeState 逐步回放，
 * 因而能同时发现动作映射、阶段收尾与最终复原一致性问题。
 */
public final class SolverRegressionTest {
    private static final List<String> SMOKE_SCRAMBLE = Arrays.asList("R", "U", "R'", "U'", "F2", "D");

    /** 与应用启动后的后台预热一致，避免把首次加载离线表的成本误算到单次路线规划。 */
    @BeforeClass
    public static void warmOfflineTables() {
        SolverFacade.warmUp();
        RouxSolver.warmUp();
        ZzSolver.warmUp();
        PieceFirstSolver.warmUp();
    }

    private static String scrambledFacelets() {
        CubeState state = new CubeState();
        state.applyMoves(SMOKE_SCRAMBLE);
        String facelets = state.facelets();
        assertFalse("烟雾乱序不能意外保持复原", CubeState.SOLVED.equals(facelets));
        assertEquals("烟雾乱序必须保持物理合法", null, SolverFacade.validate(facelets));
        return facelets;
    }

    private static void assertPlaybackSolves(String before, List<String> moves, String route) {
        assertNotNull(route + " 必须返回动作列表", moves);
        CubeState playback = new CubeState(before);
        playback.applyMoves(moves);
        assertEquals(route + " 动作逐步回放后必须完整复原", CubeState.SOLVED, playback.facelets());
    }

    @Test
    public void everyOuterAndMiddleMoveHasOrderFour() {
        for (String move : Arrays.asList("U", "R", "F", "D", "L", "B", "M", "E", "S")) {
            CubeState state = new CubeState();
            for (int turn = 0; turn < 4; turn++) state.applyMove(move);
            assertEquals(move + " 连续四次必须回到原状态", CubeState.SOLVED, state.facelets());
        }
    }

    @Test
    public void inverseMovesRestoreAnySupportedLayer() {
        for (String move : Arrays.asList("U", "R", "F", "D", "L", "B", "M", "E", "S")) {
            CubeState state = new CubeState();
            state.applyMove(move);
            state.applyMove(move + "'");
            assertEquals(move + " 与逆动作必须互相抵消", CubeState.SOLVED, state.facelets());
        }
    }

    @Test
    public void moveParsingIsStableUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        try {
            CubeState state = new CubeState();
            state.applyMove("r");
            state.applyMove("R'");
            assertEquals("内部动作记号不得受系统区域设置影响", CubeState.SOLVED, state.facelets());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void validationRejectsAnImpossibleSingleEdgeFlip() {
        char[] invalid = CubeState.SOLVED.toCharArray();
        char temporary = invalid[7];
        invalid[7] = invalid[19];
        invalid[19] = temporary;
        assertNotNull("单棱翻转必须被合法性检查阻止", SolverFacade.validate(new String(invalid)));
    }

    @Test
    public void kociembaSolutionReplaysToSolved() {
        String before = scrambledFacelets();
        assertPlaybackSolves(before, CubeState.parseMoves(SolverFacade.solve(before)), "Kociemba 两阶段");
    }

    @Test
    public void layerByLayerSolutionReplaysToSolved() {
        String before = scrambledFacelets();
        LayerByLayerSolver.Result result = LayerByLayerSolver.solve(before);
        assertFalse("层先法必须提供分阶段说明", result.stages().isEmpty());
        assertPlaybackSolves(before, result.moves(), "层先法");
    }

    @Test
    public void cfopSolutionReplaysToSolved() {
        String before = scrambledFacelets();
        CfopSolver.Result result = CfopSolver.solve(before);
        assertEquals("CFOP 必须包含 Cross、F2L、OLL、PLL 四阶段", 4, result.stages().size());
        assertPlaybackSolves(before, result.moves(), "CFOP");
    }

    @Test
    public void rouxSolutionReplaysToSolved() {
        String before = scrambledFacelets();
        RouxSolver.Result result = RouxSolver.solve(before);
        assertEquals("Roux 必须包含两个 Block、CMLL 与 LSE 四阶段", 4, result.stages().size());
        assertPlaybackSolves(before, result.moves(), "Roux");
    }

    @Test
    public void zzSolutionReplaysToSolved() {
        String before = scrambledFacelets();
        ZzSolver.Result result = ZzSolver.solve(before);
        assertTrue("ZZ 必须包含 EOLine、若干受限 ZZ-F2L、OCLL 与 PLL 阶段", result.stages().size() >= 4);
        assertPlaybackSolves(before, result.moves(), "ZZ");
    }

    @Test
    public void edgesFirstSolutionReplaysToSolved() {
        String before = scrambledFacelets();
        PieceFirstSolver.Result result = PieceFirstSolver.solveEdgesFirst(before);
        assertEquals("棱先必须包含完整十二棱与纯角收尾两个阶段", 2, result.stages().size());
        assertPlaybackSolves(before, result.moves(), "棱先");
    }

    @Test
    public void cornersFirstSolutionReplaysToSolved() {
        String before = scrambledFacelets();
        PieceFirstSolver.Result result = PieceFirstSolver.solveCornersFirst(before);
        assertEquals("角先必须包含完整八角与纯棱收尾两个阶段", 2, result.stages().size());
        assertPlaybackSolves(before, result.moves(), "角先");
    }

    @Test
    public void interruptedTwoPhaseSearchExitsBeforeLoadingOrSearching() {
        String before = scrambledFacelets();
        Thread.currentThread().interrupt();
        try {
            Search.solution(before);
            fail("已中断线程不得继续执行两阶段搜索");
        } catch (Search.SearchCancelledException expected) {
            assertTrue("取消异常应保留线程中断状态", Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void interruptedRouxRouteStopsBeforeStageSearch() {
        Thread.currentThread().interrupt();
        try {
            RouxSolver.solve(scrambledFacelets());
            fail("已中断线程不得继续执行 Roux 阶段搜索");
        } catch (CancellationException expected) {
            assertTrue("Roux 取消应保留线程中断状态", Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void interruptedEdgesFirstRouteStopsBeforeTargetSearch() {
        Thread.currentThread().interrupt();
        try {
            PieceFirstSolver.solveEdgesFirst(scrambledFacelets());
            fail("已中断线程不得继续执行棱先目标搜索");
        } catch (Search.SearchCancelledException expected) {
            assertTrue("棱先取消应保留线程中断状态", Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
