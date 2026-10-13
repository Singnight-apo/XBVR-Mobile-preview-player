# 0.2.10 原生管理与进度手势 / Native management and seeking — 2026-10-14

包名 `top.liuwei.xbvr`，versionName **0.2.10** / versionCode **13**，minSdk 29 / targetSdk 36。发布范围为 `feat/xbvr-management` 相对于 0.2.9 主线的原生服务器管理、刮削与手动匹配、文件预览、维护页面和扫描后刷新、分钟时长、底部进度手势及缓冲时控制栏显示修复。

- 本轮 `testDebugUnitTest lintDebug assembleRelease` 成功；随后强制重跑 `:app:testDebugUnitTest --rerun-tasks`，**245 项测试、0 失败、0 错误、0 跳过**。lint **0 错误、13 项警告**。Python 工具测试 **23 项**、Node 工具测试 **17 项**均通过。
- RELEASE APK 经 `aapt` 确认 **0.2.10 / 13**，`apksigner verify` 通过；沿用证书 SHA-256 `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`。
- 小米 17 Pro Max（型号 `2509FPN0BC`）通过 `adb install -r` 从 0.2.9 覆盖安装成功，未卸载或清除数据；包管理器确认 **0.2.10 / 13**，`MainActivity` 冷启动返回 `Status: ok`。
- 先前管理流程、合成服务器和进度手势验证保留在下面的历史记录；本轮没有在真实服务器重新执行扫描、刮削、匹配等写入操作，也没有重新覆盖完整 VR 格式、8K/HDR、长时间播放或全部 Android 版本。
- 当前发布源码与许可材料由 `tools/compliance-package.py` 的公开文件清单打包；私有签名密钥、工具链、缓存和本地日志不进入发布源码。

## 历史验证 / Historical validation

# 0.2.9 设置与服务器管理 / Settings and server management — 2026-10-10

包名 `top.liuwei.xbvr`，versionName **0.2.9** / versionCode **12**，minSdk 29 / targetSdk 36。沿用既有签名，支持覆盖升级。以下为本次范围；历史验收记录保留在后文。

- 首页右上角齿轮进入独立设置页，服务器与封面比例迁至“媒体库设置”，诊断及关于单独呈现。逐服务器菜单支持编辑与本机配置移除；移除保留观看记录及远端文件，当前配置被移除后选择剩余第一台，全部移除后显示添加入口。
- 设置页返回、旋转与恢复保持当前层级；首页搜索、筛选及浏览位置保留。修正动态页面窗口边距，避免标题与状态栏重叠；下层媒体库在设置期间隐藏。
- 本次发布构建：`testDebugUnitTest lintDebug assembleRelease` 成功；**195 JVM tests，0 failures / 0 errors**；lint **0 errors / 17 warnings**。工具回归 **Python 23 / Node 17** 全部通过。
- 签名 RELEASE 验签通过，证书 SHA256 `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`；`aapt2` 确认版本 **0.2.9 / 12**。源码及第三方许可材料通过项目既有分发工具打包，逐文件核对 APK 内许可资源。
- 设置功能在小米 17 Pro Max / Android 17（API 37）实测：设置入口和逐级返回、服务器切换后保留页面、移除取消、添加/编辑取消、关于/许可返回、比例修改及恢复、横竖屏、搜索与滚动位置保留。原 HTTPS 选择、自动封面比例及竖屏锁定已恢复；本轮进程日志未见崩溃或 ANR。
- 边界：未删除用户真实服务器测试“最后一台”场景；该场景与存储失败、过期请求回调由 JVM 测试覆盖。设置交互实测使用同代码的 0.2.8 候选构建，0.2.9 仅追加版本常量变化。未将本轮结果扩展为 API 29–32 实机、完整深色主题、8K/HDR 或长时间播放覆盖。

# 0.2.8 四层重构最终验收 / 0.2.8 four-layer refactor final validation — 2026-10-06

包名 top.liuwei.xbvr，versionCode **11** / versionName **0.2.8**，minSdk 29 / targetSdk 36。本段取代此前误发的 **2.7.1 / 10** 编号：**2.7.1 是发布时的错误版本号，已纠正为 0.2.8**，同时 `versionCode` 由 10 升为 **11**，使 Android 把新包视为对已发布 10 的正规升级。此前已发布的 Release notes 内容保留（GitHub 侧的删除与重发由 Lead 处理，**本 agent 未触碰 GitHub**）。HEAD `24bbd49`「继续观看按最近播放排序」的修复**已包含在本包内**。

## 本次改动

- 版本常量升为 `versionCode 11` / `versionName '0.2.8'`：这是 `app/build.gradle` 的唯一改动（`defaultConfig` 一行）；`release` buildType 的 `signingConfig signingConfigs.development` 与 `minifyEnabled false` 保持原样，依赖清单、`applicationId`、Activity 类名均未动。
- **继续观看按最近播放排序（HEAD `24bbd49`）**：新增**附加键** `seen:<key>`（epoch 毫秒），与既有 `pos:`/`view:` 在同一个 editor 事务内写入（`save(...)` 与 `entryPosition(...)` 两条路径）；旧安装缺少该键时读回 0，因而只排在所有带时间戳条目之后。`PlaybackRepository.lastWatched(String)` 由 `LocalSettings` 实现、`PlaybackStore` 转发；`LibraryController.recompute()` **仅在该 tab（continue watching）**按 `lastWatched` 降序排序，**使用稳定排序**；成员判定不变（仍是 `position > 0`），相同时间戳保持服务器顺序，其他 tab 不受影响。**未改既有键含义、未改数据格式。**

## 构建与测试（实测）

- `Invoke-Checks.ps1 -Batch REL028 -ToolTests -InstrumentationBuild`（HEAD `24bbd49`）：PASS **188 JVM**（0 失败 / 0 错误 / 0 跳过）、lint **0 错误 / 20 警告**、APK 签名校验通过（证书 SHA-256 `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`，与历史发行证书一致）、工具回归 **Python 23 + Node 17**、测试 APK 编译通过。证据 `REL028-20261006-102107-38da5404`。
- `verify_architecture.py --mode final`：**66 文件 0 违规**（四层无禁止边，根包只剩 `AppServices`、三个 Activity 与 `XbvrApplication`）。
- **发行 APK 为签名 RELEASE 构建**：`XBVR-Pocket-0.2.8.apk`（release 字节），SHA-256 `91dda9fd6ce0023f65cd7171e4810b94eed31cc20df6c325a493fcfeaf2b7f01`，大小 **10,317,215** 字节；`aapt2` 报 `package name='top.liuwei.xbvr' versionCode='11' versionName='0.2.8'`；`android:debuggable` **不存在**（即 `debuggable=false`）；`apksigner` 报 signer #1 证书 SHA-256 `20c3404b…`（v2 scheme，1 signer）；assets/licenses **56/56** 与 `licenses/` 源逐字节一致。
- 打包目录为**新目录** `D:/codex-work/output/xbvr-android-0.2.8`；`output/xbvr-android-2.7.1`、`output/xbvr-android-0.2.7-refactor-candidate*` 与 `output/xbvr-android-0.2.5*` 均未被覆盖或删除。

## 设备范围与覆盖安装（0.2.8 / 11）

- 设备：`emulator-5554`（API 36，x86_64），合成 fixture `127.0.0.1:18766`（应用内地址 `http://10.0.2.2:18766`）。**无物理真机。**
- **覆盖安装**：对设备上既有安装直接 `adb install -r XBVR-Pocket-0.2.8.apk`（**未卸载、未 clear data**）返回 **Success**。安装前为 debug 构建 `versionCode=10` / `versionName=2.7.1` / `flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]`，安装后为 `versionCode=11` / `versionName=0.2.8` / `flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`（`DEBUGGABLE` 消失）；`base.apk` SHA-256 由 `775e82b6…` 变为 `91dda9fd…`（= 新 release 包）。
- **数据保留**：`/data/data/top.liuwei.xbvr/shared_prefs/local.xml` 覆盖前后 SHA-256 完全相同（`996e2e65f4a4dcfc4663c628235700b30e8bf0509f1af90e7deef61de8a88d08`，7628 字节，字节比对一致）；`files/library-643ece43-….json` 与 `playback-diagnostics.txt` 仍在。
- **读取方式**：新包非 debuggable，`run-as` 按预期报 `run-as: package not debuggable: top.liuwei.xbvr`；因此**显式使用 `adb root`**（该模拟器允许）读取同一文件做比对。
- **冒烟**：冷启动 → 媒体库 **22 videos** → Favorites **1 videos**（Pattern 2）→ Continue watching **8 videos** → 打开 `Pattern 1` 进入播放器 → 陀螺仪开关 `off→on→off`（`Turn gyro on; currently off` → `Turn gyro off; currently on` → `Turn gyro on; currently off`）→ 返回媒体库（22）。进程存活（pid 15308），`logcat -b crash` 为空，无 `FATAL EXCEPTION`。
- **继续观看排序（本次修复的直接证据）**：可见顺序为 **Pattern 16 > Pattern 5 > Pattern 3 > Pattern 7 > Pattern 1 > Pattern 8 > Pattern 9 > Pattern 14**。Pattern 16 在服务器顺序中最后且最近被播放，因此排在最前；四个早于该时间戳键的记录（1、8、9、14）排在所有带时间戳条目之后并保持服务器顺序。偏好键清点：`pos:` 16、`seen:` 8、`view:` 8、`fav:` 1。
- 证据 `D:/codex-work/output/xbvr-refactor-evidence/REL028-device/`（含 `INDEX.md`）。

## 未覆盖 / 未验证（不得读作通过）

- **T20 真机陀螺仪**：结论来源仍为**用户 2026-10-06 的自述**，**非本 agent 执行、非本 agent 观测**；本 agent 环境 `adb devices` 仅 `emulator-5554`，无法独立复核。真机型号/Android 版本、所用 APK、以及「横竖旋转 / 后台返回 / 关闭后不漂移」是否逐一覆盖均未知。
- **Q0–Q8 仍为部分覆盖**：0.2.8 只改版本常量与分发目录，外加 `24bbd49` 的排序修复，**未新增设备用例**；Q5 全项、Q3 的章节/速度/轨道/字幕/多文件、Q4 的请求中切服务器与草稿重开、Q6 的 GL 编译与故障注入、Q7 的完整 21 组合与触摸/缩放仍未跑。
- 8K / HDR / 长时间音画同步未测试；无真实服务器、无真实凭据（仅合成 fixture）。
- API 29 未在本轮复跑。
- 离开播放器时诊断可能记录一条 Media3 `ExoTimeoutException`（release 路径），Activity 存活并正常返回媒体库，**非崩溃**；本轮未进一步定性。
- 此前 **2.7.1 / 10 的编号为发布错误**，本包已纠正为 **0.2.8 / 11**；2.7.1 的 GitHub Release/tag 的删除与重发由 Lead 负责，**本 agent 未 push、未创建/删除任何 Release 或 tag、未提交**。

# 2.7.1 验收记录（编号已废弃）/ 2.7.1 record (superseded number) — 2026-10-06

> **更正说明**：本节的 2.7.1 / 10 是当时的错误编号，已被上面的 0.2.8 / 11 取代。以下内容作为历史记录保留。

包名 top.liuwei.xbvr，versionCode **10** / versionName **2.7.1**，minSdk 29 / targetSdk 36。本次为 UI / Domain / Data / Media 四层重构（T00–T23）的最终本地候选验收。

**版本说明**：重构期间为满足「不改变已发布版本与数据格式」这一保持性约束，`app/build.gradle` 的版本常量一直固定为 `versionCode 9` / `versionName 0.2.7`；本次升为 `versionName 2.7.1` / `versionCode 10` 是**用户在本轮给出的明确新指示**（让 Android 把新包视为对既有 9 的正规升级），与重构本身无关。重构没有改动 `applicationId`、Activity 类名或数据格式。详细报告见 [docs/refactor/FINAL.md](docs/refactor/FINAL.md)，逐任务进度见 [docs/refactor/PROGRESS.md](docs/refactor/PROGRESS.md)。

## 构建与测试（实测）

- `Invoke-Checks.ps1 -Batch T23R2 -ToolTests -InstrumentationBuild`（HEAD `fe05461`）：PASS **184 JVM**（0 失败 / 0 错误 / 0 跳过）、lint **0 错误 / 20 警告**、APK 签名校验通过（签名证书 SHA-256 `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`，与历史 0.2.7 发行证书一致）、工具回归 **Python 23 + Node 17**、测试 APK 编译通过。证据 `T23R2-20261006-093043-d2ee87d2`。（同修订的 T23R 门禁证据 `T23R-20261006-092020-2fc41b91` 计数完全相同。）
- `verify_architecture.py --mode final`：**66 文件 0 违规**（四层无禁止边、根包只剩 `AppServices` 与三个 Activity + `XbvrApplication`）。
- **候选 APK 为签名 RELEASE 构建**：`app/build.gradle` 的 `release` buildType 增加 `signingConfig signingConfigs.development`（`minifyEnabled false` 保留、未加依赖、未开启混淆），另按用户指示把版本常量升为 `versionCode 10` / `versionName 2.7.1`；这两处即是该文件在本轮的**全部**改动。`assembleRelease` 产物无 `android:debuggable`，即 `debuggable=false`，与历史已发布 `XBVR-Pocket-0.2.7.apk` 的发行形态一致，并使用**同一签名证书**。
- 发行候选 APK `XBVR-Pocket-2.7.1.apk`（release 字节），SHA-256 `1ff60954bb8d80261af9cd7dce4276a95ab8c6dfa935af8f0078ee39557f4517`，大小 10,316,271 字节；`aapt2` 报 versionCode 10 / versionName 2.7.1、applicationId `top.liuwei.xbvr`；`apksigner` 报 signer #1 证书 SHA-256 `20c3404b…`。许可 assets 与 `licenses/` 源逐字节一致（56/56），第三方材料清单齐全。打包目录为**新目录** `D:/codex-work/output/xbvr-android-2.7.1`；`output/xbvr-android-0.2.7*` 与 `output/xbvr-android-0.2.5*` 均未被覆盖或删除。

## 设备范围

- `emulator-5554`，API 36，x86_64：Q0/Q1/Q2/Q8 完整跑通；Q3/Q4/Q6/Q7 子集。T23R2 的 2.7.1 release 覆盖升级与冒烟同样在此模拟器上完成。
- `emulator-5580`（AVD `XbvrQa`），**API 29**：安装、启动、服务器菜单、离线许可冒烟通过；用后已关闭。
- **真机陀螺仪（T20）**：本 agent 环境无物理设备（`adb devices` 仅 `emulator-5554`，模拟器只提供虚拟 rotation-vector 传感器），当时如实记为 `device_blocked`。**2026-10-06 用户把候选 APK 安装到真机实测并报告陀螺仪无问题**，据此关闭该 `device_blocked`。**该结论来源为用户自述，非本 agent 执行或观测，本 agent 无法独立复核**；真机型号/Android 版本、所用 APK 文件，以及「横竖旋转 / 后台返回 / 关闭后不漂移」是否逐一覆盖均未知。

## 已确认

- **覆盖安装兼容（Q8）**：在 `emulator-5554` 的既有合成安装上 `adb install -r` 候选 APK（**未卸载、未 clear data**），`shared_prefs/local.xml` 覆盖前后字节相同：`profiles`/`active`、收藏、续播位置、所选来源、视角 JSON、封面比例/自动比例全部保留；目录缓存文件仍在。
- **debug→release 同键升级（T23R）**：在已安装 debug 构建（`flags=[ DEBUGGABLE ...]`，base.apk SHA-256 `3d8edf02…`）之上直接 `adb install -r` 签名 release 候选，返回 **Success**，安装后包标志变为 `flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`（`DEBUGGABLE` 消失）。`/data/data/top.liuwei.xbvr/shared_prefs/local.xml` 升级前后 SHA-256 完全相同（`a986b418a6703ecf34931318ee13e28ecaa27edd73729b9f9ddd99f4aba9516b`，6718 字节），目录缓存文件仍在。安装后 `run-as` 按预期失败（`package not debuggable`），改用 `adb root` 读取同一文件做字节比对。设备冒烟（启动→媒体库 22→Favorites 1→Continue watching 8→打开 Pattern 1 播放器→陀螺仪开关 off→on→off→返回媒体库）全部完成，进程存活，`logcat -b crash` 为空，无 `FATAL EXCEPTION`。证据 `D:/codex-work/output/xbvr-refactor-evidence/T23R-device/`。
- **0.2.7/9 → 2.7.1/10 同键覆盖升级（T23R2）**：在 `emulator-5554` 已安装的签名 release `0.2.7`（`versionCode 9`，`base.apk` SHA-256 `0a7bcd18…`）之上直接 `adb install -r XBVR-Pocket-2.7.1.apk`（**未卸载、未 clear data**）返回 **Success**；`dumpsys package` 由 `versionCode=9 versionName=0.2.7` 变为 `versionCode=10 versionName=2.7.1`，`flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`（无 `DEBUGGABLE`），安装后 `base.apk` SHA-256 `1ff60954…` 与新 release 一致。`/data/data/top.liuwei.xbvr/shared_prefs/local.xml` 升级前后 SHA-256 完全相同（`04160ab69506364f067654a316c3879a2c2a2b0c3f5c46f4408e8d397fb579c0`，6678 字节），`files/library-643ece43-….json` 与诊断文件仍在。读取方式：候选为非 debuggable，`run-as` 按预期报 `package not debuggable`，因此**显式使用 `adb root`**（该模拟器允许）读取同一文件做比对。冒烟：启动→媒体库 22→Favorites 1→Continue watching 8→打开 Pattern 1 播放器→陀螺仪开关 `off→on→off`→返回媒体库（22）；进程存活，`logcat -b crash` 为空，无 `FATAL EXCEPTION`。证据 `D:/codex-work/output/xbvr-refactor-evidence/T23R2-device/`（含 `INDEX.md`）。
- Q0：启动、服务器菜单（仅 1 条合成服务器）、离线许可全文、进出播放器；中英文文案（API 36 英文、API 29 中文）均可用，无崩溃。
- Q1：全部 22；Studio Alpha=11；Actor One=11；Favorites=1；Continue watching=8。
- Q2：封面比例 16:9→1:1→Auto→16:9 按服务器持久化（`coverMode` 3→1→0→3）。
- Q3/Q7 子集：进入播放器并恢复「Saved manual format」；手动选择 190° equidistant fisheye + SBS 写入 `view:`（`kind=2,layout=1,capture=190,override=true`）且状态行同步；「Restore automatic detection」回到自动（`override=false`）；GL 渲染画面正常；陀螺仪开关切换无崩溃。

## 未覆盖 / 未验证（不得读作通过）

- Q1 多标签 OR 语义未获结论（对话框只保留首个标签）；缓存离线、按 Added date 排序未单独复验。
- Q3 章节 / 速度 / 轨道 / 字幕 / 多文件切源；Q4 目录请求中切服务器、连接草稿重开；Q5 全项；Q6 GL mediump 编译与故障注入；Q7 完整 21 组合投影/眼别矩阵与触摸/缩放。
- 8K / HDR / 长时间音画同步未测试；无真实服务器、无真实凭据（仅合成 fixture）。
- API 29 为全新安装冒烟，**未**做「旧数据→覆盖安装」兼容。
- 离开播放器时诊断记录到一条 Media3 `ExoTimeoutException`（release 路径），Activity 存活、非崩溃；未进一步定性。
- **Q0–Q8 仍为部分覆盖**：2.7.1/10 的升版与重新打包只改版本常量与分发目录，未新增任何设备用例，因此上列未覆盖项对 2.7.1 同样成立，不得读作 Q0–Q8 全矩阵通过。T20 真机陀螺仪结论仍为**用户自述**（见「设备范围」），非本 agent 执行或观测。

# 0.2.7 验收记录 / Validation — 2026-10-05

包名 top.liuwei.xbvr，versionCode 9 / versionName 0.2.7，minSdk 29 / targetSdk 36。本次只改媒体库海报墙布局与封面缩放行为，未改动协议、播放器、投影与解码路径。

## 本次改动

- 海报列宽目标由 168dp 提高到 190dp，网格左右边距 20→8dp、卡片间距 12/10→6/6dp，卡片圆角 14→6dp，标题字号 14→15 且上边距 9→6dp，演职员胶囊行高 48→40dp。目标是提高海报墙的屏幕利用率，同屏可见行数增加。
- 新增固定封面比例下的适配裁切：自动模式保留 FIT_CENTER 以显示完整 artwork；用户选择 1:1 / 3:2 / 16:9 时改用 CENTER_CROP 居中裁切铺满画面，不再出现灰边。
- 修复一处复用缺陷：封面缩放类型此前只在 View 首次创建时设置，GridView 复用旧卡片导致切换比例后裁切不生效；现改为每次绑定时按需更新。

## 本地验证证据

- testDebugUnitTest、lintDebug 构建通过；工具回归测试 20 项 Python 与 17 项 Node 全部通过。
- API 36 模拟器实测：自动模式、1:1、3:2、16:9 四种比例裁切行为正确，英文浅色、英文深色、中文浅色下卡片角标与「点击开始观看」均完整可读；全程 crash 缓冲区无本应用崩溃。
- 平板布局复测：16:10 与 3:2 两种比例的横竖屏共四种组合下列数与状态恢复正常，旋转两次无崩溃。两种平板横屏的 screenHeightDp 为 800/931，均高于 500 阈值，因此不进入 compact 分支，沿用顶部搜索／筛选与底部导航布局。
- 未覆盖：真实服务器连接、VR 播放与真机兼容性矩阵本次未重跑。release 包的连接对话框无法通过 adb 输入注入自动化，视觉验证在 debug 构建上完成。

# Server menu UI update / 服务器菜单布局微调 — 2026-10-04

Version remains 0.2.5 / versionCode 7, with the original signing certificate. Only server-menu presentation changed; action handlers are retained.
版本保持 0.2.5 / versionCode 7 和原签名，只调整服务器菜单外观，保留原操作逻辑。

- `assembleDebug` succeeded. On the existing API 36 emulator, dark and light menus each showed five action rows with 8dp gaps and minimum 48dp height; current-server highlighting remained visible.
- Cover-ratio and offline-license entry points opened successfully. Device language, theme and other captured settings were restored. Full playback, real-server and device-compatibility tests were not rerun for this UI update.
- APK SHA-256: `94b247591210e005ede90dfe7484170434f21be11712d8c44783fde370608ca7`.

# 0.2.5 验收记录 / Validation

日期：2026-10-04。包名 top.liuwei.xbvr，versionCode 7 / versionName 0.2.5，minSdk 29 / targetSdk 36。本次增加许可材料与离线许可入口，保留之前完成的紧凑绿色标签；未重新执行整个 VR/解码/真机矩阵。

## 本次构建及材料检查

- assembleDebug、testDebugUnitTest、lintDebug 构建成功；47 项单元测试，0 失败、0 错误。Lint 0 错误、19 警告；没有将警告写成零问题。
- 完整许可材料同步后再次 assembleDebug 成功。最终 APK 中 56 份 assets/licenses 文件与 licenses/ 源文件逐字节相同。
- apksigner 验证通过，签名证书 SHA-256：20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4，保留此前版本签名；没有发布签名私钥。
- APK SHA-256：2d4223d5822da77efc4e93a4108b746c497fb2fc45f61c1e96a197e37835e5cd。
- 36 个运行时/脱糖输入制品分别记录许可；运行时、测试与构建范围分开。根 LICENSE/NOTICE 与离线副本相同。脱糖源码快照版本为 2.1.5；公共后缀数据首选源码的规则载荷与实际压缩资源相同。
- 中英文 README 均去掉特定小米机型描述。来源脚本缺失是公开待补证项，不把本次发布写成完整版权审计。

## 单模拟器许可流程

只使用已有 API36 模拟器。媒体库为合成场景，本次没有访问真实私服。许可页面用英文系统默认语言和中文应用语言验证：

- 从服务器菜单进入，能够离线读取打包索引。
- Wi-Fi/数据连接关闭后，选择 Apache-2.0 全文；UI 树包含正文末尾 END OF TERMS AND CONDITIONS 和责任限制条款。
- 旋转后保留选中文件，中文切换后按钮翻译而法律正文保持英文；浅色/深色、手机横竖屏与 2560×1600 模拟平板布局可读。
- 返回媒体库正常，崩溃缓冲区没有本应用崩溃。刻意断网期间，后台媒体库重载可能提示服务器不可达；测试关闭该预期网络提示后检查返回，不将其描述成网络播放验收。
- 恢复模拟器尺寸、密度、语言、旋转、主题与网络设置。截图与 UI 树留在本地 validation/compliance-qa/，不将含本地测试地址的整套日志直接上传。

新 Release 提供 APK、清理后的客户端源码包、第三方许可材料包、desugar 对应版本完整上游源码及 SHA256SUMS。源码包排除工具链、私钥、缓存、local.properties、交接文档及完整上游参考副本。依赖来源细节见 docs/compliance/。

# Repository maintenance verification / 仓库维护复核 — 2026-10-04

This maintenance change updates tools, CI and documentation after v0.2.5; it does not change Android source, version or previously published APK assets.
本次维护更新工具、CI 和文档，不修改 Android 源码、版本号或已发布 APK。

- Fresh local JVM verification: 47 tests passed; `lintDebug` completed with 0 errors and 12 warnings. 本次重新执行 47 项单元测试通过，lint 无错误，保留 12 项警告。
- Tool regression tests: 20 Python tests and 17 Node tests passed, including changed/missing hashes, failed publication rollback, state restoration and the API 36 help-command exit-code case.
- Independent retained-input check: 36 binary artifacts, 33 source JARs and 4 extra legal/source resources matched the committed lock. Staged generation matched 44 existing outputs; inventory JSON was compared semantically. The 40 files under `app/src/` remain byte-identical to the preceding public snapshot.
- The existing API 36 emulator was used only for read-only state capture; its actual command output parsed successfully. Full UI flow and on-device restoration were not rerun for this maintenance change. Restoration behavior is covered by mock regression tests; unsupported or unparseable states abort before device changes.
- CI is configured to run tool tests, JVM tests and lint. Its remote execution results are available in GitHub Actions; this record describes local evidence, not a claim that every future CI run passes.

固定哈希来自已经人工审核的 v0.2.5 快照，证明后续输入与该基准一致，不代表独立上游真实性认证。历史参考脚本来源缺口继续保留，见 [SOURCE_PROVENANCE.md](docs/compliance/SOURCE_PROVENANCE.md)。本次未扩大真实服务器、视频播放或设备兼容性验证范围。

## XBVR management feature branch / 原生管理分支 — 2026-10-10

- Branch: `feat/xbvr-management`, based on `main` at `eda1b4b`. Version remains 0.2.9 / code 12; this candidate is not a new published release.
- Fresh local `testDebugUnitTest lintDebug assembleRelease`: 225 JVM tests, zero failures/errors; Android lint has zero errors and 17 warnings. Management tests cover request formats, isolated credentials, unsupported routes, timeout/503 single submission, readback verification and WAMP lock events.
- Xiaomi real-device testing used an isolated, in-memory synthetic server through ADB reverse. Verified directory scan submission, site disable/enable, single-site scrape, unmatched file listing, searching scenes without available files, matching and unmatching with numeric server IDs read back after each operation.
- Candidate rows show covers beside title/site/duration; comparison shows the selected cover without cropping. Synthetic checkerboard covers rendered on the phone. Comparison selection and cover survived landscape/portrait changes. Missing/failed image states retain the text and allow retry.
- Verified distinct authorization and unsupported-interface messages, successful recovery, network failure after removing the test connection and recovery after restoring it. Task page returned via the launcher retains its page; background request counters did not advance during the seven-second observation. Server task completion was observed independently in the fixture snapshot; task UI does not infer success or historical progress from a cleared lock.
- Removed only the temporary loopback server profile, restored the original HTTPS profile and portrait lock, and removed ADB reverse. Switching server returned management to its root with the new server identity, without old file candidates.
- Actual XBVR 0.4.40 API declarations and upstream task/settings/matching implementations were checked read-only. No production directory scan, real site scraper run or production matching write was performed. This is protocol and synthetic workflow validation, not full production engine acceptance. Existing high-resolution/HDR playback coverage is not expanded by this change; browsing/favorites/playback code remains covered by the existing JVM regression suite, and synthetic browsing was checked on the device.
- Local evidence (outside the repository): `D:/codex-work/output/xbvr-management-20261010/`, including build logs, UI dumps/screenshots, fixture counters and match readback snapshots. No real media or server credentials are committed.


## Xiaomi 13 management optimization / 小米 13 管理优化 — 2026-10-10

- Version remains 0.2.9 / code 12 on `feat/xbvr-management`; no merge or release. Final APK installed with existing data and signing certificate preserved on Xiaomi 13 (Android 16 / API 36).
- Fresh `testDebugUnitTest lintDebug assembleRelease` passed: 231 JVM tests, zero failures/errors; lint zero errors and 17 warnings. Added regression tests for task subscription reconnect, stale subscription rejection and cancellation of pending reconnect on close.
- Fixed task events remaining disconnected after a network outage. Read-only WebSocket subscriptions retry transient failures while owned by the foreground page; authentication/unsupported responses do not loop. Device test stopped and restarted the isolated server without leaving the task page: HTTP reads and real-time events recovered, and a subsequent task end event appeared. No mutation is automatically repeated.
- Verified cleaned filename prefill, automatic candidate search and covers, skip-next across file pages, editable search and candidate pagination, match/unmatch readback, site toggles and task actions using synthetic media. Editing a search query retained focus and text while task events arrived. Rotation retained the candidate query; child-page titles identify their feature and the redundant body back button is absent.
- Controlled single-run comparison on this same phone with 202 synthetic sites: old page p99 frame time 150ms (180 frames); paginated page 32ms (144 frames). This bounded observation is not a general performance guarantee. Cover requests were limited to newly visible images in the observed scroll.
- A scan completed on the fixture while the app was backgrounded; task-page HTTP polling counters did not advance during the six-second background observation. The task UI labels cleared locks as ended with result unknown.
- The user-configured server was checked read-only for browsing and management candidates; production scan/scrape/matching writes were not tested. Synthetic validation does not establish real scraper-engine, high-resolution playback or HDR acceptance.
- Removed only the temporary loopback profile and ADB reverse, restored the user's HTTPS server and portrait rotation. No app crash appeared in the final device crash buffer.
- Local evidence: `D:/codex-work/output/xbvr-management-mi13-20261010/`; final APK SHA-256 `279fc03342423c1b7b6697c8bb1caaba0809e9797564f5eee05dd41edbec41d2`. No user media or credentials are committed.

## Focused performance and file preview / 性能采样与文件预览 — 2026-10-10

- Used Test Android Apps (`android-emulator-qa`, `android-performance`) on Xiaomi 13 / Android 16. Read-only actual-server flows: scraper open/scroll 628 rendered frames, 3 janky (0.48%), p99 16ms; candidate open/covers/scroll 596 frames, 3 janky (0.50%), p99 13ms. One warm run per flow, with 30-second Perfetto captures; this does not establish universal smoothness. Framework/scheduler evidence did not identify a specific Java method hotspot. Two memory snapshots are insufficient to establish absence of leaks.
- Added full-screen file preview to candidates, confirmation and matched-file details. Preview reads the selected file's DeoVR endpoint without scene/HereSphere fallback, validates the returned file and origin, preserves token parameters while setting `dnt=true`, and disables server favorite writes. It uses the current profile's existing player authentication and media transport.
- Fresh `testDebugUnitTest lintDebug assembleRelease` passed: 234 JVM tests, zero failures/errors; lint zero errors and 17 warnings. New tests cover unmatched-file loading, viewer credentials, no fallback, no favorite writes, tracking parameter, wrong-file rejection and rejecting a foreign origin before sending credentials.
- Installed the final release candidate preserving app data/signature. On isolated synthetic media, verified actual decoded picture, 206 range requests, seeking to 14/20 seconds, real landscape dimensions 2400x1080, and return to the same candidate/comparison context. Simulated media HTTP 404 displayed a playback error without a crash. No production video playback or production management writes were performed in this round.
- Removed only the temporary loopback profile/reverse and restored the user's HTTPS profile and portrait rotation. Final crash buffer empty. Version stays 0.2.9 / code 12, feature branch only.
- Local evidence and reports: `D:/codex-work/output/xbvr-management-perf-20261010/`; UI XML and synthetic screenshot additionally in `D:/codex-work/output/xbvr-management-mi13-20261010/`. APK SHA-256 `65946be1305f4fd0f0888a8a4dd03a315bf8b79504c7dc9c62d9422a142265d2`.
- Final review additionally verified rejection of another XBVR mount on the same origin with an identical file ID; correct mounted paths with filename suffixes/tokens remain supported. The regression failed before the guard and passed after it; final APK was rebuilt and reinstalled.

## Bottom-region playback seeking / 底部横滑调整进度 — 2026-10-10

- Lower 20% of usable video surface routes predominantly horizontal single-finger drags to seeking; other drags retain view panning. Routing locks after touch slop, with 120 seconds per viewport width and time clamping. Preview updates the HUD and progress; one seek is committed on release. Native controls retain priority. Multi-touch, resizing and leaving the player cancel pending seeking.
- Fresh `testDebugUnitTest lintDebug assembleRelease` passed: 239 JVM tests, zero failures/errors; lint zero errors and 17 warnings. Tests cover routing/origin lock, vertical motion, unknown duration, clamping, cancellation, remaining-finger pan after pinch and unchanged playback intent. Review identified and corrected the remaining-finger pinch routing before the final build.
- Installed the signed release candidate on Xiaomi 13 / Android 16, retaining data. Synthetic video checks verified native SeekBar priority, portrait preview from 14 to 8 seconds and release, cancellation through background/foreground restoring 14 seconds without committing, and paused state preservation. In landscape (2400x1080), the HUD showed a six-second rewind and disappeared after release; playback resumed from 14 seconds with the prior playing intent. Upper-area horizontal and bottom-area vertical drags left an explicitly paused position at 14 seconds.
- Wireless debugging briefly stalled and lost ADB reverse; this produced a synthetic media network error. Reinstating the reverse and reopening the synthetic file recovered playback. No production video or management writes were tested. Pinch continuation is covered by routing tests and review, not physical two-finger validation in this round; high-resolution/HDR acceptance is unchanged.
- Removed the temporary localhost profile and reverse, stopped the fixture, restored the user's HTTPS server and portrait orientation. Final device crash buffer empty. Version remains 0.2.9 / code 12 on `feat/xbvr-management`, without merge or publication.
- Local evidence: `D:/codex-work/output/xbvr-bottom-seek-20261010/`, including build log and UI dumps 30–51. APK SHA-256: `b3568d15b48e008fd6ccf067989148af01c1ce8cf54b3a374ebb2728a7d4308e`; signing certificate verified unchanged.
- Follow-up: swipe preview now immediately hides the control overlay, cancels its animation and keeps it hidden after release. Only the non-interactive time HUD appears; a later single tap can show controls. Fresh tests/lint/release build passed (239 tests; lint zero errors, 17 warnings) and the revised APK was installed on Xiaomi 13. This follow-up did not repeat the synthetic device gesture flows; prior device results above describe the preceding candidate. Build evidence: `hidden-controls-build.log` in the same local folder.
- User device feedback subsequently showed controls reopening after release. Inspection found `isPlayingChanged(false)` unconditionally opened controls during seek buffering, and the end callback did likewise. Added a persistent seek-hidden state shared by these automatic display paths; only an explicit show action releases it. Regression covers repeated delayed stop notifications and a subsequent explicit show. Fresh full build passed: 240 tests, zero failures/errors; lint zero errors and 17 warnings (`seek-callback-red.log`, `seek-callback-build.log`). APK: `XBVR-Pocket-seek-callback-fix-0.2.9.apk` in the local evidence folder.
- After wireless reconnection, the callback fix was installed and device-verified on Xiaomi 13. Portrait UI dumps 75–78 show only the time HUD while held and only the video surface after release, subsequent playback and end; an explicit tap restored controls (79). Paused seeking changed 14 to 8 seconds without showing controls (80–82); the explicit tap restored a paused 8-second position. Landscape 2400x1080 likewise showed only a six-second advance HUD while held and no controls after release (83–86). Native SeekBar remained usable to establish the baseline. Tests used synthetic media only. Removed the localhost profile/reverse, restored the user's HTTPS server and portrait orientation (91), stopped the fixture; final crash buffer empty. Version and branch unchanged, no publication.

## Compact scraper management / 刮削管理紧凑布局 — 2026-10-11

- Implemented the user's selected first Product Design concept in the existing native Android page: site name/status, outlined scrape button and separate switch share one row. Long names wrap to two lines, controls retain 48dp touch height, and font scaling can enlarge the button. Refresh moved to the header; local live search and All/Enabled segments replace the separate apply/read buttons. Kept pagination, server binding, confirmation and readback behavior. Single-site confirmation identifies the selected site.
- Search replaces only list results, preserving the input field and focus. Synthetic-device requests showed no additional site reads while typing; explicit refresh retained the query. Enabled count reflects the whole server list rather than search results. A font recreation test exposed cancelled capability discovery leaving the attempted flag set; cancellation now permits rediscovery when no declaration was obtained, and explicit site refresh also retries unknown capabilities.
- Fresh full `testDebugUnitTest lintDebug assembleRelease` passed: 240 tests, zero failures/errors. After removing obsolete strings and adjusting the English count label, final `lintDebug assembleRelease` passed with zero errors and 17 warnings. Version remains 0.2.9 / code 12 on `feat/xbvr-management`; no merge or publication.
- Xiaomi 13 / Android 16 synthetic-device validation: 202-site pagination, long labels, live search, enabled-only filtering, disabled buttons, refresh/query retention, single-site scrape request bound to `site=pattern`, site disable/enable with verified readback, 1.3 font scale and actual landscape/portrait changes retaining query/selection. Final font recreation retained valid capabilities and an enabled scrape button. The user's actual server was read only to display the final page; no production scrape or setting mutation was performed.
- Installed the final signed APK with app data retained. Removed only the temporary localhost profile/reverse and stopped its fixture. Restored original font scale 1.0, portrait rotation and automatic-rotation setting. Wireless debugging was never disabled; final read returned `adb_wifi_enabled=1`. Final crash buffer empty. Font scales above 1.3 and other device sizes were not tested.
- Local screenshots, UI dumps, build logs and design comparison notes: `D:/codex-work/output/xbvr-scraper-layout-20261011/`. Final APK SHA-256: `a1730cd32c86ab21d1a2a0126df9202b664300f95012d8e978b90ee0c7a15cef`. No user server credentials, images or generated concept images are committed.
# 2026-10-11 — Media-library maintenance layout and server scan times

- Implemented revised visual option 2 in native Java: toolbar directory refresh, compact directory rows with inline scan buttons, and two equal-width update/sync actions. No version change or upstream XBVR modifications.
- Reads each volume's `last_scan`; displays relative age and a full timestamp dialog. Go zero time, absent and invalid values display no scan record. Foreground labels update every minute without network polling; foreground re-entry and observed rescan completion re-read server storage.
- `testDebugUnitTest`: 243 tests, zero failures/errors, including three new timestamp cases. Final UI package: `lintDebug assembleRelease`, successful; lint zero errors and 17 existing warnings.
- Xiaomi 13 wireless install with `install -r`: success, version 0.2.9/code 12. Actual server directory timestamps verified read-only and unchanged throughout QA. Checked toolbar refresh, timestamp detail, per-directory confirmation/cancel, scene-refresh confirmation/cancel, and App list reload. Production scan/scene-refresh tasks were not started.
- Checked font scales 1.0/1.3 and actual 2400×1080 landscape with scrolling. Fixed large-font bottom action title wrapping; final normal/large screenshots inspected. Restored font 1.0, free rotation and automatic rotation; wireless debugging remains enabled. App data/configuration preserved.
- Artifacts and source/mock comparison: `D:/codex-work/output/xbvr-maintenance-layout-20261011/`; see `design-qa.md`. APK SHA256: `82dbefb8887f71af1b90f216b2759ae1cad538a710257544b71e3d11c25568c2`.

## Follow-up: automatic library reload and matching durations

- On an observed server rescan transition from running to ended, reload the App media list automatically. Wait if another library/metadata load is busy; cancel deferred callbacks while paused and bind the pending action to the current profile. Unknown/disconnected task states do not imply completion.
- Xiaomi 13 + isolated synthetic XBVR: one scan submitted, completion observed, without tapping reload. Both `POST /deovr` and `POST /api/scene/list` increased from 1 to 2; directory storage reads also increased from 1 to 2. Evidence: `auto-before.json` / `auto-completed.json`. No production scan was started. Completion while the App has no live subscription is not claimed to trigger a reload.
- Matching file, candidate and comparison summaries show minutes only (up to one decimal, no trailing `.0`). File seconds are divided by 60; scene durations are already minutes, confirmed against upstream `ui/src/views/files/SceneMatch.vue`. Invalid/nonpositive values remain unknown. Xiaomi 13 comparison showed synthetic 20-second file as 0.3 minutes, and 20-minute scene as 20 minutes (`duration08-compare.png/xml`). No match was applied.
- Final `testDebugUnitTest lintDebug assembleRelease`: successful; 245 tests, zero failures/errors. Installed with `install -r`; original server restored, temporary profile/reverse/fixture removed, wireless debugging left on.
- Updated APK at the same output path: SHA256 `0e0e52de9d37d17ec6102a8a1e0bdb2f68571519083a39715ea9300e727c83dc`.
