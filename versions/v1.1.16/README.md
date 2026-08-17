# CubeMaster Android v1.1.16 — 多方法选择与教学路线版

**版本：** v1.1.16  
**项目：** 魔方大师 CubeMaster Android

## 本次更新

求解区新增“**选择还原策略**”横向选择器。用户可以在五种三阶魔方还原路线间切换，查看其核心思想、典型阶段、适合人群与可播放步骤。每次切换都会清除旧解法，确保后续计算和动画始终对应当前三维魔方状态。

| 策略 | 核心原理 | 阶段 | 当前应用行为 |
|---|---|---|---|
| 高效计算机解 | Kociemba 两阶段搜索 | Phase 1 → Phase 2 | 对任意完整合法状态执行独立离线求解，输出高效标准记号步骤。 |
| 入门分层讲解 | 固定已完成的层，逐层推进 | 十字 → 底层角 → 中层 → 顶层 | 展示七个经典教学阶段；播放动作由已验证的两阶段算法生成。 |
| CFOP 风格讲解 | 层先优化，角棱配对完成前两层 | Cross → F2L → OLL → PLL | 展示四个竞速学习阶段；播放动作由已验证的两阶段算法生成。 |
| Roux 棱块路线 | 两个 1×2×3 块、四角、最后六棱 | 左块 → 右块 → CMLL → LSE | 提供块构建与中层转动学习路线；播放动作由已验证的两阶段算法生成。 |
| ZZ 棱定向路线 | 先棱定向，减少后续转体和受限面转动 | EO-Line → F2L → Last Layer | 提供棱定向、前两层与末层学习路线；播放动作由已验证的两阶段算法生成。 |

## 算法边界说明

> “方法名称”和“动作来源”在界面中被明确区分。当前只有 **Kociemba 两阶段** 对任意合法状态拥有完整、独立的程序化搜索器。入门分层、CFOP、Roux 与 ZZ 在本版本中是准确的阶段教学与训练路线；其动画始终使用 Kociemba 计算并校验后的动作，不会把该动作伪称为严格的 LBL、CFOP、Roux 或 ZZ 公式序列。

这是为了让用户能选择不同的学习视角，同时保证每一次自动还原都可靠。后续如加入独立的 Cross/F2L/OLL/PLL case matcher、Roux 块构建与 CMLL/LSE 规划器、ZZ 的 EO 规划器与末层算法库，相关选项可升级为严格的独立求解器。

## 各方法原理摘要

入门分层法依次完成白色十字、白色角、中层棱、黄色十字、顶层棱定位、顶层角定位和顶层角朝向，强调不破坏已完成层，适合首次学习。[1]

CFOP 由 Cross、F2L、OLL、PLL 组成；F2L 将角块与对应棱块配对后一次插入，OLL 处理顶层朝向，PLL 完成顶层排列。完整 CFOP 常见为 57 个 OLL 与 21 个 PLL 情形；学习阶段可先从直觉 F2L、两步 OLL 与两步 PLL 开始。[2] [3]

Roux 通过两个 1×2×3 块进行块构建，再以 CMLL 处理角块、以最后六棱完成收尾；ZZ 以 EO-Line 或 EOCross 先定向棱块，从而让后续前两层主要使用 R/L/U/D 面转动。[4] [5]

## 回归验证

已对五种策略模型进行回归验证：每种均具有显示名称、方法签名、目标人群、用户说明和阶段清单；只有 Kociemba 被标记为独立任意状态求解器。Android 工程已完成完整构建验证。

## 文件说明

| 文件 | 用途 |
|---|---|
| APK 文件 | 可安装的 Android 调试包。 |
| Android-Source.zip | 对应版本的完整 Android 工程源码归档。 |
| SHA256SUMS.txt | 交付文件完整性校验值。 |

## 参考资料

[1] [Ruwix, *How To Solve The Rubik’s Cube? — Beginners Method*](https://ruwix.com/the-rubiks-cube/how-to-solve-the-rubiks-cube-beginners-method/)  
[2] [WklChris, *CFOP 简介：两步法*](https://wklchris.github.io/blog/Rubik/CFOP.html)  
[3] [Wikipedia, *CFOP method*](https://en.wikipedia.org/wiki/CFOP_method)  
[4] [Ruwix, *Different Rubik’s Cube Solving Methods*](https://ruwix.com/the-rubiks-cube/different-rubiks-cube-solving-methods/)  
[5] [Wikipedia, *Speedcubing — Methods*](https://en.wikipedia.org/wiki/Speedcubing#Methods)

返回项目首页：[`README.md`](../../README.md)。
