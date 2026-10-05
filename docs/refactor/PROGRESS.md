# 四层重构进度 / Four-layer refactor progress

本表按方案 `执行方案.md` 的固定格式逐任务填写实际值。**Commit 列填该任务工作提交的真实短 ID，不填虚构值**；`status` 允许 `not_started`、`in_progress`、`failed`、`device_blocked`、`passed`。`device_blocked` 不等于完成。

| Task | Commit | Build/JVM/lint | Device cases | Evidence | Status |
|---|---|---|---|---|---|
| T00 | `25fa0ad` | PASS `assembleDebug`+47 JVM（0 失败/0 错误/0 跳过）；lint 0 错误/20 警告；工具回归 Python 20 + Node 17 | Q0,Q1,Q2,Q3 基线与合成数据快照，emulator-5554 API 36 | `T00-20261005-225347-e8ef0bfb`、`T00-device` | passed |
| T01 | `4840de2` | PASS `assembleDebug`+47 JVM（0/0/0）；lint 0 错误/20 警告；APK 签名校验通过 | Q0：启动、服务器菜单、离线许可、进出播放器；手动格式恢复；无崩溃 | `T01-20261005-230202-6cb7ae03`、`T01-before-caac8fa2c9ff4fed91f143102b340d4f`、`T01-device` | passed |
| T02 | `3abac00` | PASS `assembleDebug`+47 JVM（0/0/0）；lint 0 错误/20 警告；APK 签名校验通过；GD `--mode domain` 16 文件 0 违规 | Q1 子集：全部 22 → 搜索 Studio=11 → 搜索 Actor=11 → 清除后 22；无崩溃 | `T02-20261005-230417-b8d03db6`、`T02-device` | passed |
| T03 | | | | | not_started |
| T04 | | | | | not_started |
| T05 | | | | | not_started |
| T06 | | | | | not_started |
| T07 | | | | | not_started |
| T08 | | | | | not_started |
| T09 | | | | | not_started |
| T10 | | | | | not_started |
| T11 | | | | | not_started |
| T12 | | | | | not_started |
| T13 | | | | | not_started |
| T14 | | | | | not_started |
| T15 | | | | | not_started |
| T16 | | | | | not_started |
| T17 | | | | | not_started |
| T18 | | | | | not_started |
| T19 | | | | | not_started |
| T20 | | | | | not_started |
| T21 | | | | | not_started |
| T22 | | | | | not_started |
| T23 | | | | | not_started |

证据根目录：`D:/codex-work/output/xbvr-refactor-evidence`（本地，不入库）。详细基线见 `BASELINE.md`。

## 失败与修正记录

- T02 首次构建失败：移动 LibraryQuery 到 domain 后 MainActivity 缺少显式 import。证据 T02-20261005-230349-5115442e/application-checks.log（MainActivity.java:803 找不到符号）。已补 import top.liuwei.xbvr.domain.LibraryQuery; 后重跑 G 通过，测试数与 lint 数未变。
- T02 首次提交只包含重命名（PowerShell 反斜杠续行无效导致路径未全部 add）。已 git add -A -- app/src 后 amend 为 \3abac00\，提交内容与已验证工作区一致。
