# 海洋生态数字电子书

海洋生态数字电子书 Web 平台的第一阶段工程。当前已完成项目私有 JDK 17、Vue 3/Vite 前端、Spring Boot 健康检查和前后端开发代理的最小可运行骨架。

## 技术栈

- 后端：JDK 17、Spring Boot 3.5.9、Maven。
- 前端：Vue 3、TypeScript、Vite。
- 数据库：MySQL 8、MyBatis-Plus、Flyway。
- 后续接入：JWT、Ant Design Vue。

## 本地启动

首次执行时，先准备本机 Docker MySQL，再分别进入 `frontend` 与 `backend` 目录安装或下载依赖。

```powershell
# 首次执行：复制环境模板并设置本机开发密码
Copy-Item .env.example .env

# 启动 MySQL 8（可用 docker compose ps 查看状态）
docker compose up -d mysql

# 终端一：后端（仅此命令进程使用项目私有 JDK 17）
cd backend
..\scripts\with-jdk17.cmd mvn spring-boot:run

# 终端二：前端
cd frontend
npm install
npm run dev
```

MySQL 默认映射到本机 `3307`，避免与已有的 `3306` 服务冲突；如需调整，可修改本机 `.env` 的 `MYSQL_PORT`。

浏览器访问 Vite 输出的地址（默认 `http://localhost:5173`）。前端将 `/api` 代理至默认的 `http://localhost:8080`；可复制 `frontend/.env.example` 为 `frontend/.env.local` 后修改代理目标。

健康检查：`GET http://localhost:8080/api/health`

Flyway 会在后端首次启动时创建数据库表和演示数据。演示管理员账号为 `admin`，初始密码为 `password`；该账号仅用于本地开发，登录功能完成后应立即修改密码。

停止本地数据库：`docker compose down`。这不会删除数据；若需要完全重新创建开发数据库，可执行 `docker compose down -v`，该命令会删除本地 MySQL 数据。

## 验证命令

```powershell
cd backend
..\scripts\with-jdk17.cmd mvn test

cd ..\frontend
npm run build
```

`scripts/use-jdk17.ps1` 可显示项目私有 JDK 的版本；`scripts/with-jdk17.cmd <命令>` 会仅为该命令设置 JDK 17，不会修改系统 `JAVA_HOME` 或其他项目的 Java 版本。

## 当前范围

本阶段只建设 Web 端。数据库迁移已纳入当前工程；分类、电子书、章节、登录权限、互动和统计将按已确认的实施计划逐项实现；嵌入式、TCP、语音和端侧大模型不在当前代码范围内。
