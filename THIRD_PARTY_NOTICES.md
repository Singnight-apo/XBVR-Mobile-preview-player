# Third-party notices / 第三方说明

This independent Android client connects to [XBVR](https://github.com/xbapps/xbvr). XBVR remains a separate server project; this repository does not redistribute its server source or media library.

本项目是连接 XBVR 的独立 Android 客户端，不分发 XBVR 服务端源码或用户的媒体库。

The playback engine is Google AndroidX Media3 / ExoPlayer 1.8.0. We thank its maintainers and contributors for decoding, playback and track-selection components. VR projection and view controls use this client's OpenGL ES renderer.

播放器内核使用 Google AndroidX Media3 / ExoPlayer 1.8.0，感谢其维护者和贡献者提供解码、播放与轨道选择组件；VR 投影和视角控制使用本客户端的 OpenGL ES 渲染器。

Dependencies retain their original copyright and license terms. The following links identify the projects used by the preview build; they do not assign a license to this client.

依赖保留各自版权及许可证；以下链接用于说明预览版使用的组件，不代表为本客户端指定开源许可证。

| Component / 组件 | Version / 版本 | Upstream terms / 原许可证 |
| --- | --- | --- |
| AndroidX Media3 | 1.8.0 | [Apache-2.0](https://github.com/androidx/media/blob/release/LICENSE) |
| OkHttp | 4.12.0 | [Apache-2.0](https://github.com/square/okhttp/blob/master/LICENSE.txt) |
| Core library desugaring | 2.1.5 | [desugar_jdk_libs license](https://github.com/google/desugar_jdk_libs/blob/master/LICENSE) |
| Gradle Wrapper | 8.13 | [Gradle license](https://github.com/gradle/gradle/blob/master/LICENSE) |
| JUnit (tests only) | 4.13.2 | [JUnit terms](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt) |
| MockWebServer (tests only) | 4.12.0 | [OkHttp terms](https://github.com/square/okhttp/blob/master/LICENSE.txt) |
| JSON-java (tests only) | 20240303 | [JSON-java license](https://github.com/stleary/JSON-java/blob/master/LICENSE) |

Android SDK, JDK, build caches and the signing private key are not included in this repository. / 仓库不包含 Android SDK、JDK、构建缓存或签名私钥。

