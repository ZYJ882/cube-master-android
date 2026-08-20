# CubeMaster Android v1.1.41

## 回归测试、兼容性与性能优化版

本版在 v1.1.40 的实际求解串行隔离基础上，补齐了可重复执行的本地回归覆盖，并修复静态检查发现的 Android 7.0+ 兼容性问题。所有修改均通过 Debug 构建、14 项离线单元测试与 Debug lint 复验。

## 本次修复与优化

| 范围 | 修改 | 结果 |
|---|---|---|
| Android 7.0/7.1 兼容 | CFOP 内核移除 `java.time.Instant/Duration`（API 26），改用单调 `System.nanoTime()` 记录毫秒耗时。 | 保持 `minSdk 24`，消除 API 26 运行时兼容风险。 |
| Android 主题兼容 | 将 `windowLightNavigationBar` 移至 `values-v27` 资源覆盖。 | Android 7.0/7.1 不再解析 API 27 专属主题属性。 |
| 相机界面 | 用 `Typeface.BOLD` 替代字体样式魔法整数。 | 消除错误常量问题，视觉样式不变。 |
| 动作解析 | `CubeState.applyMove()` 使用 `Locale.ROOT` 规范化记号。 | 土耳其语等区域设置不会改变 `R/U/F/D/L/B/M/E/S` 动作解析。 |
| 棱先/角先宏搜索 | 将每个搜索扩展节点的字符串投影键替换为无碰撞的紧凑 `long` 键。 | 保持宏搜索状态语义，降低字符串、字符数组与哈希计算分配。 |
| 液态背景 | 仅在尺寸变化时创建线性渐变和四个径向光晕 Shader。 | 平时重绘不再重复分配渐变对象与颜色数组，减少 UI 垃圾回收压力。 |

## 新增回归测试

新增 `SolverRegressionTest`，测试资源直接复用 APK 内的两阶段与 Roux 查表。运行命令为：

```bash
./gradlew :app:testDebugUnitTest
```

| 覆盖项 | 验证方式 |
|---|---|
| 状态模型 | U/R/F/D/L/B/M/E/S 每个面转连续四次回到复原态；动作与逆动作互相抵消。 |
| 合法性 | 单棱翻转的物理非法状态被拒绝。 |
| 七种路线 | Kociemba、层先、CFOP、Roux、棱先、角先、ZZ 的动作均按播放器相同的 `CubeState` 连续回放并断言完整复原。 |
| 取消链路 | 已中断线程进入 Kociemba、Roux 或棱先时均协作退出，并保留中断状态。 |
| 区域设置 | 土耳其语区域下小写动作记号仍正确解析。 |

本次最终验证结果为 **14 个测试、0 失败、0 错误、0 跳过**。Debug lint 已无错误；仍保留的非阻断警告主要是中文 UI 文案国际化建议、旧代码缩进、未使用资源和可选依赖升级提示，不影响 Android 7.0+ 安装或当前功能。

## 构建与交付验证

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
```

构建通过后，APK 已再以 Android v2 签名方案验证。归档中包含 APK、对应源码快照和 SHA-256 校验清单。

| 文件 | 用途 |
|---|---|
| `CubeMaster-3x3-v1.1.41-quality-optimized-debug.apk` | Android 7.0+ Debug 安装包。 |
| `CubeMaster-3x3-v1.1.41-Android-Source.zip` | 对应版本完整源码快照。 |
| `SHA256SUMS.txt` | 两个归档文件的 SHA-256 完整性校验。 |
