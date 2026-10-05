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
| T11 | | | | | not_started |
| T12 | | | | | not_started |
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
