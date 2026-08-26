# 海洋生态数字电子书

海洋生态数字电子书 Web 平台的第一阶段工程。当前已完成项目私有 JDK 17、Vue 3/Vite 前端、Spring Boot 健康检查和前后端开发代理的最小可运行骨架。

## 技术栈

- 后端：JDK 17、Spring Boot 3.5.9、Maven。
- 前端：Vue 3、TypeScript、Vite。
- 后续接入：MySQL 8、MyBatis-Plus、Flyway、JWT、Ant Design Vue。

## 本地启动

首次执行时，分别进入 `frontend` 与 `backend` 目录安装或下载依赖。

```powershell
# 终端一：后端（仅此命令进程使用项目私有 JDK 17）
cd backend
..\scripts\with-jdk17.cmd mvn spring-boot:run

# 终端二：前端
cd frontend
npm install
npm run dev
```

浏览器访问 Vite 输出的地址（默认 `http://localhost:5173`）。前端将 `/api` 代理至默认的 `http://localhost:8080`；可复制 `frontend/.env.example` 为 `frontend/.env.local` 后修改代理目标。

健康检查：`GET http://localhost:8080/api/health`

## 验证命令

```powershell
cd backend
..\scripts\with-jdk17.cmd mvn test

cd ..\frontend
npm run build
```

`scripts/use-jdk17.ps1` 可显示项目私有 JDK 的版本；`scripts/with-jdk17.cmd <命令>` 会仅为该命令设置 JDK 17，不会修改系统 `JAVA_HOME` 或其他项目的 Java 版本。

## 当前范围

本阶段只建设 Web 端。分类、电子书、章节、登录权限、互动、统计及 MySQL 迁移将按已确认的实施计划逐项实现；嵌入式、TCP、语音和端侧大模型不在当前代码范围内。
