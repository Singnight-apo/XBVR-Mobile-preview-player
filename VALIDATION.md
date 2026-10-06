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

