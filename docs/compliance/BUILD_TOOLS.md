# Build and test dependency notices

核对日期 / Verification date: **2026-10-04**.

These notices cover the Gradle Wrapper that accompanies the source, plus JVM-test dependencies. Runtime Android/Media3/OkHttp dependencies and desugaring notices are maintained separately by the project. This document does not grant a new license to third-party software or replace its original license.

本说明涵盖随源码附带的 Gradle Wrapper 和 JVM 测试依赖。Android、Media3、OkHttp 等运行时组件及 desugaring 的许可另行维护。法律正文保留原文；本说明不替代原许可证，也不赋予第三方软件新的许可。

## What is distributed

The source package contains `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`, and the Wrapper configuration. The Wrapper downloads Gradle 8.13 during a normal source build. The project's local `toolchain/` directory, cached dependencies, Gradle binary distribution, JDK, Android SDK, and signing private key are excluded from the public source package. A downloaded build tool is not thereby an Android runtime dependency.

`app/build.gradle` declares JUnit 4.13.2 and JSON-java 20240303 under `testImplementation`. Hamcrest Core 1.3 is JUnit's test dependency. These are JVM-test components, not this client's production runtime dependencies. Their cached JARs are not shipped with the source package; notices are supplied so the build/test provenance remains reviewable. The separate instrumentation APK has a custom Android framework runner, rather than bundling JUnit as its runner.

The dependency-scope statements above describe the project's configuration and distribution policy. This materials audit does not itself inspect or certify the final release ZIP or APK; release packaging must still verify that excluded toolchains, caches, private keys, and test binaries are absent.

## Versioned originals

| Component | Version | Applicable license / attribution | Saved original |
| --- | --- | --- | --- |
| Gradle Wrapper | 8.13, upstream tag v8.13.0 | Apache License 2.0; upstream copyright/header excerpts retained | [Embedded license](../../licenses/build-and-test/Gradle-Wrapper-8.13-LICENSE.txt), [copyright excerpts](../../licenses/build-and-test/Gradle-Wrapper-COPYRIGHT.txt) |
| Gradle upstream/distribution | v8.13.0 / 8.13 | Complete upstream license and original distribution NOTICE retained as context | [LICENSE](../../licenses/build-and-test/Gradle-8.13.0-UPSTREAM-LICENSE.txt), [NOTICE](../../licenses/build-and-test/Gradle-8.13-DISTRIBUTION-NOTICE.txt) |
| JUnit | 4.13.2 | Eclipse Public License 1.0; original `JUnit` attribution retained | [LICENSE-junit.txt](../../licenses/build-and-test/JUnit-4.13.2-LICENSE.txt) |
| Hamcrest Core | 1.3 | BSD 3-Clause; original 2000–2006 www.hamcrest.org copyright and disclaimer retained | [LICENSE.txt](../../licenses/build-and-test/Hamcrest-1.3-LICENSE.txt) |
| JSON-java | 20240303 | Upstream declaration: Public Domain | [LICENSE](../../licenses/build-and-test/JSON-20240303-LICENSE.txt) |

Legal originals are complete and untranslated. JUnit's fixed license does not supply a separate named copyright/date statement, and JSON-java's fixed LICENSE is simply `Public Domain.`; this audit does not manufacture copyright attributions absent from those originals. Hamcrest's BSD copyright line is reproduced inside its license. Gradle source and distributed-script header excerpts identify where each copied copyright statement came from.

The complete Gradle upstream LICENSE includes notices for components of the full Gradle distribution. Its contents are preserved, but this does not mean every listed component is contained in the Wrapper JAR or the Android APK. The original Gradle NOTICE is explicitly a **distribution-wide** NOTICE. It came from the checksum-verified 8.13 distribution; it is not claimed to be a nonexistent root NOTICE file at tag v8.13.0.

## Wrapper binary identity

The actual source-tree JAR was hashed, rather than inferring its version from the download URL:

```text
gradle/wrapper/gradle-wrapper.jar
SHA-256: 81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f
```

This exactly matches the [official Gradle 8.13 Wrapper checksum](https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256). The saved Wrapper license was extracted directly from that JAR's `META-INF/LICENSE` without modifying the bytes.

The local `toolchain/gradle.zip`, used only to obtain the original distribution NOTICE, has SHA-256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`. This matches the [official Gradle 8.13 binary distribution checksum](https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256). Neither that archive nor the extracted full toolchain is added to the public source package by this notice collection.

JUnit and Hamcrest licenses also match the actual fixed-version JAR entries byte for byte. The cached JUnit 4.13.2, Hamcrest Core 1.3, and JSON-java 20240303 JARs match their fixed Maven Central SHA-1 records; their SHA-256 values are preserved for local reproducibility. This does not claim a cryptographic signature verification or a final-APK dependency audit.

## Sources and offline access

The [build-and-test source record](../../licenses/build-and-test/SOURCES.md) supplies fixed tag/artifact links and the extraction method. [sources.json](../../licenses/build-and-test/sources.json) records URLs, entry names, and hashes of the saved originals. The project's in-app offline license reader can display the bundled `.txt`, `.md`, and `.json` documents when the root `licenses/` tree is packaged under `assets/licenses/`.

All URLs in this record identify upstream reference material; the license bodies are also stored locally. No build, release upload, or runtime-dependency modification was performed for this audit.
