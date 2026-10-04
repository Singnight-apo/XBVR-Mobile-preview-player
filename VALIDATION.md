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

