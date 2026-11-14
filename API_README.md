# 拾光 RESTful API（`restful/`）

本站的对外数据接口。它和网站共用 `common/` 的模型、同一张 `newhome` 库、同一套 `uploads/`
目录，所以接口里读到的和网站上看到的是一条内容，不是副本；写进去的东西网站上立刻能改。

## 部署事实

| 项目 | 值 |
| --- | --- |
| 入口 | Apache 虚拟主机文档根 = `restful/web` |
| 测试地址 | `http://192.168.8.10` |
| URL 形式 | **根相对**，`GET /blog`，不带 `/api/restful` 前缀，也不带 `index.php` |
| 请求体 | `application/json`（已注册 `JsonParser`），`/upload` 用 `multipart/form-data` |
| CSRF | 关闭：接口无会话、无 Cookie，不存在 CSRF 面 |
| 路由 | `enableStrictParsing`，只认 [config/main.php](config/main.php) 里写明的规则，其余 404 |
| 鉴权头 | `Authorization: Bearer <token>`；Apache 需 `web/.htaccess` 把该头转给 PHP（已随仓库提供） |

## 快速上手

```bash
BASE=http://192.168.8.10

# 1. 看接口清单（不需要令牌）
curl -s $BASE/

# 2. 登录拿令牌
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"你的用户名","password":"你的密码"}'

# 3. 带令牌读写
TOKEN=...   # 上一步 data.token
curl -s -X POST $BASE/moment -H 'Content-Type: application/json' \
     -H "Authorization: Bearer $TOKEN" \
     -d '{"content":"接口发的第一条动态 #试水#"}'

# 4. 要附图先上传，再把返回的 path 交给内容接口
curl -s -X POST $BASE/upload -H "Authorization: Bearer $TOKEN" \
     -F kind=moment -F file=@photo.jpg
```

## 响应封装

业务成功与失败（含框架异常）都是同一形状，见 [components/ApiResponder.php](components/ApiResponder.php)
和 [components/ApiErrorHandler.php](components/ApiErrorHandler.php)：

```json
{ "code": 200, "message": "内容已创建。", "data": { ... } }
{ "code": 422, "message": "请求参数不合法。", "data": { "errors": { "video_type": ["video_type 只能是 1（Horizontal）、2（Vertical）。"] } } }
```

| HTTP | 含义 |
| --- | --- |
| 200 | 成功（`POST` 创建、`DELETE` 都用 200，`data` 里回显被操作的 `id`） |
| 400 | 请求体不是合法 JSON |
| 401 | 没带令牌 / 令牌无效或过期 / 用户名密码不正确 |
| 403 | 动了别人的内容；账户尚未修改初始密码 |
| 404 | 不存在、类型对不上（`/blog/13` 指向动态）、无权查看的私密内容，以及**路由里没有的动词**（如 `DELETE /blog`） |
| 422 | 字段校验失败，明细在 `data.errors` |
| 429 | 认证接口超过来源 IP 频率限制 |
| 503 | 验证码邮件发送失败；请稍后重试 |
| 500 | 服务端异常；`message` 是通用文案，细节只进 `restful/runtime/logs/api.log` |

`YII_DEBUG=true`（`web/index.php`）时错误响应额外带 `debug{type,file,line,trace}`，
排查完请把它切回生产值。

## 认证

* `POST /auth/login` → `{token, token_type:"Bearer", expires_in, expires_at, user}`。
* 令牌是**自包含**的：`base64url(用户id|过期秒)` + HMAC-SHA256，服务端不落库
  （[services/ApiToken.php](services/ApiToken.php)，与站内邀请链接同一套路数）。
  有效期 `params.api.tokenExpire`，默认 7 天。
* 密钥第一次用到时随机生成，存在 `site_setting.api_token_secret`。
  **要立即作废所有令牌**：删掉这一行，下次签发会换新密钥，旧令牌全部失效。
* `POST /auth/logout` 只做客户端收尾——无状态令牌在到期前一直可用，返回值里写明了这一点。
* `GET /auth/me` 回显当前令牌所属账户（不含 `password_hash`、`auth_key`、验证码哈希）。
* 读写权限沿用前台口径：**只有作者本人**能改能删，管理员不豁免。
* 需要先改初始密码的账户拿到的是 403 + 网站改密页地址，不会发出令牌。

### 注册、邮箱验证与 找回密码

认证请求均使用 `Content-Type: application/json`，响应仍为统一的 `{code,message,data}` 格式。站点部署入口为虚拟主机根目录时，接口 URL 如下；如果部署在子路径下，请在下文 URL 前加部署前缀。

#### 申请注册验证码 `POST /auth/register/code`

```json
{ "email": "alice@example.com" }
```

邮箱格式有效、站点开放注册且该邮箱未出现在用户表中时，系统才会发送 6 位数字验证码；邮箱已注册返回 422，并在 `data.errors.email` 中说明。验证码 10 分钟内有效，最多可尝试 3 次。相同邮箱 60 秒内不能重复发送。

#### 提交注册 `POST /auth/register`

```json
{
  "username": "alice",
  "nickname": "Alice",
  "email": "alice@example.com",
  "code": "012345",
  "password": "Example123",
  "confirmPassword": "Example123"
}
```

`nickname` 可省略。用户名要求 2–24 个字符，只能包含中英文、数字、点、下划线和短横线，且不能使用保留名称；密码要求 8–64 个字符，必须同时包含英文字母和数字。注册入口服从站点 `signup_enabled` 设置；关闭公开注册时返回 403。只有邮箱验证码及所有注册字段均通过校验后，账户才会写入用户表，并直接设为已激活状态。

#### 登录 `POST /auth/login`

```json
{ "username": "alice", "password": "Example123" }
```

登录成功返回 `{token,token_type,expires_in,expires_at,user}`。后续受保护接口发送 `Authorization: Bearer <token>`。无效的用户名或密码返回 401，字段缺失或格式错误返回 422。已注册但尚未验证邮箱的账户不能登录。

#### 请求密码重置 `POST /auth/forgot-password`

```json
{ "email": "alice@example.com" }
```

邮箱格式错误、邮箱未注册，或账户未激活/已停用时返回 422，并在 `data.errors.email` 中说明原因。只有邮箱对应已激活账户时才发送 6 位数字验证码；验证码 10 分钟有效、最多尝试 3 次。此接口会区分账户状态，请客户端按响应提示处理。

#### 确认新密码 `POST /auth/reset-password`

使用邮件中的验证码提交邮箱和新密码：

```json
{
  "email": "alice@example.com",
  "code": "012345",
  "password": "NewExample456",
  "confirmPassword": "NewExample456"
}
```

`code` 推荐作为字符串提交，以保留前导零；如果客户端以 JSON 整数提交，接口会自动补齐为 6 位数字。验证码只能使用一次。错误达到 3 次或验证码过期后，需重新申请验证码；成功后需用新密码重新登录。
密码重置会使站内登录凭据失效，但已签发的无状态 API 令牌仍可使用至过期；如需立即失效，必须轮换 `site_setting.api_token_secret`，这会同时注销所有 API 用户。

#### 频率限制与邮件配置

* 注册验证码申请、注册提交分别按来源 IP 限流，每个接口每小时最多 10 次。
* 登录、找回密码、重置密码分别按来源 IP 限流，每个接口每小时最多 20 次；超过限制返回 429。
* 同一邮箱的同类验证码每 60 秒最多发送一次。
* 验证码哈希和尝试次数存入现有应用缓存，不创建验证码数据表。多台 API 实例部署时，应配置所有实例共享的缓存后端。
* 邮件由现有 SMTP 发信器投递。部署时须配置 SMTP 参数。

## 内容资源

四类内容同构：`post` 主表（`type` 区分）+ 一张类型子表。URL 段就是 `post.type`：

### 分类查询

博客发布时可用分类接口查询可选的 `cate_id`。`GET /cate` 和 `GET /cate/{id}` 都是公开只读接口，
不需要令牌；列表按分类名称升序返回所有分类（包括当前还没有文章的分类），详情不存在时返回 404。
分类的新增、修改和删除仍在站点管理后台进行，REST API 不提供分类写接口。

```bash
# 查询可用于发布博客的分类
curl -sS "$BASE/cate"

# 按 ID 查询一个分类
curl -sS "$BASE/cate/3"
```

列表成功时 `data` 的形状为 `{items: [...], total: 分类总数}`；详情的 `data` 直接返回单个分类对象。
分类字段为 `id`、`name`、`slug`、`description`、`thumb`、`thumb_url`、`created_at` 和 `updated_at`。
`thumb` 和 `thumb_url` 均为根相对路径（例如 `/uploads/thumb/cate/example.jpg`），没有封面时两者均为 `null`。
列表不分页。

| 资源 | 子表 | 列表 | 详情 | 创建 | 更新 | 删除 | 点赞 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 博客 | `blog` | `GET /blog` | `GET /blog/{id}` | `POST /blog` | `PUT`/`PATCH /blog/{id}` | `DELETE /blog/{id}` | `POST /blog/{id}/like` |
| 动态 | `moment` | `GET /moment` | `GET /moment/{id}` | `POST /moment` | `PUT`/`PATCH /moment/{id}` | `DELETE /moment/{id}` | `POST /moment/{id}/like` |
| 图集 | `images`（1:N） | `GET /images` | `GET /images/{id}` | `POST /images` | `PUT`/`PATCH /images/{id}` | `DELETE /images/{id}` | `POST /images/{id}/like` |
| 短视频 | `short_video` | `GET /short_video` | `GET /short_video/{id}` | `POST /short_video` | `PUT`/`PATCH /short_video/{id}` | `DELETE /short_video/{id}` | `POST /short_video/{id}/like` |

### 列表查询参数

`page`（从 1 开始）、`per_page`（默认 20，上限 100）、`sort`（`created_at|updated_at|like_count|views`）、
`order`（`asc|desc`）、`user_id`（只看某人）、`q`（标题模糊）、`tag`（标签名或 slug，`#` 会自动剥掉）。
返回 `{authenticated, page, per_page, total, total_pages, sort, order, items:[...]}`。

### 状态与可见性

`status` 和 `visibility` 是两个不同字段：

* `status=0` 草稿、`status=1` 已发布。访客和其他用户看不到草稿；作者带令牌时可在列表和详情中看到自己的草稿。
* `visibility=0` 私密、`visibility=1` 注册用户可见、`visibility=2` 公开。访客只看公开内容；带令牌的用户可看注册用户可见内容；私密内容仅作者可见。
* 已发布的内容也受 `visibility` 控制。列表和详情使用同一可见性规则；无权查看的详情按 404 处理，避免通过状态码暴露「它存在」。

### 公共字段

读（列表与详情都有）：`id`、`type`、`title`、`status`、`visibility`（0 私密 / 1 仅注册用户 / 2 公开）、
`like_count`、`views`、`liked`、`author{id,username,nickname,avatar_url}`、`tags[]`、`cover_url`、
`created_at`、`updated_at`（ISO-8601，带 `+08:00`）。

所有媒体路径字段及 `avatar_url`、`cover_url`、`file_url`、`thumbnail_url`、`video_url` 等 URL 字段
均只返回根相对路径（如 `/uploads/example.jpg`），不包含主机名。客户端可按自身配置使用
`api.assetBaseUrl` 拼接主机名。

写（四类通用）：`title`、`status`（0/1）、`visibility`（0/1/2）、`tags[]`。
`title` 省略时由正文/说明派生；提交空串等于要求派生一次。

`PUT` 与 `PATCH` 语义相同——**部分更新**，没提交的字段保持原值；`tags` 只在提交 `tags` 或话题字段时
才整组重算，改正文不会悄悄换标签。

### 类型专属字段

| 资源 | 读（列表） | 读（详情多给） | 写 | 创建时必填 |
| --- | --- | --- | --- | --- |
| 博客 | `summary`、`thumb_url`、`categories[]`（`content` 为 `null`） | `content`（已净化） | `summary`、`content`、`thumb`、`cate_id` | `content` |
| 动态 | `text`（正文纯文本截断）、`thumb_url` | `content` | `content`、`thumb` | `content` |
| 图集 | `caption`、`image_count`、`images[]`（`id/title/alt_text/file_url/thumbnail_url/sort_order/created_at`） | 同左 | `caption`、`file_paths[]` | `file_paths[]` |
| 短视频 | `video_path`、`video_url`、`cover_path`、`cover_url`、`duration_seconds`、`video_type`、`video_type_label`、`is_vertical` | `description` | `description`、`video_path`、`thumbnail_path`、`duration_seconds`、`video_type` | `video_path` |

补充口径（都与前台一致）：

* 富文本写入统一过 `RichText::purify()`，白名单外的标签/属性（含 `<script>`、`onclick`）被丢弃。
* 话题：动态从 `content`、图集从 `caption`、短视频从 `title`+`description` 抽取 `#话题#` 并入标签；
  博客按前台口径不解析话题。
* 图集的 `caption` 首行当每张图的 `title`，其余行当 `alt_text`（`Image::applyCaption()`）。
  只提交 `caption` 不动 `file_paths` 时，改写文案、保留顺序。
* 省略 `thumb` 新建博客/动态时，按正文第一张本站插图推出封面缩略图（同前台 `blogThumbnailPath()`）。
* 短视频请求中的 `thumbnail_path` 映射到数据库 `cover_path`；读取短视频时仍返回 `cover_path`、`cover_url`。旧请求字段 `cover_path` 暂时兼容。
* 短视频 `video_type`：1 横屏、2 竖屏。
* 通过 API `POST /upload` 上传的图片和视频均保存在 REST 应用的 `restful/web/uploads/` 对应子目录。

### 媒体字段填什么

内容接口**不接收文件**，只接收 `POST /upload` 返回的站内路径，且会校验文件真实存在：

```json
{ "thumb": "/uploads/blog/202611/AbC...xyz.jpg" }
```

形状必须是 `/uploads/{1~3 段目录}/{随机名}.{扩展名}`，越界路径（`../`、绝对路径、外站 URL）直接 422。
历史数据里的 `/images/default-cover.png` 允许原样回传。

各字段对空值的处理：`thumb`、短视频请求字段 `thumbnail_path`（旧字段 `cover_path` 也兼容）提交空串就是清空；`video_path` 提交空串视为「这次不换视频」
（该列非空，清空没有意义），新建时缺它则 422；`file_paths` 必须是非空数组，想去掉某几张图就提交剩下的完整清单。

## 发布博客、动态、图集与短视频

所有写接口都要先登录，并在每个请求中发送 `Authorization: Bearer <token>`。先将本机文件上传到站点，
博客、动态、图集可一次上传多张图片，响应中的 `imgs` 按上传顺序包含每张图片的服务器相对 `path` 和原文件名 `original_name`；
`thumbnail_path` 是第一张图片的缩略图。
短视频直接使用响应中的 `video_path` 和 `thumbnail_path`。内容接口不接收文件本身。以下示例使用 Bash，上传后请将响应中的路径复制到对应请求体。

### 发布博客

先调用分类列表，选择目标分类的 `id`；不需要分类时可省略 `cate_id` 或传 `0`：

```bash
curl -sS "$BASE/cate"
```

先上传封面图片（`kind=blog`，每张图片不能超过 20 MB；`blog`、`moment`、`images` 都支持一次上传多张图片）：

```bash
curl -sS -X POST "$BASE/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "kind=blog" \
  -F "file[]=@cover.jpg"
```

成功响应中的 `data.thumbnail_path` 是生成的 460×260 缩略图路径；如果缩略图生成失败则为 `null`，可改用
`data.imgs[0].path`。`data.imgs` 按上传顺序返回每张原图的路径和原始文件名，缩略图只根据第一张图片生成。
将 `data.thumbnail_path`（或 `data.imgs[0].path`）填入博客的 `thumb`，再提交 JSON：

```bash
curl -sS -X POST "$BASE/blog" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "第一次用 REST API 发布博客",
    "summary": "一段简短摘要",
    "content": "<p>博客正文，支持经过净化的 HTML。</p>",
    "thumb": "/uploads/thumb/blog/替换为上传响应中的文件名.jpg",
    "cate_id": 0,
    "tags": ["API", "开发"],
    "status": 1,
    "visibility": 2
  }'
```

`content` 必填；`title`、`summary`、`thumb`、`cate_id`、`tags`、`status` 和 `visibility` 可按需提交。
省略 `title` 时由正文生成标题；省略 `thumb` 时会尝试从正文第一张本站博客/动态插图生成封面。
`cate_id` 使用 `GET /cate` 返回的现有分类 ID，必须是非负整数；`0` 表示不挂分类，省略时也不挂分类。
不存在的分类 ID、负数或非整数会返回 422，不会静默发布成无分类文章。发布成功返回 `data.id`，
可用 `GET /blog/{id}` 查看完整正文。

### 发布动态

动态正文必填；可直接发文字，也可先用 `kind=moment` 一次上传多张图片，再按顺序把 `data.imgs` 中每项的 `path`
作为 HTML 图片嵌入 `content`。`data.thumbnail_path` 是第一张图片的缩略图，也可选作动态封面：

```bash
curl -sS -X POST "$BASE/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "kind=moment" \
  -F "file[]=@moment-1.jpg" \
  -F "file[]=@moment-2.jpg"
```

```bash
curl -sS -X POST "$BASE/moment" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content": "<p>今天的随手记录 #日常#</p><p><img src=\"替换为data.imgs[0].path\" alt=\"随手拍\"></p>",
    "thumb": "/uploads/thumb/moment/替换为上传响应中的文件名.jpg",
    "status": 1,
    "visibility": 2
  }'
```

`content` 必填；`thumb` 可省略，省略时接口会尝试从正文第一张本站博客/动态图片推导封面。
`title` 可省略（由正文纯文本生成），`#话题#` 会自动写入标签。成功响应返回 `data.id`；
用 `GET /moment/{id}` 查看完整正文。

### 发布图集

使用 `kind=images` 一次上传图集中的所有图片，每张不超过 20 MB。`data.imgs` 按上传顺序返回每张图片的服务器相对路径
和原始文件名；取每项的 `path` 按顺序作为 `file_paths` 提交，缩略图根据第一张图片生成：

```bash
curl -sS -X POST "$BASE/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "kind=images" \
  -F "file[]=@photo-1.jpg" \
  -F "file[]=@photo-2.jpg"
```

也可以分别上传图片，再把每次响应中的 `data.imgs[0].path` 按展示顺序合并：

```bash
curl -sS -X POST "$BASE/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "kind=images" \
  -F "file[]=@photo-1.jpg"

curl -sS -X POST "$BASE/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "kind=images" \
  -F "file[]=@photo-2.jpg"
```

```bash
curl -sS -X POST "$BASE/images" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "caption": "周末旅行\n湖边和山间的风景 #旅行#",
    "file_paths": [
      "替换为data.imgs[0].path",
      "替换为data.imgs[1].path"
    ],
    "status": 1,
    "visibility": 2
  }'
```

`file_paths` 必须是至少包含一张图片的数组，顺序就是图集展示顺序。`caption` 第一行作为每张图片的标题，
其余行作为图片说明；`caption` 可省略，但建议提供。成功响应返回 `data.id` 和 `data.images`；
用 `GET /images/{id}` 查看图集详情。

### 发布短视频

上传视频（`kind=video`，文件大小由服务器 PHP 的 `upload_max_filesize` 与 `post_max_size` 配置决定；服务器须能调用 FFmpeg 验证并抽帧、调用同目录下的 FFprobe 读取时长和尺寸）：

```bash
curl -sS -X POST "$BASE/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "kind=video" \
  -F "file=@clip.mp4"
```

视频上传成功后，响应直接包含可供内容接口使用的 `video_path` 和 `thumbnail_path`，并自动探测
`duration_seconds`（四舍五入到秒）及 `video_type`（1 横屏、2 竖屏）。若抽帧失败，`thumbnail_path` 为
`null`，此时请求短视频接口可省略 `thumbnail_path`，也可使用自备封面先以 `kind=images` 上传再填其路径。
视频文件与抽取的封面图也保存在 API 应用的 `restful/web/uploads/` 对应子目录下。

```json
{
  "kind": "video",
  "video_path": "/uploads/video/YYYYMM/实际文件名.mp4",
  "thumbnail_path": "/uploads/thumb/video/实际文件名.jpg",
  "duration_seconds": 18,
  "video_type": 2
}
```

```bash
curl -sS -X POST "$BASE/short_video" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "周末散步",
    "description": "记录周末的风景 #生活#",
    "video_path": "/uploads/video/YYYYMM/替换为实际文件名.mp4",
    "thumbnail_path": "/uploads/thumb/video/替换为上传响应中的文件名.jpg",
    "duration_seconds": 18,
    "video_type": 2,
    "tags": ["周末"],
    "status": 1,
    "visibility": 2
  }'
```

`video_path` 必填；`title` 省略时由简介生成。`video_type` 为整数：`1` 横屏、`2` 竖屏。
`duration_seconds`、`thumbnail_path` 和 `tags` 可省略。`thumbnail_path` 会保存到视频的 `cover_path` 列；读取接口仍返回 `cover_path` 和 `cover_url`。旧请求字段 `cover_path` 暂时兼容，但同一请求不能同时提交这两个字段。成功后用 `GET /short_video/{id}` 查看详情。
短视频列表会返回视频/封面路径和 URL；`description` 在详情响应中提供。

## 修改内容（四类资源）

修改需要登录，且只能修改自己创建的内容。对相应资源的 `/{id}` 使用 `PATCH` 或 `PUT`，两者都是**部分更新**：
请求中未出现的字段保持不变。以下示例用 `PATCH`；其中 `123` 替换为创建接口返回的 `data.id`。JSON 字段的详细含义、
媒体路径格式以及空值行为见上文。

修改博客标题、摘要或正文：

```bash
curl -sS -X PATCH "$BASE/blog/123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"更新后的博客标题","summary":"更新摘要","content":"<p>更新后的正文</p>"}'
```

替换博客封面时，先上传图片，再传新返回的路径；传 `"thumb": ""` 可清空封面。

修改动态正文或封面：

```bash
curl -sS -X PATCH "$BASE/moment/123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"content":"<p>更新后的动态 #近况#</p>","thumb":"/uploads/thumb/moment/YYYYMM/替换为新图片名.jpg"}'
```

动态正文变化时会重新提取正文中的话题标签；若不提交 `thumb`，原封面保持不变。

修改图集说明或图片清单：

```bash
curl -sS -X PATCH "$BASE/images/123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "caption": "更新后的图集标题\n更新后的图片说明",
    "file_paths": [
      "/uploads/images/YYYYMM/保留的图片路径.jpg",
      "/uploads/images/YYYYMM/新增的图片路径.jpg"
    ]
  }'
```

`file_paths` 是**完整的新清单**而非增量：清单外的图片记录会被移除，展示顺序按数组顺序更新；
只改 `caption` 而不提交 `file_paths` 会保留图片及顺序。新增图片路径对应的新图片记录默认使用当前图集标题、
空说明；如果要自定义标题和说明，可提交 `title` 与 `alt_text`。接口删除无其他内容引用的旧媒体文件。

修改短视频简介、方向或替换视频：

```bash
curl -sS -X PATCH "$BASE/short_video/123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "更新后的视频标题",
    "description": "更新后的视频简介 #旅行#",
    "video_type": 2,
    "duration_seconds": 24
  }'
```

替换视频时，先以 `kind=video` 上传新文件，再提交新的 `video_path`；要换封面时提交新的 `thumbnail_path`，
传 `"thumbnail_path": ""` 可清除封面。`video_path` 传空串不会清除或替换现有视频。更新标题、简介或话题字段
时会同步更新自动提取的话题标签。

任一资源都可以用以下请求更新发布状态、可见范围或标签；`tags` 是整体替换，空数组可清空显式标签
。动态、图集和短视频会从本次请求中提交的正文/简介/标题字段提取话题；如果只传 `tags: []`，不会自动把数据库里
原来的正文再读出来，已有话题也会一并清除。要保留或重算话题，请同时提交对应字段（动态 `content`、图集 `caption`、
短视频 `title` 或 `description`）：

```bash
curl -sS -X PATCH "$BASE/blog/123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":1,"visibility":2,"tags":["更新后的标签"]}'
```

### 草稿与私密发布

新建内容默认 `status=1`、`visibility=2`。创建草稿可提交 `"status": 0`；创建私密内容可提交
`"visibility": 0`。作者带令牌读取自己的草稿/私密内容；访客和其他用户不能读取。要发布草稿，可对
`/{resource}/{id}` 发 `PATCH` 并提交 `{"status":1}`。

## `POST /upload`

`multipart/form-data`：`file` + `kind`（默认 `images`）。目录、扩展名、大小逐项对齐前台各控制器，
见 [config/params.php](config/params.php)：

| `kind` | 落盘目录 | 单文件上限 | 扩展名 | 附带生成 |
| --- | --- | --- | --- | --- |
| `blog` | `/uploads/blog/YYYYMM/` | 20 MB/张 | jpg/jpeg/png/gif/webp | `/uploads/thumb/blog/` 第一张图片的 460×260 缩略图 |
| `moment` | `/uploads/moment/YYYYMM/` | 20 MB/张 | 同上 | `/uploads/thumb/moment/` 第一张图片的 460×260 缩略图 |
| `images` | `/uploads/images/YYYYMM/` | 20 MB/张 | 同上 | `/uploads/thumb/images/` 第一张图片的 460×260 缩略图 |
| `cate` | `/uploads/thumb/cate/`（平铺） | 20 MB | 同上 | — |
| `video` | `/uploads/video/YYYYMM/` | 由服务器 PHP 配置决定 | mp4/webm/mov/ogv/ogg | `/uploads/thumb/video/` 第 5 帧 460×260 抽图 |

`blog`、`moment` 和 `images` 支持用多个 `file[]` 字段一次上传多张图片，返回
`{kind, imgs, thumbnail_path}`。`imgs` 是按上传顺序排列的图片信息数组，每项包含服务器相对路径 `path` 和原始文件名
`original_name`；只为第一张图片生成缩略图，
路径在 `thumbnail_path`。这三种图片上传响应**不包含** `path`、`url`、`size` 和 `thumbnail_url`。
单张上传也使用 `file[]` 字段。`cate` 仍为单图上传，沿用 `{kind, path, url, size, thumbnail_path, thumbnail_url}`。
例如，两张图片上传成功时：

```json
{
  "kind": "images",
  "imgs": [
    {
      "path": "/uploads/images/YYYYMM/服务器文件名1.jpg",
      "original_name": "第一张图片.jpg"
    },
    {
      "path": "/uploads/images/YYYYMM/服务器文件名2.jpg",
      "original_name": "第二张图片.jpg"
    }
  ],
  "thumbnail_path": "/uploads/thumb/images/第一张图片.jpg"
}
```

若首图缩略图生成失败，`thumbnail_path` 为 `null`，并附带 `warning`；`imgs` 仍返回所有已保存原图路径和原始文件名。
视频上传返回 `{kind, video_path, thumbnail_path, duration_seconds, video_type}`。
视频 `video_type` 为 `1`（横屏）或 `2`（竖屏），按视频显示方向自动判断；
`duration_seconds` 为 FFprobe 时长四舍五入到秒后的整数。缩略图或抽帧失败不影响上传成功，改为在
`warning` 里说明；此时视频上传响应的 `thumbnail_path` 为 `null`，可省略封面字段（详情会按无封面降级渲染）。图片还会用
`getimagesize()` 复核内容，视频会由 FFmpeg 校验视频流并解码首帧，且须能调用同目录下的 FFprobe 读取时长和尺寸；
内容无效的文件会以 422 拒绝，不会保存到站点。

博客、动态、图集及短视频生成的缩略图和视频封面均固定为 `460×260` 像素，保持原图比例并在空白区域补边；
尺寸由 `common/config/params.php` 中的 `thumbnail.width`、`thumbnail.height` 统一配置。

## 删除会不会丢文件

`DELETE` 只回收**这份内容独占**的文件：删之前反查 `blog.thumb`、`moment.thumb`、`images.file_path`、
`short_video.video_path`/`cover_path` 五个媒体列，还有别处引用就留着。上传时顺带生成、没登记在库里的
附属文件（缩略图、视频抽帧）跟着源文件一起回收——但只在源文件也确定无人引用时，避免共用同一张图的
两个图集互相误伤。`/uploads/` 之外的静态资源（默认封面）永不删除。

正文 `<img>` 里内嵌的插图**不在**回收范围内：它们没有独立的库字段可判定归属，宁可留下孤立文件，
也不删掉别人正在引用的图。

## 已知边界

* 详情接口不累加 `views`。前台「同一浏览器每天算一次」靠响应里的去重 Cookie，API 客户端不存 Cookie，
  接上去只会把访问量刷成没有意义的数字。要计数请写网站自己的页面。
* 无状态令牌做不到「单独吊销某一台设备」。需要这个能力就在 `ApiToken::verify()` 里加一层 jti 黑名单。
* RESTful 注册验证和找回密码使用 API 自己的一次性邮件验证码；前台注册/重置密码的邮件链接流程保持不变。
* 认证接口有来源 IP 和邮箱级频率限制；生产环境应使用可供前端与 RESTful 应用共享的缓存服务，避免多进程分别计数。
