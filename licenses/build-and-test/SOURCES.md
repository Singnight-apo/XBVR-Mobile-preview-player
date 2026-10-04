# Build and test notices / 构建与测试材料

Verified on 2026-10-04. Original legal texts are retained without translation. This directory documents source-distributed Gradle Wrapper files and JVM-test dependencies; it does not change their licenses or the client's license.

2026-10-04 核对。法律原文保留原语言。本目录记录随源码分发的 Gradle Wrapper，以及 JVM 测试依赖；不改变这些组件或客户端的许可证。

| Component | Fixed version and scope | Original text |
| --- | --- | --- |
| Gradle Wrapper | 8.13; `gradle-wrapper.jar`, `gradlew`, and `gradlew.bat` accompany the source | [Wrapper's embedded Apache 2.0 license](Gradle-Wrapper-8.13-LICENSE.txt), [original copyright/header excerpts](Gradle-Wrapper-COPYRIGHT.txt) |
| Gradle upstream | `v8.13.0`; retain the complete upstream license, including its distribution-component notices | [Complete upstream LICENSE](Gradle-8.13.0-UPSTREAM-LICENSE.txt) |
| Gradle distribution NOTICE | Official 8.13 binary distribution; retained as contextual upstream notice, although the full distribution is not redistributed | [Original distribution NOTICE](Gradle-8.13-DISTRIBUTION-NOTICE.txt) |
| JUnit | `junit:junit:4.13.2`; JVM tests only; EPL 1.0 | [Original LICENSE-junit.txt](JUnit-4.13.2-LICENSE.txt) |
| Hamcrest Core | `org.hamcrest:hamcrest-core:1.3`; JUnit's JVM-test dependency; BSD 3-Clause | [Original LICENSE.txt](Hamcrest-1.3-LICENSE.txt) |
| JSON-java | `org.json:json:20240303`; JVM tests only; Public Domain | [Original LICENSE](JSON-20240303-LICENSE.txt) |

## Fixed sources and byte verification

- Gradle upstream source is pinned to [v8.13.0](https://github.com/gradle/gradle/tree/v8.13.0). The complete LICENSE came directly from [that tag](https://raw.githubusercontent.com/gradle/gradle/v8.13.0/LICENSE). The source/header excerpt comes from the tagged [GradleWrapperMain.java](https://raw.githubusercontent.com/gradle/gradle/v8.13.0/platforms/core-runtime/wrapper-main/src/main/java/org/gradle/wrapper/GradleWrapperMain.java). It is an identified excerpt, not an exhaustive list of every upstream source header.
- The distributed `gradle/wrapper/gradle-wrapper.jar` has SHA-256 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`, exactly matching the [official Gradle 8.13 Wrapper checksum](https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256). `Gradle-Wrapper-8.13-LICENSE.txt` was extracted unchanged from its `META-INF/LICENSE` entry. A configured distribution URL alone would not establish the JAR version; the checksum comparison does.
- There is no root `NOTICE` or `NOTICE.md` at the queried v8.13.0 raw paths. The saved NOTICE was extracted unchanged from `gradle-8.13/NOTICE` inside the official [8.13 binary distribution](https://services.gradle.org/distributions/gradle-8.13-bin.zip). The local archive's SHA-256 was `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`, matching the [official distribution checksum](https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256). This does not relabel the distribution-wide NOTICE as a Wrapper-only notice or assert that all software named there is bundled in the client.
- JUnit's original EPL 1.0 text was downloaded from the fixed [r4.13.2 tag](https://raw.githubusercontent.com/junit-team/junit4/r4.13.2/LICENSE-junit.txt) and matches `LICENSE-junit.txt` in the actual cached 4.13.2 JAR byte for byte. The license starts with the attribution `JUnit`; it supplies no separate named copyright/date line, so none has been invented here. The [tagged source](https://github.com/junit-team/junit4/tree/r4.13.2) remains available upstream.
- Hamcrest's original text was downloaded from the fixed [hamcrest-java-1.3 tag](https://raw.githubusercontent.com/hamcrest/JavaHamcrest/hamcrest-java-1.3/LICENSE.txt) and matches `LICENSE.txt` in the actual cached 1.3 JAR byte for byte. Its original notice is `Copyright (c) 2000-2006, www.hamcrest.org` followed by `All rights reserved.` Those exact notices are present in the saved full license. They have not been replaced with the different year range in Gradle's distribution-wide license list.
- JSON-java's [20240303 LICENSE](https://raw.githubusercontent.com/stleary/JSON-java/20240303/LICENSE) reads `Public Domain.` The entire 16-byte upstream file is preserved. No copyright holder/year or additional license grant has been invented. The [fixed source tag](https://github.com/stleary/JSON-java/tree/20240303) provides the source context.

The cached versioned JUnit, Hamcrest Core, and JSON-java JARs were compared with their fixed Maven Central `.jar.sha1` files. All matched; their SHA-256 values are also recorded in [sources.json](sources.json). SHA-1 is recorded because these artifacts publish it, not presented as a modern cryptographic signature. No cached test JAR or complete toolchain is added to the source distribution by this task.

See [BUILD_TOOLS.md](../../docs/compliance/BUILD_TOOLS.md) for distribution scope. [sources.json](sources.json) records original source URLs, extracted entries, and saved-file SHA-256 values; the legal text files themselves contain the full original texts, not a summary.
