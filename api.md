# 飞牛音乐非官方 Web API 与 FN Connect 接入调研

> 目标：为飞牛音乐 Android 客户端提供网络层实现依据。
>
> 本文来自当前 Web 客户端的页面交互、静态包分析和真实 HTTP 验证，不是飞牛官方 API 文档。fnOS、飞牛音乐或 FN Connect 升级后，路径、签名、字段和行为都可能改变。

## 1. 当前结论

飞牛音乐可以通过两类地址访问：

- 自定义 NAS 域名：`https://{nasHost}/music/`。
- FN Connect 入口：`https://fnos.net/{fnId}/music/`。

自定义域名可直接作为音乐服务基址。FN Connect 入口不能直接作为 API 基址；它是一个浏览器引导页，负责解析 FN ID、检测候选地址、激活 relay，最后跳转到真正的音乐服务。

已经完成的协议验证包括：

- FN Connect 引导页和 FN ID 定位接口。
- HTTPS relay 选择和 `mode=relay` Cookie 握手。
- 飞牛音乐 `authx` 请求签名。
- 账号密码登录和 `music-token` Cookie。
- 初始化、曲库、收藏、最近播放、专辑、歌手、风格、歌单和搜索的只读接口。
- 曲目元数据、歌词、封面和音频 Range 请求。
- FLAC 流的 `206 Partial Content`、`Content-Range`、`Accept-Ranges`、`ETag` 和 `Last-Modified`。
- 收藏创建/删除、播放事件上报，以及歌单创建、编辑、添加曲目、移除曲目、失效曲目清理和删除的完整写生命周期。

完整 FN Connect 只读验证结果为 `35 PASS / 0 FAIL / 0 SKIP`。自定义域名下启用写探针后的最新完整验证结果为 `41 PASS / 0 FAIL / 10 SKIP`；其中歌单写生命周期全部通过，剩余 SKIP 是未显式启用或缺少安全样本的破坏性、异常路径探针。并发覆盖、重复添加/移除和部分业务错误恢复仍需单独验证。

## 2. 证据等级与安全约定

| 标记 | 含义 |
| --- | --- |
| **V：协议实测** | Python 验证器取得了真实 HTTP 状态、响应外形、MIME 或响应头。 |
| **S：静态包确认** | 当前 Web 静态包明确包含路径、方法、签名或调用逻辑。 |
| **A：浏览器观测** | Web UI 交互直接触发了请求或成功状态。 |
| **B：行为推断** | 根据名称和交互推断用途，尚未取得完整协议。 |
| **C：待验证** | Android 实现需要，但现有证据不足。 |

本文使用以下占位符：

- `{fnId}`：FN ID。
- `{nasHost}`：自定义 NAS 域名。
- `{relayHost}`：FN Connect 返回的中继域名。
- `{trackGuid}`、`{albumGuid}`、`{artistGuid}`、`{playlistGuid}`：业务 GUID。
- `{coverId}`：封面标识。
- `{query}`：搜索文本。

本文和验证报告不保存账号、密码、密码摘要、Cookie、Token、真实 GUID、候选 IP 或曲库内容。文中的固定签名常量来自公开下发的当前 Web 静态包，不是用户凭据，也不能视为稳定的官方契约。

## 3. 整体访问流程

### 3.1 自定义域名

```text
https://{nasHost}/music/
        ↓
飞牛音乐登录
        ↓
music-token + authx
        ↓
音乐 API、封面和音频流
```

此路径已由 `verify_api.py` 实测。基础地址为：

```text
https://{nasHost}/music
```

### 3.2 FN Connect

```text
GET https://fnos.net/{fnId}/music/
        ↓
FN Connect HTML 引导页
        ↓
POST https://fnos.net/api/v1/fn/con
        ↓
候选局域网、公网、DDNS 和 relay 地址
        ↓
选择并激活 HTTPS relay
        ↓
mode=relay Cookie
        ↓
https://{relayHost}/music/
        ↓
飞牛音乐登录
        ↓
music-token + authx
        ↓
音乐 API、封面和音频流
```

`https://fnos.net/{fnId}/music/api/...` 不会代理音乐 API。直接请求它会返回 FN Connect HTML，而不是 JSON。

## 4. FN Connect 定位协议

### 4.1 引导页

```http
GET https://fnos.net/{fnId}/music/
Accept: text/html,application/xhtml+xml
```

当前实测响应：

- HTTP 200。
- `Content-Type: text/html`。
- 页面标题为 FN Connect 远程访问。
- 页面脚本提取路径中的 `{fnId}`，并保留 `/music/` 作为最终 route。

引导页本身不返回 NAS 地址，也不通过 HTTP `Location` 完成定位。浏览器 JavaScript 调用定位接口后再执行跳转。

### 4.2 FN ID 定位

```http
POST https://fnos.net/api/v1/fn/con
Content-Type: application/json
authx: nonce={nonce}&timestamp={timestampMs}&sign={sign}
fn-sign: {fnSign}

{"fnId":"{fnId}"}
```

证据：V + S。

当前成功响应使用标准 envelope：

```json
{
  "code": 0,
  "msg": "...",
  "data": {
    "ver": "...",
    "checkSum": "...",
    "fn": [],
    "ddns": [],
    "publicIpv4": [],
    "publicIpv6": [],
    "ipv4": [],
    "ipv6": [],
    "port": {
      "httpPort": 0,
      "httpsPort": 0
    },
    "forbbidPublicIpv6": false
  }
}
```

以上仅表示已确认的顶层字段；数组值、地址和端口示例均不应写入日志或持久化报告。

字段含义按当前 Web 逻辑解释：

| 字段 | 用途 | 证据 |
| --- | --- | --- |
| `fn` | FN Connect HTTPS relay 候选 | V + S |
| `ddns` | NAS 配置的 DDNS 候选 | V + S |
| `publicIpv4`、`publicIpv6` | 公网直连候选 | V + S |
| `ipv4`、`ipv6` | 局域网直连候选 | V + S |
| `port.httpPort`、`port.httpsPort` | 直连端口 | V + S |
| `ver` | NAS/FN Connect 能力版本 | V + S |
| `checkSum` | 新版连通性检测所需数据 | S；具体语义待验证 |

### 4.3 定位请求签名

FN Connect 定位请求同时使用 `authx` 和 `fn-sign`。

当前 Web 包中的定位专用常量为：

```text
AUTHX_PREFIX      = NDzZTVxnRKP8Z0jXg1VAMonaG8akvh
FN_CONNECT_API_KEY = zIGtkc3dqZnJpd29qZXJqa2w7c
```

请求体必须使用紧凑 JSON：

```text
body = {"fnId":"{fnId}"}
bodyHash = MD5_UTF8(body)
nonce = 100000..999999
timestamp = 当前 Unix 毫秒字符串

signingText = AUTHX_PREFIX
              + "_/api/v1/fn/con_"
              + nonce + "_"
              + timestamp + "_"
              + bodyHash + "_"
              + FN_CONNECT_API_KEY

authx.sign = MD5_UTF8(signingText)
```

`fn-sign` 使用同一毫秒时间戳：

```text
fnSignText = "trim_connect`{fnId}`{timestamp}`anna"
fn-sign = SHA256_UTF8(fnSignText)
```

验证脚本显式复用同一个时间戳。当前 Web 包在调用定位函数和请求拦截器时分别读取 `Date.now()`，两次读取通常发生在同一毫秒；服务端允许的偏差仍待验证。

### 4.4 候选地址选择

当前 Web 客户端会检测多类地址：

- 通过 STUN 或 iframe bridge 测试公网、IPv6 和局域网候选。
- 通过 `https://{candidate}/trimcon` 测试 DDNS 和 relay。
- 保留原 route，例如 `/{fnId}/music/` 会转换为 `/music/`。

Android 第一版建议只选择定位结果 `data.fn` 中的 HTTPS relay，并限制允许的域名后缀和端口。当前验证器仅接受：

```text
*.fnos.net:443
*.5ddd.com:443
```

这样可以避免把未经校验的定位结果当作任意 URL 请求。后续如需局域网优先，应单独设计私网地址校验、明文 HTTP 策略和证书处理，不要直接照搬浏览器的探测逻辑。

### 4.5 relay 激活

对选中的 relay 请求最终 route：

```http
GET https://{relayHost}/music/
Origin: https://fnos.net
Referer: https://fnos.net/{fnId}/music/
```

首次请求实测返回自跳转，并设置：

```http
HTTP/2 302
Location: https://{relayHost}/music/
Set-Cookie: mode=relay; Path=/; HttpOnly
```

客户端保存 `mode=relay` 后跟随跳转，第二次请求返回飞牛音乐 HTML：

```http
HTTP/2 200
Content-Type: text/html
```

如果缺少 relay 握手，直接请求 `{relayHost}` 可能被重定向回 `https://fnos.net/{fnId}/music/`。Android 客户端必须在同一 CookieJar 中完成激活和后续音乐请求。

## 5. 飞牛音乐请求签名

### 5.1 通用 `authx`

所有验证器发出的音乐 API 请求都携带 `authx`。当前 Web 包使用：

```text
AUTHX_PREFIX = NDzZTVxnRKP8Z0jXg1VAMonaG8akvh
MUSIC_API_KEY = 6D5602D4-A342-4799-A0F0-BB795E7167D0
```

签名头格式：

```text
authx: nonce={sixDigits}&timestamp={unixMillis}&sign={md5Hex}
```

签名输入：

```text
signingText = AUTHX_PREFIX
              + "_" + requestPath
              + "_" + nonce
              + "_" + timestamp
              + "_" + dataHash
              + "_" + MUSIC_API_KEY

sign = MD5_UTF8(signingText)
```

`requestPath` 是完整 URL pathname。音乐服务通常包含 `/music` 前缀：

```text
/music/api/v1/track/list
```

不要只签 `/api/v1/track/list`。

### 5.2 GET 查询规范化

GET 的 `dataHash` 按当前 Web 客户端执行以下步骤：

1. 从完整 URL 解析 query。
2. 忽略值为字符串 `undefined` 或 `null` 的参数。
3. 按参数名排序。
4. 使用 `URLSearchParams` 编码。
5. 把 `+` 替换为 `%20`。
6. 对整个查询字符串执行 `decodeURIComponent`。
7. 对结果做 UTF-8 MD5。

示例：

```text
URL: /music/api/v1/track/list?size=50&page=1
canonical: page=1&size=50
dataHash: MD5_UTF8("page=1&size=50")
```

参数名大小写参与签名。`trackGUID`、`artistGUID` 和 `playlistGUID` 不能自行改成小写。

### 5.3 非 GET 请求体

非 GET 请求使用 `JSON.stringify(data)` 的结果计算 MD5。Python 验证器等价地使用：

```text
json.dumps(data, ensure_ascii=False, separators=(",", ":"))
```

如果请求没有 data，则计算空字符串的 MD5。签名时使用的 JSON 文本必须与实际发送的字节一致；不要签名格式化 JSON 后再发送紧凑 JSON。

### 5.4 兼容性与安全

`authx` 使用 MD5 是协议兼容要求，不提供现代密码学意义上的安全认证。传输层必须使用 HTTPS。密码摘要、`authx`、Cookie 和 Token 都应按敏感凭据处理，禁止写入普通日志。

签名时间容差、nonce 重放规则和静态常量的跨版本稳定性尚未验证。客户端应允许通过版本适配更新签名实现。

## 6. 飞牛音乐登录与会话

### 6.1 密码登录

```http
POST /api/v1/user/password-login
Content-Type: application/json
authx: ...
```

请求体：

```json
{
  "username": "{username}",
  "password": "{sha256Hex}",
  "deviceId": "{32HexChars}"
}
```

字段规则：

- `password = SHA256_UTF8(用户输入的原始密码)`，输出小写十六进制。
- `deviceId` 为 32 位十六进制标识。Web 客户端会持久化设备 ID；Android 建议每次安装生成一次并安全保存。
- 登录请求本身也需要音乐 `authx`。

成功响应：

```json
{
  "code": 0,
  "msg": "...",
  "data": {
    "user": {},
    "userToken": "{token}"
  }
}
```

Web 客户端读取 `data.userToken` 后写入 Cookie：

```text
music-token={urlEncodedToken}; Path=/; SameSite=Strict
```

服务端登录响应没有在实测中直接设置该 Cookie，因此非浏览器客户端需要自行保存 `userToken`。

### 6.2 Cookie 状态

FN Connect 路径需要两个独立阶段产生的 Cookie：

| Cookie | 来源 | 作用 | 已确认属性 |
| --- | --- | --- | --- |
| `mode=relay` | relay 激活响应 | 让中继把后续请求转发到 NAS | `Path=/; HttpOnly` |
| `music-token` | 客户端从登录响应写入 | 飞牛音乐用户会话 | `Path=/; SameSite=Strict` |

两者都必须随 `{relayHost}` 下的音乐 API、封面和音频请求发送。自定义 NAS 域名不需要 `mode=relay`。

### 6.3 成功与错误判断

普通 JSON 响应使用：

```json
{
  "code": 0,
  "msg": "...",
  "data": {}
}
```

客户端必须同时判断 HTTP 状态和业务 `code`：

```text
成功 = HTTP 状态符合端点预期 AND code == 0
```

错误签名或错误登录体曾实测得到 HTTP 200 和非零业务码。只判断 HTTP 200 会误报成功。未认证的受保护接口实测返回 HTTP 401。

会话有效期、续期接口、主动注销、Token 轮换和过期错误结构仍待验证。

## 7. 已验证的只读接口

以下路径均相对于音乐基础地址 `https://{musicHost}/music`。

### 7.1 初始化与用户

| 方法 | 路径 | 已确认 `data` 顶层字段 | 证据 |
| --- | --- | --- | --- |
| GET | `/api/v1/initialization/state` | `initialized` | V + S |
| GET | `/api/v1/sys/config` | `mediasrvVersion`、`nasOAuth`、`serverGUID`、`serverName`、`serverVersion` | V + S |
| POST | `/api/v1/user/password-login` | `user`、`userToken` | V + S |
| GET | `/api/v1/user/me` | `createdAt`、`guid`、`lastAccessedAt`、`name`、`role`、`updatedAt` | V + S |
| GET | `/api/v1/shared-library/list` | `list` | V + S |
| GET | `/api/v1/task/list` | `list` | V + S |

### 7.2 曲目与播放记录

| 方法 | 路径与参数 | 已确认 `data` 顶层字段 | 证据 |
| --- | --- | --- | --- |
| GET | `/api/v1/track/list?page=&size=&sort=` | `list`、`sort`、`total` | V + S |
| GET | `/api/v1/track/metadata?guid={trackGuid}` | `audioSpec`、`track` | V + S |
| GET | `/api/v1/track/roam-start?deviceId={deviceId}` | `current`、`next`；两者均为 `{ roamId, track }`，曲目对象位于 `track` | V + S |
| GET | `/api/v1/favorite-track/list?page=&size=` | `list`、`total` | V + S |
| GET | `/api/v1/play-history/list?page=&size=` | `list`、`total` | V + S |

### 7.3 专辑、歌手和风格

| 方法 | 路径与参数 | 已确认 `data` 顶层字段 | 证据 |
| --- | --- | --- | --- |
| GET | `/api/v1/album/list?page=&size=&sort=` | `list`、`sort`、`total` | V + S |
| GET | `/api/v1/album/detail?guid={albumGuid}` | `artists`、`barcode`、`coverId`、`createdAt`、`guid`、`name`、`releaseDate`、`trackCount`、`updatedAt` | V + S |
| GET | `/api/v1/track/album-detail/list?albumGUID={albumGuid}&page=&size=` | `list`、`sort`、`total` | V + S |
| GET | `/api/v1/artist/list?page=&size=` | `list`、`sort`、`total` | V + S |
| GET | `/api/v1/artist/detail?guid={artistGuid}` | `albumCount`、`coverId`、`createdAt`、`guid`、`name`、`trackCount`、`updatedAt` | V + S |
| GET | `/api/v1/album/artist-detail/list?artistGUID={artistGuid}&page=&size=` | `list`、`sort`、`total` | V + S |
| GET | `/api/v1/track/artist-detail/list?artistGUID={artistGuid}&page=&size=` | `list`、`sort`、`total` | V + S |
| GET | `/api/v1/genre/list?page=&size=` | `list`、`sort`、`total` | V + S |

当前测试库的风格列表为空，因此尚未发现风格详情或按风格列出曲目的端点。

### 7.4 歌单

| 方法 | 路径与参数 | 已确认 `data` 顶层字段 | 证据 |
| --- | --- | --- | --- |
| GET | `/api/v1/playlist/list` | `list`、`total` | V + S |
| GET | `/api/v1/playlist/batch-detail?guids={playlistGuid}` | `list` | V + S |
| GET | `/api/v1/playlist/detail?guid={playlistGuid}` | `coverId`、`createdAt`、`guid`、`name`、`trackCount`、`updatedAt` | V + S |
| GET | `/api/v1/track/playlist-detail/list?playlistGUID={playlistGuid}&page=&size=` | `list`、`sort`、`total` | V + S |

`batch-detail` 参数名是复数 `guids`，但多个 GUID 的分隔格式和返回顺序仍待验证。

### 7.5 搜索

| 方法 | 路径与参数 | 已确认 `data` 顶层字段 | 证据 |
| --- | --- | --- | --- |
| GET | `/api/v1/search/suggest?q={query}` | `track`、`album`、`artist`、`playlist` | V + S |
| GET | `/api/v1/search/track?q={query}&page=&size=` | `list`、`total` | V + S |
| GET | `/api/v1/search/album?q={query}&page=&size=` | `list`、`total` | V + S |
| GET | `/api/v1/search/artist?q={query}&page=&size=` | `list`、`total` | V + S |
| GET | `/api/v1/search/playlist?q={query}&page=&size=` | `list`、`total` | V + S |

最短搜索词、结果上限、排序和高亮字段仍待验证。

## 8. 媒体接口

### 8.1 歌词

```http
GET /api/v1/lyric/list?trackGUID={trackGuid}
```

成功 `data` 顶层字段：

```text
list, preferred
```

证据：V + S。歌词文本格式、时间轴单位、翻译歌词和空歌词行为仍待验证。

### 8.2 封面

```http
GET /api/v1/static/cover?coverId={coverId}&size={size}
```

已确认行为：

- 本次返回 HTTP 200 和 `image/jpeg`。
- 返回 `Accept-Ranges`、`Content-Length`、`ETag` 和 `Last-Modified`。
- `coverId` 可带 `track_`、`album_` 或 `playlist_` 前缀。

`coverId` 应从列表或详情响应读取，不要根据 GUID 自行拼接。支持的 `size`、缺图行为和条件请求语义仍待验证。

### 8.3 音频流

```http
GET /api/v1/track/stream?guid={trackGuid}
Range: bytes={start}-{end}
```

`Range: bytes=0-0` 实测返回：

```http
HTTP/2 206 Partial Content
Content-Type: audio/flac
Accept-Ranges: bytes
Content-Range: bytes 0-0/{totalBytes}
Content-Length: 1
ETag: ...
Last-Modified: ...
```

证据：V。该结果确认服务支持标准字节范围请求，适合 Android Media3/ExoPlayer 的 seek 和断点读取。

仍待验证：

- 完整请求是否返回 200，以及条件请求行为。
- 多段 Range、非法 Range 和文件变化后的行为。
- MP3、AAC 等其他格式的 MIME。
- 是否存在转码、音质或设备能力参数。
- 会话过期、relay 重连和播放恢复。

### 8.4 播放请求序列

浏览器播放一首曲目时观测到：

```text
track/metadata
→ lyric/list
→ track/stream
→ event/report
```

四个接口均已完成协议验证。当前 Web 构建会在播放器判定曲目已播放后提交 `track_play`；判定阈值、失败队列和重试时机仍由播放器实现控制，服务端幂等语义待继续验证。

## 9. 写接口

当前 Web 静态包把以下端点声明为 POST，UI 也成功触发过收藏和临时歌单生命周期：

| 方法 | 路径 | UI 行为 | 证据 | 未确认内容 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/favorite-track/create` | 收藏曲目 | V + S + A | 重复创建、冲突 |
| POST | `/api/v1/favorite-track/delete` | 取消收藏 | V + S + A | 重复删除、冲突 |
| POST | `/api/v1/playlist/create` | 创建歌单 | V + S + A | 重名与上限错误已识别 |
| POST | `/api/v1/playlist/edit` | 改名或修改封面 | V + S + A | 并发覆盖语义 |
| POST | `/api/v1/playlist/add-track` | 添加一首或多首曲目 | V + S + A | 重复添加的幂等语义 |
| POST | `/api/v1/playlist/remove-track` | 移除一首或多首曲目 | V + S + A | 重复移除的幂等语义 |
| GET | `/api/v1/playlist/purge-track-count?guid={playlistGuid}` | 统计失效曲目 | V + S + A | 已确认返回 `data.total` |
| POST | `/api/v1/playlist/purge-track` | 清除失效曲目 | V + S + A | 空集合幂等语义 |
| POST | `/api/v1/playlist/delete` | 删除歌单 | V + S + A | 删除后不可由服务端恢复 |
| POST | `/api/v1/static/cover/playlist` | 上传歌单封面 | V + S + A/B | 当前 Web 包确认 multipart `file`，结果为 `data.coverId`；Android 使用模拟响应验证，尚未实服上传回归 |
| POST | `/api/v1/event/report` | 播放事件上报 | V + S + A | 服务端去重、触发阈值 |

浏览器和验证脚本使用的临时歌单均在验证结束后删除，收藏状态也会恢复。当前验证报告不把 `OPTIONS` 当作写接口证据，因为这些路径的 `OPTIONS` 请求会落到 SPA HTML。

收藏和播放事件请求体已经由当前 Web 静态包与直连服务双重验证：

```json
{"trackGUID":"{trackGuid}"}
```

该结构同时用于收藏创建和删除。曲目播放事件为：

```json
{
  "events": [
    {
      "eventType": "track_play",
      "occurredAt": 1700000000000,
      "payload": {"trackGUID": "{trackGuid}"}
    }
  ]
}
```

`occurredAt` 使用 Unix 毫秒。直连写验证结果为收藏创建、收藏删除与事件上报均 HTTP 200、`code == 0`，测试结束后收藏状态已恢复。歌单写操作使用独立的临时歌单验证，并在 `finally` 清理；请求体与当前 Web 构建保持一致。

当前 Web 构建确认的歌单 JSON 请求体为：

```json
// create
{"name":"{playlistName}","coverId":"{uploadedCoverId}"}

// edit
{"guid":"{playlistGuid}","name":"{playlistName}","coverId":"{coverId}"}

// add-track / remove-track
{"guid":"{playlistGuid}","trackGUIDs":["{trackGuid}"]}

// delete / purge-track
{"guid":"{playlistGuid}"}
```

写操作中的歌单参数名是 `guid`，不是列表接口使用的 `playlistGUID`。当前 Web UI 允许 1–32 个字符的名称，识别业务码 `160001`（名称已存在）和 `160002`（达到数量上限）。2026-09-21 重新核对 Web 包后确认：默认封面也先作为图片上传，再把返回的真实 `coverId` 传给 create/edit；`playlist_default_1` 至 `playlist_default_4` 仅是 Android 本地模板标识，不能直接当作服务端封面 ID。支持上传 JPG、JPEG、PNG 或 WEBP，Web UI 限制为 5 MiB。Android 已内置 Web 的 `static/assets/img/playlist-covers/1.png` 至 `4.png` 原始资源（344×344），预览与上传共用这些文件，上传不重新编码。来源和 SHA-256 记录于 `docs/web-assets/playlist-covers.json`。上传成功的 ID 沿用编辑草稿检查点用于失败重试。

## 10. 标识符、分页和排序

### 10.1 标识符

- 曲目、专辑、歌手和歌单使用 GUID。
- 元数据和音频流使用查询参数 `guid`。
- 歌词使用 `trackGUID`。
- 关联列表使用 `albumGUID`、`artistGUID` 或 `playlistGUID`。
- 歌单批量详情使用 `guids`。
- 封面使用 `coverId`，常见前缀为 `track_`、`album_`、`playlist_`。

参数名大小写不统一。网络层应逐个端点声明参数名，不要建立自动大小写转换规则。

### 10.2 分页

已确认列表普遍使用：

```text
page=1&size={pageSize}
```

页码从 1 开始。响应通常包含 `list` 和 `total`，部分列表还包含 `sort`。尚未确认：

- `size` 上限。
- 第 0 页、负数页和越界页行为。
- `total` 在数据变化时的一致性。
- 是否存在服务端默认页大小。

### 10.3 排序

已观测示例：

```text
track/list?page=1&size=12&sort=createdAt%2Cdesc
album/list?page=1&size=12&sort=newTrackAddedAt%2Cdesc
```

排序值使用 `字段,方向`。可用字段、方向枚举、多字段排序和非法值错误仍待验证。

## 11. Android 网络层建议

### 11.1 状态机

建议把远程接入建模为明确状态，而不是在每个 Repository 中处理重定向：

```text
UNRESOLVED
  → LOCATING
  → RELAY_SELECTED
  → RELAY_ACTIVE
  → MUSIC_AUTHENTICATED
  → READY
```

建议的失效处理：

| 现象 | 处理 |
| --- | --- |
| API 返回 FN Connect HTML | 清除当前 relay 状态，重新定位 |
| relay 302 回 `fnos.net/{fnId}/...` | 重新执行 relay 激活；失败后重新定位 |
| HTTP 401 | 清除 `music-token`，重新登录 |
| HTTP 200 但 `code != 0` | 按业务错误处理，不进入成功分支 |
| DNS、连接或 TLS 失败 | 在有限退避后重新定位；不要无限重试同一候选 |
| 签名错误 | 校准时钟并检查签名版本；不要自动重复提交密码 |

冷启动不能只用 `/api/v1/sys/config` 判断持久化 Token 是否有效：该端点在当前服务上可在失效会话下成功返回。客户端必须先用受保护的 `/api/v1/user/me` 校验 `music-token`；校验失败时使用本地加密保存的密码摘要重新登录，再加载曲库。该行为已有 MockWebServer 回归测试覆盖。

### 11.2 建议组件

| 组件 | 职责 |
| --- | --- |
| `FnConnectResolver` | 解析入口 URL、签名定位请求、筛选候选 relay |
| `RelaySession` | 激活 relay，管理 `mode=relay` |
| `MusicAuthenticator` | 生成设备 ID、摘要密码、登录并保存 `music-token` |
| `AuthxInterceptor` | 按实际 URL 和请求体计算音乐 `authx` |
| `MusicApi` | 声明已验证的 JSON 接口 |
| `CoverDataSource` | 加载封面并处理缓存 |
| `MusicDataSource` | 为 Media3 提供带 Cookie、`authx` 和 Range 的读取 |
| `SessionCoordinator` | 统一处理 401、relay 回退、重新定位和重新登录 |

签名拦截器必须看到最终 URL pathname 和最终发送的 JSON 字节。不要在签名后再次修改 query、路径或请求体。

### 11.3 Cookie 与持久化

- 使用同一个受控 CookieJar 管理 relay 和音乐请求。
- 按域名、Path、Secure 和过期时间匹配 Cookie，不要把 Cookie 复制给无关主机。
- `mode=relay` 可在进程内缓存；持久化前应确认服务端有效期和安全要求。
- `music-token` 应放入 Android Keystore 支持的加密存储，不写入普通 SharedPreferences。
- 把密码 SHA-256 摘要视为可重放凭据，不要记录或长期明文保存。
- `deviceId` 每次安装生成一次，不应每次请求随机生成。

### 11.4 版本兼容

这些接口没有官方稳定性承诺。客户端应：

- 把 FN Connect 和飞牛音乐签名实现分开版本化。
- 在启动时读取 `/sys/config` 的服务版本字段。
- 对未知 JSON 字段宽容，对必要字段缺失明确失败。
- 不依赖列表项的未验证字段名。
- 保留服务端升级后快速替换路径、常量和签名规则的能力。

## 12. 验证脚本

### 12.1 FN Connect 全流程

```bash
# 入口、定位、relay、登录和基础读取
./scripts/verify_fn_connect.py \
  --entry-url "https://fnos.net/{fnId}/music/"

# relay 激活后执行全部只读音乐 API
./scripts/verify_fn_connect.py \
  --entry-url "https://fnos.net/{fnId}/music/" \
  --full
```

输出：`fn-connect-verification-report.json`。

当前 `--full` 实测：

```text
PASS=35 FAIL=0 SKIP=0
```

其中包括 4 个 FN Connect/relay 步骤、密码登录和 30 个只读音乐探针。

### 12.2 直接音乐服务

```bash
./scripts/verify_api.py --base-url "https://{nasHost}/music"
```

输出：`api-verification-report.json`。

当前默认实测：

```text
PASS=31 FAIL=0 SKIP=11
```

11 个 SKIP 包括 10 个默认不执行的写端点发现项，以及 1 个完整写生命周期提示。启用写探针需要显式参数：

```bash
./scripts/verify_api.py \
  --base-url "https://{nasHost}/music" \
  --include-write \
  --confirm-destructive \
  --write-scope all
```

当前启用写探针后的实测结果：

```text
PASS=41 FAIL=0 SKIP=10
```

验证器会创建一个名称不超过 32 个字符的临时歌单，依次执行编辑、添加曲目、移除曲目、查询并清理失效曲目，最后在 `finally` 中删除临时歌单。报告仅保留脱敏后的协议结果。

如只验证收藏与播放事件，可使用 `--write-scope favorites-events`。所有写验证都必须同时显式提供 `--include-write --confirm-destructive`，防止误触发数据变更。

### 12.3 离线单元测试

```bash
python3 scripts/test_verify_fn_connect.py -v
```

当前结果：6 项全部通过。测试覆盖入口解析、定位签名、relay 白名单、Cookie 握手和报告脱敏，不访问外部服务。

## 13. 移动客户端能力映射

| 能力 | 已知接口或流程 | 当前状态 | 上线前缺口 |
| --- | --- | --- | --- |
| FN Connect | `fnos.net/{fnId}`、`fn/con`、relay 激活 | V + S | 地址变化策略、时间容差、更多品牌域名 |
| 登录 | `user/password-login` | V + S | 续期、注销、NAS OAuth、锁定策略 |
| 初始化 | `initialization/state`、`sys/config`、`shared-library/list` | V | 初始化顺序、库切换 |
| 首页 | 曲目、专辑、歌手、歌单、漫游接口 | V | 漫游补充与结束策略 |
| 曲库分页 | `track/list` | V | 边界、页大小上限、过滤 |
| 搜索 | suggest + 四类分页搜索 | V | 节流、排序、高亮 |
| 曲目详情 | `track/metadata` | V | 完整字段类型和可空性 |
| 封面 | `static/cover` | V | 尺寸枚举、缺图、条件缓存 |
| 歌词 | `lyric/list` | V | 格式、时间轴、多语言 |
| 播放 | `track/stream` | V | 多格式、转码、后台恢复 |
| 收藏 | list + create/delete | 读取、写入 V | 幂等、冲突 |
| 最近播放 | `play-history/list` + `event/report` | 读取、上报 V | 阈值、重试、去重 |
| 歌单 | list/detail + create/edit/add/remove/purge/delete | 读取、写入 V | 自定义封面、冲突、恢复 |
| 风格 | `genre/list` | V | 详情和曲目筛选 |

Android 客户端当前已经开放 FN Connect resolver、relay Cookie、登录、曲库分页、元数据、封面、歌词、Range 播放、收藏、`track_play` 上报，以及歌单创建、改名、默认封面、删除、添加曲目、逐首/批量移除和失效曲目清理。已有歌单的编辑面板支持通过系统照片选择器选择 JPG、PNG、WEBP（最多 5 MiB），点击完成才上传。2026-09-21 经 FN Connect 中继读取当前 Web 静态包确认：上传使用 multipart `file` 字段，响应信封中的 `data.coverId` 用于后续歌单 edit；Authx 按 `JSON.stringify(FormData)` 即 `{}` 签名，而不是对二进制 multipart 请求体签名。上传成功后的 coverId 在编辑草稿内作检查点，后续 edit 失败重试不重复上传。上传协议已添加模拟服务测试，尚未做实服上传回归。

## 14. 待验证清单

### 14.1 FN Connect

- [ ] 定位签名的时间容差和 nonce 重放规则。
- [ ] `ver`、`checkSum` 和 `forbbidPublicIpv6` 的完整语义。
- [ ] relay Cookie 的有效期、IP 绑定和多设备行为。
- [ ] relay 地址变化、故障转移和 DNS 缓存策略。
- [ ] DDNS、局域网和公网直连在 Android 上的安全选择规则。

### 14.2 会话与错误

- [ ] 登录失败、账号锁定和限流错误结构。
- [ ] 400、401、403、404、409 和 5xx 的脱敏响应样本。
- [ ] `music-token` 有效期、续期、主动注销和并发登录。
- [ ] NAS OAuth 登录流程。
- [ ] 签名时间漂移、无效 nonce 和错误 API key 的稳定错误码。

### 14.3 查询和模型

- [ ] 页码边界、`size` 上限、空页和数据变化时的分页一致性。
- [ ] 排序字段、方向和非法值行为。
- [ ] 列表项完整字段名、类型、可空性和版本兼容。
- [ ] 多 GUID 的 `playlist/batch-detail` 参数格式。
- [ ] 搜索最短文本、结果上限、排序和高亮。
- [ ] 风格详情与按风格获取曲目的接口。

### 14.4 写操作

- [x] 收藏 create/delete 的请求体与成功响应。
- [ ] 收藏重复请求、幂等性和冲突错误。
- [x] 歌单 create/edit/add/remove/purge/delete 的请求体、成功响应和临时歌单清理。
- [x] `trackGUIDs` 批量请求结构；Android 端已支持批量移除。
- [ ] 重复添加/移除、同名、数量上限、并发编辑等冲突和失败恢复。
- [ ] 曲目重排；当前 Web 构建未观测到对应端点或交互。
- [ ] 歌单封面生成与自定义封面上传。
- [x] `event/report` 的 `track_play` 事件类型与请求体。
- [ ] `event/report` 的播放阈值、服务端去重和重试规则。

### 14.5 媒体

- [ ] MP3、AAC 和其他格式的 MIME 与 Range 行为。
- [ ] 完整响应、条件请求、多段或非法 Range。
- [ ] 拖动、暂停恢复、切歌和断网重连请求序列。
- [ ] 转码、音质档位和设备能力协商。
- [ ] 歌词格式、编码、时间轴、翻译和空歌词。
- [ ] Android Media3 后台播放、会话过期和 relay 重定位恢复。

## 15. 契约使用边界

在以下条件满足前，不应把本文内容视为稳定公共 API：

1. fnOS 和飞牛音乐没有公开承诺这些端点的兼容性。
2. 签名算法和常量来自当前 Web 静态包，升级后可能改变。
3. 已验证响应只记录顶层外形，没有固化完整 DTO。
4. 写接口仍缺少完整请求和错误契约。
5. 会话续期、故障恢复和不同媒体格式尚未完成 Android 实测。

实现时应把本文当作当前版本的兼容层输入，并以验证脚本作为回归检查。服务端升级后先运行只读验证，再决定是否发布客户端更新。


### 账户密码修改（2026-09-13 客户端源码核实）

从当前音乐 Web 客户端的 API 定义和调用链核实：`POST /api/v1/user/passwd-change`，JSON 请求为 `{"password":"<新密码的 SHA-256 十六进制摘要>"}`。成功依据响应 `code == 0`，`data` 可以为 null。对应的是当前音乐用户；管理其他用户使用不同的用户编辑接口。现有账户未用于改密测试；写入行为通过 MockWebServer 和临时测试用户验证，临时用户已删除。

Android 客户端对此请求关闭连接自动重试、重定向与会话恢复重放。成功后删除活动凭据和记住的旧密码，保留连接地址与用户名，返回登录页。密码输入不参与页面快照保存。角色字符串 `admin` / `member` 由官方客户端枚举确认；未知角色不能赋予管理员权限。


### “我的”管理接口与音质（2026-09-13）

管理读取接口已在当前服务上验证，写入协议来自官方 Web 客户端，并用 MockWebServer 验证请求和失败处理：

| 功能 | 接口 | 关键字段 |
| --- | --- | --- |
| 音乐文件夹 | GET `/api/v1/shared-library/list` | `list`: guid、name、path、metadataPreference、autoDownloadLyric、contentLastChangedAt |
| 添加／编辑文件夹 | POST `/api/v1/shared-library/create`、`/edit` | path、metadataPreference（cloud_preferred / local_only）、autoDownloadLyric；编辑带 guid |
| 移除／扫描文件夹 | POST `/api/v1/shared-library/delete`、`/scan` | guid；移除的是曲库配置，不是文件删除接口 |
| 扫描任务 | GET `/api/v1/task/list` | type、name、total、successCount、failCount、done、canceled、ext.libraryGUID |
| 用户列表 | GET `/api/v1/user/list` | list、sharedLibraryAccess.mode / sharedLibraries |
| 添加／编辑用户 | POST `/api/v1/user/create`、`/edit` | username、password（SHA-256）、sharedLibraryAccess；编辑带 guid，留空密码不发送 |
| 默认权限 | GET / POST `/api/v1/settings/user` | defaultSharedLibraryAccess |
| 服务器名称 | GET / POST `/api/v1/settings/server` | name、lang；更名保留当前 lang |

权限写入格式为 `{mode, guids}`：all 包含将来新增的文件夹；partial 仅包含选定 guid；none 不授权。all / none 发送空 guids。未知权限值不允许静默提升权限。管理写请求关闭自动重试与会话恢复重放；403 刷新角色并离开管理页。现有用户、服务器名称和库配置未用于写入验收。另用临时、无曲库权限的用户实测创建、管理员修改密码、使用新密码登录和自行改密；管理员会话保持有效，临时用户已删除。

标准音质使用 POST `/api/v1/track/transcode`，请求 `{guid, output:{codec:"opus",bitrate:128,channel:2}}`；状态 ready / success 后加载 `/api/v1/track/hls/{guid}/preset.m3u8`。当前服务器已实测生成 Opus、48 kHz、双声道的 fMP4/HLS，样本码率约 126.5 kbps，AVD 使用系统 Opus 解码器持续播放超过一分钟。AAC 参数在当前服务返回 errno 8192，不作为实现方案。测试转码会话已退出。

播放期间每 10 秒 POST `/api/v1/track/transcode/heartbeat`（guid、timestamp 秒数），切换后 POST `/api/v1/track/transcode/quit`（guid）。HLS 清单不缓存；音频片段按服务器、账户和歌曲隔离缓存。原始音质继续使用已有 stream 接口。Wi-Fi 和移动网络偏好独立保存，升级均保持原始音质。
