package com.manus.cubemaster.solver;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 魔方坐标层表示。
 *
 * <p>移动设备运行时不再重建坐标表和剪枝表。所有可变查表数据由构建期导出为
 * {@code kociemba_tables_v1.bin} 并随 APK 一起发布；求解器初始化仅负责校验并加载该资源。</p>
 */
public final class CoordCube {
    public static final short N_TWIST = 2187;
    public static final short N_FLIP = 2048;
    static final short N_SLICE1 = 495;
    static final short N_SLICE2 = 24;
    private static final short N_PARITY = 2;
    private static final short N_URFtoDLF = 20160;
    private static final short N_FRtoBR = 11880;
    private static final short N_URtoUL = 1320;
    private static final short N_UBtoDF = 1320;
    private static final short N_URtoDF = 20160;
    public static final int N_URFtoDLB = 40320;
    public static final int N_URtoBR = 479001600;
    private static final short N_MOVE = 18;

    private static final int TABLE_MAGIC = 0x434D5431; // CMT1
    private static final int TABLE_FORMAT_VERSION = 1;
    private static final int TABLE_END_MARKER = 0x454E4431; // END1
    private static final String TABLE_RESOURCE = "/kociemba_tables_v1.bin";

    private static volatile boolean tablesReady = false;

    short twist;
    short flip;
    short parity;
    short FRtoBR;
    short URFtoDLF;
    short URtoUL;
    short UBtoDF;

    /** 固定的 18 步奇偶转换表，不需要从资源读取。 */
    static final short[][] parityMove = {
            {1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1},
            {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0}
    };

    static short[][] twistMove = new short[N_TWIST][N_MOVE];
    static short[][] flipMove = new short[N_FLIP][N_MOVE];
    static short[][] FRtoBR_Move = new short[N_FRtoBR][N_MOVE];
    static short[][] URFtoDLF_Move = new short[N_URFtoDLF][N_MOVE];
    static short[][] URtoDF_Move = new short[N_URtoDF][N_MOVE];
    static short[][] URtoUL_Move = new short[N_URtoUL][N_MOVE];
    static short[][] UBtoDF_Move = new short[N_UBtoDF][N_MOVE];
    static short[][] MergeURtoULandUBtoDF = new short[336][336];
    static byte[] Slice_URFtoDLF_Parity_Prun = new byte[N_SLICE2 * N_URFtoDLF * N_PARITY / 2];
    static byte[] Slice_URtoDF_Parity_Prun = new byte[N_SLICE2 * N_URtoDF * N_PARITY / 2];
    static byte[] Slice_Twist_Prun = new byte[N_SLICE1 * N_TWIST / 2 + 1];
    static byte[] Slice_Flip_Prun = new byte[N_SLICE1 * N_FLIP / 2];

    /** 从类路径资源加载全部两阶段查表数据；适用于宿主 JVM 回归测试和非 Android 调用。 */
    public static synchronized void initialize() {
        if (tablesReady) return;
        InputStream raw = CoordCube.class.getResourceAsStream(TABLE_RESOURCE);
        if (raw == null) throw new IllegalStateException("未找到 Kociemba 查表资源：" + TABLE_RESOURCE);
        initialize(raw);
    }

    /** 从 Android AssetManager 提供的流加载全部两阶段查表数据；该方法可安全重复调用。 */
    public static synchronized void initialize(InputStream raw) {
        if (tablesReady) {
            closeQuietly(raw);
            return;
        }
        if (raw == null) throw new IllegalStateException("未提供 Kociemba 查表资源流");
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(raw, 64 * 1024))) {
            if (input.readInt() != TABLE_MAGIC) throw new IOException("Kociemba 查表资源魔数不匹配");
            if (input.readInt() != TABLE_FORMAT_VERSION) throw new IOException("Kociemba 查表资源版本不兼容");

            readShortMatrix(input, twistMove, "twistMove");
            readShortMatrix(input, flipMove, "flipMove");
            readShortMatrix(input, FRtoBR_Move, "FRtoBR_Move");
            readShortMatrix(input, URFtoDLF_Move, "URFtoDLF_Move");
            readShortMatrix(input, URtoDF_Move, "URtoDF_Move");
            readShortMatrix(input, URtoUL_Move, "URtoUL_Move");
            readShortMatrix(input, UBtoDF_Move, "UBtoDF_Move");
            readShortMatrix(input, MergeURtoULandUBtoDF, "MergeURtoULandUBtoDF");
            readBytes(input, Slice_URFtoDLF_Parity_Prun, "Slice_URFtoDLF_Parity_Prun");
            readBytes(input, Slice_URtoDF_Parity_Prun, "Slice_URtoDF_Parity_Prun");
            readBytes(input, Slice_Twist_Prun, "Slice_Twist_Prun");
            readBytes(input, Slice_Flip_Prun, "Slice_Flip_Prun");
            if (input.readInt() != TABLE_END_MARKER) throw new IOException("Kociemba 查表资源结束标记无效");
            if (input.read() != -1) throw new IOException("Kociemba 查表资源包含多余数据");
            tablesReady = true;
        } catch (IOException error) {
            String detail = error.getMessage();
            if (detail == null || detail.trim().isEmpty()) detail = "资源数据不完整或格式错误";
            throw new IllegalStateException("Kociemba 查表资源加载失败：" + detail, error);
        }
    }

    private static void closeQuietly(InputStream stream) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException ignored) {
            // 已经完成初始化；关闭冗余流失败不影响求解器状态。
        }
    }

    static boolean areTablesReady() {
        return tablesReady;
    }

    private static void readShortMatrix(DataInputStream input, short[][] target, String name) throws IOException {
        int rows = input.readInt();
        int columns = input.readInt();
        int expectedColumns = target.length == 0 ? 0 : target[0].length;
        if (rows != target.length || columns != expectedColumns) {
            throw new IOException(name + " 尺寸不匹配：" + rows + "×" + columns);
        }
        for (short[] row : target) {
            for (int column = 0; column < row.length; column++) row[column] = input.readShort();
        }
    }

    private static void readBytes(DataInputStream input, byte[] target, String name) throws IOException {
        int length = input.readInt();
        if (length != target.length) throw new IOException(name + " 长度不匹配：" + length);
        input.readFully(target);
    }

    /** 从 CubieCube 构造坐标；调用前表可以尚未加载。 */
    CoordCube(CubieCube cube) {
        twist = cube.getTwist();
        flip = cube.getFlip();
        parity = cube.cornerParity();
        FRtoBR = cube.getFRtoBR();
        URFtoDLF = cube.getURFtoDLF();
        URtoUL = cube.getURtoUL();
        UBtoDF = cube.getUBtoDF();
    }

    /** 从压缩的 4-bit 剪枝表提取一个值。 */
    static byte getPruning(byte[] table, int index) {
        if ((index & 1) == 0) return (byte) (table[index / 2] & 0x0f);
        return (byte) ((table[index / 2] & 0xf0) >>> 4);
    }
}
