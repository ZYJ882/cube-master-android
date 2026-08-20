package com.manus.cubemaster;

import android.graphics.Color;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 三阶魔方的面片状态。面片顺序严格采用 URFDLB，兼容内置两阶段求解器。
 */
public final class CubeState {
    public static final String FACE_ORDER = "URFDLB";
    /** 尚未通过上色或识图确认的面片；三维模型显示为灰色，但不可进入求解器。 */
    public static final char UNKNOWN = '?';
    /** 外层 6 面与中层 3 切片。M/E/S 分别遵循 L/D/F 的标准方向。 */
    public static final String MOVE_ORDER = "URFDLBMES";
    public static final String SOLVED = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";

    private static final int[][][] DESCRIPTORS = new int[54][2][3];
    private static final int[][] MOVE_MAPS = new int[9][54];
    private static final int[][] WHOLE_ROTATION_MAPS = new int[3][54];
    private static final int[][] MOVE_AXES = {
            {0, 1, 0}, {1, 0, 0}, {0, 0, 1}, {0, -1, 0}, {-1, 0, 0}, {0, 0, -1},
            {-1, 0, 0}, {0, -1, 0}, {0, 0, 1}
    };
    private static final int[] MOVE_LAYERS = {1, 1, 1, 1, 1, 1, 0, 0, 0};

    static {
        for (int face = 0; face < 6; face++) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int index = face * 9 + row * 3 + col;
                    int[] p = positionFor(face, row, col);
                    int[] n = normalFor(face);
                    DESCRIPTORS[index][0] = p;
                    DESCRIPTORS[index][1] = n;
                }
            }
        }
        for (int move = 0; move < MOVE_ORDER.length(); move++) {
            int[] axis = MOVE_AXES[move];
            int layer = MOVE_LAYERS[move];
            for (int i = 0; i < 54; i++) MOVE_MAPS[move][i] = i;
            for (int source = 0; source < 54; source++) {
                int[] p = DESCRIPTORS[source][0];
                int[] n = DESCRIPTORS[source][1];
                if (dot(p, axis) == layer) {
                    int[] rotatedP = rotateClockwise(p, axis);
                    int[] rotatedN = rotateClockwise(n, axis);
                    int destination = descriptorIndex(rotatedP, rotatedN);
                    MOVE_MAPS[move][destination] = source;
                }
            }
        }
        int[][] wholeAxes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int rotation = 0; rotation < wholeAxes.length; rotation++) {
            int[] axis = wholeAxes[rotation];
            for (int source = 0; source < 54; source++) {
                int[] rotatedP = rotateClockwise(DESCRIPTORS[source][0], axis);
                int[] rotatedN = rotateClockwise(DESCRIPTORS[source][1], axis);
                int destination = descriptorIndex(rotatedP, rotatedN);
                WHOLE_ROTATION_MAPS[rotation][destination] = source;
            }
        }
    }

    private final char[] stickers = SOLVED.toCharArray();

    public CubeState() { }

    public CubeState(String facelets) {
        setFacelets(facelets);
    }

    public void reset() {
        setFacelets(SOLVED);
    }

    public void setFacelets(String facelets) {
        if (facelets == null || facelets.length() != 54) {
            throw new IllegalArgumentException("魔方状态必须包含 54 个面片");
        }
        for (int i = 0; i < 54; i++) {
            char color = Character.toUpperCase(facelets.charAt(i));
            if (FACE_ORDER.indexOf(color) < 0 && color != UNKNOWN) {
                throw new IllegalArgumentException("存在无法识别的面片颜色");
            }
            stickers[i] = color;
        }
    }

    public String facelets() {
        return new String(stickers);
    }

    public char get(int index) {
        return stickers[index];
    }

    public void set(int index, char color) {
        if (index < 0 || index >= 54 || (FACE_ORDER.indexOf(color) < 0 && color != UNKNOWN)) {
            throw new IllegalArgumentException("无效面片");
        }
        stickers[index] = color;
    }

    public void setFace(int faceIndex, char[] colors) {
        if (faceIndex < 0 || faceIndex > 5 || colors == null || colors.length != 9) {
            throw new IllegalArgumentException("无效面数据");
        }
        for (int i = 0; i < 9; i++) {
            if (FACE_ORDER.indexOf(colors[i]) < 0 && colors[i] != UNKNOWN) {
                throw new IllegalArgumentException("无效颜色");
            }
            stickers[faceIndex * 9 + i] = colors[i];
        }
        stickers[faceIndex * 9 + 4] = FACE_ORDER.charAt(faceIndex);
    }

    public int colorCount(char color) {
        int count = 0;
        for (char sticker : stickers) if (sticker == color) count++;
        return count;
    }

    public boolean hasUnknownStickers() {
        for (char sticker : stickers) if (sticker == UNKNOWN) return true;
        return false;
    }

    public int unknownStickerCount() {
        int count = 0;
        for (char sticker : stickers) if (sticker == UNKNOWN) count++;
        return count;
    }

    public void applyMove(String notation) {
        if (notation == null || notation.trim().isEmpty()) return;
        String move = notation.trim().toUpperCase(Locale.ROOT);
        int moveIndex = MOVE_ORDER.indexOf(move.charAt(0));
        if (moveIndex < 0) throw new IllegalArgumentException("不支持的转动：" + notation);
        int turns = move.endsWith("2") ? 2 : (move.endsWith("'") ? 3 : 1);
        for (int i = 0; i < turns; i++) applyQuarter(moveIndex);
    }

    public void applyMoves(List<String> moves) {
        for (String move : moves) applyMove(move);
    }

    public static List<String> parseMoves(String solution) {
        if (solution == null || solution.trim().isEmpty()) return Collections.emptyList();
        String[] parts = solution.trim().split("\\s+");
        List<String> moves = new ArrayList<>();
        for (String part : parts) {
            if (part.matches("[URFDLBMES](2|')?")) moves.add(part);
        }
        return moves;
    }

    /** 返回标准求解器所需的中心块朝向；中层切片后可用于将整体视图重新对齐。 */
    public String normalizeForSolver() {
        String start = facelets();
        if (hasStandardCenters(start)) return start;
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String state = queue.removeFirst();
            if (hasStandardCenters(state)) return state;
            for (int rotation = 0; rotation < 3; rotation++) {
                String next = applyMap(state, WHOLE_ROTATION_MAPS[rotation]);
                if (visited.add(next)) queue.addLast(next);
            }
        }
        return start;
    }

    public boolean normalizeOrientationForSolver() {
        String normalized = normalizeForSolver();
        if (normalized.equals(facelets())) return false;
        setFacelets(normalized);
        return true;
    }

    public static int[] rotationAxisForMove(char move) {
        int index = MOVE_ORDER.indexOf(Character.toUpperCase(move));
        if (index < 0) throw new IllegalArgumentException("不支持的转动：" + move);
        return MOVE_AXES[index].clone();
    }

    public static int rotationLayerForMove(char move) {
        int index = MOVE_ORDER.indexOf(Character.toUpperCase(move));
        if (index < 0) throw new IllegalArgumentException("不支持的转动：" + move);
        return MOVE_LAYERS[index];
    }

    private static boolean hasStandardCenters(String state) {
        for (int face = 0; face < FACE_ORDER.length(); face++) {
            if (state.charAt(face * 9 + 4) != FACE_ORDER.charAt(face)) return false;
        }
        return true;
    }

    private static String applyMap(String state, int[] map) {
        char[] before = state.toCharArray();
        char[] after = new char[54];
        for (int destination = 0; destination < 54; destination++) after[destination] = before[map[destination]];
        return new String(after);
    }

    public static int stickerIndex(int face, int row, int col) {
        return face * 9 + row * 3 + col;
    }

    public static int stickerIndexForSurface(int face, int x, int y, int z) {
        int row;
        int col;
        switch (face) {
            case 0: row = z + 1; col = x + 1; break;           // U
            case 1: row = 1 - y; col = 1 - z; break;           // R
            case 2: row = 1 - y; col = x + 1; break;           // F
            case 3: row = 1 - z; col = x + 1; break;           // D
            case 4: row = 1 - y; col = z + 1; break;           // L
            case 5: row = 1 - y; col = 1 - x; break;           // B
            default: throw new IllegalArgumentException("无效面");
        }
        return stickerIndex(face, row, col);
    }

    public static int colorArgb(char faceColor) {
        switch (faceColor) {
            case 'U': return Color.rgb(246, 248, 250); // 白
            case 'R': return Color.rgb(235, 83, 83);   // 红
            case 'F': return Color.rgb(38, 181, 112);  // 绿
            case 'D': return Color.rgb(250, 202, 68);  // 黄
            case 'L': return Color.rgb(247, 139, 55);  // 橙
            case 'B': return Color.rgb(66, 133, 244);  // 蓝
            default: return Color.rgb(104, 121, 139);
        }
    }

    private void applyQuarter(int face) {
        char[] before = stickers.clone();
        int[] map = MOVE_MAPS[face];
        for (int destination = 0; destination < 54; destination++) {
            stickers[destination] = before[map[destination]];
        }
    }

    private static int[] positionFor(int face, int row, int col) {
        switch (face) {
            case 0: return new int[]{col - 1, 1, row - 1};             // U: 后至前
            case 1: return new int[]{1, 1 - row, 1 - col};             // R: 前至后
            case 2: return new int[]{col - 1, 1 - row, 1};             // F
            case 3: return new int[]{col - 1, -1, 1 - row};            // D: 前至后
            case 4: return new int[]{-1, 1 - row, col - 1};            // L: 后至前
            case 5: return new int[]{1 - col, 1 - row, -1};            // B: 右至左
            default: throw new IllegalArgumentException("无效面");
        }
    }

    private static int[] normalFor(int face) {
        switch (face) {
            case 0: return new int[]{0, 1, 0};
            case 1: return new int[]{1, 0, 0};
            case 2: return new int[]{0, 0, 1};
            case 3: return new int[]{0, -1, 0};
            case 4: return new int[]{-1, 0, 0};
            case 5: return new int[]{0, 0, -1};
            default: throw new IllegalArgumentException("无效面");
        }
    }

    /** 从外侧观察面的顺时针 90 度旋转。 */
    private static int[] rotateClockwise(int[] vector, int[] axis) {
        int dot = dot(axis, vector);
        int[] cross = cross(axis, vector);
        return new int[]{
                axis[0] * dot - cross[0],
                axis[1] * dot - cross[1],
                axis[2] * dot - cross[2]
        };
    }

    private static int descriptorIndex(int[] position, int[] normal) {
        for (int i = 0; i < 54; i++) {
            if (Arrays.equals(position, DESCRIPTORS[i][0]) && Arrays.equals(normal, DESCRIPTORS[i][1])) {
                return i;
            }
        }
        throw new IllegalStateException("无法映射面片位置");
    }

    private static int dot(int[] a, int[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static int[] cross(int[] a, int[] b) {
        return new int[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }
}
