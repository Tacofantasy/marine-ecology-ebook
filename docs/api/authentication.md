# 认证接口

所有接口使用项目统一响应结构。登录成功后的 Token 通过请求头提交：

```http
satoken: <token>
```

## 注册普通用户

`POST /api/auth/register`

请求体：

```json
{
  "username": "ocean_user",
  "email": "ocean_user@example.com",
  "password": "password123"
}
```

- 用户名为 3 至 64 位字母、数字或下划线。
- 密码为 8 至 64 位。
- 注册接口固定创建 `USER`，不接受角色字段。
- `admin`（不区分大小写）为保留用户名，不能注册。
- 用户名或邮箱重复返回 HTTP 409 与业务码 `40901`。

## 登录

`POST /api/auth/login`

请求体中的 `account` 可为用户名或邮箱：

```json
{
  "account": "ocean_user",
  "password": "password123"
}
```

成功响应的 `data` 包含 `token` 和当前用户公开资料。Token 对应 Redis 会话，有效期为 24 小时。错误密码、未知账号和被禁用账号统一返回 HTTP 400 与业务码 `40001`，避免泄露账号状态。

本地首次启动会由初始化配置创建总管理员；默认开发凭据为 `admin` / `password`。生产环境必须通过 `INITIAL_SUPER_ADMIN_USERNAME` 与 `INITIAL_SUPER_ADMIN_PASSWORD` 提供首次总管理员凭据，不能使用默认值。

## 当前用户

`GET /api/auth/me`

需要有效 Token。成功响应只包含 `id`、`username`、`displayName`、`email`、`role`、`status`，不会返回密码哈希。缺失、过期或无效 Token 返回 HTTP 401 与业务码 `40101`。

## 退出登录

`POST /api/auth/logout`

需要有效 Token。成功后立即删除 Redis 中的当前会话；后续继续使用该 Token 会返回 HTTP 401 与业务码 `40101`。

## 管理员鉴权验证

`GET /api/admin/auth-check`

需要有效的 `ADMIN` 或 `SUPER_ADMIN` Token。普通 `USER` 调用返回 HTTP 403 与业务码 `40301`。后续所有后台内容管理接口复用同一套角色鉴权。

`GET /api/admin/super-admin/auth-check`

需要有效的 `SUPER_ADMIN` Token。`ADMIN` 与普通 `USER` 调用返回 HTTP 403 与业务码 `40301`。

## 账号管理

以下接口均要求 `ADMIN` 或 `SUPER_ADMIN` 的 `satoken` 请求头。账号列表只返回当前操作者可管理且尚未注销的账号：子管理员仅看到注册用户；总管理员看到注册用户和子管理员，不显示总管理员账号。

`GET /api/admin/users?keyword=&role=&status=&page=1&pageSize=10`

- `keyword` 匹配登录名或昵称；`role` 可为 `ADMIN`、`USER`；`status` 可为 `1`（正常）或 `0`（已禁用）。
- 子管理员的角色筛选始终限定为 `USER`。
- 返回分页 `{ total, list }`；账号 ID 为字符串。

`POST /api/admin/users/administrators`

仅总管理员可创建子管理员。请求体：

```json
{
  "username": "content_operator",
  "displayName": "内容运营",
  "email": "operator@example.com",
  "password": "initialpass"
}
```

登录名为 3–64 位字母、数字或下划线；显示昵称必填；初始密码为 8–64 位。用户名或邮箱重复返回 `40901`。

`PATCH /api/admin/users/{id}/status`

请求体为 `{ "status": 0 }`（禁用）或 `{ "status": 1 }`（启用）。禁用会立即使目标账号下线，之后可由有权限的管理员重新启用。子管理员仅可操作注册用户；总管理员可操作注册用户和子管理员；任何人均不可操作自己或总管理员。

`PUT /api/admin/users/{id}/password`

仅总管理员可重置非总管理员账号密码。请求体为 `{ "password": "newpassword" }`，长度 8–64 位。成功后目标账号所有会话立即失效，需使用新密码重新登录。

`DELETE /api/admin/users/{id}`

注销为不可恢复的逻辑删除，会立即使目标账号下线且不再出现在账号列表。权限边界与账号状态更新一致。
