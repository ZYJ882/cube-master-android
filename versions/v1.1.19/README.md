# CubeMaster Android v1.1.19 — 真实 CFOP 独立规划版

**版本：** v1.1.19  
**项目：** 魔方大师 CubeMaster Android

## 本版目标

本版新增可播放的**真实 CFOP**还原路线。它不是把 Kociemba 两阶段解法改成 `Cross / F2L / OLL / PLL` 的文字说明，而是独立运行 CFOP 阶段内核，并将每段产生的动作映射回应用固定的三维主魔方坐标。

| 阶段 | 实际状态目标 | 进入下一阶段前的验证 |
|---|---|---|
| Cross | 建立白色十字，并使四条棱与侧面中心色一致。 | 4 条十字棱的顶面颜色和侧面颜色均正确。 |
| F2L | 将四组角棱以配对方式完成前两层。 | 白色面完整，四个侧面前两行与中心色一致。 |
| OLL | 保持前两层，完成顶层面片朝向。 | 顶层 9 个面片颜色统一，前两层不被破坏。 |
| PLL | 排列最后一层。 | 54 面片回到完整复原状态。 |

## 独立性与许可

CFOP 动作来自经 Java 适配的独立 CFOP 内核，不调用或重放 Kociemba 的返回动作。内核源自 Divins Mathew 的 MIT 许可项目 [CubeXdotNet-Rubiks-Cube-Solver](https://github.com/divinsmathew/CubeXdotNet-Rubiks-Cube-Solver)。原始版权、完整 MIT 许可证和 Android 适配说明均已包含在 [`THIRD_PARTY_LICENSES.md`](../../THIRD_PARTY_LICENSES.md) 与 `solver/cfop/LICENSE-MIT-CubeX.txt`。

## 验证

对三组不同的当前合法打乱状态执行了完整回归。CFOP 分段动作分别总计为：

| 状态 | Cross | F2L | OLL | PLL | 总步数 |
|---|---:|---:|---:|---:|---:|
| Case A | 11 | 29 | 8 | 14 | 62 |
| Case B | 6 | 30 | 13 | 14 | 63 |
| Case C | 9 | 29 | 11 | 16 | 65 |

每组均由 Cross、F2L、OLL、PLL 依次产生动作，并将完整结果应用到当前 `CubeState` 后验证回到复原态。

## Roux 与 ZZ 的边界

Roux 和 ZZ 仍在研发，尚未作为可选还原方法显示。它们将分别在具有完整独立的 `First Block → Second Block → CMLL → LSE` 与 `EOLine → ZZ-F2L → OCLL → PLL` 规划器、阶段不变量验证及回归后再加入；本版不会以 CFOP 或 Kociemba 动作代替它们。

## 文件说明

| 文件 | 用途 |
|---|---|
| APK 文件 | 可安装 Android 调试包。 |
| Android-Source.zip | 对应版本的完整 Android 工程源码归档。 |
| SHA256SUMS.txt | APK 和源码归档的 SHA-256 完整性校验。 |

返回项目首页：[`README.md`](../../README.md)。
