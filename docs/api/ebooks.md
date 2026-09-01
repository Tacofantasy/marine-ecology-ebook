# 电子书接口

所有接口返回 `{ code, message, data, timestamp }`；成功业务码为 `0`。列表 `data` 为 `{ total, list }`，默认每页 10 条，`pageSize` 最大 50。

电子书与分类 ID 以十进制字符串返回和传递，避免浏览器处理 64 位整数时丢失精度。

## 公开查询

`GET /api/ebooks?categoryId=&keyword=&page=1&pageSize=10`

访客可调用，只返回已发布电子书。`categoryId` 必须是二级分类；`keyword` 仅匹配标题和简介。前台按发布时间倒序展示。

`GET /api/ebooks/{id}`

访客查看一本已发布电子书；草稿和不存在的电子书均返回 `40401`。

## 管理端列表与草稿维护

以下接口均要求 `ADMIN` 或 `SUPER_ADMIN` 的 `satoken` 请求头。

`GET /api/admin/ebooks?categoryId=&keyword=&page=1&pageSize=10`

返回全部状态的电子书，按最近更新时间倒序。

`POST /api/admin/ebooks`、`PUT /api/admin/ebooks/{id}`

请求体示例：

```json
{
  "categoryId": 2,
  "title": "认识珊瑚礁生态系统",
  "summary": "草稿可省略；发布时必须填写 20 至 500 个字符。",
  "sourceNote": "项目组自制内容；参考公开科普资料。"
}
```

新建电子书默认是草稿。更新仅允许草稿；已发布电子书须先撤回。`sourceNote` 最长 1000 个字符。

## 封面上传

`POST /api/admin/ebooks/cover?ebookId={id}`

请求为 `multipart/form-data`，文件字段名为 `file`。仅草稿可上传或替换封面；服务端按文件内容识别 JPEG、PNG、WebP，最大 5 MB。成功后返回 `/uploads/covers/...` 相对路径。

## 发布、撤回与删除

- `POST /api/admin/ebooks/{id}/publish`：发布前校验二级分类、标题、20–500 字简介、封面、来源说明及至少一篇正文非空章节。
- `POST /api/admin/ebooks/{id}/unpublish`：将已发布电子书撤回为草稿。
- `DELETE /api/admin/ebooks/{id}`：仅草稿可删除，同时删除其独占封面文件。
