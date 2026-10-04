# 隐私说明 / Privacy

适用范围：本仓库当前 Android 客户端。最后更新：2026-10-04。
Scope: the Android client in this repository. Updated: 2026-10-04.

## 本机数据 / On-device data

服务器配置（包括地址、播放器账号密码及可选代理认证）使用 Android Keystore 中的 AES 密钥，以 AES-GCM 加密后存入应用私有偏好。并非所有本机数据都加密：媒体库目录与元数据缓存、当前服务器标识、收藏、续播进度、视角和显示偏好存放在应用私有文件或偏好中；缓存可能包含媒体地址。封面保存在内存缓存中。卸载或通过 Android 清除应用存储会删除应用本机数据；仅清除缓存不等于清除所有记录。

Server profiles, including addresses, player credentials and optional proxy credentials, are encrypted using AES-GCM with an Android Keystore key and stored in private preferences. Not all local data is encrypted: catalog and metadata caches, the active server identifier, favorites, resume positions, view settings and display preferences use private files or preferences. Catalog caches can contain media URLs. Covers use an in-memory cache. Uninstalling or clearing app storage in Android removes local app data; clearing the cache alone does not remove all records.

## 网络请求 / Network requests

客户端连接用户指定的 XBVR 服务器及服务器返回的媒体、封面、字幕等资源地址。相关资源可以位于第三方域名，接收方可能记录 IP 地址、请求时间和所请求资源。播放器凭据不跨服务器源转发，代理 Basic 认证仅附加到配置服务器的同源请求。HTTP 连接允许明文传输；HTTPS 使用正常证书验证，建议在可用时使用 HTTPS。

The client contacts the configured XBVR server and media, cover and subtitle URLs supplied by that server. Resources may use third-party domains whose operators can log IP addresses, request times and requested resources. Player credentials are not forwarded across server origins; proxy Basic authentication is only attached to requests matching the configured server origin. HTTP allows unencrypted transport; HTTPS uses normal certificate validation. Prefer HTTPS when available.

服务器支持并允许写入收藏时，用户选择“服务器收藏”会发送收藏状态更新；本地收藏和续播记录保存在本机。应用不提供删除视频、修改服务器设置或转码操作。服务器和资源提供方的数据处理规则由其运营者负责。

When the server supports and permits favorite updates, choosing the server-favorite action sends that update. Local favorites and resume records stay on the device. The app provides no video deletion, server-setting modification or transcoding actions. Server and resource operators are responsible for their own data handling.

## 权限、备份与统计 / Permissions, backups and telemetry

应用声明互联网和网络状态权限，可使用可选陀螺仪；未声明相机、麦克风、通讯录、位置或共享媒体存储读取权限。当前源码未集成分析统计、广告、崩溃上传 SDK 或开发者遥测接收端；网络访问不因此成为匿名访问。Android 清单关闭自动备份，数据提取规则排除云备份和设备迁移中的应用数据；这不保证所有厂商或第三方备份工具均遵守这些设置。

The app declares Internet and network-state permissions and optionally uses the gyroscope. It does not declare camera, microphone, contacts, location or shared-media-storage read permissions. The current source integrates no analytics, advertising, crash-upload SDK or developer telemetry endpoint; this does not make network requests anonymous. The manifest disables automatic backup and the extraction rules exclude app data from cloud backups and device transfers. These settings cannot guarantee the behavior of every vendor or third-party backup tool.

## 联系与核查依据 / Contact and evidence

问题可通过 [GitHub Issues](https://github.com/Singnight-apo/XBVR-Mobile-preview-player/issues) 反馈，请勿公开账号、密码、服务器地址或私人媒体信息。安全漏洞请遵循 [SECURITY.md](SECURITY.md)。本说明依据 `Store.java`、`Api.java`、`MainActivity.java`、`PlayerActivity.java`、Android 清单及数据提取规则整理；依赖或数据流改变时需要重新核查。

Use [GitHub Issues](https://github.com/Singnight-apo/XBVR-Mobile-preview-player/issues) for questions, without publishing credentials, server addresses or private media details. Follow [SECURITY.md](SECURITY.md) for vulnerabilities. This statement is based on the storage, networking, library and player code, manifest and extraction rules; changes to dependencies or data flows require a new review.
