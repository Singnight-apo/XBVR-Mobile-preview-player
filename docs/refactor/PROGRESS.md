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
| T13 | `fc66584` | PASS `assembleDebug`+**132** JVM（0/0/0，较 T12 +9）；lint 0 错误/20 警告；签名校验通过；GD `--mode domain` 46 文件 0 违规 | Q1：22 / 5 / 1 / 11（与基线一致） | `T13-device` | passed |
| T14 | `d85e6b5` | PASS `assembleDebug`+**140** JVM（0/0/0，较 T13 +8）；lint 0 错误/20 警告；签名校验通过；GD `--mode domain` 48 文件 0 违规 | Q4：滚到 Pattern 9（top=567）后改封面比例、进出播放器仍保持；切 tab 回顶为既有行为 | `T14-device` | passed |
| T15 | `3da6efe` | PASS `assembleDebug`+**140** JVM（0/0/0）；lint 0 错误/20 警告；签名校验通过；GD `--mode domain` 51 文件 0 违规 | 完整媒体库矩阵：22 / 5 / 1、搜索 Studio=11、服务器菜单、新增草稿旋转保留；无崩溃 | `T15-device` | passed |
| T16 | `9661f4a` | PASS `assembleDebug`+**148** JVM（0/0/0，较 T15 +8）；lint 0 错误/20 警告；签名校验通过；GD `--mode domain` 54 文件 0 违规 | Q3：格式对话框链路（应用→关闭→packing）实测正常；自动隐藏在暂停态未获结论 | `T16-device` | passed |
| T17 | `a1a97a6` | PASS `assembleDebug`+**161** JVM（0/0/0，较 T16 +13）；lint 0 错误/20 警告；签名校验通过；GD `--mode domain` 60 文件 0 违规 | Q3：播放器可用、新场景进度持久化；Q5 未捕获到 dump | `T17-device` | passed |
| T18 | `4991381` | PASS `assembleDebug`+**166** JVM（0/0/0，较 T17 +5）；lint 0 错误/20 警告；签名校验通过；工具回归；GD `--mode domain` 64 文件 0 违规 | `RendererFailureInstrumentation` OK 3 项 + 播放/保存检查；**完整 Q3/Q5/Q6 手工矩阵未跑**（见 T18 说明） | `T18-device` | passed |
| T19 | `8698d74` | PASS `assembleDebug`+**171** JVM（0/0/0，较 T18 +5）；lint 0 错误/20 警告；签名校验通过；工具回归；GD `--mode domain` 66 文件 0 违规 | Q7 子集：instrumentation OK 3 项；**21 组合视觉矩阵未跑完**（见 T19 说明） | `T19-device` | passed |
| T20 | `aafa2ec` | PASS `assembleDebug`+**178** JVM（0/0/0，较 T19 +7）；lint 0 错误/20 警告；签名校验通过；GD `--mode domain` 67 文件 0 违规 | **真机陀螺仪由用户实测确认无问题**（2026-10-06 用户自述，**非本 agent 执行**；本 agent 环境 `adb devices` 仅模拟器，无法独立复核）；本 agent 设备侧仅验证安装与陀螺仪开关不崩溃 | `T20-device` | passed |
| T21 | `9ecd893` | GT PASS `assembleDebug`+**184** JVM（0/0/0，较 T20 +6）；lint 0 错误/20 警告；签名校验通过；工具回归；GD `--mode domain` 69 文件 0 违规 | instrumentation 重跑 + 「许可界面 + 诊断对话框」设备检查 | `T21-device` | passed |
| T22 | `99bdc4c` | GT PASS `assembleDebug`+**184** JVM（0/0/0）；lint 0 错误/20 警告；签名校验通过；工具回归；**GD `--mode final` 66 文件 0 违规** | 两项 instrumentation + 应用冒烟；依赖未变核查 | `T22-device` | passed |
| T23 | `d85ea9b` | GT PASS `assembleDebug`+**184** JVM（0/0/0）；lint 0 错误/20 警告；签名校验通过；工具回归 **Python 23 + Node 17**；测试 APK 编译通过；GD `--mode final` 66 文件 0 违规 | Q0/Q1/Q2/Q8 已跑；Q3/Q4/Q6/Q7 部分；Q5 未跑；覆盖安装兼容通过（见 T23 说明）。**部分 Q 用例未跑；T20 的真机陀螺仪另由用户实测确认（见 T20 行）** | `T23-20261006-020201-6526aa7c`、`T23-device` | passed |
| T23R | 未提交（HEAD `fe05461`） | PASS `assembleRelease`（**签名 RELEASE，`debuggable=false`**，`app/build.gradle` release buildType 增加 `signingConfig signingConfigs.development`）+ 门禁 `Invoke-Checks -Batch T23R -ToolTests -InstrumentationBuild` **184 JVM**（0 失败/0 错误/0 跳过）/ lint **0 错误/20 警告** / 签名校验通过 / 工具回归 **Python 23 + Node 17** / 测试 APK 编译通过；`--mode final` **66 文件 0 违规** | debug→release 同键覆盖安装 **Success**（未卸载、未 clear data），`local.xml` 升级前后字节相同；启动 22 / Favorites 1 / Continue watching 8；打开 Pattern 1 播放器；陀螺仪开关 off→on→off；返回媒体库；进程存活、crash 缓冲为空 | `T23R-20261006-092020-2fc41b91`、`T23R-release`、`T23R-device` | passed |
| T23R2 | 未提交（HEAD `fe05461`） | PASS `assembleRelease`（**签名 RELEASE，`debuggable=false`**，证书同历史 0.2.7）+ 版本常量按用户明确指示升为 `versionName 2.7.1` / `versionCode 10`；门禁 `Invoke-Checks -Batch T23R2 -ToolTests -InstrumentationBuild` **184 JVM**（0 失败/0 错误/0 跳过）/ lint **0 错误/20 警告** / 签名校验通过 / 工具回归 **Python 23 + Node 17** / 测试 APK 编译通过；`--mode final` **66 文件 0 违规** | 0.2.7/9（release）→ 2.7.1/10（release）同键覆盖安装 **Success**（未卸载、未 clear data），`local.xml` 升级前后字节相同（`adb root` 读取，因非 debuggable `run-as` 失败）；启动 22 / Favorites 1 / Continue watching 8；打开 Pattern 1 播放器；陀螺仪开关 off→on→off；返回媒体库；进程存活、crash 缓冲为空 | `T23R2-20261006-093043-d2ee87d2`、`T23R2-release`、`T23R2-device` | passed |

| REL028 | 未提交（HEAD `24bbd49`） | PASS `assembleRelease`（**签名 RELEASE，`debuggable=false`**，证书同历史 0.2.7）+ 版本常量升为 `versionName 0.2.8` / `versionCode 11`（**纠正误发的 2.7.1/10**，HEAD `24bbd49` 的继续观看按最近播放排序修复已含在内）；门禁 `Invoke-Checks -Batch REL028 -ToolTests -InstrumentationBuild` **188 JVM**（0 失败/0 错误/0 跳过）/ lint **0 错误/20 警告** / 签名校验通过 / 工具回归 **Python 23 + Node 17** / 测试 APK 编译通过；`--mode final` **66 文件 0 违规** | 2.7.1/10（debug）→ 0.2.8/11（release）同键覆盖安装 **Success**（未卸载、未 clear data），`local.xml` 前后字节相同（`adb root` 读取，因非 debuggable `run-as` 失败）；启动 22 / Favorites 1 / Continue watching 8；**Continue watching 顺序 16 > 5 > 3 > 7 > 1 > 8 > 9 > 14**；打开 Pattern 1 播放器；陀螺仪开关 off→on→off；返回媒体库；进程存活、crash 缓冲为空 | `REL028-20261006-102107-38da5404`、`REL028-release`、`REL028-device` | passed |

> T13–T22 行的 Commit 取自 `git log` 的工作提交，Build/JVM/lint/GD 与设备列取自各任务「执行说明」。T18/T19 设备矩阵为**部分覆盖**；T20 的真机陀螺仪由**用户于 2026-10-06 实测确认无问题**（**用户自述，非本 agent 执行，本 agent 无法独立复核**）；T23 其余部分 Q 用例仍未跑，**T23R2 只改版本常量与分发目录、未新增设备用例**，因此不得读作 Q0–Q8 全矩阵通过。**REL028 纠正了 T23R2 误发的 2.7.1/10 编号（改为 0.2.8/11），并包含 HEAD `24bbd49` 的继续观看按最近播放排序修复；同样未新增设备用例，Q0–Q8 仍为部分覆盖。**

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

## T20 执行说明

- T20 由**单个顺序 subagent** 实现，Lead 独立复跑 G（PASS 178 JVM / lint 0-20 / 签名）与 GD（67 文件 0 违规）后才提交。
- **状态已由 `device_blocked` 改为 `passed`**：本 agent 环境（`adb devices` 仅 emulator-5554，且模拟器提供的是虚拟 rotation-vector 传感器 type 15/20/11）**无法执行方案要求的真机验证**，因此当时如实记为 `device_blocked`。
- **2026-10-06 用户将候选 APK 安装到真机实测，报告陀螺仪无问题**，据此关闭该 `device_blocked`。
  - **证据来源：用户自述（本轮对话），非本 agent 执行、非本 agent 观测。** 工具输出只能证明本机未连接物理设备（仅 `emulator-5554`），无法独立复核该结果。
  - 未知细节（未在任何工具输出中出现，故不臆造）：真机型号与 Android 版本、所用 APK 的确切文件、以及「横竖旋转 / 后台返回 / 关闭后不漂移」三项是否逐一覆盖。
- 保留语义：`active` 门控、`SENSOR_DELAY_GAME`、基准重置、两个不同的 toast 字符串 id、`wrappedDelta` 的 ±180 环绕、pitch 边界与四种 rotation 映射；传感器在 `onStop` 注销而非等 `onDestroy`。
- 偏差：纯映射/状态机放在生产文件内的 `GyroController.Mapping`（公开嵌套类），以便 JVM 测试无需 Robolectric；`VrView` 无需改动。

## T21 执行说明

- T21 由**单个顺序 subagent** 实现，Lead 独立复跑 GT（PASS 184 JVM / lint 0-20 / 签名 / 工具回归 / 测试 APK）与 GD（69 文件 0 违规），并亲自重跑 instrumentation 与「许可界面 + 诊断对话框」设备检查后才提交。
- 语义保持：`DiagnosticsSink` 未改、仍是唯一 Domain 端口（未新建第二个同名接口）；单一 uncaught handler（有 `installed` 守卫，存在旧 handler 时委托而非吞掉）；脱敏正则、30KB 上限与 UTF-8 边界、8 cause/64 frame 限制、GPU 串格式均保持。
- 主动报告偏差：①根 `PlaybackDiagnostics` 直接删除而非留空壳（它引用已迁移的 Ui，无法编译），因此 **T22 的"删除 PlaybackDiagnostics"项已提前满足**；②`DiagnosticsDialog`（ui）import `data.DiagnosticsStore`——这是方案指定放置的必然结果，`--mode domain` 通过，但在 T22 的 `--mode final` 下会表现为 ui→data 边，需在 T22 重新接线。

## T22 执行说明

- T22 由**单个顺序 subagent** 实现，Lead 独立复跑 GT、**`--mode final`（66 文件 0 违规）**、依赖未变核查与两项 instrumentation + 应用冒烟后才提交。
- 根包现已只剩 `AppServices`、三个 Activity 与 `XbvrApplication`；root `Api/Protocol/Store` 已删除。`app/build.gradle` 无改动（无新依赖/模块/框架）。
- 主动报告偏差：①`PlayerActivity.tracks()` 仍读 Media3 `Tracks.Group/Format` 以生成菜单**标签**（override 只在 session，T18 已记录的取舍）；②`projectionFormats()/viewingEye()` 仍作为手动选择器的 UI 状态编辑 Projection 字段（渲染数学自 T19 已在 media）；③Domain `CoverRepository` 增加 `retryAll()`、`DiagnosticsDialog.show` 改签名、`MainView.Actions` 增 `normalizeBase`；④两个 instrumentation 按新类型重写、断言未减（存储仍 11 项）。
- 观察（非回归）：继续观看由 5 增到 7，是本轮及此前设备测试播放新场景累计的续播记录。

## T23 执行说明（最终设备回归与本地候选）

- **未提交**：T23 只改文档与打包工具，工作区留给 Lead 审查；HEAD 仍为 `36d0a9e`。
- 门禁（实测）：`Invoke-Checks.ps1 -Batch T23 -ToolTests -InstrumentationBuild` → PASS **184 JVM**（0 失败/0 错误/0 跳过，与 T21/T22 相同，计数未下降）/ lint **0 错误 20 警告** / APK 签名校验通过（证书 `20c3404b…`）/ 工具回归 **Python 23 + Node 17** / 测试 APK 编译通过；`verify_architecture.py --mode final` → **66 文件 0 违规**。证据 `T23-20261006-020201-6526aa7c`。
- 签名证书 SHA256：`20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`；候选 APK SHA256：`3d8edf026511dca2afa963f208412f799fbd7ba39fd01717578e3a59c35419c0`（与门禁构建的 `app-debug.apk` 同字节）。
- 打包工具：`tools/compliance-package.py` 改为**必须显式传 `--output-dir` 与 `--name-prefix`**（另加 `--apk`/`--desugar-source`），并拒绝旧前缀 `XBVR-Pocket-0.2.5` 与旧目录名 `xbvr-android-0.2.5`；`tools/test_compliance_package.py` 增加 3 项测试，验证显式输出只写目标目录、历史目录仅保留自身 marker、缺参数即失败。**未用旧 0.2.5 硬编码路径运行过打包器**。
- 源码包白名单新增 `docs/refactor` 与 `docs/superpowers`（已审查的计划/架构文档），仍排除工具链、私钥、`local.properties`、本地证据与 fixture 媒体。
- 覆盖安装（Q8）：对 `emulator-5554` 上已有合成安装执行 `adb install -r`（**未卸载、未 clear data**），`shared_prefs/local.xml` 覆盖前后**字节相同**（SHA256 `32f7036e…`）：`profiles`/`active`、1 个 `fav:`、16 个 `pos:`、8 个 `source:`、8 个 `view:`、`coverMode`/`coverAuto` 全部保留；`files/library-<profile>.json` 仍在。
- 设备范围：`emulator-5554`（API 36，x86_64）；另启动 `emulator-5580`（AVD `XbvrQa`，**API 29**）做安装+启动+服务器菜单+离线许可冒烟，**用后已 `emu kill` 关闭**。**无物理真机**。
- **未跑**：Q5 全项；Q3 的章节/速度/轨道/字幕/多文件切源；Q4 的目录请求中切服务器/连接草稿重开；Q6 的 GL mediump 编译与故障注入；Q7 的完整 21 组合与触摸/缩放；8K/HDR/长时间音画同步。Q1 的多标签 OR 因对话框只保留首个标签**未获结论**，未写成通过。
- 观察：离开播放器时 `DiagnosticsStore` 记录到一条 Media3 `ExoTimeoutException`（`PlayerActivity.onStop`→`PlaybackController.stop`→release），Activity 存活并正常返回媒体库，**非崩溃**；报告已在诊断对话框中正确脱敏显示。

## T23R 执行说明（签名 RELEASE 候选 + release 设备回归）

- **未提交**：T23R 只改 `app/build.gradle`（release 签名）与三份文档，工作区留给 Lead 审查；HEAD 仍为 `fe05461`。
- **构建文件改动（唯一）**：`buildTypes { debug { signingConfig signingConfigs.development }; release { minifyEnabled false; signingConfig signingConfigs.development } }` —— 只给 `release` 增加 `signingConfig signingConfigs.development`；`minifyEnabled false` 保留，未加依赖、未启用收缩、`versionName 0.2.7` / `versionCode 9` / applicationId / Activity 名未动。
- **门禁（实测）**：`Invoke-Checks.ps1 -Batch T23R -ToolTests -InstrumentationBuild` → PASS **184 JVM**（0 失败/0 错误/0 跳过，与 T21/T22/T23 相同，计数未下降）/ lint **0 错误 20 警告** / APK 签名校验通过（证书 `20c3404b…`）/ 工具回归 **Python 23 OK + Node 17 pass 0 fail** / `assembleDebugAndroidTest` 通过；`verify_architecture.py --mode final` → **66 文件 0 违规**。证据 `T23R-20261006-092020-2fc41b91`。
- **RELEASE APK 事实（原始证据）**：`app-release.apk` 大小 **10,316,271** 字节，SHA-256 `0a7bcd18a515243dedf367aae57ef4bc67cadb6afead8e37488768dd27387eda`；`aapt2 dump xmltree` 中 **`android:debuggable` 不存在**（即 `debuggable=false`）；`aapt2 dump badging` 报 versionCode 9 / versionName 0.2.7；`apksigner verify --print-certs` 报 signer #1 证书 SHA-256 `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`（v2 scheme，1 signer）；`assets/licenses` **56/56** 与 `licenses/` 源逐字节一致。
- **debug→release 同键升级**：设备上原为 debug 构建（`flags=[ DEBUGGABLE …]`，base.apk SHA-256 `3d8edf02…`），直接 `adb install -r app-release.apk`（未卸载、未 clear data）返回 **Success**；安装后 `flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`（`DEBUGGABLE` 消失），base.apk SHA-256 变为 `0a7bcd18…`。`shared_prefs/local.xml` 升级前后 SHA-256 完全相同（`a986b418a6703ecf34931318ee13e28ecaa27edd73729b9f9ddd99f4aba9516b`，6718 字节）：`profiles`/`active`、`fav:` 1、`pos:` 16、`source:` 8、`view:` 8、`coverMode`/`coverAuto` 全部保留；`files/library-643ece43-….json` 仍在。
- **读取方式说明**：升级前用 `run-as` 与 `adb root cat` 两种方式取到同一字节流；升级后应用不再 debuggable，`run-as` 按预期报 `package not debuggable`，改用 `adb root` 读取同一文件做比对（`adb root` 在该模拟器可用）。
- **冒烟（release 构建）**：启动 → 媒体库 **22** → Favorites **1** → Continue watching **8** → `Pattern 1` 进入播放器 → 陀螺仪开关 `off→on→off` → 返回媒体库（22）；进程存活，`logcat -b crash` 为空，无 `FATAL EXCEPTION`。证据 `T23R-device/`（含 `INDEX.md`）。
- **打包**：`tools/compliance-package.py --output-dir D:/codex-work/output/xbvr-android-0.2.7-refactor-candidate-release --name-prefix XBVR-Pocket-0.2.7-refactor-candidate --apk app/build/outputs/apk/release/app-release.apk`；写入**新目录**，旧的 debug 候选目录与 `output/xbvr-android-0.2.5*` 未被触碰。
- **诚实边界（未变）**：T20 真机陀螺仪结论仍是**用户自述**（2026-10-06），非本 agent 执行或观测；本 agent 无物理真机。T23R 的 release 设备验证全部在 `emulator-5554`（API 36）上、只用合成 fixture `127.0.0.1:18766` 完成；Q3/Q4/Q6/Q7 的未跑子项与 Q5 全项仍未跑，不得读作 Q0–Q8 全矩阵通过。

## T23R2 执行说明（版本升为 2.7.1/10 + 重新打包与覆盖升级回归）

- **未提交**：T23R2 只改 `app/build.gradle` 的版本常量与三份文档，工作区留给 Lead 审查；HEAD 仍为 `fe05461`。
- **构建文件改动（本轮全部）**：①T23R 给 `release` buildType 增加 `signingConfig signingConfigs.development`；②T23R2 把 `defaultConfig` 的 `versionCode 9` / `versionName '0.2.7'` 改为 `versionCode 10` / `versionName '2.7.1'`。`minifyEnabled false`、依赖清单、applicationId、Activity 名、签名配置均未动。
- **版本说明**：重构的保持性约束要求版本常量固定为 `0.2.7` / `9`；**升为 `2.7.1` / `10` 是本轮用户的明确新指示**，使 Android 视为对既有 9 的正规升级。与重构内容无关，也不代表新增功能或数据格式变化。
- **门禁（实测）**：`Invoke-Checks.ps1 -Batch T23R2 -ToolTests -InstrumentationBuild` → PASS **184 JVM**（0 失败/0 错误/0 跳过，与 T21/T22/T23/T23R 相同，计数未下降）/ lint **0 错误 20 警告** / APK 签名校验通过（证书 `20c3404b…`）/ 工具回归 **Python 23 OK + Node 17 pass 0 fail** / `assembleDebugAndroidTest` 通过；`verify_architecture.py --mode final` → **66 文件 0 违规**。证据 `T23R2-20261006-093043-d2ee87d2`。
- **RELEASE APK 事实（原始证据）**：`app-release.apk` 大小 **10,316,271** 字节，SHA-256 `1ff60954bb8d80261af9cd7dce4276a95ab8c6dfa935af8f0078ee39557f4517`；`aapt2 dump xmltree` 中 **`android:debuggable` 不存在**（即 `debuggable=false`）；`aapt2 dump badging` 报 `package name='top.liuwei.xbvr' versionCode='10' versionName='2.7.1'`；`apksigner verify --print-certs` 报 signer #1 证书 SHA-256 `20c3404b…`（v2 scheme，1 signer，与历史 0.2.7 发行证书相同）；`assets/licenses` **56/56** 与 `licenses/` 源逐字节一致；证据 `T23R2-release/`。
- **0.2.7/9 → 2.7.1/10 同键覆盖升级**：设备上原为 T23R 的签名 release `0.2.7`（`versionCode 9`，base.apk SHA-256 `0a7bcd18…`，`flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`），直接 `adb install -r XBVR-Pocket-2.7.1.apk`（未卸载、未 clear data）返回 **Success**；安装后 `versionCode=10` / `versionName=2.7.1`，base.apk SHA-256 变为 `1ff60954…`。`shared_prefs/local.xml` 升级前后 SHA-256 完全相同（`04160ab69506364f067654a316c3879a2c2a2b0c3f5c46f4408e8d397fb579c0`，6678 字节），`files/` 目录列表前后一致。
- **读取方式说明**：候选为非 debuggable，`run-as` 按预期报 `run-as: package not debuggable: top.liuwei.xbvr`，因此**显式用 `adb root`**（该模拟器允许）读取同一文件做比对；升级前读取亦走 `adb root`。
- **冒烟（2.7.1 release 构建）**：冷启动 → 媒体库 **22** → Favorites **1** → Continue watching **8** → `Pattern 1` 进入播放器 → 陀螺仪开关 `off→on→off` → 返回媒体库（22）；进程存活（pid 13545），`logcat -b crash` 为空，无 `FATAL EXCEPTION`。证据 `T23R2-device/`（含 `INDEX.md`）。
- **打包**：`tools/compliance-package.py --output-dir D:/codex-work/output/xbvr-android-2.7.1 --name-prefix XBVR-Pocket-2.7.1 --apk app/build/outputs/apk/release/app-release.apk --desugar-source D:/codex-work/output/xbvr-android-0.2.7/desugar_jdk_libs-2.1.5-source.zip`；产出 `XBVR-Pocket-2.7.1.apk`（SHA-256 `1ff60954…`）、源码包、第三方材料包、`SHA256SUMS.txt`、`public-files.json`。写入**新目录**；`output/xbvr-android-0.2.7-refactor-candidate*` 与 `output/xbvr-android-0.2.5*` 均未被覆盖或删除。
- **诚实边界（未变）**：T20 真机陀螺仪结论仍是**用户自述**（2026-10-06），非本 agent 执行或观测；本 agent 无物理真机。T23R2 的设备验证全部在 `emulator-5554`（API 36）上、只用合成 fixture `127.0.0.1:18766` 完成；升版只改版本常量与分发目录，**未新增设备用例**，Q3/Q4/Q6/Q7 的未跑子项与 Q5 全项仍未跑，Q0–Q8 仍为部分覆盖，不得读作全矩阵通过。

## REL028 执行说明（纠正为 0.2.8/11 + 继续观看排序修复 + 重新打包与覆盖升级回归）

- **未提交**：REL028 只改 `app/build.gradle` 的版本常量与三份文档，工作区留给 Lead 审查；HEAD 为 `24bbd49`（本任务开始前的已提交点）。
- **版本更正**：此前误发的 **2.7.1 / 10** 是错误版本号，已纠正为 **0.2.8 / 11**；`versionCode` 由 10 升为 **11**，Android 视为对已发布 10 的正规升级。已发布的 Release notes 内容保留（GitHub 侧的删除与重发由 Lead 处理）。**本 agent 未 push、未创建/删除任何 GitHub Release 或 tag、未提交。**
- **构建文件改动（本轮全部）**：`app/build.gradle` `defaultConfig` 的 `versionCode 10` / `versionName '2.7.1'` → `versionCode 11` / `versionName '0.2.8'`。T23R 加入的 `release { minifyEnabled false; signingConfig signingConfigs.development }` 与依赖清单、`applicationId`、Activity 名均未动。
- **功能修复（HEAD `24bbd49`，已提交，包含在本包内）**：新增**附加**偏好键 `seen:<key>`（epoch 毫秒），与 `pos:`/`view:` 同事务写入（`save(...)` 与 `entryPosition(...)`）；`PlaybackRepository.lastWatched(String)` 由 `LocalSettings` 实现、`PlaybackStore` 转发；`LibraryController.recompute()` **仅在该 tab** 按 `lastWatched` 降序做**稳定排序**，成员判定不变（`position > 0`）。legacy 记录（无该键，读回 0）排在所有有时间戳条目之后；其他 tab 不受影响。新增 4 项 controller 测试（JVM 由 184 升到 188）。
- **门禁（实测）**：`Invoke-Checks.ps1 -Batch REL028 -ToolTests -InstrumentationBuild` → PASS **188 JVM**（0 失败/0 错误/0 跳过，较 T23R2 的 184 上升，计数未下降）/ lint **0 错误 20 警告** / APK 签名校验通过（证书 `20c3404b…`）/ 工具回归 **Python 23 OK + Node 17 pass 0 fail** / `assembleDebugAndroidTest` 通过；`verify_architecture.py --mode final` → **66 文件 0 违规**。证据 `REL028-20261006-102107-38da5404`。
- **RELEASE APK 事实（原始证据）**：`app-release.apk` 大小 **10,317,215** 字节，SHA-256 `91dda9fd6ce0023f65cd7171e4810b94eed31cc20df6c325a493fcfeaf2b7f01`；`aapt2 dump xmltree` 中 **`android:debuggable` 不存在**（即 `debuggable=false`）；`aapt2 dump badging` 报 `package name='top.liuwei.xbvr' versionCode='11' versionName='0.2.8'`；`apksigner verify --print-certs` 报 signer #1 证书 SHA-256 `20c3404b…`（v2 scheme，1 signer，与历史 0.2.7 发行证书相同）；`assets/licenses` **56/56** 与 `licenses/` 源逐字节一致；证据 `REL028-release/`。
- **2.7.1/10（debug）→ 0.2.8/11（release）同键覆盖升级**：设备上原为 debug 构建（`versionCode=10` / `versionName=2.7.1`，`flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]`，base.apk SHA-256 `775e82b6…`），直接 `adb install -r XBVR-Pocket-0.2.8.apk`（未卸载、未 clear data）返回 **Success**；安装后 `versionCode=11` / `versionName=0.2.8`，`flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`（`DEBUGGABLE` 消失），base.apk SHA-256 变为 `91dda9fd…`。`shared_prefs/local.xml` 升级前后 SHA-256 完全相同（`996e2e65…`，7628 字节，字节比对一致）；`files/` 中 `library-643ece43-….json`（12263 B）与 `playback-diagnostics.txt`（5255 B）仍在。
- **读取方式说明**：候选为非 debuggable，`run-as` 按预期报 `run-as: package not debuggable: top.liuwei.xbvr`，因此**显式用 `adb root`**（该模拟器允许）读取同一文件做比对；升级前读取亦走 `adb root`。
- **冒烟（0.2.8 release 构建）**：冷启动 → 媒体库 **22 videos** → Favorites **1 videos**（Pattern 2）→ Continue watching **8 videos** → `Pattern 1` 进入播放器 → 陀螺仪开关 `off→on→off` → 返回媒体库（22）；进程存活（pid 15308），`logcat -b crash` 为空，无 `FATAL EXCEPTION`。
- **继续观看排序（本次修复的设备证据）**：可见顺序 **Pattern 16 > Pattern 5 > Pattern 3 > Pattern 7 > Pattern 1 > Pattern 8 > Pattern 9 > Pattern 14**；偏好键 `pos:` 16 / `seen:` 8 / `view:` 8 / `fav:` 1。证据 `REL028-device/`（含 `INDEX.md`）。
- **打包**：`tools/compliance-package.py --output-dir D:/codex-work/output/xbvr-android-0.2.8 --name-prefix XBVR-Pocket-0.2.8 --apk app/build/outputs/apk/release/app-release.apk --desugar-source D:/codex-work/output/xbvr-android-0.2.7/desugar_jdk_libs-2.1.5-source.zip`（desugar 源 SHA-256 `cf0b48cb…` 校验一致）；产出 `XBVR-Pocket-0.2.8.apk`（SHA-256 `91dda9fd…`）、源码包、第三方材料包、`SHA256SUMS.txt`、`public-files.json`，共 206 个白名单文件且全部许可资产匹配。写入**新目录**；`output/xbvr-android-2.7.1`、`output/xbvr-android-0.2.7-refactor-candidate*` 与 `output/xbvr-android-0.2.5*` 均未被覆盖或删除。
- **诚实边界（未变）**：T20 真机陀螺仪结论仍是**用户自述**（2026-10-06），非本 agent 执行或观测；本 agent 无物理真机。REL028 的设备验证全部在 `emulator-5554`（API 36）上、只用合成 fixture `127.0.0.1:18766` 完成；除已提交的排序修复外**未新增设备用例**，Q3/Q4/Q6/Q7 的未跑子项与 Q5 全项仍未跑，Q0–Q8 仍为部分覆盖，不得读作全矩阵通过。
