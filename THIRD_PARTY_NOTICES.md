# Third-party notices / 第三方通知

Project-authored materials use Apache-2.0 within [LICENSE_SCOPE.md](licenses/LICENSE_SCOPE.md). Third-party originals are preserved in [licenses/](licenses/README.md), the same directory copied into APK assets for offline reading. [inventory.json](licenses/inventory.json) records exact resolved artifact coordinates, scopes, SHA-256 hashes, fixed-version Maven POM/source evidence and local notice filenames.

本次核对 36 个运行时/脱糖输入制品，包括 Media3、AndroidX、OkHttp、Okio、Kotlin、Guava、JetBrains annotations 与 desugar；这不是声称每个输入类都会进入最终 DEX。Guava listenablefuture 是避免依赖冲突的空 jar；desugar configuration 是带 BSD-3-Clause 正文的构建输入。许可证按实际制品记录，不把测试依赖列成 APK 运行时。

- Runtime executable libraries except desugar: Apache-2.0, with version-specific original notices in licenses/runtime/. OkHttp’s bundled Public Suffix List data separately uses MPL-2.0 (licenses/publicsuffix/).
- desugar_jdk_libs 2.1.5: GPLv2 with Classpath Exception, additional original terms and source headers in licenses/desugar/. [Complete release-source distribution](licenses/SOURCE_AVAILABILITY.md) accompanies the APK.
- desugar_jdk_libs_configuration 2.1.5: BSD-3-Clause; original R8 authors' license retained in licenses/runtime/.
- Test-only: JUnit 4.13.2 (EPL-1.0), Hamcrest 1.3 (BSD-3-Clause), JSON-java 20240303 (Public Domain), MockWebServer 4.12.0 (Apache-2.0 / same OkHttp source notices). See licenses/build-and-test/ and docs/compliance/BUILD_TOOLS.md.
- Redistributed build component: Gradle Wrapper 8.13, Apache-2.0 and applicable originals under licenses/build-and-test/. SDK, JDK, Gradle distribution, caches and signing private key are excluded.

Playback uses [AndroidX Media3 / ExoPlayer](https://github.com/androidx/media) 1.8.0. Thanks to its maintainers and contributors. This independent client connects to [XBVR](https://github.com/xbapps/xbvr); API formats and the cover-proxy strategy were referenced. It does not redistribute the XBVR server. No MIT license is assigned to XBVR. Specific development-source evidence and unresolved fish-eye-script provenance are recorded in [SOURCE_PROVENANCE.md](docs/compliance/SOURCE_PROVENANCE.md).

感谢 XBVR、Media3 和依赖维护者。致谢不构成第三方授权。原始法律正文保持原文，中文说明不能替代正文。没有作出整个项目零侵权或已完全清理全部历史来源的保证。

OkHttp includes Public Suffix List data under MPL-2.0. Its full terms, unmodified preferred-form historical list and generator are provided in [licenses/publicsuffix/](licenses/publicsuffix/README.md). 数据源码已与压缩资源逐字节核对；该数据不适用 Apache-2.0。
