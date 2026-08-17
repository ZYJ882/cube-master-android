package com.manus.cubemaster;

/** 应用中可对任意完整合法状态实际执行的三阶还原方法。 */
public enum SolveMethod {
    KOCIEMBA(
            "高效计算机解",
            "Kociemba 两阶段",
            "最快可用",
            "使用两阶段搜索直接计算当前状态的高效标准记号步骤。",
            new String[]{"Phase 1：进入受限子群", "Phase 2：完成排列与复原"}),
    LAYER_BY_LAYER(
            "真实入门层先法",
            "Layer-By-Layer",
            "逐层完成",
            "实际先完成白色底层十字、首层角块、中层棱块，再处理顶层；每一阶段均在主魔方状态上验证后才进入下一阶段。",
            new String[]{"1. 底层十字", "2. 完成首层", "3. 完成中层", "4. 顶层十字", "5. 顶层朝向", "6. 顶层排列并复原"}),
    CFOP(
            "真实 CFOP",
            "Cross · F2L · OLL · PLL",
            "速度进阶",
            "独立 CFOP 内核实际先完成十字，再以角棱配对完成 F2L，随后执行 OLL 与 PLL；每一阶段都在三维主状态上验证。",
            new String[]{"1. Cross：底层十字", "2. F2L：四组角棱配对", "3. OLL：顶层朝向", "4. PLL：顶层排列并复原"}),
    ROUX(
            "真实 Roux",
            "First Block · Second Block · CMLL · LSE",
            "块构建进阶",
            "独立受约束搜索先构建左右两个 1×2×3 块，随后完成 CMLL，最后仅用 M/U 完成 LSE；每阶段均验证块和最后层不变量。",
            new String[]{"1. First Block：左侧 1×2×3 块", "2. Second Block：右侧 1×2×3 块", "3. CMLL：顶层四角", "4. LSE：M/U 最后六棱并复原"}),
    ZZ(
            "真实 ZZ",
            "EOLine · ZZ-F2L · OCLL · PLL",
            "棱定向进阶",
            "独立受约束搜索先完成全部棱定向与 DF/DB 线；后续 F2L、OCLL 和 PLL 仅使用 R/L/U，并逐段验证。",
            new String[]{"1. EOLine：全棱定向 + DF/DB Line", "2. ZZ-F2L：受限 R/L/U 角棱对", "3. OCLL：顶层角块朝向", "4. PLL：顶层排列并复原"});

    private final String displayName;
    private final String signature;
    private final String audience;
    private final String description;
    private final String[] stages;

    SolveMethod(String displayName, String signature, String audience, String description, String[] stages) {
        this.displayName = displayName;
        this.signature = signature;
        this.audience = audience;
        this.description = description;
        this.stages = stages;
    }

    public String displayName() { return displayName; }
    public String signature() { return signature; }
    public String audience() { return audience; }
    public String description() { return description; }
    public String[] stages() { return stages.clone(); }
    public boolean hasIndependentSolver() { return true; }

    public String stageSummary() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < stages.length; i++) {
            if (i > 0) out.append("  →  ");
            out.append(stages[i]);
        }
        return out.toString();
    }
}
