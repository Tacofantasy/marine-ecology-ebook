# 分类接口

所有接口均返回统一结构 `{ code, message, data, timestamp }`；成功业务码为 `0`。

分类 ID 以十进制字符串返回和传递，避免浏览器处理 64 位整数时丢失精度。

## 公开分类树

`GET /api/categories`

访客可调用。返回已排序的两级分类树；一级分类的 `parentId` 为 `null`，`children` 仅包含二级分类。

## 管理端分类树

`GET /api/admin/categories`

需要 `ADMIN` 或 `SUPER_ADMIN` 会话，请求头为 `satoken: <token>`。

## 新增分类

`POST /api/admin/categories`

请求体：`{ "name": "海洋生态系统" }` 创建一级分类；`{ "parentId": 1, "name": "珊瑚礁生态" }` 创建二级分类。分类名称最长 100 个字符；一级名称全局唯一，二级名称在同一上级下唯一。

## 修改分类名称

`PUT /api/admin/categories/{id}`

请求体：`{ "name": "新的分类名称" }`。接口不接受上级分类变更。

## 删除分类

`DELETE /api/admin/categories/{id}`

仅空分类可以删除：仍包含二级分类的一级分类、或仍关联电子书的二级分类会返回 `40901`。
