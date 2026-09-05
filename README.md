# 海洋生态数字电子书平台

用于综合实训的海洋科普阅读与内容管理平台。已实现注册登录、两级分类、电子书草稿与发布、图文章节编辑、在线阅读、点赞收藏、账号管理和阅读统计。

前端：Vue 3 / TypeScript / Ant Design Vue / Tiptap / ECharts。后端：Java 17 / Spring Boot / MyBatis-Plus / Flyway。MySQL 8 存储业务数据，Redis 保存登录会话和阅读去重计数。面向单台服务器和实训演示部署。

## 功能与角色

| 身份 | 可用功能 |
| --- | --- |
| 访客 | 分类、搜索、分页、阅读已发布内容、查看来源和统计 |
| 注册用户 | 访客功能，以及点赞、收藏、我的收藏、个人资料 |
| 子管理员 | 分类、草稿、封面、章节编辑排序、发布撤回；管理注册用户状态与注销 |
| 总管理员 | 子管理员功能，以及创建子管理员、管理其状态、重置受管理账号密码 |

管理员不参与读者互动；不能管理自己或总管理员。已注销账号不能恢复。发布要求：二级分类、名称、20–500 字简介、真实封面、内容来源说明和非空章节。修改或删除已发布内容须先撤回。

## 开发启动

需要 Node.js 22.12+、Java 17、Maven 3.9+、Docker Desktop（Linux 容器）。从项目根目录执行：

```powershell
# 首次配置；已有 .env 时请编辑它，不要覆盖
Copy-Item .env.example .env
# 编辑数据库密码和首次管理员凭据
docker compose up -d --wait mysql redis

# 终端一
.\scripts\with-jdk17.cmd mvn -f backend/pom.xml spring-boot:run

# 终端二
cd frontend
npm ci
npm run dev
```

浏览器入口通常为 http://localhost:5173，API 健康检查为 http://localhost:8080/api/health。MySQL 开发端口为 3307，Redis 为 6379，可在 .env 调整。前端 /api 和 /uploads 经 Vite 代理，可在 frontend/.env.local 设置 VITE_API_PROXY_TARGET。

with-jdk17.cmd 优先使用项目私有 .tools/jdk17；新电脑可设置 JAVA_HOME 指向自己的 Java 17。真实配置、上传文件和私有工具不提交 Git。

## 实训交付：单个 JAR（推荐）

前端构建结果嵌入后端 JAR。接收方只需要 Java 17、MySQL、Redis，不需要 Node.js 或 Maven。

```powershell
# 在开发电脑构建，包含前端构建和后端单元测试
powershell -ExecutionPolicy Bypass -File scripts/package-delivery.ps1

# 从项目根目录启动，确保读取 .env
docker compose up -d --wait mysql redis
.\scripts\with-jdk17.cmd java -jar backend/target/marine-ebook-api-0.0.1-SNAPSHOT.jar
```

访问 http://localhost:8080。交接 JAR、.env.example、compose.yaml 和本说明；接收方创建自己的 .env 后使用 java -jar 启动。数据库由 Flyway 自动迁移，已有数据保留。前端嵌入由 Maven 的 delivery profile 控制，平常开发使用普通构建即可。

上传路径默认相对启动目录。搬迁环境时必须一起交接数据库备份和上传目录；建议在 .env 设置绝对路径，例如 UPLOAD_ROOT=D:/marine-ebook-data/uploads，Linux 可用 /srv/marine-ebook/uploads。

## 完整容器部署

```powershell
# .env 需包含 .env.example 的全部配置
docker compose -f compose.delivery.yaml up -d --build --wait
docker compose -f compose.delivery.yaml ps
```

访问 http://localhost:8088，端口由 WEB_PORT 控制。包含 Nginx 前端、后端、MySQL、Redis，使用独立于开发环境的持久卷，不自动复制开发数据。MySQL 与 Redis 不向主机暴露端口，Redis 开启 AOF，上传文件独立持久化。首次构建需访问 Docker Hub、npm、Maven。

停止使用对应的 docker compose down，保留卷内数据。日常停止不要添加 -v，它会删除持久数据。容器镜像下载不通时，可用单 JAR 方式。

## 测试与验收

```powershell
# 不依赖 MySQL / Redis 的单元测试
.\scripts\with-jdk17.cmd mvn -f backend/pom.xml test

# 隔离集成测试：固定使用 13307 / 16379，不复用开发服务
docker compose -f compose.test.yaml up -d --wait
.\scripts\with-jdk17.cmd mvn -f backend/pom.xml test -Pintegration

# 前端类型检查和构建
cd frontend
npm run build
```

浏览器自动验收需要已构建的后端 JAR。先完成集成测试，再启动以下进程，不要让两种测试同时写入测试数据库。

```powershell
# 终端一，项目根目录
powershell -ExecutionPolicy Bypass -File scripts/start-test-api.ps1

# 终端二
cd frontend
npx playwright install chromium
npm run test:e2e
# Windows 已有 Edge 时可设置 $env:PLAYWRIGHT_CHANNEL='msedge'，省去 Chromium 下载
```

Playwright 自动启动 15173 前端，API 固定连接 18080 测试实例。验收会创建临时账号和电子书，不可指向业务数据。验收单 JAR 时设置 $env:E2E_BASE_URL='http://127.0.0.1:18080'，直接测试 JAR 页面。

测试结束后先停止 API，再运行 docker compose -f compose.test.yaml down。测试数据库使用临时内存文件系统，不作持久数据存储。

人工演示步骤、统计口径和检查项见 [实训验收清单](docs/handoff/training-acceptance.md)。本轮验证结果见 [交付检查记录](docs/handoff/2026-09-05-training-delivery.md)。

## 初始内容与范围

本地模板管理员为 admin / password，仅作本机演示。首次部署可通过 INITIAL_SUPER_ADMIN_USERNAME 和 INITIAL_SUPER_ADMIN_PASSWORD 设置凭据。已有自定义账号不会因修改环境变量而自动重置密码。

Flyway 自带一份待完善的草稿，首次书库为空属于正常状态。管理员补充简介、来源和封面后发布，前台即可阅读。实训内容应使用原创或有明确授权来源的资料。

本次平台交付不包含智能问答终端、TCP、语音和实时通知。历史规划文档保留作过程材料，当前运行能力以本说明与验收清单为准。
