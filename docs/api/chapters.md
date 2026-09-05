# 章节与在线阅读接口

所有接口返回 `{ code, message, data, timestamp }`。电子书与章节 ID 都以十进制字符串返回；前端必须作为字符串保存和传递，避免 64 位整数精度丢失。

章节为线性目录：同一本书下按 `sortOrder` 从小到大排列，没有父子章节。

## 管理端

以下接口要求 `ADMIN` 或 `SUPER_ADMIN` 的 `satoken` 请求头。新增、编辑、删除和排序只允许电子书为 `DRAFT`；已发布书籍须先调用撤回接口。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/ebooks/{ebookId}/chapters` | 读取该书完整章节目录，不返回正文。 |
| GET | `/api/admin/ebooks/{ebookId}/chapters/{chapterId}` | 读取一章正文。 |
| POST | `/api/admin/ebooks/{ebookId}/chapters` | 追加新章节到目录末尾。 |
| PUT | `/api/admin/ebooks/{ebookId}/chapters/{chapterId}` | 编辑标题、正文和来源补充。 |
| DELETE | `/api/admin/ebooks/{ebookId}/chapters/{chapterId}` | 删除章节，并收紧后续目录序号。 |
| PUT | `/api/admin/ebooks/{ebookId}/chapters/order` | 提交完整章节 ID 顺序。 |
| POST | `/api/admin/content-images` | 上传富文本正文图片。 |

新增或编辑正文请求示例：

```json
{
  "title": "珊瑚礁生态系统",
  "content": "<h2>珊瑚礁</h2><p>正文内容。</p><img src=\"/uploads/content/example.png\" alt=\"珊瑚礁\">",
  "sourceNote": "本章资料参考公开海洋科普资料。"
}
```

服务端会净化 HTML，移除脚本、事件属性和危险 URL；净化后的纯文本为空时拒绝保存。`title` 最长 200 字符，`sourceNote` 最长 1000 字符。

排序请求必须包含该书全部章节且不得重复：

```json
{
  "chapterIds": ["2094773790913531906", "2094773790913531907"]
}
```

正文图片使用 `multipart/form-data`，文件字段名为 `file`。只接受真实的 JPEG、PNG、WebP 图片，最大 5 MB；成功返回 `/uploads/content/...` 相对路径。

## 公开阅读

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ebooks/{ebookId}/chapters` | 返回已发布电子书下的已发布章节目录。 |
| GET | `/api/ebooks/{ebookId}/chapters/{chapterId}` | 返回一章已净化的富文本正文。 |
| POST | `/api/ebooks/{ebookId}/chapters/{chapterId}/read` | 记录一次阅读。 |

公开接口不会暴露草稿书籍、草稿章节或不属于该书的章节。阅读接口匿名可调用：同一已登录用户或同一访客地址哈希在 30 分钟内对同一章节只计一次，首次计数会同时增加章节与所属电子书阅读量；重复调用仍返回成功。

前端在登录时为阅读请求携带 `satoken`；匿名请求不携带。阅读页使用 `?chapter=<章节 ID>` 保持刷新及浏览器前进后退时的章节位置。

排序 ID 按数值身份判重，`1` 与 `01` 不能代表两个章节。章节来源字段可通过空字符串或省略值清空，保存后查询仍为空。
