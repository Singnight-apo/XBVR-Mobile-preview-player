# XBVR Android Java 四层重构执行方案 / Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. 如执行环境没有该技能，按本文的任务、验收门槛和交接规则顺序执行。只有用户明确选择并行 agent 工作时才使用 `superpowers:subagent-driven-development`，禁止多个 agent 同时修改同一工作目录。

**Goal:** 保持 Java、现有用户功能、签名和数据兼容，把 Android app 逐步整理为 UI / Domain / Data / Media，令 MainActivity 与 PlayerActivity 主要承担 Android 生命周期、界面连接和事件转发。

**Architecture:** 保留单一 app Gradle 模块，先建立子包和职责边界，再逐个切换生产调用。Domain 是纯 Java 模型和规则；Data 负责协议、认证和持久化；Media 负责 Media3、Surface、GL 与传感器；UI 负责页面和页面协调。根包仅保留三个 Activity、Application 和手动组装入口 AppServices，不增加架构框架。

**Tech Stack:** Java 17、AGP 8.13.2、Gradle 8.13、Android API 29–36、Media3 1.8.0、OkHttp 4.12.0、desugar_jdk_libs 2.1.5、JUnit 4、MockWebServer、现有自定义 Android instrumentation。

---

## 1. 执行前提与授权边界

基准：`D:/codex-work/github/XBVR-Mobile-preview-player`，2026-10-05 读取的 HEAD 为 `dd5e888ebbad55ccddb3fdab2a3c285082ff45ce`，0.2.7 / versionCode 9。

这是交给其他 agent 的完整实施方案。本次编写方案没有执行应用重构或应用构建。仓库内新增本文；方案包中的辅助脚本测试另有记录，不可冒充应用测试。

- 保持 applicationId `top.liuwei.xbvr`、minSdk 29、targetSdk 36 和原签名。内部重构阶段不改版本号；正式发布版本由用户决定。
- 禁止迁移到 Kotlin/Compose，禁止引入 Hilt、Room、Retrofit、RxJava 等框架，禁止为拆包而拆 Gradle 模块。
- 不更改 UI 布局、文案、排序、筛选语义、格式识别、鱼眼模型、线程数量、超时、缓存限额或收藏权限规则。
- 不顺便修复重构之外的新问题。发现影响迁移的缺陷时，记录复现和证据，将修复与机械迁移分开；新的业务规则必须请用户决定。
- 不替换/上传签名私钥，不输出真实账号密码，不把真实私服资料放入测试、提交或交接包。
- 使用独立本地分支，逐任务提交。已有脏工作区先识别归属；不得 reset、stash、清理或覆盖他人的改动。计划文档可以单独提交。
- 本任务授权本地实现和验证，不默认授权 push、创建 Release、替换公开 APK 或上传测试记录。最终先交付可审查的本地候选结果。
- Activity 保持原全限定名；AndroidManifest 的入口、Intent extras `profile`/`url`/`title`、saved-state 键都保持。
- 按 T00→T23 执行。大任务内部可拆成多个小提交，但每次生产调用切换都要验证。不要预先一次创建全部目标类。

## 2. 方案包及操作约定

交接包默认在 `D:/codex-work/output/XBVR-four-layer-refactor-plan`，包含本文副本、任务说明、统一检查脚本、AST 排版校验器、架构源代码检查器及合成 fixture。

本文的 `J` 表示 `app/src/main/java/top/liuwei/xbvr`，`T` 表示 `app/src/test/java/top/liuwei/xbvr`，`A` 表示 `app/src/androidTest/java/top/liuwei/xbvr`，均相对当前项目根目录。按类/方法名定位，不依赖格式化后失效的行号。

每个任务都有固定的“写保护现行为的测试→看到旧实现通过→新增边界/实现→切生产调用→完整检查→设备回归→审查和独立提交”顺序。机械移动不要求人为制造失败；新增规则/接口的测试应先体现缺失实现或真实回归，禁止写恒真断言。

PowerShell 会话初始化（换电脑只改本地路径，不改仓库行为）：

```powershell
$projectDirectory = 'D:/codex-work/github/XBVR-Mobile-preview-player'
$planDirectory = 'D:/codex-work/output/XBVR-four-layer-refactor-plan'
$jdkDirectory = 'D:/codex-work/xbvr-android/toolchain/jdk/jdk-17.0.20.1+1'
$javaExecutable = "$jdkDirectory/bin/java.exe"
$pythonExecutable = 'C:/Users/TP16 of LW/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$nodeExecutable = 'C:/Users/TP16 of LW/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
$adbExecutable = 'D:/codex-work/xbvr-android/toolchain/sdk/platform-tools/adb.exe'
Set-Location -LiteralPath $projectDirectory
```

**G：每个源码步骤必跑的应用门槛：**

```powershell
& "$planDirectory/Invoke-Checks.ps1" -Project $projectDirectory -Batch T02
```

将 `T02` 换成当前任务编号；每次产生独立证据目录。脚本要求原签名已存在，不会生成新密钥；使用 `--rerun-tasks` 获取新报告，运行 assembleDebug、全部 JVM 和 lint，再检查实际 APK 签名。预期 `PASS`、至少 47 项 JVM、0 failures/errors/skipped、lint 0 errors。测试增加后数量应增加，不得删测试压回 47。逐项审查新增 lint 警告，不用全局 suppression 消音。

**GT：涉及工具、CI、源码包装或测试入口时：**

```powershell
& "$planDirectory/Invoke-Checks.ps1" -Project $projectDirectory -Batch T10 -ToolTests -InstrumentationBuild
```

额外跑 Python/Node 工具回归并编译测试 APK。基准工具测试为 20 Python、17 Node；增加测试不能减少原覆盖。

**GD：阶段 1 后的 Domain 依赖检查：**

```powershell
& $pythonExecutable "$planDirectory/verify_architecture.py" $projectDirectory --mode domain
if ($LASTEXITCODE -ne 0) { throw 'Domain dependency guard failed' }
```

最终改成 `--mode final`。这是源代码静态护栏，不是编译器或运行验证的替代品；人工还要检查 wildcard imports、跨包全限定名及构造时实际依赖。

每次提交前：`git diff --check`、`git diff --stat`、`git diff --find-renames`，人工核对只包含本任务及对应测试/验收记录。显式列文件 `git add`，不要 `git add .`。提交信息采用各任务给出的职责标题。

## 3. 目标结构与依赖验收

```text
top.liuwei.xbvr/
  MainActivity, PlayerActivity, LicensesActivity, XbvrApplication, AppServices
  domain/
    Models, LibraryQuery, ServerProfile, LibraryFilterState, CoverRatioPolicy
    Projection, FormatInference, SelectedFormatPolicy, ResourceIdentity
    LibraryEvent, EntryMetadata, LibraryRepository, CoverRepository<I>
    ProfileRepository, PlaybackRepository, FavoriteRepository, CoverSettings
    PlayerPort, TrackOption, MediaDetailsRepository, DiagnosticsSink
  data/
    XbvrApi, XbvrProtocol, HttpTransport
    ProfileJsonMapper, ProfileStore, PlaybackStore, FavoriteStore, LocalSettings
    PlaybackJsonCodec, LibraryCache, DefaultLibraryRepository, DefaultMediaDetailsRepository, BitmapCoverRepository
    DiagnosticsStore
  media/
    Media3PlaybackSession, PlaybackLifecycle, VrView, VrRenderer, RendererShader
    RenderMath, ProjectionMath, GyroController
  ui/common/
    Ui, IconDrawable
  ui/library/
    LibraryController, LibraryUiState, MainView, PosterAdapter
    GridScrollRestorer, ServerDialogs, FacetDialogs
  ui/player/
    PlaybackController, PlaybackUiState, PlayerView, PlayerDialogs, ControlsVisibility
  ui/licenses/
    LicenseView
  ui/diagnostics/
    DiagnosticsDialog
```

类清单是职责地图，不要求为每个按钮或 getter 增加类。T00–T23 指定每个类何时出现；没有被实际调用的空壳不算完成。

- Domain 只依赖 JDK 和 Domain 自身；不能引用 Android、JSON、OkHttp、Media3、R 或具体实现。
- Data 和 Media 依赖 Domain，不互相 import，不操作 Activity/对话框。VrView 作为 Android 渲染/输入适配可以依赖 GLSurfaceView 和触摸 API。
- UI 依赖 Domain、Media 的显示/播放适配及 UI 自身。具体 Data 的构造在 AppServices；避免 Activity 调用 raw prefs 或 raw JSON。
- AppServices 是小的手动组装入口：把已有 HTTP 客户端转成媒体数据源工厂、把应用 Context 给存储实现、把执行器/主线程调度器传给对象。不放业务流程，不全局保存 Activity/View/Surface。
- 同层内部可有 `package-private` 类。R 在 UI 子包需要显式 `import top.liuwei.xbvr.R`。
- 暂存 root `Store`/`Api`/`Protocol` 兼容门面可以用于过渡，但最终 T22 删除，不允许两个实现同时生效。
- 采用普通 Java 类、明确字段/方法和少量接口。不要在重构中同时把所有可变模型改成不可变、重新命名数据字段或建立通用事件总线。

## 4. 必须保留的行为与数据

| 项目 | 兼容基准 |
|---|---|
| 签名证书 SHA256 | `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`（本次用 keytool 重新读取确认） |
| SharedPreferences / Keystore | 偏好文件 `local`；alias `xbvr-profiles`；AES/GCM/NoPadding；Base64(IV) + `:` + Base64(ciphertext) |
| 配置字段 | id、base、user、password、basicUser、basicPassword；active 的回退选第一项逻辑 |
| 收藏/观看 | `fav:`、`pos:`、`view:`、`source:`；场景和文件的 key 均要保留 |
| 封面设置 | `coverMode:`、`coverAuto:` 按服务器分开；自动首次有效比例；固定比例裁切 |
| 视角 JSON | kind/layout/capture/eye/yaw/pitch/fov/cx/cy/radius/rotation/mirror/half/override；默认值和 restore 的局部更新顺序 |
| 目录文件 | `library-<id>.json` 和 `.tmp`；保留 `_metadata`、`_retryAfter` 和元数据优先级 |
| 标识 | scene/file 区分、代理前缀、协议/主机/默认端口，以及已识别 XBVR URL 忽略临时 query 的逻辑 |
| 网络 | 玩家认证 JSON 与代理 Basic 分开；跨源不转发；重定向上限/超时/回退/元数据并发限额原样 |
| 图片 | 16 MiB 解码图 LruCache；12 MiB 响应上限；640×960 采样门槛；代理/原图顺序；失败重试与 pending 隔离 |
| 生命周期 | Main 的 generation、滚动恢复、旋转草稿；Player 的暂停意图、保存进度、Surface 解绑、停止和传感器注册顺序 |

旧代码不抛错的 fallback 不能擅自改成抛错；旧代码拒绝的认证/格式不能为了测试通过而放宽。真实业务差异用旧/新实现对照说明。

## 5. T00：检查基准、环境与回退点

**Files:** 读取 README、VALIDATION、build.ps1、app/build.gradle、local.properties、toolchain/debug.keystore、tools 和全部 16 个主 Java。创建 `docs/refactor/PROGRESS.md`；证据只在本地 output。

- [ ] 记录 `git status --short --branch`、`git rev-parse HEAD` 和当前版本。若 HEAD 已推进，对比 `dd5e888..HEAD`，把新功能也列入保护清单，不覆盖它。
- [ ] 识别本文造成的文档改动并单独提交；确认没有他人未提交源码后建立本地 `refactor/four-layer` 分支。若分支已存在，先核对其归属和进度，不重置。
- [ ] 核对 JDK 17、Gradle 8.13、SDK 36、Build Tools 36.0.0。当前 local.properties 指向旧目录 SDK；不复制整个 22 GB 工具链。新电脑自行提供同版本路径。
- [ ] 检查原签名和基准 APK 的证书。不能把“重新生成开发签名”当作迁移准备步骤。
- [ ] 跑 GT，保存当前真实结果；旧报告的 47 tests / 20 warnings 只用于预期，不充当本次成功证据。
- [ ] 安装基准 debug APK 到明确选定的测试设备，录制 Q0、Q1、Q2、Q3 的当前行为。设备不可用则记录 device_blocked，仍可推进已批准的纯逻辑步骤；涉及 Media 生命周期的步骤要等设备门槛补齐。
- [ ] 用合成 profile 建立配置、收藏、进度、手动格式和封面设置，再保存仅含合成数据的迁移前快照。不要读取/导出真实凭据。
- [ ] 提交 `docs(refactor): record baseline and compatibility gates`。

PROGRESS.md 固定格式：

```markdown
| Task | Commit | Build/JVM/lint | Device cases | Evidence | Status |
|---|---|---|---|---|---|
| T00 | 实际提交ID | 实际测试数/警告数 | Q0,Q1,Q2,Q3的实际结果 | 本地证据目录名 | passed或device_blocked |
```

逐任务填实际值，状态允许 `not_started`、`in_progress`、`failed`、`device_blocked`、`passed`。不把 `device_blocked` 记成全部完成。

## 6. T01：两个 Activity 仅排版

**Files:** 修改 J/MainActivity.java、J/PlayerActivity.java；后续可添加 `.git-blame-ignore-revs`。

- [ ] 复制两份源文件到独立 baseline 证据目录。
- [ ] 使用固定的 google-java-format 1.24.0，记录来源和实际 SHA256；不要加到 app 运行依赖中。
- [ ] 下列禁用行为的参数已按 [v1.24.0 官方参数解析源码](https://github.com/google/google-java-format/blob/v1.24.0/core/src/main/java/com/google/googlejavaformat/java/CommandLineOptionsParser.java) 核对；下载后仍要确认文件来源并执行 AST 比较。
- [ ] 仅执行排版，禁用 import 排序、未使用 import 删除、长字符串重排和 javadoc 改写：

```powershell
$baselineDirectory = Join-Path 'D:/codex-work/output/xbvr-refactor-evidence' ('T01-before-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $baselineDirectory | Out-Null
Copy-Item -LiteralPath "$projectDirectory/app/src/main/java/top/liuwei/xbvr/MainActivity.java" -Destination "$baselineDirectory/MainActivity.java"
Copy-Item -LiteralPath "$projectDirectory/app/src/main/java/top/liuwei/xbvr/PlayerActivity.java" -Destination "$baselineDirectory/PlayerActivity.java"
$formatterJar = "$planDirectory/tools/google-java-format-1.24.0-all-deps.jar"
New-Item -ItemType Directory -Path "$planDirectory/tools" -Force | Out-Null
if (-not (Test-Path -LiteralPath $formatterJar)) {
    Invoke-WebRequest -Uri 'https://github.com/google/google-java-format/releases/download/v1.24.0/google-java-format-1.24.0-all-deps.jar' -OutFile $formatterJar
}
Get-FileHash -LiteralPath $formatterJar -Algorithm SHA256 | Select-Object Hash | Out-File -LiteralPath "$baselineDirectory/formatter-sha256.txt"
& $javaExecutable -jar $formatterJar --aosp --skip-sorting-imports --skip-removing-unused-imports --skip-reflowing-long-strings --skip-javadoc-formatting --replace app/src/main/java/top/liuwei/xbvr/MainActivity.java app/src/main/java/top/liuwei/xbvr/PlayerActivity.java
if ($LASTEXITCODE -ne 0) { throw 'Formatter failed' }
& $javaExecutable "$planDirectory/CompareJavaAst.java" "$baselineDirectory/MainActivity.java" "$projectDirectory/app/src/main/java/top/liuwei/xbvr/MainActivity.java"
if ($LASTEXITCODE -ne 0) { throw 'MainActivity AST changed' }
& $javaExecutable "$planDirectory/CompareJavaAst.java" "$baselineDirectory/PlayerActivity.java" "$projectDirectory/app/src/main/java/top/liuwei/xbvr/PlayerActivity.java"
if ($LASTEXITCODE -ne 0) { throw 'PlayerActivity AST changed' }
```

`$baselineDirectory` 必须指向 T01 实际保存的两份原文件，不能指向格式化后的文件。下载地址：[固定发行 JAR](https://github.com/google/google-java-format/releases/download/v1.24.0/google-java-format-1.24.0-all-deps.jar)；选项依据[固定版本参数解析源码](https://github.com/google/google-java-format/blob/v1.24.0/core/src/main/java/com/google/googlejavaformat/java/CommandLineOptionsParser.java)。JAR 未随方案包分发。

- [ ] AST 相同后跑 G 和 Q0；原始 APK/字节码哈希不同不判为失败，不能因此忽略实际 AST 差异。
- [ ] 独立提交 `style: format activity sources without behavior changes`；将真实格式化提交 ID 写入 `.git-blame-ignore-revs`，不填虚构 ID。

## 7. T02：移动已有 Models 与 LibraryQuery

**Files:** 将 J/Models.java、J/LibraryQuery.java 移到 J/domain/；修改引用它们的 Api/Protocol/Activity 和 JVM 测试；测试 LibraryQuery 移到 T/domain/。

- [ ] 跑现有 LibraryQueryTest，保存分类、标签 OR、维度 AND、搜索和缺失元数据行为。
- [ ] 仅改 package/import，不改嵌套 Entry/Source/Detail/Tag/Subtitle 字段、默认值、Set/List 类型或排序算法：

```java
package top.liuwei.xbvr.domain;
// LibraryQuery 中使用：
import top.liuwei.xbvr.domain.Models.Entry;
// 其余调用者使用：
import static top.liuwei.xbvr.domain.Models.*;
```

- [ ] 查询整个 app/src 的旧引用，逐一更新；LibraryQueryTest 改同包或显式 import，不扩大类可见性来掩盖包错误。
- [ ] 跑 G、GD、Q1 的筛选/排序子集。
- [ ] 提交 `refactor(domain): move existing library models and selection`。

## 8. T03：分离 Projection 配置与格式识别

**Files:** 将 root Projection 移到 J/domain/Projection.java，创建 FormatInference.java；本步骤同步更新全部类型引用；测试 T/domain/FormatInferenceTest.java 和既有 CoreTest/RenderMathTest。

- [ ] 先补默认 FLAT/MONO、capture=180、FOV=75、未知 reason 的特征断言。
- [ ] Projection 的字段和 label 原样进入 Domain。把旧 infer 及 has/token/layout/EDGE/END 原样移到 FormatInference，入口为 `public static Projection infer(String metadata, String filename, String stereo, double fov)`。
- [ ] Projection 暂留同签名的 infer 方法，只执行 `return FormatInference.infer(metadata, filename, stereo, fov);`，逐调用替换后删除兼容入口。map 的数学暂留该纯 Java 类，T19 再移动；不得让 Domain import Media。
- [ ] 迁移映射必须保留这些测试：可靠所选文件优先、通用场景格式被精确镜头标签补充、RF52=190、SBS_MONO、FULL/half packing、unsupported Cube/EAC/双鱼眼、各 fov 边界。
- [ ] 更新 Store/Ui/VrView/PlayerActivity 及测试的 Projection 类型；更新自定义 instrumentation 中反射类定位（此时编译测试 APK）。
- [ ] 跑 GT、GD、Q3 的自动格式显示与手动恢复子集。
- [ ] 提交 `refactor(domain): isolate projection settings and format inference`。

## 9. T04：封面规则与筛选状态

**Files:** 创建 J/domain/CoverRatioPolicy.java、LibraryFilterState.java；修改 MainActivity；创建 T/domain/CoverRatioPolicyTest.java、LibraryFilterStateTest.java。

先以旧 private 方法的行为写特征测试，再接入以下完整纯规则：

```java
package top.liuwei.xbvr.domain;
public final class CoverRatioPolicy {
    public static final float DEFAULT = 16f / 9f;
    private CoverRatioPolicy() {}
    public static int mode(int value) { return value >= 0 && value <= 3 ? value : 0; }
    public static float fixed(int value) { return value == 1 ? 1f : value == 2 ? 3f / 2f : DEFAULT; }
    public static boolean valid(float value) { return Float.isFinite(value) && value > 0; }
    public static float resolve(int value, float cached) {
        int mode = mode(value);
        return mode == 0 && valid(cached) ? cached : fixed(mode);
    }
    public static boolean changed(float current, float next) {
        return valid(next) && !(Math.abs(next - current) < .0001f);
    }
    public static boolean crop(int value) { return value != 0; }
}
```

```java
@Test public void invalidCacheDoesNotChangeTheDefault() {
    assertEquals(16f / 9f, CoverRatioPolicy.resolve(0, Float.NaN), 0f);
    assertEquals(16f / 9f, CoverRatioPolicy.resolve(0, -1f), 0f);
    assertEquals(3f / 2f, CoverRatioPolicy.resolve(2, Float.POSITIVE_INFINITY), 0f);
    assertFalse(CoverRatioPolicy.changed(1f, 1f));
    assertTrue(CoverRatioPolicy.crop(1));
    assertFalse(CoverRatioPolicy.crop(0));
}
```

- [ ] LibraryFilterState 使用已有 LibraryQuery，不新增算法；对象定义为：

```java
package top.liuwei.xbvr.domain;
import java.util.LinkedHashSet;
public final class LibraryFilterState {
    public String category = "全部", query = "", studio = "", actor = "";
    public final LinkedHashSet<String> tags = new LinkedHashSet<>();
    public int tab;
    public void clearFacets() { studio = ""; actor = ""; tags.clear(); }
    public boolean hasFacets() { return !studio.isEmpty() || !actor.isEmpty() || !tags.isEmpty(); }
}
```
- [ ] 将 manualCoverRatio 和比例数值校验改为委托。`ImageView.ScaleType` 映射、grid.post、coverInferenceQueued、服务器身份检查、滚动锚点仍留 UI。
- [ ] 实测 `.0001f` 阈值边界时以旧表达式的浮点结果为准，不能把 epsilon 改成另一个近似值。
- [ ] 跑 G、GD、Q2 四种比例及复用切换。测试“自动→固定→自动”、返回自动重新推断、刷新和切服务器后的旧图片隔离。
- [ ] 提交 `refactor(domain): extract cover rules and library filter state`。

## 10. T05：所选文件格式规则

**Files:** 创建 J/domain/SelectedFormatPolicy.java；修改 PlayerActivity.inferSource；创建 T/domain/SelectedFormatPolicyTest.java。

```java
package top.liuwei.xbvr.domain;
import top.liuwei.xbvr.domain.Models.Detail;
import top.liuwei.xbvr.domain.Models.Source;
public final class SelectedFormatPolicy {
    private SelectedFormatPolicy() {}
    public static Projection infer(Detail detail, Source source) {
        boolean single = detail.sources.size() == 1;
        String metadata = source.projection.isBlank() ? (single ? detail.metadata : "") : source.projection;
        String stereo = source.stereo.isBlank() ? (single ? detail.stereo : "") : source.stereo;
        return FormatInference.infer(metadata, source.filename.isBlank() ? detail.title : source.filename,
                stereo, source.fov > 0 ? source.fov : single ? detail.fov : 0);
    }
}
```

- [ ] 测试单来源使用场景回退、多来源不使用场景回退、文件字段优先、filename 空用 detail.title、source.fov 非正时的回退。
- [ ] 恢复自动格式时保留眼别、yaw/pitch/FOV 的动作仍按旧 automatic() 执行；不顺便保留此前被重置的镜头参数。
- [ ] inferSource 只委托一次，生产用新规则，旧方法体不留第二份。
- [ ] 跑 G、GD、Q3 的多文件/恢复自动子集；提交 `refactor(domain): extract selected-source format policy`。

## 11. T06：稳定媒体标识

**Files:** 创建 J/domain/ResourceIdentity.java；修改 Protocol.identity、Store.playbackKey、两个 Activity 与测试；测试 T/domain/ResourceIdentityTest.java。

- [ ] 将 Protocol 的 identity 和它依赖的默认端口逻辑原样移到 `ResourceIdentity.of(String url)`；`playbackKey(String profileId,String url)` 返回 `profileId + ":" + of(url)`。
- [ ] Protocol.identity 暂只委托；URL base/resolve/sameOrigin 属 Data，不迁入领域模型。
- [ ] 旧/新差分用例固定为：HTTP/HTTPS 默认端口、显式非默认端口、域名大小写、代理前缀、scene/file、deovr/heresphere、dms file、query/session、未知 URL 与非法 URI。未知 URL 必须原字符串返回。
- [ ] 用旧 Store 写的合成 pos/fav/source key 让新函数查询，不能只用新函数写再读证明兼容。
- [ ] 跑 G、GD、Q1/Q3 收藏和续播；提交 `refactor(domain): preserve stable scene and file identities`。

## 12. T07：服务器配置显式模型

**Files:** 创建 J/domain/ServerProfile.java、ProfileRepository.java，J/data/ProfileJsonMapper.java；修改 Store 和服务器对话框边界；测试 T/data/ProfileJsonMapperTest.java。

```java
package top.liuwei.xbvr.domain;
public final class ServerProfile {
    public final String id, base, user, password, basicUser, basicPassword;
    public ServerProfile(String id, String base, String user, String password, String basicUser, String basicPassword) {
        this.id = id; this.base = base; this.user = user; this.password = password;
        this.basicUser = basicUser; this.basicPassword = basicPassword;
    }
    // 不生成包含账号或密码的 toString。
}
```

```java
package top.liuwei.xbvr.domain;
import java.util.List;
public interface ProfileRepository {
    List<ServerProfile> all() throws Exception;
    ServerProfile current() throws Exception;
    ServerProfile find(String id) throws Exception;
    void save(ServerProfile value) throws Exception;
    void remove(String id) throws Exception;
    void select(String id);
}
```

- [ ] Mapper 内部按上述六个字符串字段 optString/getter 映射，JSON 只留 Data。保存逻辑按原 MainActivity.connection 中的 id、同地址匹配、替换/新增和删除行为逐行迁移，不重新定义去重策略。
- [ ] 未编辑的 profile 原 JSON 由 Data 保留，不因 List<ServerProfile> 转换丢弃未知字段；编辑后的 JSON 是否保留字段，以旧 connection 的替换行为为准，测试锁住。
- [ ] profile 为空/active 失效/多个服务器、播放器认证与代理 Basic 独立、旋转时五字段草稿都要覆盖。
- [ ] MainActivity 不再把 JSONObject 放进草稿状态；PlayerActivity 的 Intent 仍只带 profile id，Data lookup 返回 ServerProfile。
- [ ] 跑 G、GD、Q0/Q1 的新增、编辑、切换、删除和草稿；提交 `refactor(data): introduce typed server profiles without schema migration`。

## 13. T08：封装 prefs 与播放 JSON 编解码

**Files:** 创建 J/data/LocalSettings.java、PlaybackJsonCodec.java；创建 Domain/CoverSettings.java、PlaybackRepository.java、FavoriteRepository.java；修改 Store 和 Activity 的直接 prefs 调用；测试 T/data/PlaybackJsonCodecTest.java。

```java
package top.liuwei.xbvr.domain;
public interface PlaybackRepository {
    long position(String key);
    void save(String key, long position, Projection view, boolean manual);
    boolean restore(String key, Projection target);
    String selectedSource(String entryKey);
    void selectedSource(String entryKey, String url);
    void entryPosition(String entryKey, long position);
}
```

```java
package top.liuwei.xbvr.domain;
public interface FavoriteRepository {
    boolean favorite(String key);
    void favorite(String key, boolean value);
}
```

```java
package top.liuwei.xbvr.domain;
public interface CoverSettings {
    int mode(String profileId);
    void mode(String profileId, int mode);
    float inferredRatio(String profileId);
    void inferredRatio(String profileId, float ratio);
    void clearInferredRatio(String profileId);
}
```

- [ ] LocalSettings 接管 coverMode/coverAuto/source/entry pos 键；初期 Store 转发，避免同时改加密与目录缓存。
- [ ] PlaybackJsonCodec 只搬旧 save/restore 的 JSON 内容；编码字段名、默认值、异常处理和局部更新顺序不改。save(key) 会 clamp 文件 pos；entryPosition 保留旧 Activity 直接写原 pos 的行为，两者不能误合并。
- [ ] Codec 测试使用旧字符串固定样本：空对象、完整手动、override=false、缺少字段、非法 JSON 和保存后往返。manual=true 但缺 kind/layout 时的处理必须与旧 restore 一致，不“修正”旧局部更新。
- [ ] `rg 'store\.prefs|SharedPreferences'` 确认 Activity 直接访问消失，业务动作仍按原顺序调用接口。
- [ ] 跑 G、GD、Q2/Q3；提交 `refactor(data): encapsulate preferences and preserve playback JSON`。

## 14. T09：拆 Store 的实现与 Android 存储验证

**Files:** 创建 J/data/ProfileStore.java、PlaybackStore.java、FavoriteStore.java、LibraryCache.java；root Store 暂作转发；创建 A/StorageCompatibilityInstrumentation.java 和 app/src/androidTest/AndroidManifest.xml。

- [ ] 依次迁移 ProfileStore → PlaybackStore/FavoriteStore → LibraryCache，每次切换都跑 G；最后一个子步骤跑 GT。共用原 `local` prefs 和 application Context。
- [ ] ProfileStore 原样使用 AndroidKeyStore alias、AES/GCM 与 IV:ciphertext。LibraryCache 原样沿用 tmp 文件和 REPLACE_EXISTING 行为，不借机加不同原子写规则。
- [ ] 新建独立 instrumentation runner `StorageCompatibilityInstrumentation`，继承 Instrumentation；onStart 运行 Context/Keystore 真实存储断言，try/finally 恢复测试前的合成 profile 和 active。禁止 pm clear 或修改真实 profile。

测试manifest添加第二runner，保留Gradle生成的现有RendererFailure runner：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <instrumentation android:name="top.liuwei.xbvr.StorageCompatibilityInstrumentation"
      android:targetPackage="top.liuwei.xbvr" />
</manifest>
```

runner明确在onCreate调用start；onStart成功时Bundle写`passed=true`和实际`checks`并finish(Activity.RESULT_OK, bundle)，失败写`passed=false`、失败阶段和异常类名并finish(Activity.RESULT_CANCELED, bundle)。不要输出任意异常消息或配置明文。
- [ ] 两个测试 APK 使用同签名；测试仅对专用合成 profile 操作。旧 APK 写 fixture 配置/pos/view/fav/source/cover，覆盖安装后新实现读取；再从新实现写回，用旧格式 codec 读取。
- [ ] 验证缺失 cache 返回空字符串、写异常继续传播、profile active 失效回退、负 pos clamp、scene/file 两套观看记录和收藏隔离。
- [ ] Android Keystore 不能用普通 JVM stub 结果冒充；设备不可用则 T09 状态 device_blocked，后续纯规则可独立整理，最终完成必须补齐。
- [ ] 提交 `refactor(data): split local stores with installed-data compatibility checks`。

## 15. T10：迁移 API/Protocol，隔离 HTTP 实现

**Files:** 将 Api→J/data/XbvrApi.java、Protocol→J/data/XbvrProtocol.java；创建 J/data/HttpTransport.java；更新 ApiSecurityTest/ApiMetadataTest/LibraryMetadataTest/CoreTest 的导入和 instrumentation 反射定位。

- [ ] XbvrApi 改接收 ServerProfile；初始构造适配从 Domain 字段读取，JSON 请求 body 和响应解析仍按旧方法。
- [ ] HttpTransport 仅接管客户端构造、CookieJar 和网络 interceptor；JSON 禁自动重定向的客户端与媒体/封面客户端分别保留。XbvrApi 委托，不改 endpoint、重试或超时。
- [ ] 现有 ApiSecurityTest 加差分保护：307/308 body、同源默认/显式端口、跨源拒绝、不发送玩家认证到 REST、Basic 只对同源、媒体 query 不丢失。
- [ ] Protocol 的 Base/resolve/detail/metadata/fileMetadata/normalized/merge/authorized/hereUrl 原算法迁移；identity 委托 Domain。不能改“无认证时空字段”和选中文件元数据优先级。
- [ ] 更新所有包级测试，不为访问测试而无差别 public 化内部工具；按新包移动相关测试或提供合理公共 API。
- [ ] root Api/Protocol 临时转发可保留，instrumentation 类名表在本步骤更新并编译。不可推迟到最后才修测试入口。
- [ ] 跑 GT、GD、Q1 的接口失败/缓存回退；提交 `refactor(data): isolate XBVR protocol and HTTP transport`。

## 16. T11：封面请求、缓存和采样

**Files:** 创建 J/domain/CoverRepository.java，J/data/BitmapCoverRepository.java；修改 MainActivity.Gallery.getView 与 posterKey；测试 T/data/CoverRequestTest.java、T/ui/library/CoverBindingTest.java。

```java
package top.liuwei.xbvr.domain;
import java.util.List;
public interface CoverRepository<I> {
    interface Observer<I> { void complete(String key, I image, int width, int height); }
    I cached(String key);
    boolean failed(String key);
    void retry(String key);
    void request(long epoch, String key, List<String> candidates, Observer<I> observer);
    void invalidate(long epoch);
    void clear();
}
```

`I` 让 Domain 不依赖 Bitmap；生产实现是 `CoverRepository<Bitmap>`，fake 测试用普通 Object。此处使用一个泛型接口，不发展成通用资源框架。

- [ ] 原 images/pending/imageProblems 和下载、响应长度检查、Bitmap bounds/采样进入 BitmapCoverRepository，继续使用 Main 原四线程 executor 和注入的主线程 Executor。
- [ ] profile id、Entry URL、poster、posterCandidates.hashCode 组成的 key 原样保留；pendingToken 继续包含 epoch。clear 与 invalidate 的调用时机按原 open/load/onDestroy。
- [ ] 网络字节读取与候选回退用 MockWebServer 验证：失败→下一个候选、超 12 MiB 拒绝、无效图片、相同 key 并发只发一次、不同 epoch 不相互阻塞。
- [ ] UI 只绑定“当前 key”的卡片；回调必须检查 epoch、服务器上下文和页面存活，旧结果不能写新缓存或推断新服务器比例。封面比例推断仍调用 T04 规则及 UI 延迟调度。
- [ ] 解码后按 getByteCount 计 LruCache，原 16 MiB 不改成压缩字节限额；Bitmap 采样在设备测试。
- [ ] 跑 G、GD、Q2、Q4 的旧请求隔离；提交 `refactor(data): extract cover loading without changing cache behavior`。

## 17. T12：目录仓库保留三阶段加载

**Files:** 创建 J/domain/EntryMetadata.java、LibraryEvent.java、LibraryRepository.java；J/data/DefaultLibraryRepository.java；修改 MainActivity.load；测试 T/data/LibraryRepositoryTest.java。

接口约定：

```java
package top.liuwei.xbvr.domain;
public interface LibraryRepository {
    interface Request { void cancel(); }
    interface Observer { void event(LibraryEvent event); }
    Request load(boolean useCache, Observer observer);
}
```

LibraryEvent 是纯 Java 数据：kind 枚举 `CACHE/DIRECTORY/METADATA/DIRECTORY_ERROR/METADATA_ERROR`、List<Entry>、Map<String,EntryMetadata>、Throwable；工厂只填该事件需要的字段。EntryMetadata 包含 studio、actors/tags 的有序集合、posterCandidates、metadataLoaded；重试时间和原始 JSON 留 Data。

- [ ] 先以 fake XbvrApi 和 cache 编写顺序测试；生产流程原 load 分为 cache、directory、metadata，不改等待或错误处理。
- [ ] Data 保存原 response 和 `_metadata`，完成 enrichment 后检查 Request 未取消再写 cache。取消用于原 generation/isDestroyed 隔离，不扩展为新后台服务。
- [ ] 目录已显示后 enrichment 失败只能发 METADATA_ERROR，不能发目录失败、清空卡片或重复连接弹窗。空目录不误标 metadataBusy。
- [ ] UI 仅接收事件，不持有 JSONObject。Domain 元数据合并按 identity 更新有序字段；原 retryAfter 与 force 刷新行为在 Data 原样保留。
- [ ] 测试缓存损坏、网络失败有/无 cache、元数据部分失败、服务切换旧请求成功/失败、取消后不写缓存。保持 Main 4线程、元数据内部3线程和 Player单线程。
- [ ] 跑 G、GD、Q1/Q4；提交 `refactor(data): preserve cached-directory-metadata load sequencing`。

## 18. T13：媒体库协调器与页面状态

**Files:** 创建 J/ui/library/LibraryController.java、LibraryUiState.java；修改 MainActivity.open/load/apply/filter/updateState；测试 T/ui/library/LibraryControllerTest.java。

- [ ] LibraryUiState 持有当前 ServerProfile、Entry/visible 列表、LibraryFilterState、busy/metadataBusy/failed、封面推断状态与语义化提示枚举。R.string 在 MainView 映射，不在 Controller 中持有 Android View/Context/Bundle。
- [ ] Controller 构造接收 ProfileRepository、LibraryRepository、PlaybackRepository、FavoriteRepository、CoverSettings 和页面监听。封面观察只传 key/尺寸；需要订阅封面时使用纯 Java 泛型接口，不在 Controller 引用 Bitmap。`CoverRepository<Bitmap>` 的图像绑定留 PosterAdapter。
- [ ] 以可控 fake callback 写测试，生产接口保持：

```java
public interface Listener {
    void changed(LibraryUiState state, boolean keepScroll);
    void connectionFailed(Throwable failure);
}
// LibraryController 的公共动作：
// open(ServerProfile profile, boolean reset), refresh(),
// filterChanged(LibraryFilterState filter, boolean preserveScroll), close()
```

- [ ] generation 由 Controller 管，每次 open/load 的递增与失效时机按旧代码。close 后旧成功/失败不发 UI 事件；切服务器清 facet 与刷新清 cover 的条件保持。
- [ ] 先迁移 load 事件处理，再迁移 filter/三个 tab，最后迁移 updateState 判定，每个子步骤跑 G。GridView 保存/恢复由 Listener 的 keepScroll 动作触发，不把 Parcelable 搬进去。
- [ ] fake 测试必须包含：缓存先显示→目录→元数据；metadata 失败保留目录；旧 generation 不更新；tab=继续观看按 position>0；本机收藏；category 排序；服务器切换重置条件。
- [ ] 不把对话框构建或下载实现挪进 LibraryController。Controller 不应包含 okhttp3、org.json、android.* imports。
- [ ] 跑 G、GD、Q1/Q4；提交 `refactor(ui): introduce testable library state coordination`。

## 19. T14：PosterAdapter 与滚动恢复

**Files:** 创建 J/ui/library/PosterAdapter.java、GridScrollRestorer.java；移动 MainActivity 的 Card/Gallery/PosterFrame、ScrollAnchor/captureAnchor/restore/cancelRestore；测试 T/ui/library/ScrollTargetTest.java 和设备回归。

- [ ] PosterAdapter 接收 Entry 列表、CoverRepository<Bitmap>、当前比例/颜色和点击回调；不得自己读取 Store 或执行 HTTP。
- [ ] 保留 Card 复用、标题/演职员点击、duration/heart/progress、contentDescription、imageHint 和每次绑定 ScaleType 更新。不要优化 key、layout 或换 RecyclerView。
- [ ] GridScrollRestorer 持有 GridView/Parcelable/监听，仅属 Android UI。保存 url/index/top/nativeState；恢复时先按 identity 找项目，找不到按旧 index 边界回退。
- [ ] onPreDraw 的“两次选择/取消监听”原路径保留，含触摸模式使用 nativeState 的注释与行为。页面重建时目标 GridView 变化、销毁和空列表都要取消旧监听。
- [ ] 从 Activity 返回、刷新 metadata、改变封面比例、切筛选、旋转，逐项观察第一行项目与 top 像素；仅“同一项还在屏幕上”不等于滚动恢复正确。
- [ ] 纯测试保护 identity 目标选择/回退；native GridView 行为用 Q1/Q2/Q4，不能用 mock 完全替代。
- [ ] 跑 G、GD；提交 `refactor(ui): extract poster binding and grid scroll restoration`。

## 20. T15：MainView 和媒体库对话框

**Files:** 创建 J/ui/library/MainView.java、ServerDialogs.java、FacetDialogs.java；修改 MainActivity.build、servers、connection、facetDialog、track 和 onConfigurationChanged。

- [ ] 先把纯布局 build/divider/spacing/label/glyph/chip/filterCapsule/buildNavigation/columns 移到 MainView；View 只接收状态和动作接口。
- [ ] 下一小步骤移动服务器/封面比率/分类/facet 对话框；profile 使用 T07 模型，保存选择调用 Controller/仓库接口，不在 Dialog 内操作 raw JSON/prefs。
- [ ] modal 状态包含原 kind、editedProfile、5字段草稿、facetKind；取消对话框不提交草稿，旋转重开种类和已输入值与旧实现一致。
- [ ] MainActivity 剩下创建服务/View/Controller、Intent 跳转、onResume、Bundle→状态桥接、onConfigurationChanged 的重建顺序和 onDestroy 关闭。
- [ ] `compact = landscape && screenHeightDp < 500` 保持；侧栏 300dp、网格宽度扣侧栏、手机/平板布局不重新设计。
- [ ] 跑 G、GD、Q0/Q1/Q2/Q4 的完整媒体库矩阵；提交 `refactor(ui): move library layouts and dialogs behind view actions`。

## 21. T16：PlayerView、菜单和控件显隐

**Files:** 创建 J/ui/player/PlayerView.java、PlayerDialogs.java、ControlsVisibility.java；修改 PlayerActivity.build/adaptControls/positionTransport/positionSubtitles/more/present/display/各菜单；测试 T/ui/player/ControlsVisibilityTest.java。

- [ ] PlayerView 承担原布局、Insets、安全边距、按钮尺寸、字幕位置、主题更新和动画。VrView 仍按原构造/绑定，暂不改 GL 所有权。
- [ ] PlayerDialogs 承担 more/menuGroup/menuAction/palette、files/chapters/speed/tracks/formats/packing/lens/favorites 的 Android 表示；业务动作继续委托旧 Activity，T17 再切 Controller。
- [ ] 暂不改 picker 列表顺序、字幕 MIME 判定、数值上下限或手动投影重建逻辑。镜头 cx/cy 0～1、radius .1～2、Float.isFinite 与错误提示都保持。
- [ ] 控件显隐的纯条件为：

```java
package top.liuwei.xbvr.ui.player;
public final class ControlsVisibility {
    private ControlsVisibility() {}
    public static boolean canHide(boolean active, boolean shown, boolean seeking,
            int dialogs, boolean hasPlayer, boolean playing) {
        return active && shown && !seeking && dialogs == 0 && hasPlayer && playing;
    }
}
```

- [ ] 六个条件逐一反转的测试均应禁止隐藏；保持4200ms延迟、显示160ms/隐藏220ms、停止时移除回调。Handler 和 Android 动画留 PlayerView，不放 Domain。
- [ ] 跑 G、GD、Q3/Q5 的菜单、暂停、seek、字幕和弹窗时不隐藏；提交 `refactor(ui): separate player presentation and dialog construction`。

## 22. T17：播放状态协调与保存

**Files:** 创建 J/ui/player/PlaybackController.java、PlaybackUiState.java；创建 J/domain/PlayerPort.java、TrackOption.java、MediaDetailsRepository.java 和 J/data/DefaultMediaDetailsRepository.java；修改 PlayerActivity.select/inferSource/automatic/save/ticker/favorites 及事件转发；测试 T/ui/player/PlaybackControllerTest.java。

```java
package top.liuwei.xbvr.domain;
import java.util.List;
import top.liuwei.xbvr.domain.Models.Source;
import top.liuwei.xbvr.domain.Models.Subtitle;
public interface PlayerPort {
    void prepare(Source source, List<Subtitle> subtitles, long position, boolean play);
    boolean hasEngine();
    long position();
    long duration();
    boolean playWhenReady();
    boolean isPlaying();
    boolean ended();
    void play();
    void pause();
    void seekTo(long position);
    void speed(float value);
    List<TrackOption> tracks();
    void audioAuto();
    void subtitlesOff();
    void selectTrack(String token);
    void stop();
}
```

TrackOption 字段：token、kind（AUDIO/TEXT）、language、mimeType、supported、selected。token 只在该播放会话的 tracks 版本内有效；媒体实现管理 token→Tracks.Group/index，不在 Domain 存 TrackGroup。

```java
package top.liuwei.xbvr.domain;
import top.liuwei.xbvr.domain.Models.Detail;
public interface MediaDetailsRepository {
    Detail detail(String url) throws Exception;
    void favorite(Detail detail, boolean value) throws Exception;
}
```

- [ ] 先以旧 Activity 的薄 PlayerPort 适配接 Controller，下一任务才改 engine 所有权；不要此时改 Media3/Surface。
- [ ] DefaultMediaDetailsRepository 逐行封装现有 XbvrApi 的 detail/favorite 调用，保留授权、失败和收藏写入语义；由根组装入口或当前 Activity 的临时组装代码注入接口。Controller 不直接调用 XbvrApi，T22 消除 Activity 的具体 Data 构造。
- [ ] PlaybackUiState 含 Detail、Source、selected、Projection、manual、entryKey/fileKey、savedPosition、wasPlaying、active、loaded、HDR 和故障状态，纯 Java，不含 Surface/Sensor/播放器对象。
- [ ] select(index,keep) 按原顺序：读 position/playWhenReady→save旧来源→新 source/key→infer→restore→更新视角/图像尺寸→keep当前进度或读文件进度→写source→loaded/gyro基准重置→更新UI→active时prepare。
- [ ] 自动识别、重置视角、手动镜头、切眼和半幅设置只改 Projection/持久化并 requestRender，不重建网络播放器。修改文件才 prepare。
- [ ] save 同时写文件 view/pos 和场景 entry pos；ticker 每秒更新、每5次save，seekTouch 时不覆盖拖动进度。保留 STATE_PLAY_WHEN_READY=`player.playWhenReady`。
- [ ] fake PlayerPort/PlaybackRepository 记录事件顺序；覆盖 keep=true/false、pause后前后台、恢复自动、无detail、无source、加载失败/销毁、服务器收藏无权不可发请求/有权需确认。
- [ ] 明确 view/generation/active 是主线程管理；详情加载继续用旧单线程 executor，结束后通过注入的主线程 Executor 通知。
- [ ] 跑 G、GD、Q3/Q5；提交 `refactor(ui): isolate playback decisions and persistence coordination`。

## 23. T18：Media3 播放会话

**Files:** 创建 J/media/Media3PlaybackSession.java、PlaybackLifecycle.java、J/domain/DiagnosticsSink.java 和 J/AppServices.java（若此前已逐步建立则修改）；修改 PlayerActivity.createPlayer/startPlayer/stopPlayer/监听；测试 T/media/PlaybackLifecycleTest.java、更新 A/RendererFailureInstrumentation.java。

- [ ] 先创建下面的纯 Java 诊断接口。此阶段 AppServices 将旧 PlaybackDiagnostics.record 适配为接口，T21 再替换为 DiagnosticsStore；临时适配只留组装入口。AppServices 此时只提供媒体工厂与诊断注入，不接收页面业务流程，T22 完成其他依赖组装。

```java
package top.liuwei.xbvr.domain;
public interface DiagnosticsSink {
    void record(String phase, Throwable failure, String graphics);
}
```

- [ ] Media3PlaybackSession implements PlayerPort；构造仅接收 Context、已配置的 DataSource.Factory、DiagnosticsSink 和 listener。**不接收 XbvrApi/Store/ProfileStore**。
- [ ] 工厂在 AppServices 使用 T10 的安全媒体 OkHttpClient 建立 `new OkHttpDataSource.Factory(client)`，保持相同 interceptor/CookieJar；媒体流不能绕过认证或另建默认不安全客户端。
- [ ] 将原 MediaItem/subtitles/ExoPlayer.Listener/create/stop 代码机械迁移，外部字幕 MIME 仍按旧 name+URL 判定 .vtt，否则 SUBRIP；保留无 decoderSurface 时先prepare、Surface稍后attach的路径。
- [ ] media 专用方法 `attachSurface(Surface)`、`clearSurface()` 与 listener 的 videoSize/cues/error/playing/state/HDR 属 Media API，不污染 Domain PlayerPort。UI 的 SubtitleView 可接收原 List<Cue>；不得把 Cue 放 Domain。
- [ ] stop 的重要顺序：先取 current 再将 engine 字段置null→尝试获取最后position→clearVideoSurface→release；每个RuntimeException记录后继续后续释放。不能用一个catch吞掉整段。
- [ ] prepare 在新建之前释放旧 engine，与失败回调一次性交付相配合；view listeners 不持有已销毁 Activity。代码只在 Android 主线程访问 Media3，使用假的 Backend/命令记录器测试调度顺序，不能在普通 JVM 实例化 ExoPlayer。
- [ ] 最小 fake 生命周期断言示例（由 production Backend 包装 Media3，fake 实现记录方法，不能只模拟 Controller 自己）：

```java
@Test public void releaseContinuesAfterDetachFails() {
    FakeBackend backend = FakeBackend.throwOn("detach");
    PlaybackLifecycle lifecycle = new PlaybackLifecycle(backend, diagnostics, 100L);
    lifecycle.stop();
    assertEquals(List.of("position", "detach", "release"), backend.calls());
    assertFalse(lifecycle.hasEngine());
    assertEquals(List.of("player.detach"), diagnostics.phases());
}
```

可直接用于生产迁移的最小生命周期策略如下，创建 J/media/PlaybackLifecycle.java；Media3PlaybackSession 使用它拥有 Backend，不能另留未受该策略管理的播放器字段：

```java
package top.liuwei.xbvr.media;
import top.liuwei.xbvr.domain.DiagnosticsSink;
public final class PlaybackLifecycle {
    public interface Backend {
        long position();
        void detach();
        void release();
    }
    private Backend backend;
    private final DiagnosticsSink diagnostics;
    private long savedPosition;
    public PlaybackLifecycle(Backend backend, DiagnosticsSink diagnostics, long initialPosition) {
        this.backend = backend; this.diagnostics = diagnostics; this.savedPosition = initialPosition;
    }
    public boolean hasEngine() { return backend != null; }
    public long savedPosition() { return savedPosition; }
    public void stop() {
        Backend current = backend;
        if (current == null) return;
        backend = null;
        try { savedPosition = current.position(); }
        catch (RuntimeException e) { diagnostics.record("player.position", e, ""); }
        try { current.detach(); }
        catch (RuntimeException e) { diagnostics.record("player.detach", e, ""); }
        try { current.release(); }
        catch (RuntimeException e) { diagnostics.record("player.release", e, ""); }
    }
}
```

Media3 Backend 的三方法分别调用 current.getCurrentPosition/clearVideoSurface/release；其他播放控制通过仍存活的同一个Backend适配，不创建第二个engine所有者。新建session和替换来源时明确初始化savedPosition；stop后取savedPosition回到Controller，UI按钮由外层listener更新。诊断实现使用旧record语义；如果记录本身可能抛异常，必须先与旧实现对照，不在此引入不同错误吞掉策略。

上面示例测试中两个fake的完整定义可放进测试类：

```java
static final class FakeBackend implements PlaybackLifecycle.Backend {
    private final java.util.List<String> calls = new java.util.ArrayList<>();
    private final String failed;
    private FakeBackend(String failed) { this.failed = failed; }
    static FakeBackend throwOn(String failed) { return new FakeBackend(failed); }
    private void invoke(String name) {
        calls.add(name);
        if (name.equals(failed)) throw new IllegalStateException(name);
    }
    public long position() { invoke("position"); return 5000L; }
    public void detach() { invoke("detach"); }
    public void release() { invoke("release"); }
    java.util.List<String> calls() { return calls; }
}
static final class FakeDiagnostics implements DiagnosticsSink {
    private final java.util.List<String> phases = new java.util.ArrayList<>();
    public void record(String phase, Throwable failure, String graphics) { phases.add(phase); }
    java.util.List<String> phases() { return phases; }
}
```

每个测试创建 `FakeDiagnostics diagnostics = new FakeDiagnostics()`；import `org.junit.Test`、`static org.junit.Assert.*`、`java.util.List`、Domain.DiagnosticsSink和Media.PlaybackLifecycle。再加position失败保留100L、release失败hasEngine=false、stop两次不重复释放三个断言。

- [ ] 更新 instrumentation 对 PlayerActivity.player 的断言：定位 session 的实际 engine，验证 null/released。保留原“Activity活着、故障对话框可见、原异常保留、第二次故障不替换”的语义。
- [ ] 跑 GT、GD、Q3/Q5/Q6。没有设备不得把此任务记 passed。
- [ ] 提交 `refactor(media): encapsulate Media3 engine and surface lifecycle`。

## 24. T19：VR 配置/数学/GL 的边界

**Files:** 移 VrView→J/media/VrView.java，RendererShader/RenderMath→J/media/；创建 J/media/ProjectionMath.java、VrRenderer.java；修改 Domain Projection.map 和测试/仪器定位。

- [ ] 第一个小步骤只移包，更新 rootActivity、PlayerView、Ui 和所有 shader/math 测试。RendererShader 的同包测试也移至 T/media，不无差别扩大成员可见性。
- [ ] 第二个步骤把 Projection.map 逐行移成 `ProjectionMath.map(Projection p,double x,double y,double z,int width,int height)`；所有原 map 测试改调用此函数，断言坐标保持。删除 Domain→Media 任何 delegate，Domain 不再承担 renderer 算法。
- [ ] 第三个步骤将 onSurfaceCreated/onSurfaceChanged/onDrawFrame、shader/link/fail/deliverFailure、SurfaceTexture/Surface/program/texture及surfaceLock整体交给 VrRenderer；VrView 留 GLSurfaceView、触摸输入、settings桥接和requestRender。
- [ ] **一个所有者负责资源**，不要在 VrView/VrRenderer 两边各持有一套 SurfaceTexture 或 release 标记。原 released/failed/frame、回调线程和同步范围保持；不改 highp fallback、UV翻转、gamma、曝光或GLSL字符串。
- [ ] VrView 把设置/视频尺寸更新到 renderer，仍保留一个当前 Projection 引用。不在此步骤改成不可变快照或修改GL跨线程更新策略。
- [ ] 修改故障仪器：GL link/fail/reflection的所有者改成 VrRenderer，surfaceLock/renderFailure/failureDelivered也从该对象读取；调用真实renderer.onDrawFrame验证故障后安全返回。测试不删、不改成捕获预期异常就宣告所有步骤成功。
- [ ] 跑 GT、GD、Q6/Q7。着色器源码正文与基准逐字节比对；对坐标输出用原数值容差，图像对比允许声明渲染设备噪声但不能掩盖镜像/眼别错误。
- [ ] 提交 `refactor(media): separate projection math and GL resource ownership`。

## 25. T20：传感器与视角更新

**Files:** 创建 J/media/GyroController.java；修改 PlayerActivity 的 SensorEventListener、toggleGyro、onSensorChanged/onStart/onStop/onConfigurationChanged；测试 T/media/GyroMappingTest.java。

- [ ] GyroController 管 SensorManager、GAME_ROTATION_VECTOR→ROTATION_VECTOR 回退、监听注册、基准角和display rotation映射；UI只发开关/激活/停止/重置动作并接收状态。
- [ ] 保留 active 约束、SENSOR_DELAY_GAME、gyroBase重置、register失败提示、yaw wrappedDelta、pitch边界和四个rotation坐标映射。
- [ ] 纯测试用捕获的合成旋转向量/矩阵或拆出的角度输入，覆盖 ±180 wrap、四方向、首帧只建基准、inactive忽略、reset重建；物理sensor注册和无sensor fallback用设备。
- [ ] onStart/onStop 与 GL/播放生命周期顺序不变。传感器停止必须在Activity停止时执行，不等待onDestroy。
- [ ] 跑 G、GD、Q5/Q7；至少一次真机验证陀螺仪、横竖旋转、后台返回和关闭后不漂移。无真机可记录结构完成/真机未验证，不声称真实传感器验收通过。
- [ ] 提交 `refactor(media): isolate gyro registration and orientation mapping`。

## 26. T21：公共 UI、许可和诊断分层

**Files:** 移 Ui/IconDrawable→J/ui/common/；抽 LicensesActivity 的界面为 J/ui/licenses/LicenseView；沿用 T18 的 Domain/DiagnosticsSink，拆 PlaybackDiagnostics 为 Data/DiagnosticsStore、UI/DiagnosticsDialog；修改 XbvrApplication、AppServices 和所有调用。

- [ ] Ui 的 text/colors/ripple/edgeToEdge/insets/projection labels/errorMessage 原样迁移；IconDrawable 路径、字体、资源id不改。LicenseView 保持打包assets、原文、中英文控件、selected文档和滚动恢复。
- [ ] 保持 T18 创建的 DiagnosticsSink 接口和调用契约，只替换实现，不创建第二个同名接口。

- [ ] PlaybackDiagnostics.install/record/read/safe/bounded/time 的数据与崩溃记录进入 DiagnosticsStore（application Context）；show 和对话框进入 DiagnosticsDialog。report/device/exits/reason 分别保留原收集和显示语义，系统进程退出信息允许在 Data 使用 Android API，不额外建立视频能力检查业务。
- [ ] Media3PlaybackSession/VrRenderer 只依赖 DiagnosticsSink；AppServices 注入 `(phase,failure,graphics) -> diagnosticsStore.record(phase,failure,graphics)`，不得让 Media import UI 的 DiagnosticsDialog。
- [ ] 旧 report 的脱敏、长度限制、异常cause遗漏和GPU字符串保留。JVM补 safe/bounded 的敏感URL/token/Authorization样本，真实GL注入仪器继续验证完整报告。
- [ ] XbvrApplication.onCreate 使用 DiagnosticsStore.install；不能重复安装两个default uncaught handlers、重复记录或吞掉原handler。
- [ ] 更新仪器的PlaybackDiagnostics.report定位，改为Data报告接口并保留三项集成结果；offline许可脚本仍从根 MainActivity启动。
- [ ] 跑 GT、GD、Q0/Q6/Q8；提交 `refactor(ui): separate shared views and diagnostic storage from presentation`。

## 27. T22：组装、删除兼容门面与架构验收

**Files:** 完成 J/AppServices.java；删除 root Api/Protocol/Store/Projection/Ui/IconDrawable/VrView/RenderMath/RendererShader/PlaybackDiagnostics 旧实现；更新整个 app/src、所有测试和 docs/VALIDATION。

- [ ] AppServices 使用 application Context 构造存储/HTTP/诊断，按 profile创建仓库和媒体会话，向页面提供接口。页面生命周期所拥有的 executor/request/session 不放静态全局单例；由页面协调器关闭。
- [ ] Media数据源Factory在组装入口准备，认证、CookieJar保持；Domain与Media不依赖XbvrApi/Store，UI不直接导入Data具体类。
- [ ] 删除无调用门面及旧字段；逐一检索旧全限定名、Class.forName、字符串组件名和测试反射。Activity名字及applicationId保持，不改Manifest入口。
- [ ] `MainActivity` 不含 raw JSON/HTTP/prefs/Bitmap下载实现；`PlayerActivity` 不含 ExoPlayer/TrackSelectionOverride/SensorManager/投影算法/JSON持久化。Bitmap显示、Surface桥接和Bundle桥接可留UI。
- [ ] 列出原58/69个Activity方法最终归属，不要求人为字节阈值；核对Controller没有吸收所有原方法，View没有隐藏网络或存储。
- [ ] 运行架构final检查，并人工检查四层import、跨包字段、同一资源是否两边拥有；库所有Entry/Source/Projection同一类型，不存在兼容类型转换副本。

```powershell
& $pythonExecutable "$planDirectory/verify_architecture.py" $projectDirectory --mode final --out 'D:/codex-work/output/xbvr-refactor-evidence/final-architecture.json'
if ($LASTEXITCODE -ne 0) { throw 'Final architecture gate failed' }
```

- [ ] 运行 GT，测试数量不降低；检查构建依赖无新增运行时架构框架。记录lint新增/消失的id及原因，不简单只看总数。
- [ ] 提交 `refactor: complete four-layer composition and remove legacy facades`。

## 28. T23：最终设备回归与本地候选交付

**Files:** 更新 VALIDATION.md、docs/refactor/PROGRESS.md、docs/refactor/FINAL.md；如需打包，另改 tools/compliance-package.py 的显式输出参数及测试。

- [ ] 完成 Q0–Q8，至少 API29 与API36模拟器（无对应设备则记录），并针对已有真机执行播放/gyro/HDR等可用片源检查。不能声称8K/HDR/长时间音画同步已验收，除非确实做了这些用例。
- [ ] 专用合成设备从原0.2.7覆盖安装新候选，账号配置/收藏/继续观看/来源/格式/封面设置都保留；不卸载、不clear data来绕过兼容问题。
- [ ] 最后一次GT之后记录HEAD、APK版本、签名证书、APK和源码包SHA256、测试数量和具体设备范围。检查许可assets与licenses文件字节一致，所有第三方材料仍在。
- [ ] 不直接运行写死0.2.5的旧packager覆盖历史目录。若要交付source zip，先给脚本增加显式输出目录/文件前缀参数，并新增测试保证旧目录不写入；公开文件白名单包含已审查的计划/架构测试，排除本地证据/fixture媒体/私钥/工具链/local.properties。
- [ ] 内部候选命名可用 `XBVR-Pocket-0.2.7-refactor-candidate.apk`，元数据仍是0.2.7/9；用户决定正式发布版本。签名已确认可覆盖安装后才标记兼容。
- [ ] 汇报FINAL：完成任务、结构依赖、功能对照、实际测试、未覆盖项目、回退点、交付哈希。存在device_blocked就给出明确的部分完成状态，不能写全面重构全部验证通过。
- [ ] 提交文档/打包工具后按变更类型再跑G或GT，交付本地可审查分支/候选产物。没有用户要求不push、不创建或替换Release。

## 29. 合成测试服务和设备操作

方案包的 `fixture/filter-fixture.cjs` 来自本机历史合成QA脚本，已只读核查为22个虚构场景、两种目录顺序、21种投影/布局组合、REST元数据404、缺失封面的代理回退。随包只有合成mp4/PNG，没有真实影片/账号。脚本不随app源码公开包上传。

从方案包目录运行，**不会修改项目数据**：

```powershell
Set-Location -LiteralPath "$planDirectory/fixture"
& $nodeExecutable './filter-fixture.cjs' --self-test
if ($LASTEXITCODE -ne 0) { throw 'Synthetic fixture self-test failed' }
& $nodeExecutable './filter-fixture.cjs'
```

服务监听主机127.0.0.1:18766；Android Emulator中用 `http://10.0.2.2:18766`、认证留空。Node在前台运行时保留此终端；如需后台启动，Start-Process加`-WindowStyle Hidden`并记录PID，只结束本次进程。

已有QA脚本的qa.cjs假定项目`toolchain/sdk/platform-tools/adb.exe`。新仓库没有这个SDK副本，不能不检查就运行。可单独为qa.cjs添加`XBVR_QA_ADB`可配置路径，保持旧路径默认、emulator serial限制和acceptedExitCodes；修改后跑qa-adb.test与GT。或直接使用下列已选定adb路径，不复制整个SDK。

```powershell
& $adbExecutable devices -l
$env:XBVR_QA_SERIAL = 'emulator-5580' # 使用 devices 实际列出的专用模拟器，未列出就不能执行
& $adbExecutable -s $env:XBVR_QA_SERIAL install -r "$projectDirectory/app/build/outputs/apk/debug/app-debug.apk"
& $adbExecutable -s $env:XBVR_QA_SERIAL shell am start -n 'top.liuwei.xbvr/.MainActivity'
```

设备状态改变前使用现有 `licenses-device-state.cjs` 的 preflight/capture/withRestoration/restore。不能直接reset屏幕/语言/网络后声称恢复原状态；恢复失败也要记录并通知用户。QA仅针对专用模拟器，不默认修改连接的真实手机。

渲染instrumentation只接受 `http://10.0.2.2:18766/deovr/<id>`；先在应用保存合成服务器配置、确认测试媒体能正常播放，再运行：

```powershell
& "$planDirectory/Invoke-Checks.ps1" -Project $projectDirectory -Batch T19 -ToolTests -InstrumentationBuild
& $adbExecutable -s $env:XBVR_QA_SERIAL install -r "$projectDirectory/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
& $adbExecutable -s $env:XBVR_QA_SERIAL shell am instrument -w -e url 'http://10.0.2.2:18766/deovr/1' 'top.liuwei.xbvr.test/top.liuwei.xbvr.RendererFailureInstrumentation'
```

自定义runner不是JUnit runner，不能只靠`connectedDebugAndroidTest`或adb退出码判断成功。保存输出，要求 `passed=true`、checks=3、三项语义检查成功；当前Instrumentation.RESULT_OK通常输出`INSTRUMENTATION_CODE: -1`，失败可能仍是shell退出0。第二StorageCompatibilityInstrumentation用完整runner类名启动，需在测试APKmanifest声明并固定返回passed/详细检查数。

## 30. Q0–Q8 设备验收矩阵

每条记录设备/API、方向、语言/主题、旧版本预期、新版本实际、截图/UI树/日志和通过/失败。截图只使用合成资料。

| ID | 操作 | 必须确认 |
|---|---|---|
| Q0 | 启动、服务器菜单、离线许可、进入/退出播放器 | 原入口及中英文文案可用；未新增崩溃；menu选中状态和许可全文保持 |
| Q1 | All/Added date排序；搜索studio/actor/tag；多标签与跨facet；收藏/继续观看；缓存离线 | 结果/顺序与旧实现一致；取消不提交；刷新失败保留已显示目录 |
| Q2 | 自动/1:1/3:2/16:9；自动→固定→自动；滚动复用卡片；刷新/重启/换服务器 | 自动完整显示，固定居中裁切；无旧scaleType；按服务器保存比例；旧图不影响新服务器 |
| Q3 | 播放/暂停/seek；章节、速度、轨道、字幕；切文件、手动格式/眼别/packing/镜头/恢复自动 | 暂停/进度保留；格式不重建播放器；多文件格式按选中来源；收藏写权限保持 |
| Q4 | 目录/图片请求中切服务器/返回；旋转/主题变化时菜单和连接草稿重开；播放后返回海报墙 | 旧回调不改新页面；滚动url/first/top/native状态保持；无销毁View访问 |
| Q5 | 播放/暂停各自切后台返回；seek时弹窗；等待自动隐藏；Surface重建；连续切文件 | 用户暂停意图保持；无双播放器；无隐藏中的交互冲突；释放和恢复正确 |
| Q6 | 真实GL有效mediump编译；故障注入；报告查看复制 | 一次故障回调、停止播放但Activity活着、对话框可见、二次故障不替换；无合成secret泄露 |
| Q7 | flat/180/360/fisheye180/190/200/220 × mono/SBS/TB；触摸、缩放、切眼、gyro | 与旧合成基准一致；每眼方向/镜像/比例正确；传感器无设备时可触摸；真机gyro另记 |
| Q8 | 原APK数据建立→覆盖安装候选；读取与再次保存；英文/中文、浅/深、手机/平板横竖 | 配置/收藏/进度/来源/视角/封面模式全部兼容；许可材料保留；布局没有误重设计 |

媒体库矩阵覆盖四种封面比例×中/英×浅/深，以及手机横竖和平板16:10/3:2横竖。每步只跑相关子集；T15和T23跑完整媒体库矩阵，T19/T23跑完整VR投影/眼别。避免每次小改都重复无关的完整矩阵。

fixture未提供多来源/字幕/服务器收藏写权限的所有设备场景，不能据22场景通过声称这些都测过。执行T07/T17/T18时用专门MockWebServer测试及添加仅合成的对应fixture路线补齐；新增路线必须验证请求/响应与生产协议。真实服务器收藏写入只在用户明确提供测试目标和授权时做。

## 31. 失败处理、恢复与继续工作

- 构建失败：先查看当前task的application-checks.log，不使用旧APK继续验收。保持当前小步状态，修类型/导入/测试入口后重跑G。
- 测试失败：判断是搬错还是暴露旧行为，不改预期来迁就新实现；记录旧/新差异。失败当前task不能记passed。
- 设备行为变化：暂停该职责后续迁移，提交或保存最小复现；先回退当前独立任务的变更并重新验证基准。使用普通revert或仅恢复自己的明确文件，不用reset --hard/全目录删除。
- 环境缺失：记录具体SDK/JDK/adb/fixture/设备缺项，不能悄悄生成不同签名或下载不明代理；保留可验证的纯逻辑工作。
- 续接：新agent先读PROGRESS/FINAL、git log、git status和当前失败日志，从第一个非passed任务继续；不要重做已验证任务或一次创建余下所有类。
- 额度紧张：保存当前完成的小步骤、真实检查结果和剩余任务。不要因额度快用完而跳过验收或宣称整个阶段完成。

## 32. 每批交接内容

```markdown
任务：Txx + 本批具体职责
基准与完成提交：实际Git ID
改动文件：明确路径及原方法→新所有者
功能变化：无；若存在差异列出触发条件并标记未获批准
应用构建：真实命令、退出码、APK哈希/证书
JVM：实际总数、失败/错误/跳过数
Lint：错误/警告，以及新增警告原因
工具/仪器：实际结果；未运行就写未运行
设备：Q用例、设备/API、结果、证据路径
兼容：哪些旧数据已验证、哪些没有
剩余：第一个未完成任务和明确阻碍
回退：本批独立commit，不包含他人改动
```

最终交付清单：本地分支/提交历史、源代码、候选APK（只有构建与签名通过后）、SHA256SUMS、PROGRESS/FINAL、测试报告和设备证据索引。测试资料只保留合成内容；不包含keystore、私密缓存、22GB工具链或未审查历史脚本。

## 33. 执行者开始指令

先读全部方案，再完成T00并报告基准、签名、实际构建测试、可用设备。之后按任务顺序逐项实现、验证和提交，普通机械决策自主完成，不每一步向用户重复确认；新的业务规则、公开发布或缺少真实设备授权时才升级处理。不要把整套重构一次性提交。
