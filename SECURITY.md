# 安全反馈 / Security

最后更新 / Updated: 2026-10-04.

本项目处于预览阶段。维护优先面向最新公开源码和版本；未承诺旧版补丁、响应时限或漏洞奖励。当前 APK 使用历史开发签名以保持覆盖更新兼容，尚未配置商店发布签名。更新必须保持相同签名身份；请勿为方便发布而公开签名密钥。

This project is in preview. Maintenance focuses on the latest public source and release; no legacy patch policy, response deadline or bounty is promised. The current APK retains the historical development signing identity for update compatibility; store-release signing is not configured. Updates require the same signing identity. Never publish signing private keys.

## 报告漏洞 / Reporting vulnerabilities

优先查看仓库 [Security Advisories](https://github.com/Singnight-apo/XBVR-Mobile-preview-player/security/advisories) 是否提供 **Report a vulnerability** 私密报告入口。仅当该入口可用时提交私密报告。如果入口未启用，可在 [Issues](https://github.com/Singnight-apo/XBVR-Mobile-preview-player/issues) 请求建立私密沟通渠道，但不要公开利用步骤、凭据或私人数据。本文件不代表已经启用了 GitHub 私密报告功能。

Check [Security Advisories](https://github.com/Singnight-apo/XBVR-Mobile-preview-player/security/advisories) for a **Report a vulnerability** private-reporting option. Use it only if available. If it is unavailable, request a private communication channel through [Issues](https://github.com/Singnight-apo/XBVR-Mobile-preview-player/issues), without publishing exploit instructions, credentials or private data. This document does not claim that GitHub private reporting has been enabled.

报告请包含版本、Android 版本、影响范围、最小复现步骤及脱敏日志。普通播放或界面问题可直接提交 Issue。请只测试自己拥有或获得授权的设备和服务器。

Include the app version, Android version, impact, minimal reproduction steps and sanitized logs. Ordinary playback or UI issues can be filed directly as issues. Test only devices and servers you own or are authorized to assess.

## 维护注意事项 / Maintainer practices

密钥仅保存在被忽略的私有目录，提交前使用 `git check-ignore` 和检查待提交文件。`.gitignore` 不会保护已跟踪文件或阻止强制添加；忽略规则也不能证明历史没有泄露。公共证书可能使用 `.pem` 后缀，因此不按该后缀一概忽略；私钥应放在 `private-keys/` 并使用 `.key` 等明确文件名。不要把私服输入、工具链、缓存或日志打包发布。

Keep keys in ignored private directories; use `git check-ignore` and inspect staged files before committing. Ignore rules do not protect already tracked files or prevent forced additions, and do not prove that history is leak-free. Public certificates may use `.pem`, so that suffix is not universally ignored; keep private keys in `private-keys/` with explicit names such as `.key`. Do not publish private-server inputs, toolchains, caches or logs.
