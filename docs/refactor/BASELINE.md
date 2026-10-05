# T00 基准与兼容门槛 / Baseline and compatibility gates

本文件记录四层重构开始前的实测基准。所有数值均为本次实际运行结果，不引用历史报告。

## 1. 基准提交与分支

| 项 | 值 |
|---|---|
| 方案基准 HEAD | `dd5e888ebbad55ccddb3fdab2a3c285082ff45ce` |
| 实际核对 HEAD | `dd5e888ebbad55ccddb3fdab2a3c285082ff45ce`（**无漂移**，`git diff dd5e888..HEAD` 为空） |
| 分支 | `refactor/four-layer`（自 `dd5e888` 新建，本地，不 push） |
| 计划文档提交 | `14ee5de` `docs(refactor): add the four-layer refactor plan`（照方案 §T00 单独提交；仓库内文件与方案包 `执行方案.md` SHA256 相同：`98575AF4C4067D6A…`） |
| 工作区 | 干净；除本方案文档外无未提交源码，无他人改动 |
| 版本 | `versionName 0.2.7` / `versionCode 9`（内部重构阶段不改） |

## 2. 工具链（实测版本）

| 组件 | 路径 / 版本 |
|---|---|
| JDK | `D:/codex-work/xbvr-android/toolchain/jdk/jdk-17.0.20.1+1`，Eclipse Adoptium 17.0.20.1+1 |
| Gradle | 8.13（`D:/codex-work/xbvr-android/toolchain/gradle/gradle-8.13`） |
| Android SDK | `D:/codex-work/xbvr-android/toolchain/sdk`，platforms `android-36` |
| Build Tools | 36.0.0（`apksigner` 实测使用） |
| Platform Tools | 同 SDK `platform-tools/adb.exe` |
| Python / Node | 方案包默认路径均存在，工具回归已实际运行 |

`local.properties` 指向参考目录 SDK，仅本机键值，不入库；未复制 22 GB 工具链。

## 3. 签名与基线产物

| 项 | 值 |
|---|---|
| 期望签名证书 SHA256 | `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4` |
| keytool 实测（`toolchain/debug.keystore`） | `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4` ✅ 一致 |
| keystore 文件 SHA256 | `2AED72C33AC4674428259A45932F57DA3CD3FDBAEDBCCF0F8FE5931C70FC9765` |
| 线上 0.2.7 APK 签名（对照） | `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`（与本地 keystore 同源，可覆盖安装） |
| 基线 APK | `app/build/outputs/apk/debug/app-debug.apk`，`de56ed1b02af8538962c70952bad0809467146ff0335008f6086393f16fca5f2` |

未重新生成、未替换签名私钥。

## 4. 基线构建与测试（真实结果）

命令：`Invoke-Checks.ps1 -Project <repo> -Batch T00 -ToolTests -InstrumentationBuild`

| 门槛 | 实测 |
|---|---|
| `assembleDebug` | PASS |
| JVM 测试 | **47**，失败 0 / 错误 0 / 跳过 0 |
| Android lint | **0 错误 / 20 警告** |
| APK 签名校验 | PASS（证书与基线一致） |
| 工具回归 Python | **20** 项 OK |
| 工具回归 Node | **17** 项 pass / 0 fail |
| 测试 APK 构建 | PASS（`assembleDebugAndroidTest`） |
| 证据目录 | `D:/codex-work/output/xbvr-refactor-evidence/T00-20261005-225347-e8ef0bfb` |

架构护栏基线（`verify_architecture.py --mode baseline`）：**16 文件，0 违规**。

## 5. 设备与合成环境

| 项 | 值 |
|---|---|
| 设备 | `emulator-5554`，AVD `XbvrUi36`，API 36，x86_64，1080×2400 |
| 另一可用 AVD | `XbvrQa`（API 29），本次未启动 |
| 合成服务 | `fixture/filter-fixture.cjs` 自检 PASS（22 identities、两种目录顺序、21 种投影/布局、OR/AND 元数据、REST404、wide 代理回退），监听 `127.0.0.1:18766` |
| 主机可达性 | `http://127.0.0.1:18766/deovr` → HTTP 200 |
| 应用内地址 | `http://10.0.2.2:18766` |
| 真机 | **无连接设备**（`adb devices` 仅模拟器）；真机陀螺仪/HDR 等未验证 |

## 6. 合成数据迁移前快照（仅合成，无真实凭据）

活动 profile id：`643ece43-2aa5-4a4a-82a5-f131e94c0da7`，base `http://10.0.2.2:18766`，服务器菜单仅此一条。

`shared_prefs/local.xml` 键（13 个，值已在证据目录留档）：

| 键 | 类型 / 值 |
|---|---|
| `profiles` | 加密 JSON（AndroidKeyStore alias `xbvr-profiles`，AES/GCM，`IV:ciphertext`），**合成 profile，无账号密码** |
| `active` | `643ece43-2aa5-4a4a-82a5-f131e94c0da7` |
| `fav:…/scene:2` | `true`（收藏） |
| `pos:…/file:1`、`pos:…/file:8`、`pos:…/scene:1`、`pos:…/scene:8` | long（续播进度） |
| `source:…/scene:1`、`source:…/scene:8` | 所选文件 URL |
| `view:…/file:1` | `override=true`，`kind=2,layout=1,capture=190`（手动等距鱼眼 190 + SBS） |
| `view:…/file:8` | `override=false`，`kind=1,layout=1,capture=360`（自动 360） |
| `coverMode:…` | `int = 2`（固定 3:2 裁切） |
| `coverAuto:…` | `float = 1.7777778`（自动推断比例缓存） |

目录缓存：`files/library-643ece43-2aa5-4a4a-82a5-f131e94c0da7.json`，12263 字节，
SHA256 `6b3baa989a08d3b4a2a6c4816fc6716d7c8ba6e368298f381aa900bb09be84d9`。

证据：`T00-device/prefs-synthetic-baseline.xml`、`library-cache-before.json`。

## 7. Q0–Q3 基线观察（旧实现，模拟器截图 + UI 树）

| ID | 操作 | 观察结果 |
|---|---|---|
| Q0 | 启动 | 进入媒体库；服务器菜单仅列 1 条合成服务器；`Open-source licenses` 离线渲染全部文档（README/表格/许可正文） |
| Q1 | All/收藏/继续观看 | All 22 项；`Favorites` 1 项；`Continue watching` 2 项；搜索框、Studio/Actor/Tags 筛选入口可用 |
| Q2 | 封面比例 | 默认 `Auto · Original image`；选择 `3:2` 后卡片按比例裁切，`coverMode` 按服务器持久化；`coverAuto` 记录推断比例 |
| Q3 | 播放与格式 | 播放器进入即播放；`Format`→`Projection and layout`→`190° equidistant fisheye`→`Side by side (SBS)` 写入 `view:` 且 `override=true`；状态行显示当前/检测格式 |

截图与 UI 树：`T00-device/q0-*.png|xml`、`q1-*.png|xml`、`q2-*.png|xml`、`q3-*.png|xml`。

## 8. 必须保留的兼容门槛（本重构的验收基线）

签名证书、`SharedPreferences`/Keystore 方案、配置字段与 active 回退、收藏/观看键、封面设置键、
视角 JSON 字段与 restore 局部更新顺序、目录缓存文件与 `_metadata`/`_retryAfter`、
scene/file 标识与代理前缀、玩家认证与代理 Basic 分离、跨源不转发、重定向上限与超时、
图片 16 MiB LruCache 与 12 MiB 响应上限、Main generation 与滚动恢复、Player 暂停意图与释放顺序。
逐项定义见方案 §4。

## 9. 未验证 / 遗留

- 真机（陀螺仪、HDR、长时间音画同步、真机覆盖安装）未验证；当前仅有模拟器。
- API 29 模拟器（`XbvrQa`）本次未启动，T23 需要时再跑。
- fixture 未覆盖多来源、字幕、服务器收藏写权限；这些要用 MockWebServer 或补充仅合成路线。
