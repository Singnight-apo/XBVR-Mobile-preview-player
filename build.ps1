param([string[]]$Tasks = @('assembleDebug','testDebugUnitTest','lintDebug'))
$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
Set-Location -LiteralPath $projectDirectory
if (-not $env:JAVA_HOME) {
    $javaDirectory = Get-ChildItem -LiteralPath "$projectDirectory\toolchain\jdk" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($javaDirectory) { $env:JAVA_HOME = $javaDirectory.FullName }
}
if (-not $env:JAVA_HOME) { throw '请安装 JDK 17 并设置 JAVA_HOME' }
$env:GRADLE_USER_HOME = "$projectDirectory\toolchain\gradle-home"
$env:ANDROID_USER_HOME = "$projectDirectory\toolchain\user-home\.android"
$env:ANDROID_SDK_HOME = "$projectDirectory\toolchain\user-home"
$socketDirectory = "$projectDirectory\toolchain"
New-Item -ItemType Directory -Path $socketDirectory -Force | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true -Djdk.net.unixdomain.tmpdir=$socketDirectory -Duser.home=$projectDirectory\toolchain\user-home"
if (-not (Test-Path -LiteralPath "$projectDirectory\toolchain\debug.keystore")) {
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore "$projectDirectory\toolchain\debug.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=XBVR Pocket Development' -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw '无法创建开发签名' }
}
if (Test-Path -LiteralPath "$projectDirectory\toolchain\gradle\gradle-8.13\bin\gradle.bat") { & "$projectDirectory\toolchain\gradle\gradle-8.13\bin\gradle.bat" --no-daemon @Tasks }
else { & "$projectDirectory\gradlew.bat" --no-daemon @Tasks }
if ($LASTEXITCODE -ne 0) { throw "构建失败，退出码 $LASTEXITCODE" }
