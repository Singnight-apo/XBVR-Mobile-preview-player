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

To reproduce the reviewed v0.2.5 dependency snapshot:

```powershell
.\build.ps1 -Tasks @(':app:complianceArtifacts', '--init-script', 'tools/compliance-init.gradle')
python tools/compliance-collect.py
.\build.ps1
python tools/compliance-package.py
```

The collector reads the committed [compliance-input-lock.json](../../tools/compliance-input-lock.json), not the generated inventory, as its trusted input baseline. The initial lock copies the 36 artifact hashes, approved license classifications, POM evidence and available source-JAR hashes from the human-reviewed, published v0.2.5 inventory. The retained v0.2.5 cache supplies the OkHttp source-ZIP hash; published legal texts supply the Apache/MPL text hashes. This establishes continuity with that reviewed snapshot, **not independent proof of upstream authenticity**. Provenance gaps remain documented in SOURCE_PROVENANCE.md.

锁文件固定以下输入：全部 36 项制品的坐标、scope、二进制 SHA-256 和经审核许可；33 个可用的固定版本源码 JAR；Apache-2.0 与 MPL-2.0 法律正文；固定提交 `73170c345e6a762fc6a1f0301bb15218850023ef` 的 desugar 完整源码 ZIP（SHA-256 `cf0b48cb046b6517ed8e2a816c125116a907e13e48e341882ba6f4016a155d9f`）；OkHttp `parent-4.12.0` 源码 ZIP。新工作目录没有源码缓存时，采集器会从锁中的固定 URL 获取并验证后缓存；制品二进制仍须由上述 Gradle 解析任务提供。URL 的版本或 tag 名本身不能证明可信，下载字节必须与审核锁的哈希一致。

Only the reviewed listenablefuture empty conflict-avoidance JAR retains its explicit historical `sourceNoticeEvidence.unavailable` gap; it has no fabricated source hash. The two desugar artifacts use the separately locked full-source ZIP and retained binary terms. This exception cannot be applied to other coordinates. Test-only inputs are excluded from this runtime/desugar collector and remain maintained under build-and-test/.

未知、缺失或变更的运行时/脱糖坐标、scope、制品哈希、源码哈希、额外资源哈希，以及缺失审核许可都会失败。采集器不再默认赋予 Apache-2.0，也不会把下载/哈希错误降格为“源码不可用”。全部输入先预校验，生成及归档内容检查在临时目录完成后才写回 legal 输出；写回中的普通错误会逐项尝试回滚已成功替换的文件；某项恢复失败不阻止其它项恢复，最终汇总未恢复项并保留原始错误。运行中断或回滚本身遭遇磁盘错误时仍须检查输出，不能把跨文件写回当作文件系统事务。

The collector has no option to accept new hashes or overwrite its lock. For a dependency update, separately review the new coordinate, binary/source hashes, original terms and source provenance; then explicitly edit the lock and release-specific documentation in a reviewed change. Do not regenerate the lock from whatever a fresh Gradle resolution or a modified inventory happens to contain.

Offline regression tests use only Python's standard library and synthetic inputs; they cover changed/unknown/missing artifacts, source and legal-resource tampering, missing licenses, failed downloads, unchanged legal outputs on validation/generation failure, and rollback of a simulated publication error:

```powershell
python -m unittest discover -s tools -p test_compliance_collect.py -v
```

A successful collector run confirms agreement with the reviewed input lock and retains original text; release-specific source-availability URLs and upstream provenance still need human review. It is not a complete legal audit. Do not publish validation/compliance-resolved-local.json or the private cache.
