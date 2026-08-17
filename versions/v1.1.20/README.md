# CubeMaster Android v1.1.20

## Roux / ZZ 独立阶段规划版

`v1.1.20` 将三阶还原方法选择器扩展为五种**实际独立计算**的路线：Kociemba 两阶段、入门层先法、CFOP、Roux 与 ZZ。新增方法不读取、拆分或重命名 Kociemba 返回的完整动作；每个阶段均由受约束搜索针对自身目标生成，并在唯一的 `CubeState` 三维主状态上立即验证。

| 方法 | 实际阶段 | 阶段约束与验证 |
|---|---|---|
| **Roux** | First Block → Second Block → CMLL → LSE | 先后构建左右两个 `1×2×3` 块；CMLL 完成顶层四角；LSE 仅允许 `M/U`，完成最后六棱并复原整颗魔方。 |
| **ZZ** | EOLine → ZZ-F2L → OCLL → PLL | EOLine 同时验证十二条棱定向及 `DF/DB` Line；F2L、OCLL、PLL 均限制为 `R/L/U`，前序 F2L 不变量持续保持。 |

本版加入了针对 Roux 三棱两角 Block 的**组合剪枝表**，使第二块的受约束搜索拥有精确距离下界。三组不同、由合法面转产生的回归状态均已端到端验证：Roux 与 ZZ 都能逐段生成动作，逐段验证，再将主状态还原到标准 `URFDLB` 已复原面片。首次选择 Roux 时会在后台建立 Block 剪枝表；该工作由既有的 12 秒计算保护覆盖，之后同一进程内会复用缓存。

## 中层切片状态修正

`M/E/S` 在固定中心色坐标中现只移动相应中层面片，不再错误地循环六个中心贴纸。因此，Roux LSE 的 `M` 动作可直接在三维模型上播放，并与离线求解器的立方体坐标一致。手动中层转动、阶段验证和动画回放由同一 `CubeState` 管理。

## 许可证与归属

Roux/ZZ 受约束搜索的投影坐标、动作转移和剪枝表设计参考 Torjus Iveland 的 MIT 项目 [`torjusti/cube-solver`](https://github.com/torjusti/cube-solver)，但本项目未打包或执行其 JavaScript；已按 CubeMaster 的 `CubieCube` / `CubeState` 约定重新实现 Java 内核。完整归属与 MIT 文本见根目录 [`THIRD_PARTY_LICENSES.md`](../../THIRD_PARTY_LICENSES.md) 和 `app/src/main/java/com/manus/cubemaster/solver/LICENSE-MIT-cube-solver.txt`。

## 归档文件

发布归档包含以下可校验交付物：

| 文件 | 用途 |
|---|---|
| `CubeMaster-3x3-v1.1.20-roux-zz-debug.apk` | Android 7.0+ 可安装 Debug APK。 |
| `CubeMaster-3x3-v1.1.20-Android-Source.zip` | 对应版本完整源码快照。 |
| `SHA256SUMS.txt` | APK 与源码包的 SHA-256 校验清单。 |

安装前如系统提示来源未知，请在系统设置中仅对当前安装来源授予一次安装权限。应用无需网络连接即可进行本地还原；相机识别结果仍应在计算前人工复核。
