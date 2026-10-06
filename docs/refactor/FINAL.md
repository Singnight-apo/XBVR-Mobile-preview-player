# T23 最终交付报告 / Final delivery report — 2026-10-06

本文件是四层重构（T00–T23）的最终交接报告。数值均为本次实际运行结果；未验证项在 §7 明确列出，**不得读作全面重构全部验证通过**。

## 1. 完成状态

| 项 | 值 |
|---|---|
| 分支 | `refactor/four-layer`（本地，不 push） |
| HEAD | `fe05461506031753f28c44a02f486dcafb0c2e48`（`fe05461`，关闭 T20 `device_blocked`） |
| 已提交范围 | T00–T23（T23 提交 `d85ea9b`；T20 关闭提交 `fe05461`） |
| 未提交范围 | **T23R / T23R2：`app/build.gradle`（release 签名 + 版本常量升为 2.7.1/10）+ 本文档 / `PROGRESS.md` / `VALIDATION.md`**，工作区留给 Lead 审查 |
| 版本 | `versionName 2.7.1` / `versionCode 10`（**本轮用户明确指示升版**；重构期间为保持性约束一直固定为 `0.2.7` / `9`） |
| 签名证书 SHA-256 | `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`（与历史 0.2.7 发行证书一致） |
| 候选 APK | `XBVR-Pocket-2.7.1.apk`，**签名 RELEASE 构建（`android:debuggable` 不存在，即 `debuggable=false`）**，SHA-256 `1ff60954bb8d80261af9cd7dce4276a95ab8c6dfa935af8f0078ee39557f4517`，10,316,271 字节 |
| 源码包 | `XBVR-Pocket-2.7.1-source.zip`，SHA-256 见同目录 `SHA256SUMS.txt`（源码包内含本文件，自引用哈希无法写回包内） |

**总体状态：结构、依赖、JVM/lint/工具门禁与覆盖安装兼容已通过；候选已从 debug 改为签名 RELEASE 构建（`debuggable=false`，`release` buildType 使用既有 `development` signingConfig），按用户新指示把版本常量升为 `2.7.1` / `10`，并在同一模拟器上完成 0.2.7/9（release）→ 2.7.1/10（release）同键覆盖安装与冒烟（见 §6/§6.2）；T20 真机陀螺仪验收已由用户 2026-10-06 实测确认（用户自述，本 agent 无法独立复核），原 `device_blocked` 关闭；Q0–Q8 为部分覆盖（详见 §5/§7）。**

## 2. 目标结构与依赖

- 四层 `ui / domain / data / media` + 组装根（`AppServices`、`XbvrApplication`、`MainActivity`、`PlayerActivity`、`LicensesActivity`）；Domain 不依赖 Android/okhttp/org.json，Data/Media 不依赖 UI。
- `verify_architecture.py --mode final`：**66 文件，0 违规**（T22 与本轮均复跑）。
- 无新增架构框架、无新增依赖、无模块化改造；`app/build.gradle` 依赖清单未变。本轮的构建文件改动只有两处：T23R 给 `release` buildType 增加 `signingConfig signingConfigs.development`（一个子句，`minifyEnabled false` 保留、未开启收缩），T23R2 按用户明确指示把版本常量从 `versionCode 9` / `versionName '0.2.7'` 升为 `versionCode 10` / `versionName '2.7.1'`。`applicationId top.liuwei.xbvr`、Activity 类名、签名配置均未动。

## 3. 功能对照（保留行为）

应用签名、`SharedPreferences`/AndroidKeyStore 方案、配置字段与 active 回退、收藏/续播键、封面设置键、视角 JSON 字段与 restore 局部更新顺序、目录缓存文件与 `_metadata`/`_retryAfter`、scene/file 标识与代理前缀、玩家认证与代理 Basic 分离、跨源不转发、重定向上限与超时、图片 16 MiB LruCache 与 12 MiB 响应上限、Main generation 与滚动恢复、Player 暂停意图与释放顺序 —— 均按方案 §4 保持；逐任务差异与主动报告的偏差见 `PROGRESS.md` 各任务「执行说明」。

## 4. 实际测试（命令与真实结果）

```
Invoke-Checks.ps1 -Project D:/codex-work/github/XBVR-Mobile-preview-player -Batch T23R2 -ToolTests -InstrumentationBuild
  → PASS: 184 JVM tests; lint 0 errors/20 warnings; signed APK verified
  → Evidence: D:/codex-work/output/xbvr-refactor-evidence/T23R2-20261006-093043-d2ee87d2
  （HEAD fe05461；Python 23 OK；Node 17 pass / 0 fail；assembleDebugAndroidTest 通过）
  （同修订较早一次 T23R 门禁 T23R-20261006-092020-2fc41b91 计数完全相同）

verify_architecture.py <repo> --mode final
  → final: 66 files; 0 violations

gradle --no-daemon assembleRelease   （release buildType 使用 development signingConfig）
aapt2 dump xmltree --file AndroidManifest.xml app-release.apk
  → android:debuggable 不存在（release 构建 debuggable=false）
  → versionCode 10 / versionName 2.7.1
aapt2 dump badging app-release.apk
  → package name='top.liuwei.xbvr' versionCode='10' versionName='2.7.1'
apksigner verify --print-certs app-release.apk
  → Signer #1 certificate SHA-256 20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4（v2 scheme，1 signer）
app-release.apk SHA-256 1ff60954bb8d80261af9cd7dce4276a95ab8c6dfa935af8f0078ee39557f4517，10,316,271 字节
assets/licenses 56/56 与 licenses/ 源逐字节一致
```

| 门槛 | 实测 | 基线 |
|---|---|---|
| JVM 测试 | **184**，失败 0 / 错误 0 / 跳过 0 | 184（T23 前）；计数未下降 |
| Android lint | **0 错误 / 20 警告** | 0 / 20 |
| APK 签名校验 | PASS，`20c3404b…` | 同 |
| 工具回归 Python | **23 OK** | 23 |
| 工具回归 Node | **17 pass / 0 fail** | 17 |
| 测试 APK | `assembleDebugAndroidTest` PASS | PASS |
| 发行候选 APK | release 构建，`debuggable=false`，2.7.1 / 10，SHA-256 `1ff60954…`，56/56 许可资产 | 与历史 0.2.7 发行形态/证书一致 |

## 5. 设备范围与 Q0–Q8

设备：`emulator-5554`（API 36，x86_64）、`emulator-5580`（AVD `XbvrQa`，**API 29**，用后已 `emu kill`）。**无物理真机。**

| ID | 是否运行 | 设备/API | 观察结果 |
|---|---|---|---|
| Q0 | ✅ | 5554/36、5580/29 | 启动、服务器菜单（仅 1 条合成服务器）、离线许可全文、进出播放器；英文与中文文案可用；无崩溃 |
| Q1 | ⚠️ 部分 | 5554/36、5580/29 | 全部 22；Studio Alpha=11；Actor One=11；Favorites=1；Continue watching=8。多标签 OR **未获结论**；Added date 排序、缓存离线未单独复验 |
| Q2 | ⚠️ 部分 | 5554/36 | `coverMode` 3→1→0→3 按服务器持久化；固定比例视觉裁切未逐项截图比对 |
| Q3 | ⚠️ 部分 | 5554/36 | 进入播放器恢复「Saved manual format」；播放/暂停按钮切换；GL 画面正常。章节/速度/轨道/字幕/多文件切源**未跑** |
| Q4 | ⚠️ 部分 | 5554/36 | 服务器菜单在横竖屏往返后保留。目录请求中切服务器/连接草稿重开**未跑** |
| Q5 | ❌ 未跑 | — | 未验证 |
| Q6 | ⚠️ 部分 | 5554/36 | 诊断对话框渲染本地报告，脱敏（原文省略）、`CLOSE`/`COPY REPORT` 可见。GL mediump 编译与故障注入**未跑** |
| Q7 | ⚠️ 部分 | 5554/36 | 手动 190° equidistant fisheye + SBS 应用并持久化；Restore automatic detection 回到自动（`override=false`）；陀螺仪开关切换不崩溃。**完整 21 组合矩阵、触摸/缩放、真实 gyro 未跑** |
| Q8 | ✅ | 5554/36 | 见 §6 |

**未声称**：8K/HDR/长时间音画同步、完整 21 组合 VR 矩阵、真实服务器/凭据（全程只用合成 fixture `127.0.0.1:18766`）。

## 6. 覆盖安装兼容（Q8，关键证据）

- 对 `emulator-5554` 上已有合成安装执行 `adb install -r app-debug.apk`：**Success**。**未卸载、未 `clear data`**。
- `shared_prefs/local.xml` 覆盖前后 **SHA-256 完全相同**（`32f7036ed3941e756f9fa01d9aeeeafbee12c9bcb536daf1efbcaf8cb29691cf`）：
  - `profiles`（AndroidKeyStore 加密合成配置）与 `active=643ece43-2aa5-4a4a-82a5-f131e94c0da7` 保留；
  - `fav:` 1、`pos:` 16、`source:` 8、`view:` 8、`coverMode:` 1、`coverAuto:` 1 全部保留；
  - `files/library-643ece43-….json` 仍在，应用启动后可从 fixture 正常刷新。
- 证据：`T23-20261006-preoverlay/prefs-before.xml`、`T23-device/prefs-after-overlay.xml`、`T23-device/files-after-overlay.txt`。

### 6.1 debug→release 同键升级（T23R，release 候选）

- 设备上原安装为 debug 构建：`dumpsys package` 报 `flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]`，其 `base.apk` SHA-256 为 `3d8edf026511dca2afa963f208412f799fbd7ba39fd01717578e3a59c35419c0`（即旧 debug 候选）。
- 直接在该安装之上执行 `adb install -r app-release.apk`（**未卸载、未 clear data**）：返回 **Success**；安装后 `flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`，`DEBUGGABLE` 消失，`base.apk` SHA-256 变为 `0a7bcd18a515243dedf367aae57ef4bc67cadb6afead8e37488768dd27387eda`（与 release 构建一致）。
- **数据保留**：`/data/data/top.liuwei.xbvr/shared_prefs/local.xml` 升级前后 SHA-256 完全相同（`a986b418a6703ecf34931318ee13e28ecaa27edd73729b9f9ddd99f4aba9516b`，6718 字节，`Compare-Object` 无差异）：`profiles`/`active`、`fav:` 1、`pos:` 16、`source:` 8、`view:` 8、`coverMode`/`coverAuto` 全部保留；`files/library-643ece43-….json` 仍在。
- 读取方式：升级前 `run-as` 与 `adb root cat` 两种方式得到同一字节流；升级后应用不再 debuggable，`run-as` 按预期报 `package not debuggable`，改用 `adb root` 读取同一文件做比对（`adb root` 在该模拟器上可用）。
- 冒烟（release 构建）：启动 → 媒体库 **22** → Favorites **1** → Continue watching **8** → 打开 `Pattern 1` 进入播放器 → 陀螺仪开关 `off→on→off`（`content-desc` 由 `Turn gyro on; currently off` 变为 `Turn gyro off; currently on` 再变回）→ 返回媒体库（仍 22）。进程存活（`pidof` 返回 pid），`logcat -b crash` 为空，无 `FATAL EXCEPTION`。
- 证据目录：`D:/codex-work/output/xbvr-refactor-evidence/T23R-device/`（含 `INDEX.md`、`install-over-existing.txt`、`prefs-before-root.xml`、`prefs-after-root.xml`、`q0-launch.xml`、`q1-library.xml`、`q3-player.xml`、`q7-gyro-after-on.xml`、`logcat-errors.txt` 等）。

### 6.2 0.2.7/9（release）→ 2.7.1/10（release）同键覆盖升级（T23R2）

- 设备上原安装为 T23R 的**签名 release 0.2.7**：`dumpsys package` 报 `versionCode=9`、`versionName=0.2.7`、`flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`（无 `DEBUGGABLE`），其 `base.apk` SHA-256 为 `0a7bcd18a515243dedf367aae57ef4bc67cadb6afead8e37488768dd27387eda`。
- 版本常量为何现在才升：重构期间为满足「不改变已发布版本」的保持性约束，`app/build.gradle` 固定为 `0.2.7` / `9`；**升为 `2.7.1` / `10` 是本轮用户的明确新指示**，以便 Android 把新包识别为对既有 9 的正规升级。
- 直接在该安装之上执行 `adb install -r XBVR-Pocket-2.7.1.apk`（**未卸载、未 clear data**）：返回 **Success**；安装后 `versionCode=10`、`versionName=2.7.1`，`flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`，`base.apk` SHA-256 变为 `1ff60954bb8d80261af9cd7dce4276a95ab8c6dfa935af8f0078ee39557f4517`（即新 release 构建）。
- **数据保留**：`/data/data/top.liuwei.xbvr/shared_prefs/local.xml` 升级前后 SHA-256 完全相同（`04160ab69506364f067654a316c3879a2c2a2b0c3f5c46f4408e8d397fb579c0`，6678 字节）：`profiles`/`active`、`fav:`、`pos:`、`source:`、`view:`、`coverMode`/`coverAuto` 全部保留；`files/library-643ece43-….json`（12263 字节）与 `playback-diagnostics.txt` 仍在，目录列表 `ls -la` 前后一致。
- 读取方式：候选非 debuggable，`run-as` 按预期报 `run-as: package not debuggable: top.liuwei.xbvr`；因此**显式使用 `adb root`**（该模拟器允许）读取同一文件并做 SHA-256 比对。升级前读取同样走 `adb root`。
- 冒烟（2.7.1 release 构建）：冷启动 → 媒体库 **22** → Favorites **1** → Continue watching **8** → 打开 `Pattern 1` 进入播放器 → 陀螺仪开关 `off→on→off`（`Turn gyro on; currently off` → `Turn gyro off; currently on` → `Turn gyro on; currently off`）→ 返回媒体库（仍 22）。进程存活（`pidof` 返回 13545），`logcat -b crash` 为空，无 `FATAL EXCEPTION`。
- 证据目录：`D:/codex-work/output/xbvr-refactor-evidence/T23R2-device/`（含 `INDEX.md`、`install-over-existing.txt`、`installed-before/after-dumpsys.txt`、`installed-before/after-apk-sha256.txt`、`prefs-before/after-hash.txt`、`prefs-after-runas.txt`、`q0-launch.xml`、`q1-library.xml`、`q3-player.xml`、`q7-gyro-after-on.xml`、`logcat-errors.txt` 等）。

## 7. 未验证项（明确部分完成）

> 唯一原 `device_blocked` 项（T20 真机陀螺仪）已由用户 2026-10-06 实测确认关闭，见本条；其余未验证项如下。

- **T20 真机陀螺仪验收：已由用户于 2026-10-06 实测确认无问题**，据此从 `device_blocked` 改为 `passed`。**证据来源为用户自述（本轮对话），非本 agent 执行、非本 agent 观测**；本 agent 环境 `adb devices` 仅 `emulator-5554`，无法独立复核。真机型号/Android 版本、所用 APK 文件、以及「横竖旋转 / 后台返回 / 关闭后不漂移」是否逐一覆盖均未知（相关工具输出中不存在，故不臆造）。
- Q5 全项、Q3 的章节/速度/轨道/字幕/多文件、Q4 的请求中切服务器与草稿重开、Q6 的 GL 编译与故障注入、Q7 的完整矩阵与触摸/缩放均**未跑**。
- 8K/HDR/长时音画同步未测试；无真实服务器/账号。
- API 29 仅全新安装冒烟，未做旧数据覆盖安装。
- 现象（非崩溃）：离开播放器时诊断记录一条 Media3 `ExoTimeoutException`（`PlayerActivity.onStop`→release），Activity 存活并正常返回媒体库，未进一步定性。
- 打包器改动前的 0.2.5 硬编码路径**从未被运行**；T23 的 debug 候选产物写入 `output/xbvr-android-0.2.7-refactor-candidate`，T23R 的 release 候选写入 `output/xbvr-android-0.2.7-refactor-candidate-release`，T23R2 的 2.7.1 发行包写入**新目录** `output/xbvr-android-2.7.1`（此前两个候选目录与历史 `output/xbvr-android-0.2.5*` 均未被覆盖或删除）。
- T23R / T23R2 的发行候选验证全部在 `emulator-5554`（API 36，合成 fixture `127.0.0.1:18766`）上完成；**本 agent 无物理真机**，§7 首条的真机陀螺仪结论仍为用户自述、非本 agent 观测。2.7.1 的升版只改版本常量与分发目录，**未新增任何设备用例**，故 §7 其余未验证项对 2.7.1 同样成立，Q0–Q8 仍为部分覆盖。

## 8. 回退点

- 代码回退：`git checkout fe05461`（T00–T23 全部已提交的稳定点）即可回到全部已提交历史。
- T23R / T23R2 未提交改动可单独丢弃：`git restore app/build.gradle VALIDATION.md docs/refactor/FINAL.md docs/refactor/PROGRESS.md`（不触碰已提交历史）。
- APK/源码包为 `output/` 下的本地副本，删除目录即回退；不影响仓库。

## 9. 交付清单

| 文件 | 说明 |
|---|---|
| `XBVR-Pocket-2.7.1.apk` | 签名 **RELEASE** 发行候选 APK（2.7.1 / 10，`debuggable=false`，证书 `20c3404b…`，SHA-256 `1ff60954bb8d80261af9cd7dce4276a95ab8c6dfa935af8f0078ee39557f4517`） |
| `XBVR-Pocket-2.7.1-source.zip` | 公开源码包（白名单含已审查计划/架构文档，排除工具链/私钥/`local.properties`/本地证据/fixture 媒体） |
| `XBVR-Pocket-2.7.1-third-party-license-materials.zip` | 第三方许可材料 |
| `desugar_jdk_libs-2.1.5-source.zip` | 脱糖对应源码 |
| `SHA256SUMS.txt` / `public-files.json` | 哈希与白名单 |
| `VALIDATION.md`、`docs/refactor/PROGRESS.md`、`docs/refactor/FINAL.md` | 验收与交接文档 |

本地目录：`D:/codex-work/output/xbvr-android-2.7.1`（T23R2 的 2.7.1 发行包；T23R 的 `xbvr-android-0.2.7-refactor-candidate-release` 与 T23 的 debug 候选目录 `xbvr-android-0.2.7-refactor-candidate` 均保留未动）。未 push、未创建或替换 Release；未分发私钥、工具链或 `local.properties`。
