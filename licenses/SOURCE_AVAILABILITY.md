# desugar source availability / 脱糖库源码提供

APK 0.2.5 uses com.android.tools:desugar_jdk_libs:2.1.5 via core-library desugaring. Its own GPLv2 terms and Classpath Exception remain applicable. No local changes were made to the upstream library sources. D8 performs Android bytecode conversion according to the project's Gradle configuration; the app's independent code retains its own license.

本版本随 APK 同一 Release 提供完整未修改的上游发布源码快照（含 jdk11/jdk/src、Bazel BUILD/WORKSPACE、repos.bzl、setup.bzl、选择器及 Maven 打包脚本），不以普通项目首页链接替代源码交付。独立客户端源码和 Gradle 配置在同一 Release 的客户端源码包中。

- Release source asset: [desugar_jdk_libs-2.1.5-source.zip](https://github.com/Singnight-apo/XBVR-Mobile-preview-player/releases/download/v0.2.5/desugar_jdk_libs-2.1.5-source.zip)
- SHA-256: `cf0b48cb046b6517ed8e2a816c125116a907e13e48e341882ba6f4016a155d9f`
- Original archive: https://codeload.github.com/google/desugar_jdk_libs/zip/73170c345e6a762fc6a1f0301bb15218850023ef
- Fixed upstream commit: https://github.com/google/desugar_jdk_libs/tree/73170c345e6a762fc6a1f0301bb15218850023ef
- Commit message: Prepare for jdk-11 based desugared library version 2.1.5.
- VERSION_JDK11.txt in the archive declares 2.1.5.
- Binary and configuration artifact hashes: inventory.json.
- License originals: desugar/LICENSE.txt, desugar/ADDITIONAL_LICENSE_INFO.txt, desugar/ASSEMBLY_EXCEPTION.txt; retained copyright headers: desugar/SOURCE_NOTICES.txt.

Upstream build entry: `bazel build //:maven_release_jdk11` (with its required Bazel/Android SDK and external dependencies described in WORKSPACE, repos.bzl and setup.bzl). The provided archive contains the upstream repository source and build scripts; those scripts describe external build inputs. It is not a claim that this project has rebuilt Google's published jar byte-for-byte. The archive is the pinned release-preparation source, not a Maven sources.jar (Google Maven did not publish one for this coordinate).

此交付固定版本源码与构建描述，尚未进行 Google 制品的逐字节复现审计。源码提供不修改库的许可，Classpath Exception 也不表示整个 APK 的所有第三方部分都变为 Apache。再分发本 APK 时，保留这些材料，并同步提供上述源码和客户端构建配置，避免只复制 APK 而遗失源码提供方式。
