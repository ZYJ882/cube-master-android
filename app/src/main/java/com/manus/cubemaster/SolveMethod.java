package com.manus.cubemaster;

/**
 * 用户可选的三阶魔方还原策略。
 *
 * <p>只有 Kociemba 模式目前拥有针对任意合法状态的独立搜索器。其他模式提供真实的
 * 学习阶段与讲解路线，但播放动作仍由已校验的 Kociemba 求解器给出，避免把计算机路径
 * 误称为严格的人工方法公式序列。</p>
 */
public enum SolveMethod {
    KOCIEMBA(
            "高效计算机解",
            "Kociemba 两阶段",
            "最快可用",
            "对当前完整、合法状态运行两阶段搜索，直接给出高效标准记号步骤。",
            "自动求解",
            new String[]{"Phase 1：进入受限子群", "Phase 2：完成排列与复原"}),
    LAYER_BY_LAYER(
            "入门分层讲解",
            "Layer-By-Layer",
            "适合第一次学习",
            "按白色十字、底层角、中层、顶层的经典分层路线讲解；动画动作仍采用已验证的计算机解。",
            "教学讲解",
            new String[]{"1. 底层十字", "2. 底层角块", "3. 中层棱块", "4. 顶层十字", "5. 顶层棱块定位", "6. 顶层角块定位", "7. 顶层角块朝向"}),
    CFOP(
            "CFOP 风格讲解",
            "Cross · F2L · OLL · PLL",
            "竞速进阶",
            "介绍十字、前两层配对、顶层朝向和顶层排列；动画动作仍采用已验证的计算机解。",
            "教学讲解",
            new String[]{"Cross：底面十字", "F2L：四组角棱配对", "OLL：顶层朝向", "PLL：顶层排列"}),
    ROUX(
            "Roux 棱块路线",
            "Block Building · CMLL · LSE",
            "块构建 / 中层转动",
            "介绍两个 1×2×3 块、CMLL 与最后六棱；当前展示学习路线，动画动作仍采用已验证的计算机解。",
            "原理路线",
            new String[]{"左侧 1×2×3 块", "右侧 1×2×3 块", "CMLL：四角处理", "LSE：最后六棱与中心"}),
    ZZ(
            "ZZ 棱定向路线",
            "EO-Line · F2L · Last Layer",
            "棱定向 / 少转体",
            "介绍先完成棱定向与线、再做前两层和末层；当前展示学习路线，动画动作仍采用已验证的计算机解。",
            "原理路线",
            new String[]{"EO-Line：棱定向与线", "F2L：仅用 R/L/U/D 完成前两层", "Last Layer：末层处理"});

    private final String displayName;
    private final String signature;
    private final String audience;
    private final String description;
    private final String capabilityLabel;
    private final String[] stages;

    SolveMethod(String displayName, String signature, String audience, String description, String capabilityLabel, String[] stages) {
        this.displayName = displayName;
        this.signature = signature;
        this.audience = audience;
        this.description = description;
        this.capabilityLabel = capabilityLabel;
        this.stages = stages;
    }

    public String displayName() { return displayName; }
    public String signature() { return signature; }
    public String audience() { return audience; }
    public String description() { return description; }
    public String capabilityLabel() { return capabilityLabel; }
    public String[] stages() { return stages.clone(); }
    public boolean hasIndependentSolver() { return this == KOCIEMBA; }

    public String stageSummary() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < stages.length; i++) {
            if (i > 0) out.append("  →  ");
            out.append(stages[i]);
        }
        return out.toString();
    }
}
