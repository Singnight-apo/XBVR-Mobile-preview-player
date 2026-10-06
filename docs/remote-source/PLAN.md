# 远程文件源（WebDAV）实施计划 / Remote file source (WebDAV) plan

分支 `feat/webdav-remote`，基线 `b23eb03`（= 已发布的 0.2.8 / versionCode 11）。
本文档是需求盘问（30 项决策）后的**共同理解基线**；任何实施细节与本文冲突时，以本文为准，改本文需单独提交说明原因。

## 1. 目标与边界

让应用能直接访问用户自己的 WebDAV 共享并播放其中的视频文件 —— 用于播放 **XBVR 刮削不到的裸文件**。

**已确认的前提**（用户原话归纳）：XBVR 主机**能**挂载该共享，但**没必要**，因为没有刮削信息。因此本功能是**独立内容源**，不是 XBVR 的扩展。

**明确不做**：外挂字幕、缩略图、NFO 元数据、搜索、面包屑、SMB/FTP（仅预留抽象口）、本地缓存/先下载后播。

## 2. 锁定的决策（30 项）

| # | 决策 | 结论 |
|---|---|---|
| Q1 | 功能成立性 | 成立：目标是 XBVR 未刮削的裸文件 |
| Q2 | 内容模型 | **独立「网络位置」内容源**（不与 XBVR 场景混用） |
| Q3 | 协议范围 | **仅 WebDAV** |
| Q4 | 用途 | 自用 + 可公开发布 → 只接受 Apache-2.0/MIT 依赖 |
| Q5 | 目标环境 | 通用 NAS；http 与 https 都要 |
| Q6 | 播放模型 | **边播边取**（流式随机访问） |
| Q7 | 凭据存储 | 与现有服务器配置同等待遇 |
| Q8 | 浏览范围 | 只要目录 + 播放（**不做字幕**） |
| Q9 | 播放器接入 | 复用 `PlayerActivity`，新增「直连来源」分支（不新写播放器） |
| Q10 | 无 XBVR 可用性 | 独立可用；首启给两个入口，默认入口仍是 XBVR |
| Q11 | 认证/证书 | 匿名 + Basic；自签名**按位置开关、默认关**（不做全局信任） |
| Q12 | 地址形态 | **拆三段** host/port/path + http/https 切换；可保存多个位置 |
| Q13 | 列表规则 | 只列视频扩展名；目录优先；单层进入 + 返回上级；失败分类中文提示 |
| Q14 | 续播 | 要续播；**不进**媒体库「继续观看」标签 |
| Q15 | 抽象层次 | 抽**薄端口**，为后续 SMB/FTP 留口 |
| Q16 | 验收 | 模拟器 + 自建 WebDAV 服务 + **用户真机连 NAS** |
| Q17 | 排序 | 名称升序（默认）/ 名称降序 / 修改时间倒序；**目录永远置顶**；**全局记住** |
| Q18 | 播放器取舍 | 保留：投影与格式按文件名推断、手动投影镜头、眼别、半幅、速度、轨道选择、陀螺仪、诊断。去掉：文件/章节菜单、**收藏按钮**、服务器入口。标题 = 文件名 |
| Q19 | 入口与状态 | 现有服务器菜单加「网络位置」；与 XBVR 状态**完全独立** |
| Q20 | 浏览界面 | **新建简单列表界面**（不用海报网格） |
| Q21 | 路径探测 | **显式「自动探测」按钮**，依次试 `/`、`/dav`、`/webdav`、`/remote.php/dav` |
| Q22 | Range 降级 | 启动探测；不支持则照常播放 + 提示不可拖动 + 进度条不可拖 |
| Q23 | 存储位置 | **独立加密存储**，不改现有 `profiles` 格式 |
| Q24 | 续播键 | **位置 ID + 相对路径**（改端口/路径/scheme 不丢进度） |
| Q25 | 提交粒度 | 6 阶段，每阶段独立提交 + 门禁 |
| Q26 | 验收矩阵 | 见 §6（12 项） |
| Q27 | 版本与发布 | **0.2.9 / versionCode 12**；真机验收通过后发布；沿用中英分节说明 |
| Q28 | 测试服务 | 进仓库 `tools/`；自签名证书**运行时生成、绝不入库** |
| Q29 | 状态记忆 | 记住上次位置 + 该位置最后目录 + 排序；默认选中上次使用的位置 |
| Q30 | 超时与重试 | 复用现有超时；列目录**不自动重试**，给手动「重试」 |

## 3. 技术方案

### 3.1 依赖：零新增

- `OkHttp 4.12.0`（已在）：`PROPFIND` 是任意 HTTP 方法，OkHttp 原生支持
- `media3-datasource-okhttp`（已在）：`AppServices.mediaDataSourceFactory()` 即 `OkHttpDataSource.Factory`
- Android 内置 `XmlPullParser`：解析 `multistatus` 响应
- 复用 `ProfileStore` 的 AndroidKeyStore + `AES/GCM/NoPadding` 加密模式

→ APK 体积几乎不涨；无 LGPL 等许可负担；F-Droid 合规零风险。

### 3.2 分层落地（沿用四层架构）

- **domain/**：`RemoteLocation`（id/name/host/port/path/secure/user/password/allowSelfSigned）、`RemoteEntry`（name/isDirectory/path/size/modified）、`RemoteFileRepository`（薄端口：`list` / 播放地址）、`RemoteLocationRepository`（增删改查 + 当前选中）、`RemoteError`（`AUTH` / `TIMEOUT` / `NOT_WEBDAV` / `NOT_FOUND` / `PERMISSION` / `RANGE_UNSUPPORTED` / `IO`）
- **data/**：`RemoteLocationStore`（独立加密 blob，不碰 `profiles`）、`WebDavClient`（PROPFIND depth=1、Basic、自签名策略、路径探测、Range 探测）、`WebDavXml`（标准 RFC 4918 属性解析）
- **ui/remote/**：位置管理界面、目录浏览界面、`RemoteBrowserController`（纯 Java，可 JVM 测试）
- **ui/player/**：`PlayerActivity` 直连分支；`PlaybackController` 支持「无 `Detail`」路径
- **media/**：为远程位置构造带认证与自签名策略的 `DataSource.Factory`（**绝不全局信任证书**）

### 3.3 `Detail` 合成形状

单个 `Source`：`name` = `filename` = 文件名，`url` = WebDAV 文件地址；`projection`/`stereo` 留空，交给现有 `FormatInference` 按文件名推断（`SBS`/`TB`/`fisheye` 仍可自动识别）；`Detail.title` = 文件名。直连模式下显式隐藏文件/章节菜单、收藏按钮与服务器入口。

## 4. 实施阶段（每阶段独立提交 + 门禁）

1. `test(remote): add a minimal WebDAV fixture` — Node 最小 WebDAV（PROPFIND/GET/Range/Basic，仅内置模块）+ 自签名证书生成脚本（PowerShell，运行时生成）+ 工具回归
2. `feat(domain,data): add the WebDAV remote file client` — 薄端口 + 客户端 + XML 解析 + 错误分类 + JVM 测试
3. `feat(data,ui): store and edit remote locations` — 独立加密存储 + 增删改 UI + http/https 切换 + 自签名开关 + 自动探测
4. `feat(ui): browse WebDAV directories` — 列表、排序切换、单层导航、错误分类、手动重试、状态记忆
5. `feat(ui,media): play remote files directly` — 播放器直连分支 + 续播
6. `docs: record remote-source validation` — 文档 + 版本号 0.2.9 / code 12

## 5. 风险与未覆盖

- ⚠️ **`PlaybackController` 支持「无 `Detail`」的接缝**：基于代码结构判断可行（`open()` → `media.detail(url)` 是唯一入口），但第 5 阶段一上手即验证。若必须大改，**停下来报告**，不硬改。
- ⚠️ 各 NAS 的 `PROPFIND` XML 命名空间/属性存在差异：按 RFC 4918 标准属性解析，遇到差异再补兼容。
- ⚠️ **http + Basic 为明文传输凭据**（仅链路层），UI 需明确提示。
- ⚠️ 未验证（待真机）：用户 NAS 是否支持 Range；`https://nas:5007/` 与 `https://nas:5007/dav` 两种形态能否连通。
- 边播边取无本地缓存，成片质量取决于网络；无降级到「先下载」的路径（后续可选）。

## 6. 验收矩阵（12 项）

1. 匿名认证 2. Basic 认证 3. http 4. https 5. 自签名关=拒绝且提示可读 / 开=连通
6. 多级子目录 / 空目录 / 无权限目录各自提示正确 7. 播放并 seek 到中段（Range 生效）
8. Range 禁用时：正常播放 + 提示不可拖动 + 进度条不可拖 9. 1GB+ 大文件中段起播
10. 续播（看一半退出→重进续上；**改地址后进度仍在**）11. 失败分类提示（认证失败/超时/非有效 WebDAV/404）12. 排序切换与记忆生效
13. 竖屏 / 横屏 / 平板不崩 14. **用户真机连 NAS 验收一次**

## 7. 交付与分支模型

- 开发在 `feat/webdav-remote`；推送到 GitHub（`ci.yml` 的 `on: push` 不限分支，会**自动在分支上跑 CI**）
- 6 阶段完成后 → 用户真机验收 → `git merge --ff-only` 合回 `main` → 打 `v0.2.9` → 按既有流程建 Release
- `main` 在合并前始终保持为已发布的 0.2.8，发布产物溯源清晰
