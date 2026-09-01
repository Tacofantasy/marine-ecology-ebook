# Docker Compose 项目组统一方案

## 目标

将本项目的 MySQL 与 Redis 保持为两个独立服务，但在 Docker Desktop 中归入同一个 `marine-ebook` Compose 项目组。开发者可用一组 Compose 命令统一启动、停止和查看它们。

## 配置

- 在根目录 `compose.yaml` 设置顶层项目名 `name: marine-ebook`。
- 移除两个服务的 `container_name`。容器名由 Compose 生成，例如 `marine-ebook-mysql-1`、`marine-ebook-redis-1`，避免不同工作目录抢占固定名称。
- 为数据卷指定稳定名称 `marine-ebook-mysql-data` 与 `marine-ebook-redis-data`，不依赖工作目录名称。

## 现有数据迁移

当前 MySQL 与 Redis 分别使用旧工作目录生成的数据卷。切换时停止旧容器但不删除旧卷；创建新的稳定命名卷后，将旧卷数据复制到对应新卷，再由 `marine-ebook` 项目启动服务。复制完成后验证数据库迁移记录、Redis 连通性与健康检查。旧卷保留，不在本次改动中删除。

## 验收

1. `docker compose ps` 将 MySQL 与 Redis 显示为同一 Compose 项目。
2. 两个服务健康，端口仍为 MySQL `3307` 与 Redis `6379`。
3. 后端可连接既有 MySQL 数据和 Redis 会话服务。
4. 不执行 `down -v`、`volume rm` 或其他删除数据卷的操作。
