# 0.2.4 验收记录 / Validation

日期：2026-10-04。包名 top.liuwei.xbvr，versionCode 6、versionName 0.2.4，最低 API29、目标 API36。

新增中文与英文界面资源，使用 Android 系统语言匹配；非中文语言回退到英文。Android13及以上可通过系统应用语言设置单独选择。片名、厂商、演员及标签保留服务器原文。媒体库、播放器、设置、诊断与常见连接错误已本地化，切换语言保留筛选条件。

## 构建和签名 / Build and signing

- assembleDebug、testDebugUnitTest、lintDebug：BUILD SUCCESSFUL in 30s，日志 validation/bilingual-build.log。
- 47 项单元测试，0 失败、0 错误。lint 0 错误、16 警告，包含 localeConfig 仅 API33 生效和英文数量文本建议使用 plurals 等提醒。
- apksigner 验证通过。证书 SHA256：20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4，与此前版本一致。
- APK SHA256：03822a847d0fcd48f65e9cd280ef25d51543526ebeaa66e39c6f880dfa0b36ef。

## 模拟器检查 / Emulator verification

最终安装包进行中文播放器 26 项、单行筛选 9 项、语言匹配 17 项检查。证据分别保存在 validation/player-ui-qa、toolbar-qa、locale-qa；源码发布压缩包包含结果、截图、构建与单元测试报告。GitHub 源码仓库提供界面截图及此摘要。

语言流程覆盖英文系统默认、中文应用语言、语言切换保留筛选、英文搜索与筛选、陀螺仪状态、格式与眼别菜单、手机横竖屏、320dp/fontScale1.3、平板及法语回退英文。末尾清空应用语言覆盖以恢复跟随系统，并核对已安装 APK 哈希与崩溃缓冲。

播放器保留格式、文件、陀螺仪和重置四个主动作，眼别位于格式面板。四个主按钮均为纯图标，长按有中英文提示；陀螺仪关闭白色、实际开启黄色，使用截图像素检查两种状态；中英文截图均人工复核。遮罩在合成视频同一暂停帧显示/隐藏时采样顶部与底部物理边缘，验证背景铺满窗口。单行筛选支持横向滚动、独立取消及全部清除。

## 范围和边界 / Scope and limits

仅使用一个 Android16/API36 模拟器 emulator-5582，SwiftShader。手机1080×2400、420dpi；窄屏960×2080、480dpi、字号1.3；平板2560×1600及1600×2560、240dpi。测试结束恢复手机尺寸、密度及字号。平板截图是同一模拟器调整显示规格后的实拍，尚未测试实体平板。

本次使用本机22个合成场景，无真实用户媒体或服务器配置。未连接用户小米17 Pro Max；模拟器传感器状态与数学测试不等同真机手感，原先退出桌面仍缺真机崩溃日志，个别真实封面加载还需实际资源复核。

Build, signing and 47 unit tests passed. The final APK undergoes 52 emulator checks across Chinese player/filter flows and system-language matching. Other locales fall back to English. Screenshots use synthetic media only. Physical Xiaomi testing, actual sensor feel and rare real-server cover failures remain outside these emulator results.

源码排除签名私钥、toolchain、local.properties、HANDOFF和私有配置。重建需自备工具链及签名；发布安装包沿用原有开发签名。项目尚未选择自身源码许可证，依赖声明见 THIRD_PARTY_NOTICES.md。
