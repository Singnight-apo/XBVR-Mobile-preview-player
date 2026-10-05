# 四层重构进度 / Four-layer refactor progress

本表按方案 `执行方案.md` 的固定格式逐任务填写实际值。**Commit 列填该任务工作提交的真实短 ID，不填虚构值**；`status` 允许 `not_started`、`in_progress`、`failed`、`device_blocked`、`passed`。`device_blocked` 不等于完成。

| Task | Commit | Build/JVM/lint | Device cases | Evidence | Status |
|---|---|---|---|---|---|
| T00 | `25fa0ad` | PASS `assembleDebug`+47 JVM（0 失败/0 错误/0 跳过）；lint 0 错误/20 警告；工具回归 Python 20 + Node 17 | Q0,Q1,Q2,Q3 基线与合成数据快照，emulator-5554 API 36 | `T00-20261005-225347-e8ef0bfb`、`T00-device` | passed |
| T01 | `4840de2` | PASS `assembleDebug`+47 JVM（0/0/0）；lint 0 错误/20 警告；APK 签名校验通过 | Q0：启动、服务器菜单、离线许可、进出播放器；手动格式恢复；无崩溃 | `T01-20261005-230202-6cb7ae03`、`T01-before-caac8fa2c9ff4fed91f143102b340d4f`、`T01-device` | passed |
| T02 | `3abac00` | PASS `assembleDebug`+47 JVM（0/0/0）；lint 0 错误/20 警告；APK 签名校验通过；GD `--mode domain` 16 文件 0 违规 | Q1 子集：全部 22 → 搜索 Studio=11 → 搜索 Actor=11 → 清除后 22；无崩溃 | `T02-20261005-230417-b8d03db6`、`T02-device` | passed |
| T03 | `5c7ef6c` | GT PASS `assembleDebug`+**56** JVM（0/0/0，较 T02 +9）；lint 0 错误/20 警告；APK 签名校验通过；工具回归 Python 20 + Node 17；测试 APK 编译通过；GD `--mode domain` 17 文件 0 违规 | Q3：手动格式恢复（190° 鱼眼 SBS）；恢复自动→Flat Mono；自动识别 360°/TB、fisheye190/SBS、mkx200/Mono；无崩溃 | `T03-20261005-230821-a63c6f82`、`T03-characterization`、`T03-device` | passed |
| T04 | `afe8aa1` | PASS `assembleDebug`+**69** JVM（0/0/0，较 T03 +13）；lint 0 错误/20 警告；APK 签名校验通过；GD `--mode domain` 19 文件 0 违规 | Q2：四种比例切换 2→1→2→3→0 并逐服务器持久化；1:1 居中裁切实测；刷新后状态保留 22 项；无崩溃 | `T04-20261005-231439-bb941fef`、`T04-device` | passed |
| T05 | `36ac1d0` | PASS `assembleDebug`+**77** JVM（0/0/0，较 T04 +8）；lint 0 错误/20 警告；APK 签名校验通过；GD `--mode domain` 20 文件 0 违规 | Q3：自动识别（190° 鱼眼 SBS）→ 手动 200° 鱼眼 Mono → 恢复自动回到 190°；无崩溃 | `T05-20261005-231719-392c563a`、`T05-device` | passed |
| T06 | `127a825` | PASS `assembleDebug`+**87** JVM（0/0/0，较 T05 +10）；lint 0 错误/20 警告；APK 签名校验通过；GD `--mode domain` 21 文件 0 违规 | Q1/Q3：旧实现写入的 `fav:`/`pos:` 键仍被解析——收藏 1 项、继续观看 5 项、全部 22 项带续播；无崩溃 | `T06-20261005-232026-d5a9e277`、`T06-device` | passed |
| T07 | `691ba93` | PASS `assembleDebug`+**96** JVM（0/0/0，较 T06 +9）；lint 0 错误/20 警告；APK 签名校验通过；GD `--mode domain` 24 文件 0 违规 | Q0/Q1：服务器菜单仅 1 条；新增对话框五字段草稿横竖屏往返保留；编辑当前服务器按类型预填并**原地替换**（blob 225 字符不变、仍 1 条）；播放器经类型化查找进入；无崩溃 | `T07-20261005-232510-aa1ba30c`、`T07-device` | passed |
| T08 | `3edf223` | PASS `assembleDebug`+**105** JVM（0/0/0，较 T07 +9）；lint 0 错误/20 警告；APK 签名校验通过；GD `--mode domain` 29 文件 0 违规 | Q2/Q3：旧 `view:` 记录读回一致；手动 200° 鱼眼 SBS 写出**同一 14 键 JSON**、`override=true`，重进恢复「Saved manual format」；`coverMode` 走新路径往返；收藏 1 / 继续观看 5 / 全部 22；无崩溃 | `T08-20261005-233138-258bb38a`、`T08-device` | passed |
| T09 | `3d35d4a` | GT PASS `assembleDebug`+**105** JVM（0/0/0）；lint 0 错误/20 警告；APK 签名校验通过；工具回归 Python 20 + Node 17；测试 APK 编译通过；GD `--mode domain` 33 文件 0 违规 | 设备：`StorageCompatibilityInstrumentation` **OK（11 项存储断言）**、两个 runner 均已注册；运行后 profile blob（225）/active/files 目录**与运行前完全一致、无探针残留**；应用 Q0/Q1：22 / 5 / 1；无崩溃 | `T09-20261005-234332-98b5d4eb`、`T09-device` | passed |
| T10 | `7af2a56` | GT PASS `assembleDebug`+**109** JVM（0/0/0，较 T09 +4）；lint 0 错误/20 警告；APK 签名校验通过；工具回归 Python 20 + Node 17；测试 APK 编译通过；GD `--mode domain` 36 文件 0 违规 | Q1：在线 22 项；**停掉 fixture 后刷新仍保留 22 项缓存目录**、无崩溃；fixture 恢复后刷新回到 22 项 | `T10-20261005-234743-14dc107f`、`T10-device` | passed |
| T11 | `c012d2f` | PASS `assembleDebug`+**117** JVM（0/0/0，较 T10 +8）；lint 0 错误/20 警告；APK 签名校验通过；GD `--mode domain` 39 文件 0 违规 | Q2/Q4：22 项封面全部解码渲染；`coverAuto` 由解码位图重新推断；3:2 居中裁切、收藏与续播标记保持；无崩溃 | `T11-20261005-235300-2e9ad913`、`T11-device` | passed |
| T12 | `ffecdb7` | PASS `assembleDebug`+**123** JVM（0/0/0，较 T11 +6）；lint 0 错误/20 警告；APK 签名校验通过；GD `--mode domain` 44 文件 0 违规 | Q1/Q4：三阶段加载后 22 项、收藏 1 项；无崩溃 | `T12-20261005-235722-4ae93639`、`T12-device` | passed |
| T13 | | | | | not_started |
| T14 | | | | | not_started |
| T15 | | | | | not_started |
| T16 | | | | | not_started |
| T17 | | | | | not_started |
| T18 | | | | | not_started |
| T19 | | | | | not_started |
| T20 | | | | | not_started |
| T21 | | | | | not_started |
| T22 | | | | | not_started |
| T23 | | | | | not_started |

证据根目录：`D:/codex-work/output/xbvr-refactor-evidence`（本地，不入库）。详细基线见 `BASELINE.md`。

## 失败与修正记录

- T02 首次构建失败：移动 LibraryQuery 到 domain 后 MainActivity 缺少显式 import。证据 T02-20261005-230349-5115442e/application-checks.log（MainActivity.java:803 找不到符号）。已补 import top.liuwei.xbvr.domain.LibraryQuery; 后重跑 G 通过，测试数与 lint 数未变。
- T02 首次提交只包含重命名（PowerShell 反斜杠续行无效导致路径未全部 add）。已 git add -A -- app/src 后 amend 为 \3abac00\，提交内容与已验证工作区一致。

## 失败与修正记录（续）

- T03 提交 5c7ef6c 时 git 将重命名显示为 Projection.java => domain/FormatInference.java（相似度 51%），因为被抽出的 infer 正文比残留的 Projection 更长。提交树本身正确：domain/Projection.java 为移动后的类，domain/FormatInference.java 为新增文件，根 Projection.java 已删除（git cat-file -e HEAD:app/src/main/java/top/liuwei/xbvr/Projection.java 返回 128）。仅影响 git show 的重命名配对显示。
- T03 测试先行证据：新增特征断言先在旧实现上运行通过（49 项，0 失败，T03-characterization/tests-old-impl.log），再做移动与抽取。

## 失败与修正记录（T04）

- T04 首次 G 失败：新增 LibraryFilterStateTest 我最初把标签语义写成了 AND（期望只有 1 项），实际既有语义是**标签内 OR、跨 facet AND**（LibraryQuery javadoc 与 LibraryQueryTest.sameFacetOrAcrossFacetsAndAndCancelRestores 已锁定）。已按既有语义修正测试期望并补充 OR/AND 两个断言，未改动生产代码。证据 T04-20261005-231405-6efde0d8/application-checks.log。
- 说明：LibraryFilterState 在 T04 建立并带测试，生产容器（LibraryUiState/LibraryController）在 T13 接入，MainActivity 现有筛选字段届时一并迁移。

## 失败与修正记录（T07）

- T07 首次构建失败：Android 的 org.json.JSONObject.put/JSONArray.put(int,Object) 抛出受检 JSONException，新的 ProfileJsonMapper 未声明。已在 mapper 内把不可能发生的序列化失败收敛为 IllegalStateException（六个非 null 字符串），保持调用方无需处理受检路径。证据 T07-20261005-232357-bd0662d5、T07-20261005-232436-a3311cd8。
- 第二次失败：测试编译时 JVM org.json 同样要求 	hrows。已为测试方法补齐签名。
- 设备观察（非本次改动引入）：模拟器横竖屏往返后状态栏 insets 生效，界面整体下移约 73px，服务端按钮从 y≈147 变为 y≈220。已在后续设备脚本中按实时 bounds 定位；未修改应用代码。

## 失败与修正记录（T08）

- 编写 PlaybackJsonCodecTest.manualFlagWithoutKindLeavesTheSamePartialUpdate 时我最初按「kind 与 known 都已写入」断言，与旧
estore 的真实顺序不符：旧代码在 p.kind=j.getInt("kind") 之后、p.known=true 之前就因缺失 layout 抛出。已按旧顺序改为 kind 保留、known/
eason 不变、后续字段全部未写入。证据 T08-20261005-233138-258bb38a（该断言随 G 一起通过）。
- 设备侧排查说明：T08 首次查看「继续观看 3 项 / 收藏 0 项」疑似回归，实为搜索框仍残留 Pattern 1 过滤所致；清空搜索后为 5 / 1，与 T06 基线一致。
- 观察（非本次改动）：coverAuto 在自动模式下会按首个可用封面重新推断；合成素材含 1:1 与 16:9 封面，因此该值可能在 1.0 与 1.7777778 间变化，属既有行为。

## 失败与修正记录（T09）

- **测试 manifest 只合并出一个 <instrumentation>**：manifest merger 不按 ndroid:name 识别 instrumentation 元素，两个 manifest 各声明一个时会塌缩成单个元素（	ools:node="merge" 也无效，日志为 instrumentation defined in both files...）。修正：两个 runner 都写在 pp/src/androidTest/AndroidManifest.xml 同一个文件里，合并结果同时保留 RendererFailureInstrumentation 与 StorageCompatibilityInstrumentation。证据 T09-manifest-merge.log。
- **Instrumentation.finish() 会先结束进程，inally 不再执行**：因此测试用的探针 .tmp 文件第一次运行后留在了 iles/。修正：把设备还原（profile blob、active、探针文件）移出 inally，在成功与失败两条路径上都在 inish() **之前**调用；inally 保留为兜底。修正后连跑两次，files 目录与 prefs 与运行前完全一致。
- 编译失败一次：Store 未转发 ProfileStore.remove，androidTest 报「找不到符号 remove(String)」。已补 Store.removeProfile(String)。

## 失败与修正记录（T10）

- 编译失败一次：root Protocol 转发 XbvrProtocol.normalized 时未声明受检 JSONException。已为转发方法加上 	hrows org.json.JSONException。
- **脚本变量名冲突（我的失误）**：PowerShell 变量名大小写不敏感，$T（测试目录）被 $t（文件内容）覆盖，导致后续路径拼接失败并报「路径语法不正确」。所幸只有第一个（路径仍有效）的 ApiMetadataTest 写入成功，其余写入全部失败、未产生损坏；已核查全仓无 XbvrXbvr 之类的重复替换残留，并改用 edit 工具完成剩余测试文件。

## 失败与修正记录（T11）

- 编译失败一次：Gallery.getView 的观察者 lambda 参数名 key 与方法内既有变量冲突（「已定义变量 key」）。已改名为 completedKey。
- 方案要求的 T/ui/library/CoverBindingTest.java 未创建：适配器绑定需要 Android View，本项目未引入 Robolectric，且方案禁止新增架构/测试框架。绑定路径改由 Q2 设备检查（封面渲染、比例裁切、收藏/续播标记）与既有滚动检查覆盖；未用假的 JVM View 测试冒充。
- BitmapCoverRepository.retryAll() 是为复刻旧 imageProblems.clear()（元数据补充完成后允许重试失败封面）而加的，Domain 接口本身未改。

## 续接点 / Handover (T11 完成时)

- 分支
efactor/four-layer，工作区干净，T00–T11 全部 passed，T12–T23
ot_started。
- 环境：mulator-5554（API 36）在线；合成 fixture 需以
ode ./filter-fixture.cjs 于方案包 ixture/ 目录启动，监听 127.0.0.1:18766；应用内地址 http://10.0.2.2:18766。
- 设备脚本注意：横竖屏往返后状态栏 insets 会让界面下移约 73px，**每次按实时 UI dump 的 bounds 定位**；卡片 bounds 形如 [x1,y1][x2,y2]，需先把 ][ 替换成 , 再切分。
- 下一步 T12 要点：EntryMetadata/LibraryEvent/LibraryRepository + DefaultLibraryRepository，把 MainActivity.load 拆成 cache→directory→metadata 三阶段，保持 4/3/1 线程与 retryAfter/force 语义；测试用 fake XbvrApi 与 fake cache。
- 命令模板：Invoke-Checks.ps1 -Project <repo> -Batch T12（涉及工具/测试入口时加 -ToolTests -InstrumentationBuild）；Guard：erify_architecture.py <repo> --mode domain。
- 证据根目录：D:/codex-work/output/xbvr-refactor-evidence（本地，不入库）。

## 失败与修正记录（T12）

- 编译失败两次：①LibraryEvent 使用 Entry 未 import；②Store 声明实现 LibraryCacheStore 但方法名是 cache(...)，与接口的
ead/write 不符。已在 Store 上补
ead/write 实现并保留 cache(...) 作为转发别名。测试侧：三个 JSON 构造辅助方法缺 	hrows Exception。
- 范围偏差（已在提交信息记录）：方案要求 Domain 负责元数据合并；当前合并仍走 Data 侧的共享协议规则（快照已含累计 posterCandidates），由 MainActivity.mergeMetadata 按 identity 赋回活动条目，净结果与旧 Protocol.merge 一致，但合并本身尚未成为 Domain 函数。

## T13 执行说明

- 本轮 T13 的实现由**单个顺序 subagent** 完成（非并行，同一工作目录仅一个写入者），Lead 独立复跑门禁后才提交：G 通过 132 JVM / lint 0-20 / 签名校验，GD 46 文件 0 违规，Q1 设备 22/5/1/11 与基线一致。subagent 未提交、未改 PROGRESS。
- 边界（subagent 主动报告，已记入提交信息）：①额外提供 state()/generation() 访问器，generation() 供海报适配器沿用封面 epoch；②封面推断字段已进 LibraryUiState，但延迟 Bitmap 工作与 CoverRepository<Bitmap> 绑定仍在 Activity（T14 范围），旧的 covers.retryAll() 由 Activity 监听状态消息变化触发；③MainActivity 内保留 ProfileRepository 适配器与 LibraryRepository 委托。

## T14 执行说明

- T14 同样由**单个顺序 subagent** 实现，Lead 独立复跑门禁（PASS 140 JVM / lint 0-20 / 签名；GD 48 文件 0 违规）并补跑设备回归后才提交。
- 设备回归要点：方案要求观察「第一行项目与 top 像素」。实测滚到 Pattern 9（top=567）后，**改封面比例**与**进出播放器**都保持 Pattern 9 top=567。切 tab 会回顶部，但已用 `git show 665265c` 逐字节核对：T14 前后 tab 点击处理器完全相同且都调用 `filter(false)`（设计上重置到第 0 项），**非本次回归**。
- subagent 报告的边界：①`GridScrollRestorer` 用 `Page` 接口而非保存 GridView（`build()` 会替换 GridView）；②`PosterAdapter.setCovers` 可变以保留切服务器时的旧回调防护；③`PosterAdapter` 仍 import 旧 root `Ui`，`--mode final` 会临时出现 ui→legacy 边，待 T20 迁到 `ui/common` 消除。

## T15 执行说明

- T15 由**单个顺序 subagent** 实现，Lead 独立复跑 G（PASS 140 JVM / lint 0-20 / 签名）与 GD（51 文件 0 违规）并补跑设备矩阵后才提交。MainActivity 由 1426 行降到 461 行。
- 设备矩阵与基线一致：22 / 5 / 1 / 搜索 Studio=11；服务器菜单 1 条 + Add server + Edit current server；编辑对话框为类型化表单（Edit server / Cancel / Save and connect）；**新增服务器草稿在横竖屏往返后保留**；无崩溃。
- subagent 报告一次中间失败：6 个 javac 错误全部是跨包可见性（MainActivity 在 root 包），已通过放开 MainView 成员可见性修复，**未削弱任何测试或门禁**。

## T16 执行说明

- T16 由**单个顺序 subagent** 实现，Lead 独立复跑 G（PASS 148 JVM / lint 0-20 / 签名）与 GD（54 文件 0 违规）后才提交；PlayerActivity 删除 803 行、新增 246 行。
- 主动报告的偏差：单选对话框改为**先 dismiss 再回调**（旧实现是 files/eye/packing 先应用后关闭、layout 为应用→关闭→打开 packing）。状态写入与渲染相同，仅 dismiss 回调略早；packing 仍在 layout 关闭后出现——设备实测该链路正常。
- 中间一次 lint 失败（SubtitleView 的 5 个 UnsafeOptInUsageError）已按仓库既有 `@OptIn(markerClass = UnstableApi.class)` 修复，未削弱测试或门禁。
- 诚实标注：自动隐藏（4200ms）在设备上因视频处于暂停态而**未获结论**，`canHide` 明确要求 playing；该路径由新增的 8 项单测覆盖，未写成设备已验证。

## T17 执行说明

- T17 由**单个顺序 subagent** 实现，Lead 独立复跑 G（PASS 161 JVM / lint 0-20 / 签名）与 GD（60 文件 0 违规）后才提交；新增 7 个文件、PlayerActivity 净减 328 行中的大部分逻辑外移。
- 主动报告偏差：①轨道菜单仍直接读 Media3 `getCurrentTracks()` 以保持分组标签/编号，`PlayerPort.tracks()/selectTrack()` 已在适配器实现但控制器尚未驱动（T18 范围）；②Activity 仍构造 `DefaultMediaDetailsRepository`（按方案，T22 由组装入口替换）；③`Listener` 较宽（18 个语义方法）以保持 Android-free。
- 诚实标注：设备侧 Q5（设置面板/对话框）本次未捕获到 dump，**未声称已验证**；Q3 的核心证据是播放器可用且新场景进度被持久化。

## T18 执行说明

- T18 由**单个顺序 subagent** 实现，Lead 独立复跑 GT（PASS 166 JVM / lint 0-20 / 签名 / 工具回归 / 测试 APK）与 GD（64 文件 0 违规），并**亲自重跑 RendererFailureInstrumentation（OK 3 项）**与播放/保存检查后才提交。
- 主动报告偏差：①新增 `PlaybackLifecycle.backend()`；②新增 `Listener.stopped()` 以在释放后刷新播放按钮；③轨道菜单仍用 Media3 group 对象（`currentTracks()/overrideTrack()`）而非 `TrackOption` token，否则会改变可见标签/编号（T18 范围内既定取舍）；④`prepare()` 现经策略释放旧引擎（多一次 clearVideoSurface 与位置读取）；⑤`position()` 无引擎时回退到已保存位置（旧代码会 NPE）。
- **测试修正声明**：subagent 修正了一处**既有失效断言**——instrumentation 期望中文脱敏标记，而生产实际输出 `[URL omitted]`/`[credentials omitted]`；只改断言以匹配真实输出，保密性检查未动。Lead 复核认可为「修正」而非「削弱」。
- 诚实边界：完整 Q3/Q5/Q6 手工矩阵未跑，**T18 不记为完全设备通过**。

## T19 执行说明

- T19 由**单个顺序 subagent** 实现，Lead 独立复跑 GT（PASS 171 JVM / lint 0-20 / 签名 / 工具回归 / 测试 APK）与 GD（66 文件 0 违规），并**亲自重跑 instrumentation（OK 3 项）与 Q7 子集**后才提交。
- 可信度要点：`RendererShader` 的 diff **只有 package 行**（GLSL 逐字节一致），`ProjectionMath.map` 非常量体逐行搬移，触摸/陀螺仪映射未动。
- 主动报告偏差：①Activity 改用 `setSettings`/`setVideoSize` 转发（VrView 需向渲染器推送设置与视频几何，同时保持单一 Projection 引用）；②instrumentation 通过反射 VrView 私有 `renderer` 字段定位（`GLSurfaceView.getRenderer()` 非公开 API）；③新增 `ProjectionMathTest`（方案未点名但门禁要求 JVM 数上升）。
- 诚实标注：完整 21 组合视觉矩阵未手工跑完，**T19 不记为完全通过 Q7 全矩阵**。

## T20 执行说明与部分完成状态

- T20 由**单个顺序 subagent** 实现，Lead 独立复跑 G（PASS 178 JVM / lint 0-20 / 签名）与 GD（67 文件 0 违规）后才提交。
- **状态为 device_blocked**（方案的 `device_blocked` 不等于完成）：代码结构与门禁已完成，但方案要求的「至少一次真机验证陀螺仪、横竖旋转、后台返回、关闭后不漂移」**无法执行**——`adb devices` 仅 emulator-5554，且模拟器提供的是虚拟 rotation-vector 传感器（type 15/20/11），无传感器回退分支也不可达。设备侧仅验证了安装与陀螺仪开关不崩溃。
- 保留语义：`active` 门控、`SENSOR_DELAY_GAME`、基准重置、两个不同的 toast 字符串 id、`wrappedDelta` 的 ±180 环绕、pitch 边界与四种 rotation 映射；传感器在 `onStop` 注销而非等 `onDestroy`。
- 偏差：纯映射/状态机放在生产文件内的 `GyroController.Mapping`（公开嵌套类），以便 JVM 测试无需 Robolectric；`VrView` 无需改动。
- 若你希望把 T20 标为完全 passed，需要一台带真实陀螺仪的设备；否则按方案应保持 device_blocked。

## T21 执行说明

- T21 由**单个顺序 subagent** 实现，Lead 独立复跑 GT（PASS 184 JVM / lint 0-20 / 签名 / 工具回归 / 测试 APK）与 GD（69 文件 0 违规），并亲自重跑 instrumentation 与「许可界面 + 诊断对话框」设备检查后才提交。
- 语义保持：`DiagnosticsSink` 未改、仍是唯一 Domain 端口（未新建第二个同名接口）；单一 uncaught handler（有 `installed` 守卫，存在旧 handler 时委托而非吞掉）；脱敏正则、30KB 上限与 UTF-8 边界、8 cause/64 frame 限制、GPU 串格式均保持。
- 主动报告偏差：①根 `PlaybackDiagnostics` 直接删除而非留空壳（它引用已迁移的 Ui，无法编译），因此 **T22 的"删除 PlaybackDiagnostics"项已提前满足**；②`DiagnosticsDialog`（ui）import `data.DiagnosticsStore`——这是方案指定放置的必然结果，`--mode domain` 通过，但在 T22 的 `--mode final` 下会表现为 ui→data 边，需在 T22 重新接线。

## T22 执行说明

- T22 由**单个顺序 subagent** 实现，Lead 独立复跑 GT、**`--mode final`（66 文件 0 违规）**、依赖未变核查与两项 instrumentation + 应用冒烟后才提交。
- 根包现已只剩 `AppServices`、三个 Activity 与 `XbvrApplication`；root `Api/Protocol/Store` 已删除。`app/build.gradle` 无改动（无新依赖/模块/框架）。
- 主动报告偏差：①`PlayerActivity.tracks()` 仍读 Media3 `Tracks.Group/Format` 以生成菜单**标签**（override 只在 session，T18 已记录的取舍）；②`projectionFormats()/viewingEye()` 仍作为手动选择器的 UI 状态编辑 Projection 字段（渲染数学自 T19 已在 media）；③Domain `CoverRepository` 增加 `retryAll()`、`DiagnosticsDialog.show` 改签名、`MainView.Actions` 增 `normalizeBase`；④两个 instrumentation 按新类型重写、断言未减（存储仍 11 项）。
- 观察（非回归）：继续观看由 5 增到 7，是本轮及此前设备测试播放新场景累计的续播记录。
