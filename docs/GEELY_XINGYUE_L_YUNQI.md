# 适配 24 款吉利星越 L 云起

本文件声明 DiPlay 对 **2024 款吉利星越 L 云起（Geely Xingyue L Yunqi）** 车机的适配目标与改造范围。改造依据当前源代码与用户需求记录；未据此确认 APK 发布、车机安装或实车显示效果。本文件不修改既有 `README.md` / `README.zh-CN.md`，后者保留作历史参考。

## 目标车机与前提

| 项 | 说明 |
| --- | --- |
| 目标车型 | 2024 款吉利星越 L 云起（Geely Xingyue L Yunqi） |
| 车机能力 | 已开 ADB，可装 APK；Android 版本以实车为准 |
| 车辆数据桥 | 用户车机**无** GD 车辆数据桥（`com.geely.desktop.vehicle.bridge`）；不开发车辆数据功能 |
| HUD | 目标车机**无 HUD**。HUD 相关代码（`GeelyHudProjection`、`GeelyProjectionLayout`、`GeelyProjectionEditorActivity`、`GEELY_NAVIGATION` 的 HUD 导航与布局编辑器）**不在本次改造范围内**，保持原样不修改、不删除、不新增 |
| 仪表盘（instrument cluster） | 仪表盘 ≠ HUD。仪表盘镜像属于工作流 1，与 HUD 无关，不复用、不改动 HUD 代码 |

整体功能不变；可调优与界面优化。UI 仅做文案、视觉与布局微调。

## 改造范围

### 工作流 1 — 仪表盘镜像（已完成）

新增**完整 CarPlay 镜像到仪表盘**功能：把主 CarPlay 画面（流 110）镜像到车机的副屏（仪表盘），iPhone 无感知。详见 [仪表盘镜像](CLUSTER_MIRROR.md)。

- 新增类：[ClusterMirrorProjection.kt](../common/src/main/java/com/shilapi/xcertplay/ClusterMirrorProjection.kt)。自包含：内联 `DisplayManager` 副屏枚举，不调用 HUD 代码；Surface 回调调 `AndroidMediaSink.setMirrorSurface(SCREEN_TYPE_MAIN, "cluster_mirror", surface)`（流 110）。
- 接线：[CarPlayHostActivity.kt](../common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt) 在 `VehicleMapProjection.attach/close` 对称位置调 `ClusterMirrorProjection.attach/close`。
- 设置入口：[DiPlayActivity.kt](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt) 新增 `SettingsSection.CLUSTER_MIRROR`，映射到唯一分类 `SettingsCategory.DISPLAY`（控制 CarPlay 在副屏呈现）。开关在下次连接生效，保存后调 `markReconnectNeeded()`。
- 字符串：`settings_cluster_mirror_*` 已加入 `values/strings.xml` 及全部 `values-xx` 目录。
- 无副屏回退：找不到可用副屏时 `stage = "DISPLAY_UNAVAILABLE"`，显示「无可用仪表显示屏」，不建 overlay、不崩溃。

### 工作流 2 — 移除 BYD 代码

BYD 专用代码全部移除，使软件为 Geely-only。范围：

- `common/.../` 下 `ClusterMapPresentation`、`DiLink51ClusterMonitor`、`DiLink51ClusterLayout`、`AdbClusterRouter`、`AdbClusterActivity`、`SteeringControlsActivity`、`BydVehicleSettingsBackend`。
- `shared/.../hud/` 下 `BydOutputSettings`、`BydVehicleFields`、`BydVehicleCapabilityProbe`、`BydHudBridge`、`BydClusterBridge`、`BydDiLink3ClusterOutput`、`BydClusterSong`、`BydClusterMapPause`、`BydCarPlayCall`、`BydBattery`、`BydWheelSpeed`、`BydStandaloneNavigationBridge`、`BydOemClusterNavi`、`BydNavigationOutputs`。
- 对应测试：所有 `Byd*`、`AdbCluster*`、`ClusterMapPresentationTest`、`DiLink3ClusterRecoveryTest`、`DiLink51ClusterLayoutTest` 等。
- 设置清理：移除 `SettingsSection.BYD_ADB`、`BYD_NAVIGATION`、`VEHICLE_SCAN` 及其 `sectionsByCategory` 映射、`bydAdbSettings` / `bydNavigation` 构建器、BYD 字段、`SetupGuide.Feature.BYD_NAVIGATION`。
- 引用重指向：`CarPlayHostActivity`、`DiPlayActivity`、`HomeScreenMonitor` 中的 BYD cluster 引用改指向 `ClusterMirrorProjection`。**不触碰** `GeelyHudProjection` 等 HUD 代码。

移除后 `SettingsLayoutPolicyTest` 须仍通过：每个剩余 section 恰好映射一个分类、无重复。

### 工作流 3 — UI 去品牌化 + 吉利视觉

- [DiPlayPalette.kt](../common/src/main/java/com/shilapi/xcertplay/DiPlayPalette.kt) accent / 路面色向星越 L 云起配色集中；`GeelyHudGuidanceView` 硬编码色值迁入 palette。
- [themes.xml](../common/src/main/res/values/themes.xml) 调整基础主题。
- 字符串：`values/strings.xml` 及全部 `values-xx`，替换 BYD 品牌文案、应用名、About 文本；新文案用 `settings_` 前缀。
- `AirPlayPersistence.DEFAULT_OEM_LABEL` 已是「吉利」。
- 按 `AGENTS.md` 规则 5：附 compact + full + 大字号截图；触及行对齐时加 Arabic 截图。

### 工作流 4 — 新文档（本文件与 CLUSTER_MIRROR.md）

不原地修改 `README.md` / `README.zh-CN.md`；改造说明写入本文件与 [仪表盘镜像](CLUSTER_MIRROR.md)。

## 保留

- `VehicleBridgeClient` 绑定 `com.geely.desktop.vehicle.bridge` 保留代码。用户无此桥时，`ProjectionVehicleReader` 已有空 / `IDLE` 状态，无桥时优雅降级，不崩溃。
- HUD 代码全部保留，不在本次改造范围内。

## 模块清单

| 模块 | 用途 |
| --- | --- |
| `common` | CarPlay 主机 Activity、设置、投影、UI（共享库） |
| `mobile` | 主 DiPlay 应用（手机端体验） |
| `automotive` | 车机端 Android Auto / Automotive 适配 |
| `shared` | 视频 / 音频 / AirPlay 协议层（含 NDK） |
| `vehicle-probe` | 独立 GD 车辆桥 APK 的客户端 |
| `maphost` | 地图演示应用（`samples/maphost`） |
| `home` | 主页 / 启动器集成示例（`samples/home`） |

## 构建与验证

CI 命令引用 `AGENTS.md`：

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest \
  :mobile:lintDebug :home:lintDebug :maphost:lintDebug \
  :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

- 需设置 `ANDROID_HOME`（或 `local.properties` 的 `sdk.dir`）与 `JAVA_HOME`。
- `:shared` 依赖 NDK；NDK 缺失时 `:shared` 无法 configure，会阻断全部编译验证。NDK 须为 `AGENTS.md` / 工程要求的版本。

实车验证：装 APK 到星越 L 云起车机，进入 **设置 → 显示 → 仪表镜像**，选择副屏；若仪表盘未作为安卓副屏暴露，确认显示「无可用仪表显示屏」而非崩溃。
