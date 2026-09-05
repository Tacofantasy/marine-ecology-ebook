$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
# Use isolated test services; never connect browser tests to development data.
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:13307/marine_ebook_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:SPRING_DATASOURCE_USERNAME = 'marine_test'
$env:SPRING_DATASOURCE_PASSWORD = 'marine-test-password'
$env:SPRING_DATA_REDIS_HOST = '127.0.0.1'
$env:SPRING_DATA_REDIS_PORT = '16379'
$env:APP_STATS_ENABLED = 'false'
$env:UPLOAD_ROOT = Join-Path $projectRoot 'backend/target/test-uploads'
$env:INITIAL_SUPER_ADMIN_USERNAME = 'admin'
$env:INITIAL_SUPER_ADMIN_PASSWORD = 'password'

Push-Location $projectRoot
try {
    & (Join-Path $PSScriptRoot 'with-jdk17.cmd') java -jar backend/target/marine-ebook-api-0.0.1-SNAPSHOT.jar --server.port=18080
    if ($LASTEXITCODE -ne 0) { throw 'Test API failed to start.' }
} finally { Pop-Location }
