# 电子书互动接口（点赞与收藏）

所有接口返回 `{ code, message, data, timestamp }`；成功业务码为 `0`。电子书 ID 与 `likeCount` 均以十进制字符串返回，前端必须作为字符串处理，避免 64 位整数精度丢失。

## 角色限制

互动接口仅面向角色为 `USER` 的注册用户：

- 访客未登录调用任何互动接口返回 `40101`；
- `ADMIN`、`SUPER_ADMIN` 调用返回 `40301`，即使已登录也不得产生互动记录。

管理员可以正常浏览公开电子书，但点赞者身份不对任何人公开；收藏关系、收藏数量与收藏者身份均不公开。

## 通用规则

- 点赞和收藏的对象都是**电子书**，不是章节。
- 所有互动只面对 `PUBLISHED` 电子书；草稿、已撤回或不存在的电子书统一返回 `40401`（`电子书不存在或尚未发布`），不泄露内容状态。
- 同一用户对同一本电子书至多保留一条点赞记录和一条收藏记录。
- 创建（`POST`）与取消（`DELETE`）均**幂等**：重复创建、重复删除都返回 `200 + code 0` 及真实当前状态，不会返回 `40901`，也不会产生重复行。
- 电子书撤回时不删除互动记录；书重新发布后，既有收藏自动恢复可见。

## 读取互动状态

`GET /api/ebooks/{ebookId}/interaction`

仅 `USER`。返回当前登录用户的布尔状态与公开点赞总数，不含任何用户 ID、姓名或收藏统计：

```json
{
  "liked": true,
  "favorited": false,
  "likeCount": "12"
}
```

## 点赞与收藏

| 方法 | 路径 | 成功结果 |
| --- | --- | --- |
| POST | `/api/ebooks/{ebookId}/like` | 点赞后的最新 `InteractionState` |
| DELETE | `/api/ebooks/{ebookId}/like` | 取消后的最新 `InteractionState` |
| POST | `/api/ebooks/{ebookId}/favorite` | 收藏后的最新 `InteractionState` |
| DELETE | `/api/ebooks/{ebookId}/favorite` | 取消后的最新 `InteractionState` |

示例响应 `data`：

```json
{
  "liked": true,
  "favorited": false,
  "likeCount": "13"
}
```

幂等语义：`POST` 已存在记录时保持点赞/收藏状态；`DELETE` 无记录时保持未点赞/未收藏状态，两者均返回成功。

## 我的收藏

`GET /api/me/favorites?page=1&pageSize=10`

仅 `USER`。只返回当前用户收藏且仍处于已发布状态的电子书，按收藏时间倒序（`created_at DESC, id DESC`）分页。`page >= 1`，`1 <= pageSize <= 50`，默认每页 10 条；`pageSize` 为 `0` 或 `51` 返回 `40001`。`page` 超出实际页数时返回空 `list`，`total` 不变（不会因超大页码报错）。

示例响应 `data`：

```json
{
  "total": 1,
  "list": [
    {
      "id": "9007199254740993",
      "categoryId": "2",
      "categoryName": "珊瑚礁生态",
      "title": "认识珊瑚礁生态系统",
      "coverUrl": "/uploads/covers/example.webp",
      "summary": "……",
      "status": "PUBLISHED",
      "publishedAt": "2026-09-02T10:00:00",
      "updatedAt": "2026-09-02T10:00:00",
      "likeCount": "12",
      "favoritedAt": "2026-09-02T10:10:00"
    }
  ]
}
```

撤回的书不会出现在列表中，但收藏记录仍保留；重新发布后自动恢复。收藏其他用户不可见。

## 公开电子书的点赞总数

`GET /api/ebooks` 与 `GET /api/ebooks/{id}` 的响应项均包含 `likeCount`（字符串），见[电子书接口](ebooks.md)。该字段向后兼容；现有前端不读取也不受影响。
