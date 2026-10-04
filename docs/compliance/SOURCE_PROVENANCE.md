# Source provenance / 源码来源核查

核查日期：2026-10-04。范围：Android 客户端的 XBVR 协议实现及已知参考来源。本文件是技术来源记录，不是完整版权审计或零侵权保证，也不授予第三方代码、内容或商标的使用权。

**English summary:** The reviewed Java client consumes XBVR interfaces rather than implementing its Go server. API schemas and the Web cover-proxy strategy informed the implementation. Local Go reference files are verbatim upstream copies and must remain outside the public client distribution. The historical fisheye userscript could not be located, so its authorship, terms, and possible expression-level influence remain unresolved. No claim of a complete clean-room implementation or zero copying is made.

## 检查对象和固定来源

本次读取了客户端 `app/src/main/java/top/liuwei/xbvr/Protocol.java`、`Api.java`，并核对下表的公开上游文件。为避免移动分支造成来源漂移，使用 XBVR 仓库页面中读取的固定提交 **`dc8c9f04396cc9e6dde1b0f4600713673c3586ce`**。该提交是此次复核基准，不代表开发时已经保存了相同提交号，也不代表各文件最初创建时的提交。

| 上游对象 | 此次核对内容 | 固定来源 |
| --- | --- | --- |
| `pkg/api/deovr.go` | DeoVR 目录、详情和播放器认证的响应结构 | [固定提交源码](https://github.com/xbapps/xbvr/blob/dc8c9f04396cc9e6dde1b0f4600713673c3586ce/pkg/api/deovr.go) |
| `pkg/api/heresphere.go` | HereSphere 详情、媒体源、时间标签、权限与更新请求字段 | [固定提交源码](https://github.com/xbapps/xbvr/blob/dc8c9f04396cc9e6dde1b0f4600713673c3586ce/pkg/api/heresphere.go) |
| `pkg/api/dms.go` | `/api/dms/file/…` 媒体路由 | [固定提交源码](https://github.com/xbapps/xbvr/blob/dc8c9f04396cc9e6dde1b0f4600713673c3586ce/pkg/api/dms.go) |
| `pkg/api/scenes.go` | 场景列表查询接口和响应 | [固定提交源码](https://github.com/xbapps/xbvr/blob/dc8c9f04396cc9e6dde1b0f4600713673c3586ce/pkg/api/scenes.go) |
| `pkg/api/files.go` | 文件详情查询接口 | [固定提交源码](https://github.com/xbapps/xbvr/blob/dc8c9f04396cc9e6dde1b0f4600713673c3586ce/pkg/api/files.go) |
| `pkg/models/model_scene.go` | 场景元数据、图片表示及列表查询参数 | [固定提交源码](https://github.com/xbapps/xbvr/blob/dc8c9f04396cc9e6dde1b0f4600713673c3586ce/pkg/models/model_scene.go) |
| `ui/src/views/scenes/SceneCard.vue` | `getImageURL` 的封面代理选择策略，212–219 行 | [固定提交源码](https://github.com/xbapps/xbvr/blob/dc8c9f04396cc9e6dde1b0f4600713673c3586ce/ui/src/views/scenes/SceneCard.vue#L212) |

客户端核查快照的 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| `Protocol.java` | `bb73e42cbddb6fceb89cef6669526cabcbcc09a64da95df283fb3351a930a52d` |
| `Api.java` | `b0a76d4b1d26ab78b4e1423febac10853ff5a108c3b22cc89fd7986b008777e6` |

任何后续代码修改均需重新确定快照；这些哈希不覆盖整个客户端、APK 或第三方依赖。

## 核对方法与实际实现

1. 阅读 Java 的请求、解析和模型转换路径，与上游 Go 路由、认证过滤器及响应生成路径逐项对照。
2. 对六份上游 Go 文件和两份 Java 文件逐行去除首尾空白，检查超过 35 字符的完全相同整行；没有发现匹配。此方法只捕获一部分直接文本复制，不能检出变量改名、跨语言改写、短表达或算法结构的借用。
3. 比较本地 Go 参考快照与固定提交的原文哈希，确认参考文件本身是否为完整上游副本。
4. 在可访问的本机工作区按用户脚本文件名查找，并检索其历史引用。没有读取私有媒体库或把交接文件、用户数据纳入本文。

`Protocol.java` 遍历服务器 JSON，生成客户端 `Entry`、`Detail`、`Source` 等模型，处理秒／毫秒转换、媒体 URL、稳定对象标识、分类内顺序和元数据合并。它没有实现 Go 服务端的路由注册、数据库查询、密码哈希核验、响应实体构造或媒体文件发送。相同的路径、JSON 键和字段含义是明确参考的接口兼容信息；本次没有识别出把对应 Go 函数主体逐段移植进这份 Java 文件的证据。

`Api.java` 通过 OkHttp 发起请求，处理客户端 Cookie、同源认证、受控重定向、元数据缓存及有限并发回退。上游实现则接收请求并生成响应。客户端对 `authorized`／`access` 的检查、DeoVR 与 HereSphere 的不同账户字段、媒体源与收藏权限字段，均依赖上游接口约定。客户端没有复制上游的 Go HTTP 服务或服务器端收藏更新、数据库、文件发送实现。本次结论限于已阅读文件和上述对照方法，不是完整开发历史的证明。

## 已确认的借鉴与完整副本

**API 数据格式。** 目录和详情字段、请求路径、时间单位及应用级认证状态来源于 XBVR 接口实现。场景、文件和元数据查询接口也是此客户端互操作设计的依据。本文承认这些接口参考；没有将其描述为客户端独创的协议。

**封面代理策略。** `Protocol.addPoster` 参考了 XBVR Web `SceneCard.getImageURL`：绝对 HTTP 地址可通过服务器的 `/img/700x/` 路径取图。Java 版本使用 URI 解析和有序候选集合，增加已代理地址防嵌套、原图回退及身份检查；未直接粘贴 Vue 函数，也没有原样实现其 `encodeURI`／`decodeURI` 表达。此处属于可确认的行为策略借鉴，不能隐去上游来源。

**本地参考副本。** 本机 `references/deovr.go` 和 `references/heresphere.go` 的 SHA-256 与上述固定提交全文一致：

| 参考文件 | SHA-256 |
| --- | --- |
| `deovr.go` | `e965f8c956880bfae116f9c7cf9a2c4f078992d2b634d8931ee59c3010190f08` |
| `heresphere.go` | `f70318ef63595f332011430bcb55bae16f68c3dbd07f2a99e7b1e9d07c5c103e` |

它们是第三方完整源码副本，并非本项目原创文件。应继续排除在公开客户端源码包中，用固定上游链接提供来源。本次没有从 XBVR 根目录列表或所检查文件头确认可用于再分发这些副本的明确许可证。未看到许可证不等于取得复制、改编或再授权许可；若拟分发这些参考文件，应先确认实际权利依据。本次没有认定副本的本地参考使用构成未经授权侵权。

## 鱼眼用户脚本及其他历史参考

本机历史交接记录提到 `xbvr-fisheye-player.user.js` **1.0.0**，以及 Video.js **7.21.7**、videojs-vr **3.3.0**。记录描述了 13 种参考模式、通用等距鱼眼、190° 取 95° 半视角、触摸和重置、切换时保留进度／暂停等行为。

本次未在可访问工作区找到该用户脚本原文件，也没有确定其公开仓库、作者、许可证、源码哈希或提交。因此只能确认曾参考其模式名称和行为要求，无法将当前 Java／GLSL 与其原文比较，更不能断言没有复制或改写过其具体表达。13 种模式作为功能基线与通用投影概念的参考来源应保留说明；对原脚本的权利状态仍需补证。

历史记录另列 [videojs-vr 上游](https://github.com/blaineam/videojs-vr)、[DeoVR 文档](https://deovr.com/documentation)和 [Media3 支持格式文档](https://developer.android.com/media/media3/exoplayer/supported-formats)。本次没有完成这些项目与全部客户端渲染代码的表达级比对。引用名称、投影数学或功能相似本身不足以证明复制；也不足以证明当前实现完全独立。Media3、OkHttp 等实际依赖的版权和许可证应另按依赖版本记录，不能用本文件替代依赖合规检查。

## 结论边界与后续处理

本次未在检查的 `Protocol.java`／`Api.java` 与指定 XBVR Go 文件之间识别到需要立即撤下的具体复制片段；同时确认了 API 结构和图片代理行为的参考，以及两个本地完整上游参考副本。没有证据据此宣布整个项目已完成版权清理，或保证零复制、零侵权。

- 不把本地 Go 参考副本、历史交接文件、未确认来源的用户脚本、用户媒体或凭据作为客户端原创材料公开。
- 若后续拟纳入任何第三方具体源码、着色器、截图或媒体，应先核对其来源和许可；来源或授权不清的复制材料应暂停纳入和再分发。本次未发现并认定某个运行代码片段属于未经授权复制。
- 若取得用户脚本原文件，应补充作者、来源 URL、版本／提交、全文哈希和许可，再逐项比对 `Projection`、`RenderMath`、`VrView` 等实现；必要时保留通知、取得授权或替换相关表达。
- 项目自己的许可证范围只能覆盖其有权授权的表达，不能替第三方代码、服务器内容或商标授权。未来完整审查仍需覆盖渲染、UI、图标、测试、构建工具、依赖和分发材料。

本次仅新增此来源文档；没有修改运行代码、执行构建、联系上游或上传仓库。
