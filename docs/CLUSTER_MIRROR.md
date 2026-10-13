# 仪表盘镜像

本功能把主 CarPlay 画面镜像到车机的副屏（如仪表盘），iPhone 无感知。以下行为依据当前源代码记录；未据此确认 APK 发布、车机安装或实车显示效果。

## 原理

DiPlay 的视频镜像基础设施已存在：[AndroidMediaSink.setMirrorSurface(type, key, surface)](../shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt) 为每个 `(type, key)` 创建独立的 `VideoDecoder`，`onVideoFrame` 把同一流 NALU 扇出到所有镜像解码器。无需 iPhone 端协商。

| 流类型 | 常量 | 用途 |
| --- | --- | --- |
| 主 CarPlay 画面 | `SCREEN_TYPE_MAIN = 110` | 仪表盘镜像使用此流 |
| 仪表地图流 | `SCREEN_TYPE_ALT = 111` | 既有 `mirrorSink` 地图镜像使用此流，**不在本功能改动范围** |

[ClusterMirrorProjection.kt](../common/src/main/java/com/shilapi/xcertplay/ClusterMirrorProjection.kt) 的工作流程：

1. **枚举副屏**：内联 `DisplayManager.getDisplays()`，排除主屏（`DEFAULT_DISPLAY`）与已关闭屏（`STATE_OFF`），读取各屏分辨率。**自包含**，不调用 HUD 代码（`GeelyHudProjection` 等保持原样不动）。
2. **建 overlay 窗口**：在选定副屏上用 `WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY)` + `ClusterVideoTexture`（通用 Surface 包装）创建窗口。配方参考 `VehicleMapProjection.createWindow`，但 Surface 回调调 `sink?.setMirrorSurface(110, "cluster_mirror", surface)`。
3. **解码第二份**：`AndroidMediaSink` 按 `(type, key)` 隔离镜像解码器，主解码器不受影响；iPhone 继续喂主画面，sink 解码第二份到镜像 Surface。
4. **轮询**：每 500ms 刷新一次，检测副屏变化、权限变化与 Surface 有效性。

三处刻意区别于既有 `VehicleMapProjection`（仪表地图流投影）：
- Surface 回调调 `setMirrorSurface(110, …)`（主画面流），而非 `setSurface(111, …)`（地图流）。
- 不走 `CarPlayClusterDisplay` config / `clusterProjectionSessionToken`——镜像主屏已解码内容，iPhone 无感知。
- 无 vehicle bridge、无 projection session token、无 `setProjectionUiShown`。

## 配置入口

在 DiPlay 设置的「显示」区域：

1. 开启「仪表镜像」，按系统提示允许悬浮显示。
2. 在「投影屏幕」中选择目标副屏（仪表盘）。
3. 开关在**下次连接**生效，保存后显示「立即重连」提示条；不擅自断开运行中的会话。

选择偏好存独立 SharedPreferences（`cluster_mirror_projection`），含 `enabled`、`display_id`、`display_name`。

## 限制

- **星越 L 仪表盘必须由 Android 向当前应用公开且可用**作为副屏；无法通过本功能开启厂商私有显示通道。若仪表盘不作为安卓副屏暴露，`stage = "DISPLAY_UNAVAILABLE"`，设置页显示「无可用仪表显示屏」，不建 overlay、不崩溃。
- 获得悬浮授权及选择显示屏后，系统仍可能拒绝窗口创建，须以实际设备结果判断。
- 镜像解码双倍视频内存；`AndroidMediaSink` 按 `(type, key)` 隔离镜像解码器，主解码器不受影响；需实车测试。
- 本功能镜像主 CarPlay 画面，不提供独立的仪表地图流或车辆数据；车辆数据须由独立 GD 桥提供（用户车机无此桥，相关项降级为空）。

## 实车验证步骤

1. 装构建好的 APK 到星越 L 云起车机（已开 ADB，可装 APK）。
2. 打开 DiPlay，完成 CarPlay 连接（有线或无线）。
3. 进入 **设置 → 显示 → 仪表镜像**，开启开关并选择仪表盘副屏。
4. 按提示「立即重连」或在下次连接时生效。
5. 确认仪表盘上显示与主屏一致的 CarPlay 画面。
6. 若设置页显示「无可用仪表显示屏」：说明该车机仪表盘未作为安卓副屏暴露，本功能在该车机不可用，不影响主屏 CarPlay。
7. 关闭开关或断开连接后，确认仪表盘镜像停止，不留残留窗口。
