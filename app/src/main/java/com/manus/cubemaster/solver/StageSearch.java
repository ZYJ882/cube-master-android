package com.manus.cubemaster.solver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 有约束的逐阶段 IDA* 搜索。它不调用两阶段完整解；每个阶段都由目标块/面向的投影距离表直接引导。
 * 坐标和剪枝结构参考 torjusti/cube-solver（MIT）；本实现按 CubeMaster 的 CubieCube 约定重新编写。
 */
final class StageSearch {
    static final int U = 0;
    static final int R = 3;
    static final int F = 6;
    static final int D = 9;
    static final int L = 12;
    static final int B = 15;
    static final int M = 18;

    static final int[] ALL_OUTER_MOVES = range(0, 18);
    static final int[] RLU_MOVES = {0, 1, 2, 3, 4, 5, 12, 13, 14};
    static final int[] RLUD_MOVES = {0, 1, 2, 3, 4, 5, 9, 10, 11, 12, 13, 14};
    static final int[] MU_MOVES = {0, 1, 2, 18, 19, 20};

    private static final Transform[] TRANSFORMS = new Transform[21];
    private static final Map<String, DistanceOracle> ORACLE_CACHE = new HashMap<>();
    private static final int ROUX_TABLE_MAGIC = 0x434D5231; // CMR1
    private static final int ROUX_TABLE_VERSION = 1;

    static {
        for (int move = 0; move < 18; move++) {
            CubieCube cube = new CubieCube();
            CubieCube base = CubieCube.moveCube[move / 3];
            for (int turn = 0; turn <= move % 3; turn++) {
                cube.cornerMultiply(base);
                cube.edgeMultiply(base);
            }
            TRANSFORMS[move] = Transform.from(cube);
        }
        // M 的方向与 CubeState.MOVE_ORDER 的定义相同（遵循 L 的标准方向）。
        Transform middle = new Transform(
                new int[]{0, 1, 2, 3, 4, 5, 6, 7},
                new int[]{0, 0, 0, 0, 0, 0, 0, 0},
                new int[]{0, 3, 2, 7, 4, 1, 6, 5, 8, 9, 10, 11},
                new int[]{0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0}
        );
        TRANSFORMS[M] = middle;
        TRANSFORMS[M + 1] = Transform.compose(middle, middle);
        TRANSFORMS[M + 2] = Transform.compose(TRANSFORMS[M + 1], middle);
    }

    private StageSearch() { }

    static List<String> solve(CubieCube cube, Goal goal, int[] moves, int maxDepth, long timeoutMs) {
        Snapshot initial = Snapshot.from(cube);
        if (goal.isSolved(initial)) return new ArrayList<>();
        long deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L;
        int lowerBound = goal.heuristic(initial);
        int[] path = new int[Math.max(1, maxDepth)];
        for (int depth = lowerBound; depth <= maxDepth; depth++) {
            if (dfs(initial, goal, moves, depth, -1, path, 0, deadlineNanos)) {
                List<String> out = new ArrayList<>();
                for (int i = 0; i < depth; i++) out.add(notation(path[i]));
                return out;
            }
            if (System.nanoTime() >= deadlineNanos) return null;
        }
        return null;
    }

    static CubieCube apply(CubieCube source, List<String> moves) {
        Snapshot snapshot = Snapshot.from(source);
        for (String move : moves) snapshot.apply(parse(move));
        return snapshot.toCubieCube();
    }

    private static boolean dfs(Snapshot current, Goal goal, int[] moves, int remaining, int lastFace,
                               int[] path, int used, long deadlineNanos) {
        if (System.nanoTime() >= deadlineNanos) return false;
        int h = goal.heuristic(current);
        if (h > remaining) return false;
        if (goal.isSolved(current)) return true;
        if (remaining == 0) return false;
        for (int move : moves) {
            int face = faceOf(move);
            if (face == lastFace || (opposite(face, lastFace) && face < lastFace)) continue;
            Snapshot next = current.copy();
            next.apply(move);
            path[used] = move;
            if (dfs(next, goal, moves, remaining - 1, face, path, used + 1, deadlineNanos)) return true;
        }
        return false;
    }

    static Goal goal(DistanceOracle... oracles) {
        return new Goal(oracles);
    }

    static DistanceOracle edges(int[] pieces, int[] moves) {
        return getOracle(new PieceOracle(false, pieces, moves));
    }

    static DistanceOracle corners(int[] pieces, int[] moves) {
        return getOracle(new PieceOracle(true, pieces, moves));
    }

    /** 精确联合跟踪一个 Roux 1×2×3 Block 的三条棱和两只角，作为单一剪枝坐标。 */
    static DistanceOracle block(int[] edgePieces, int[] cornerPieces, int[] moves) {
        return getOracle(new BlockOracle(edgePieces, cornerPieces, moves));
    }

    static DistanceOracle allEdgeOrientation(int[] moves) {
        return getOracle(new EdgeOrientationOracle(moves));
    }

    static DistanceOracle allCornerOrientation(int[] moves) {
        return getOracle(new CornerOrientationOracle(moves));
    }

    /** 将四张 Roux Block 精确距离表写入可随 APK 发布的紧凑二进制资源。仅供构建期生成器使用。 */
    static void writeRouxBlockTables(OutputStream output) throws IOException {
        DataOutputStream data = new DataOutputStream(output);
        data.writeInt(ROUX_TABLE_MAGIC);
        data.writeInt(ROUX_TABLE_VERSION);
        BlockOracle[] tables = rouxBlockOracles();
        data.writeInt(tables.length);
        for (BlockOracle candidate : tables) {
            DistanceOracle cached = getOracle(candidate);
            if (!(cached instanceof BlockOracle)) throw new IOException("Roux Block 表缓存类型异常");
            BlockOracle table = (BlockOracle) cached;
            data.writeUTF(table.cacheKey());
            table.writePacked(data);
        }
        data.flush();
    }

    /** 从 APK 资源直接加载四张 Roux Block 精确距离表；内容和坐标键均经过版本及键值校验。 */
    static void loadRouxBlockTables(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        if (data.readInt() != ROUX_TABLE_MAGIC || data.readInt() != ROUX_TABLE_VERSION) {
            throw new IOException("Roux 剪枝表资源版本不匹配");
        }
        BlockOracle[] tables = rouxBlockOracles();
        if (data.readInt() != tables.length) throw new IOException("Roux 剪枝表资源数量不匹配");
        for (BlockOracle table : tables) {
            String key = data.readUTF();
            if (!table.cacheKey().equals(key)) throw new IOException("Roux 剪枝表坐标不匹配");
            table.readPacked(data);
            synchronized (ORACLE_CACHE) {
                ORACLE_CACHE.put(table.cacheKey(), table);
            }
        }
    }

    private static BlockOracle[] rouxBlockOracles() {
        return new BlockOracle[]{
                new BlockOracle(new int[]{6, 9, 10}, new int[]{5, 6}, ALL_OUTER_MOVES),
                new BlockOracle(new int[]{4, 8, 11}, new int[]{4, 7}, ALL_OUTER_MOVES),
                new BlockOracle(new int[]{6, 9, 10}, new int[]{5, 6}, RLU_MOVES),
                new BlockOracle(new int[]{4, 8, 11}, new int[]{4, 7}, RLU_MOVES)
        };
    }

    /**
     * 查表构建可能持续数秒；不能持有全局缓存锁执行 BFS，否则后台预热 Roux 时会阻塞已就绪的
     * ZZ 阶段查询。构建完成后再以短锁提交缓存；极端竞争下宁可丢弃一个重复构建结果，也不阻塞交互求解。
     */
    private static DistanceOracle getOracle(DistanceOracle oracle) {
        String key = oracle.cacheKey();
        synchronized (ORACLE_CACHE) {
            DistanceOracle cached = ORACLE_CACHE.get(key);
            if (cached != null) return cached;
        }
        oracle.initialize();
        synchronized (ORACLE_CACHE) {
            DistanceOracle cached = ORACLE_CACHE.get(key);
            if (cached != null) return cached;
            ORACLE_CACHE.put(key, oracle);
            return oracle;
        }
    }

    private static int[] range(int start, int end) {
        int[] values = new int[end - start];
        for (int i = 0; i < values.length; i++) values[i] = start + i;
        return values;
    }

    private static boolean opposite(int a, int b) {
        return (a == 0 && b == 3) || (a == 3 && b == 0)
                || (a == 1 && b == 4) || (a == 4 && b == 1)
                || (a == 2 && b == 5) || (a == 5 && b == 2);
    }

    private static int faceOf(int move) {
        return move >= M ? 6 : move / 3;
    }

    private static String notation(int move) {
        char face = move < M ? "URFDLB".charAt(move / 3) : 'M';
        int power = move % 3;
        return face + (power == 1 ? "2" : power == 2 ? "'" : "");
    }

    private static int parse(String notation) {
        char face = notation.charAt(0);
        int base = "URFDLB".indexOf(face) * 3;
        if (face == 'M') base = M;
        if (notation.endsWith("2")) return base + 1;
        if (notation.endsWith("'")) return base + 2;
        return base;
    }

    static final class Goal {
        private final DistanceOracle[] oracles;

        Goal(DistanceOracle[] oracles) {
            this.oracles = oracles;
        }

        int heuristic(Snapshot cube) {
            int value = 0;
            for (DistanceOracle oracle : oracles) value = Math.max(value, oracle.distance(cube));
            return value;
        }

        boolean isSolved(Snapshot cube) {
            for (DistanceOracle oracle : oracles) if (!oracle.isSolved(cube)) return false;
            return true;
        }
    }

    abstract static class DistanceOracle {
        final int[] moves;

        DistanceOracle(int[] moves) {
            this.moves = moves.clone();
        }

        abstract String cacheKey();
        abstract void initialize();
        abstract int distance(Snapshot cube);
        abstract boolean isSolved(Snapshot cube);
    }

    /** Tracks selected cubies' exact position and orientation, giving a compact exact lower bound. */
    private static final class PieceOracle extends DistanceOracle {
        private final boolean corner;
        private final int[] pieces;
        private final int base;
        private final int states;
        private final int tableSize;
        private final int goalKey;
        private byte[] distances;
        private Map<Integer, Byte> sparseDistances;

        PieceOracle(boolean corner, int[] pieces, int[] moves) {
            super(moves);
            this.corner = corner;
            this.pieces = pieces.clone();
            this.states = corner ? 3 : 2;
            this.base = (corner ? 8 : 12) * states;
            long size = 1;
            long goal = 0;
            long multiplier = 1;
            for (int piece : pieces) {
                goal += (long) (piece * states) * multiplier;
                multiplier *= base;
                size *= base;
            }
            if (size > Integer.MAX_VALUE) throw new IllegalArgumentException("阶段投影过大");
            this.tableSize = (int) size;
            this.goalKey = (int) goal;
        }

        @Override String cacheKey() {
            return "pieces:" + (corner ? 'c' : 'e') + ':' + Arrays.toString(pieces) + ':' + Arrays.toString(moves);
        }

        @Override void initialize() {
            // 数组表适合 Block/CMLL/F2L 投影；LSE 六棱投影仅含约 4.6 万个可达状态，用稀疏表避免 191 MB。
            if (tableSize <= 8_500_000) {
                distances = new byte[tableSize];
                Arrays.fill(distances, (byte) -1);
                fillArrayTable();
            } else {
                sparseDistances = new HashMap<>();
                fillSparseTable();
            }
        }

        private void fillArrayTable() {
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            distances[goalKey] = 0;
            queue.add(goalKey);
            while (!queue.isEmpty()) {
                int key = queue.removeFirst();
                int nextDepth = distances[key] + 1;
                for (int move : moves) {
                    int next = transition(key, move);
                    if (distances[next] < 0) {
                        distances[next] = (byte) nextDepth;
                        queue.addLast(next);
                    }
                }
            }
        }

        private void fillSparseTable() {
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            sparseDistances.put(goalKey, (byte) 0);
            queue.add(goalKey);
            while (!queue.isEmpty()) {
                int key = queue.removeFirst();
                byte nextDepth = (byte) (sparseDistances.get(key) + 1);
                for (int move : moves) {
                    int next = transition(key, move);
                    if (!sparseDistances.containsKey(next)) {
                        sparseDistances.put(next, nextDepth);
                        queue.addLast(next);
                    }
                }
            }
        }

        private int transition(int key, int move) {
            Transform transform = TRANSFORMS[move];
            int multiplier = 1;
            int result = 0;
            for (int ignored : pieces) {
                int value = (key / multiplier) % base;
                int position = value / states;
                int orientation = value % states;
                int nextPosition = corner ? transform.cornerDest[position] : transform.edgeDest[position];
                int delta = corner ? transform.co[nextPosition] : transform.eo[nextPosition];
                int nextValue = nextPosition * states + ((orientation + delta) % states);
                result += nextValue * multiplier;
                multiplier *= base;
            }
            return result;
        }

        private int keyOf(Snapshot cube) {
            int result = 0;
            int multiplier = 1;
            for (int piece : pieces) {
                int position = corner ? cube.cornerPosition(piece) : cube.edgePosition(piece);
                int orientation = corner ? cube.co[position] : cube.eo[position];
                result += (position * states + orientation) * multiplier;
                multiplier *= base;
            }
            return result;
        }

        @Override int distance(Snapshot cube) {
            int key = keyOf(cube);
            if (distances != null) return distances[key] & 0xFF;
            Byte result = sparseDistances.get(key);
            return result == null ? 0 : result & 0xFF;
        }

        @Override boolean isSolved(Snapshot cube) {
            return keyOf(cube) == goalKey;
        }
    }

    /** 联合三棱两角坐标的精确距离表，针对 Roux 块构建比独立投影提供更强下界。 */
    private static final class BlockOracle extends DistanceOracle {
        private final PieceOracle edges;
        private final PieceOracle corners;
        private final int edgeSize;
        private final int size;
        private final int goalKey;
        private byte[] distances;
        /** 资源加载后每个距离使用半字节；构建期 BFS 保持字节表示以保留 -1 未访问标记。 */
        private boolean packedDistances;

        BlockOracle(int[] edgePieces, int[] cornerPieces, int[] moves) {
            super(moves);
            edges = new PieceOracle(false, edgePieces, moves);
            corners = new PieceOracle(true, cornerPieces, moves);
            edgeSize = edges.tableSize;
            long combined = (long) edgeSize * corners.tableSize;
            if (combined > 16_000_000L) throw new IllegalArgumentException("Block 剪枝表过大");
            size = (int) combined;
            goalKey = edges.goalKey + edgeSize * corners.goalKey;
        }

        @Override String cacheKey() {
            return "block:" + edges.cacheKey() + ':' + corners.cacheKey();
        }

        @Override void initialize() {
            distances = new byte[size];
            Arrays.fill(distances, (byte) -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            distances[goalKey] = 0;
            queue.add(goalKey);
            while (!queue.isEmpty()) {
                int key = queue.removeFirst();
                int edgeKey = key % edgeSize;
                int cornerKey = key / edgeSize;
                int nextDepth = distances[key] + 1;
                for (int move : moves) {
                    int next = edges.transition(edgeKey, move) + edgeSize * corners.transition(cornerKey, move);
                    if (distances[next] < 0) {
                        distances[next] = (byte) nextDepth;
                        queue.addLast(next);
                    }
                }
            }
        }

        private int keyOf(Snapshot cube) {
            return edges.keyOf(cube) + edgeSize * corners.keyOf(cube);
        }

        void writePacked(DataOutputStream data) throws IOException {
            int packedSize = (size + 1) >>> 1;
            data.writeInt(size);
            data.writeInt(packedSize);
            if (packedDistances) {
                data.write(distances);
                return;
            }
            for (int index = 0; index < size; index += 2) {
                int low = distances[index] & 0xFF;
                int high = index + 1 < size ? distances[index + 1] & 0xFF : 0;
                // 255 是构建期 BFS 的不可达投影标记；资源中以 15 保存，不会出现在当前阶段的可达状态上。
                low = low == 255 ? 15 : low;
                high = high == 255 ? 15 : high;
                if (low > 15 || high > 15) throw new IOException("Roux Block 距离超过紧凑表范围");
                data.writeByte(low | (high << 4));
            }
        }

        void readPacked(DataInputStream data) throws IOException {
            int storedSize = data.readInt();
            int packedSize = data.readInt();
            int expectedSize = (size + 1) >>> 1;
            if (storedSize != size || packedSize != expectedSize) throw new IOException("Roux Block 表大小不匹配");
            distances = new byte[packedSize];
            data.readFully(distances);
            packedDistances = true;
        }

        @Override int distance(Snapshot cube) {
            int key = keyOf(cube);
            if (!packedDistances) return distances[key] & 0xFF;
            int value = distances[key >>> 1] & 0xFF;
            return (key & 1) == 0 ? value & 0x0F : value >>> 4;
        }

        @Override boolean isSolved(Snapshot cube) {
            return keyOf(cube) == goalKey;
        }
    }

    /** Exact lower bound for the complete 12-edge orientation coordinate used by ZZ EOLine. */
    private static final class EdgeOrientationOracle extends DistanceOracle {
        private final byte[] distances = new byte[2048];

        EdgeOrientationOracle(int[] moves) {
            super(moves);
        }

        @Override String cacheKey() {
            return "edge-orientation:" + Arrays.toString(moves);
        }

        @Override void initialize() {
            Arrays.fill(distances, (byte) -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            distances[0] = 0;
            queue.add(0);
            while (!queue.isEmpty()) {
                int key = queue.removeFirst();
                int nextDepth = distances[key] + 1;
                for (int move : moves) {
                    int next = transition(key, move);
                    if (distances[next] < 0) {
                        distances[next] = (byte) nextDepth;
                        queue.add(next);
                    }
                }
            }
        }

        private int transition(int key, int move) {
            int[] orientation = new int[12];
            for (int index = 10; index >= 0; index--) {
                orientation[index] = key & 1;
                key >>= 1;
            }
            orientation[11] = 0;
            for (int index = 0; index < 11; index++) orientation[11] ^= orientation[index];
            Transform transform = TRANSFORMS[move];
            int result = 0;
            for (int destination = 0; destination < 11; destination++) {
                int source = transform.ep[destination];
                int nextOrientation = orientation[source] ^ transform.eo[destination];
                result = result * 2 + nextOrientation;
            }
            return result;
        }

        private int keyOf(Snapshot cube) {
            int result = 0;
            for (int index = 0; index < 11; index++) result = result * 2 + cube.eo[index];
            return result;
        }

        @Override int distance(Snapshot cube) {
            return distances[keyOf(cube)] & 0xFF;
        }

        @Override boolean isSolved(Snapshot cube) {
            return keyOf(cube) == 0;
        }
    }

    /** Exact lower bound for the complete corner-orientation coordinate used by ZZ OCLL. */
    private static final class CornerOrientationOracle extends DistanceOracle {
        private final byte[] distances = new byte[2187];

        CornerOrientationOracle(int[] moves) {
            super(moves);
        }

        @Override String cacheKey() {
            return "corner-orientation:" + Arrays.toString(moves);
        }

        @Override void initialize() {
            Arrays.fill(distances, (byte) -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            distances[0] = 0;
            queue.add(0);
            while (!queue.isEmpty()) {
                int key = queue.removeFirst();
                int nextDepth = distances[key] + 1;
                for (int move : moves) {
                    int next = transition(key, move);
                    if (distances[next] < 0) {
                        distances[next] = (byte) nextDepth;
                        queue.add(next);
                    }
                }
            }
        }

        private int transition(int key, int move) {
            int[] orientation = new int[8];
            for (int index = 6; index >= 0; index--) {
                orientation[index] = key % 3;
                key /= 3;
            }
            orientation[7] = 0;
            for (int index = 0; index < 7; index++) orientation[7] = (orientation[7] + orientation[index]) % 3;
            orientation[7] = (3 - orientation[7]) % 3;
            Transform transform = TRANSFORMS[move];
            int result = 0;
            for (int destination = 0; destination < 7; destination++) {
                int source = transform.cp[destination];
                result = result * 3 + ((orientation[source] + transform.co[destination]) % 3);
            }
            return result;
        }

        private int keyOf(Snapshot cube) {
            int result = 0;
            for (int index = 0; index < 7; index++) result = result * 3 + cube.co[index];
            return result;
        }

        @Override int distance(Snapshot cube) {
            return distances[keyOf(cube)] & 0xFF;
        }

        @Override boolean isSolved(Snapshot cube) {
            return keyOf(cube) == 0;
        }
    }

    private static final class Transform {
        final int[] cp;
        final int[] co;
        final int[] ep;
        final int[] eo;
        final int[] cornerDest = new int[8];
        final int[] edgeDest = new int[12];

        Transform(int[] cp, int[] co, int[] ep, int[] eo) {
            this.cp = cp;
            this.co = co;
            this.ep = ep;
            this.eo = eo;
            for (int dest = 0; dest < 8; dest++) cornerDest[cp[dest]] = dest;
            for (int dest = 0; dest < 12; dest++) edgeDest[ep[dest]] = dest;
        }

        static Transform from(CubieCube cube) {
            int[] cp = new int[8];
            int[] co = new int[8];
            int[] ep = new int[12];
            int[] eo = new int[12];
            for (int i = 0; i < 8; i++) { cp[i] = cube.cp[i].ordinal(); co[i] = cube.co[i]; }
            for (int i = 0; i < 12; i++) { ep[i] = cube.ep[i].ordinal(); eo[i] = cube.eo[i]; }
            return new Transform(cp, co, ep, eo);
        }

        static Transform compose(Transform first, Transform second) {
            int[] cp = new int[8];
            int[] co = new int[8];
            int[] ep = new int[12];
            int[] eo = new int[12];
            for (int destination = 0; destination < 8; destination++) {
                cp[destination] = first.cp[second.cp[destination]];
                co[destination] = (first.co[second.cp[destination]] + second.co[destination]) % 3;
            }
            for (int destination = 0; destination < 12; destination++) {
                ep[destination] = first.ep[second.ep[destination]];
                eo[destination] = (first.eo[second.ep[destination]] + second.eo[destination]) & 1;
            }
            return new Transform(cp, co, ep, eo);
        }
    }

    static final class Snapshot {
        final int[] cp;
        final int[] co;
        final int[] ep;
        final int[] eo;

        Snapshot(int[] cp, int[] co, int[] ep, int[] eo) {
            this.cp = cp;
            this.co = co;
            this.ep = ep;
            this.eo = eo;
        }

        static Snapshot from(CubieCube cube) {
            int[] cp = new int[8];
            int[] co = new int[8];
            int[] ep = new int[12];
            int[] eo = new int[12];
            for (int i = 0; i < 8; i++) { cp[i] = cube.cp[i].ordinal(); co[i] = cube.co[i]; }
            for (int i = 0; i < 12; i++) { ep[i] = cube.ep[i].ordinal(); eo[i] = cube.eo[i]; }
            return new Snapshot(cp, co, ep, eo);
        }

        Snapshot copy() {
            return new Snapshot(cp.clone(), co.clone(), ep.clone(), eo.clone());
        }

        void apply(int move) {
            Transform transform = TRANSFORMS[move];
            int[] oldCp = cp.clone();
            int[] oldCo = co.clone();
            int[] oldEp = ep.clone();
            int[] oldEo = eo.clone();
            for (int destination = 0; destination < 8; destination++) {
                cp[destination] = oldCp[transform.cp[destination]];
                co[destination] = (oldCo[transform.cp[destination]] + transform.co[destination]) % 3;
            }
            for (int destination = 0; destination < 12; destination++) {
                ep[destination] = oldEp[transform.ep[destination]];
                eo[destination] = (oldEo[transform.ep[destination]] + transform.eo[destination]) & 1;
            }
        }

        int edgePosition(int target) {
            for (int position = 0; position < 12; position++) if (ep[position] == target) return position;
            throw new IllegalStateException("缺少棱块");
        }

        int cornerPosition(int target) {
            for (int position = 0; position < 8; position++) if (cp[position] == target) return position;
            throw new IllegalStateException("缺少角块");
        }

        CubieCube toCubieCube() {
            CubieCube out = new CubieCube();
            for (int i = 0; i < 8; i++) {
                out.cp[i] = com.manus.cubemaster.solver.utils.Corner.values()[cp[i]];
                out.co[i] = (byte) co[i];
            }
            for (int i = 0; i < 12; i++) {
                out.ep[i] = com.manus.cubemaster.solver.utils.Edge.values()[ep[i]];
                out.eo[i] = (byte) eo[i];
            }
            return out;
        }
    }
}
