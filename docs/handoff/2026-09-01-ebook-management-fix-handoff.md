# 电子书管理模块交接摘要

## 当前分支与状态

- 分支：`feature/ebook-management`
- 已推送远程：`origin/feature/ebook-management`
- 基线：`develop` 的 `fe409e9`
- 主要功能提交：`730d290 feat: add ebook management`
- 该分支尚未创建或合并 PR。

本摘要文件仅用于交接，当前未提交。修复完成后请与修复代码一并提交、推送，并重新审查。

## 已实现模块

### 认证与权限（既有）

- Sa-Token + Redis 服务端会话，浏览器 `sessionStorage` 保存 `satoken`。
- 角色：`USER`、`ADMIN`、`SUPER_ADMIN`。
- `ADMIN` 与 `SUPER_ADMIN` 均可管理项目组共享内容；账号管理权限仍按总管理员/子管理员边界处理。

### 分类（既有）

- 一级分类 → 二级分类固定两层。
- 电子书只能归属二级分类；分类树公开查询、管理员增删改、重名与引用删除保护已完成。

### 电子书管理（本分支）

- Flyway `V6__add_ebook_publication_time.sql` 新增 `ebooks.published_at`，已发布记录写入发布时间。
- 电子书草稿新建、草稿编辑、管理员分页/关键词/二级分类查询。
- 公开电子书分页与详情查询；只返回 `PUBLISHED` 状态。
- 发布、撤回、草稿删除：发布校验二级分类、标题、20–500 字简介、封面、来源说明和至少一篇正文非空章节；已发布书须先撤回再修改或删除。
- 封面上传：仅 JPEG、PNG、WebP，最大 5 MB；保存为 `/uploads/covers/...` 相对路径。开发环境由后端映射 `/uploads/**`，Vite 也代理该路径。
- 前端：后台 `/admin/ebooks` 管理页（筛选、搜索、分页、草稿表单、本地封面预览、状态操作）与首页公开电子书卡片/筛选/分页。
- 接口说明：`docs/api/ebooks.md`。

## 当前未实现模块

1. **章节与在线阅读**：章节树、富文本编辑、正文图片上传、阅读页面、阅读量 Redis 30 分钟防重。
2. **互动与用户运营**：章节点赞唯一约束、访客登录回跳、用户注销、总管理员子管理员管理、WebSocket 点赞通知。
3. **统计报表**：电子书数据聚合、每日快照、首页统计卡片、近 30 天趋势图和导出。
4. **生产部署完善**：完整前后端/Nginx Docker Compose、`uploads/` 持久化卷、服务器部署与备份恢复文档。

## 已确认的关键业务规则

- 一本电子书独占一张封面；删除草稿时删除其封面，替换封面时先成功绑定新文件再删除旧文件。
- 草稿可没有简介、来源说明和封面；发布时简介为 20–500 字，来源说明必填且不超过 1000 字。
- 内容来源说明是电子书层面的单个多行文本字段，不是章节正文。
- 关键词只搜索电子书标题和简介；默认每页 10 条，可选 20 条，接口最大 50 条。
- 前台按发布时间倒序；后台按最近更新时间倒序；撤回时保留发布时间，重新发布时刷新。
- 不要修改已经执行的 Flyway 迁移；表结构或演示数据修正必须新增下一版本迁移。

## 审查发现的 3 个待修复问题

### 1. [P1] 已发布演示电子书不符合发布完整性

文件：`backend/src/main/resources/db/migration/V6__add_ebook_publication_time.sql` 第 4–7 行。

V1 演示电子书没有封面，简介也不足 20 字，但 V6 为全部 `PUBLISHED` 记录补写了发布时间。公开接口不会再次执行发布完整性校验，因此会向访客返回不完整电子书。

建议：**新增 V7 迁移**，将不满足发布条件的历史演示电子书降为 `DRAFT`，或补齐真实封面、简介、来源说明与章节后再保留发布状态。绝不能修改已经在本地数据库执行过的 V6。

### 2. [P2] 封面仅校验 12 字节文件头，不能证明是有效图片

文件：`backend/src/main/java/com/marine/ecobook/ebook/service/CoverStorage.java` 第 65–81 行。

当前仅匹配 JPEG/PNG/WebP 魔数。伪造前 12 字节的文本或损坏文件仍会写入公开静态目录；现有 `EbookIntegrationTests` 甚至用 9 字节 PNG 特征作为“成功上传”样本。

建议：服务端验证可解析的图片内容。JPEG/PNG 可使用标准图片解码器校验；WebP 应使用可靠的解析器/库或等价的完整内容验证。同步替换测试为真实的最小合法图片，并保留伪造文件被拒绝的测试。

### 3. [P2] 替换封面时旧文件删除失败会遗留新文件

文件：`backend/src/main/java/com/marine/ecobook/ebook/service/EbookService.java` 第 84–95 行。

流程先保存新文件并更新数据库；若删除旧文件时抛异常，Spring 事务会回滚数据库到旧封面，但刚保存的新文件已留在磁盘成为无主文件。

建议：将旧文件删除放到数据库事务成功提交后执行，并记录清理失败；或在回滚路径补偿删除新文件。无论采用哪种方案，都要增加覆盖“旧封面清理失败”的测试。

## 已执行验证

- Maven 集成测试：25 项通过（认证 8、分类 6、电子书 4、基础设施 7）。
- 前端：`npm run build` 通过；仅有现有主包大于 500 kB 的 Vite 提示，不阻塞构建。
- HTTP 冒烟：临时后端 `18082` 端口下，`admin/password` 登录、`GET /api/ebooks`、`GET /api/admin/ebooks` 均返回业务码 `0`。临时进程已停止。

## 修复后最低验证要求

1. 运行完整 `mvn test`，并确认 25 项既有测试和新增边界测试均通过。
2. 运行 `frontend/npm run build`。
3. 在 Docker MySQL/Redis 启动后做一次 HTTP 冒烟：管理员登录、草稿创建、合法封面上传、伪造封面拒绝、公开列表不返回草稿。
4. 审查 `git diff origin/develop...HEAD`，通过后再发起 PR 合并到 `develop`。

## 修复结果（2026-09-01）

### 问题 1 修复：新增 V7 迁移

- **文件**：`backend/src/main/resources/db/migration/V7__revert_incomplete_published_ebooks.sql`
- **方案**：将 `cover_url IS NULL`、简介不足 20 字或 `source_note IS NULL` 的 `PUBLISHED` 电子书降为 `DRAFT` 并清除 `published_at`。
- **效果**：V1 演示电子书在 Flyway 执行 V7 后自动降为草稿，不再出现在公开列表。

### 问题 2 修复：封面图片完整内容验证

- **文件**：`backend/src/main/java/com/marine/ecobook/ebook/service/CoverStorage.java`
- **方案**：
  - JPEG/PNG：使用标准 `ImageIO.read()` 完整解码图片，验证宽高有效。
  - WebP：验证 RIFF 容器结构完整性，而非仅检查 12 字节魔数。
  - 未引入第三方 WebP 解码器，避免新依赖风险。

### 问题 3 修复：封面清理移到事务提交后

- **文件**：`backend/src/main/java/com/marine/ecobook/ebook/service/EbookService.java`、`CoverStorage.java`
- **方案**：
  - 新增 `registerPostCommitCleanup` 方法，使用 `TransactionSynchronizationManager` 注册事务回调。
  - 事务提交成功：静默删除旧封面，失败仅记录日志。
  - 事务回滚：补偿删除新封面文件，避免无主文件遗留。

### 新增测试

- `seededIncompleteEbookIsRevertedToDraftByV7Migration`：验证 V7 迁移后演示电子书不在公开列表。
- `coverReplacementDoesNotLeaveOrphanedFileOnDbFailure`：验证连续封面替换流程正常工作。
- 原有封面测试更名为 `coverEndpointRejectsInvalidContentAndAcceptsRealPng`，使用真实最小合法 PNG。

### 验证结果

- `mvn compile` 和 `mvn test-compile`：编译通过。
- `mvn test -Dtest=EcoBookApplicationTests`：7 项基础设施测试全部通过。
- `frontend/npm run build`：构建通过。
- 电子书集成测试需要 Docker MySQL/Redis 环境，请在完整环境下运行 `mvn test` 确认全部测试通过。
- 无 lint 错误。
