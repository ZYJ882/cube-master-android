package com.manus.cubemaster.solver;

import java.io.InputStream;

import com.manus.cubemaster.solver.utils.Corner;
import com.manus.cubemaster.solver.utils.Edge;

/** 面向应用的离线求解器门面，包含颜色数量、方向和奇偶性校验。 */
public final class SolverFacade {
    private static final String SOLVED = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";

    private SolverFacade() { }

    /** 在界面空闲时从类路径资源加载坐标和剪枝表，供宿主 JVM 调用。 */
    public static void warmUp() {
        Search.warmUp();
    }

    /** 在 Android 端从 APK AssetManager 打开的资源流加载坐标和剪枝表。 */
    public static void warmUp(InputStream tableResource) {
        CoordCube.initialize(tableResource);
    }

    public static boolean isSolved(String facelets) {
        return SOLVED.equals(facelets);
    }

    public static String validate(String facelets) {
        if (facelets == null || facelets.length() != 54) return "需要完整录入 54 个面片。";
        String order = "URFDLB";
        int[] counts = new int[6];
        for (int i = 0; i < facelets.length(); i++) {
            int color = order.indexOf(facelets.charAt(i));
            if (color < 0) return "存在不可识别的颜色。";
            counts[color]++;
        }
        for (int i = 0; i < 6; i++) {
            if (counts[i] != 9) return "每种颜色必须恰好 9 个；当前 " + order.charAt(i) + " 色为 " + counts[i] + " 个。";
            if (facelets.charAt(i * 9 + 4) != order.charAt(i)) return "六个中心块必须保持标准配色。";
        }
        try {
            CubieCube cube = new FaceCube(facelets).toCubieCube();
            int edgeFlip = 0;
            boolean[] seenEdges = new boolean[12];
            for (int i = 0; i < 12; i++) {
                int id = cube.ep[i].ordinal();
                if (seenEdges[id]) return "存在重复或缺失的棱块，请检查上色。";
                seenEdges[id] = true;
                edgeFlip += cube.eo[i];
            }
            if (edgeFlip % 2 != 0) return "棱块翻转方向不合法，请检查相邻两面的颜色。";

            int cornerTwist = 0;
            boolean[] seenCorners = new boolean[8];
            for (int i = 0; i < 8; i++) {
                int id = cube.cp[i].ordinal();
                if (seenCorners[id]) return "存在重复或缺失的角块，请检查上色。";
                seenCorners[id] = true;
                cornerTwist += cube.co[i];
            }
            if (cornerTwist % 3 != 0) return "角块朝向不合法，请检查角块的三种颜色。";
            if (cube.edgeParity() != cube.cornerParity()) return "棱块与角块奇偶性不一致，状态无法由正常转动得到。";
        } catch (RuntimeException e) {
            return "魔方状态无法识别，请检查录入颜色。";
        }
        return null;
    }

    public static String solve(String facelets) {
        String error = validate(facelets);
        if (error != null) throw new IllegalArgumentException(error);
        if (isSolved(facelets)) return "";
        String answer = Search.solution(facelets);
        if (answer == null || "Error".equalsIgnoreCase(answer.trim())) {
            throw new IllegalStateException("未能计算出解法，请检查录入状态。");
        }
        return answer.trim();
    }
}
