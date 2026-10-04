# Dependency audit / 依赖核查

Build reviewed: 0.2.5, versionCode 7, Android minSdk 29 / targetSdk 36. Runtime resolution was collected from Gradle debugRuntimeClasspath; coreLibraryDesugaring and debugUnitTestRuntimeClasspath were collected separately. The local input file contains absolute cache paths and is excluded from distribution.

公开的 [inventory.json](../../licenses/inventory.json) 只保存制品坐标、版本、scope、SHA-256、Maven POM/对应版本源码证据及通知文件。核对了 36 个运行时/脱糖输入制品，不把整个工具链缓存当作 APK 依赖。

## License findings

- Media3 1.8.0, AndroidX dependencies, OkHttp 4.12.0 executable code, Okio 3.6.0, Kotlin 1.9.10, Guava 33.3.1-android / failureaccess 1.0.2 and JetBrains annotations 13.0: Apache-2.0. Original headers/notices were collected from the resolved binary archives and exact-version Maven source jars. Guava's child POM lacks its own license section; versioned source headers supply evidence. The listenablefuture conflict-avoidance jar is empty; Kotlin JDK7/JDK8 compatibility artifacts are merged/empty shims. They are recorded as inputs, not claimed to add independent runtime classes.
- OkHttp's publicsuffixes.gz: MPL-2.0. The original binary NOTICE was retained. The historic preferred-form public_suffix_list.dat is supplied as licenses/publicsuffix/public_suffix_list.txt along with the full MPL text and original generator. Its generated rule/exception payload matches the published gzip's decompressed bytes exactly. This is separate from the Apache-covered OkHttp code.
- desugar_jdk_libs 2.1.5: GPLv2 with Classpath Exception, not Apache. Full upstream release-preparation source and build scripts are supplied alongside the APK; see [SOURCE_AVAILABILITY.md](../../licenses/SOURCE_AVAILABILITY.md). Classpath Exception is reproduced with its per-file applicability language. Additional OpenJDK notices and original source headers remain intact. No upstream library source was locally modified; Android D8 bytecode conversion uses the client's published Gradle configuration. A byte-for-byte rebuild of Google's jar was not performed.
- desugar_jdk_libs_configuration 2.1.5: BSD-3-Clause. The actual binary's complete R8 authors' LICENSE is retained. This configuration is a build input, not itself an Android runtime library.
- Test-only: JUnit 4.13.2 EPL-1.0, Hamcrest 1.3 BSD-3-Clause, JSON-java 20240303 Public Domain, MockWebServer 4.12.0 Apache-2.0. These do not appear as additional APK runtime dependencies. They remain declared in app/build.gradle and are described in build-and-test materials. MockWebServer shares the OkHttp project terms; executable OkHttp notices are retained under runtime/.
- Source-distributed Gradle Wrapper 8.13: its jar's SHA-256 matches the official wrapper checksum. Embedded LICENSE, original headers, fixed-tag full upstream LICENSE and official distribution NOTICE are retained under licenses/build-and-test/. The full Gradle distribution, SDK, JDK, caches and signing key are not included in the client source package.

## Packaging and reproduction

The root licenses/ directory is the single source for legal files. Gradle syncLicenseAssets copies it unchanged into assets/licenses/. The offline Activity recursively enumerates all .txt/.md/.json documents. Root LICENSE matches licenses/Apache-2.0.txt; root NOTICE matches licenses/NOTICE.txt. Root project license grants only the project-authored material it has authority to license; third-party software, underlying media and trademarks retain their terms.

运行库源码 JAR 仅作为本次版本通知采集证据，不随客户端源码包发布。脱糖库完整源码 ZIP 单独附 Release；MPL 数据首选源码直接在 APK 与客户端源码包内提供。公开包排除本地完整 XBVR Go 参考副本、历史交接文件、私服数据和凭据。来源复核缺口见 [SOURCE_PROVENANCE.md](SOURCE_PROVENANCE.md)。

To refresh after dependency changes:

```powershell
.\build.ps1 -Tasks @(':app:complianceArtifacts', '--init-script', 'tools/compliance-init.gradle')
python tools/compliance-collect.py
.\build.ps1
python tools/compliance-package.py
```

The collector uses fixed-version source artifacts and preserves original text, but release-specific source-availability URLs, upstream source archives and provenance still need human review. A successful collector run is not a complete legal audit. Do not publish validation/compliance-resolved-local.json or the private cache.
