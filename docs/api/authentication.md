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
