# npm downloader

该模块是 `code` 下的通用 npm 基建，负责按照后端下发的精确快照按需准备 JavaScript 依赖归档：

1. 根据入口包计算后端依赖图的完整闭包；
2. 先向 npm registry 校验所有缺失包的名称、精确版本和 SRI；
3. 全部预检通过后，才从后端给出的 URL 下载 tarball；
4. 校验真实响应的 SRI，并通过临时文件和原子移动写入缓存。

npm 返回的 `dependencies` 不参与客户端解析，依赖关系只以后端快照为准。该模块不依赖
`code:language`，因此动态语言、普通 JS 插件等消费者都可以复用。当前阶段不负责 tar 解包、
`exports` 解析或 QuickJS Module 名称映射。

## 网络请求协议

完整流程包含以下三类 GET 请求。客户端不会请求版本列表、dist-tag、搜索接口，也不会发送 HEAD 请求。

### 1. 发布快照

```text
GET {businessApi}/npm/releases/latest
```

该请求由业务层负责，具体路径可按现有后端规范调整。`code:npm` 不主动请求快照，只接收已经反序列化的
`NpmReleaseSnapshot`。响应格式见下方“后端快照示例”。

### 2. 精确版本元数据

```text
GET {registryBaseUrl}/{encodedPackageName}/{encodedVersion}
```

例如：

```text
GET https://registry.npmjs.org/%40cyxbs%2Flanguage-javascript/1.4.0
```

该请求只针对依赖闭包中未命中本地缓存的包。开始下载任何 tarball 前，所有缺失包都必须完成元数据
预检。内部元数据服务至少需要返回以下字段，其他字段会被忽略：

```json
{
  "name": "@cyxbs/language-javascript",
  "version": "1.4.0",
  "dist": {
    "integrity": "sha512-..."
  }
}
```

客户端只校验 `name`、精确 `version` 和 `dist.integrity`，不会读取响应中的 `dependencies` 或
`dist.tarball`。内部服务可通过 `NpmPackageDownloader` 的 `registryBaseUrl` 参数接入。

### 3. tarball 下载

```text
GET {baseUrl}/{encodedPackageName}/{encodedVersion}.tgz
```

例如：

```text
GET https://cdn.example/npm/%40cyxbs%2Flanguage-javascript/1.4.0.tgz
```

`baseUrl` 来自发布快照顶层 `urls`。多个地址按声明顺序回退；响应必须匹配快照中的 SRI 才会写入
缓存。缓存命中的包不会请求精确版本元数据，也不会重新下载 tarball。

请求顺序固定为：业务层获取快照 → 计算入口依赖闭包 → 检查缓存 → 预检全部缺失包元数据 → 下载
缺失 tarball。任意元数据预检失败时，不会开始 tarball 下载。

后端快照示例：

```json
{
  "releaseTime": "2026.08.03 12:01:10",
  "entries": {
    "@cyxbs/language-javascript": "dist/index.js"
  },
  "urls": ["https://cdn.example/npm"],
  "packages": {
    "@cyxbs/language-javascript": {
      "version": "1.4.0",
      "dependencies": ["@lezer/javascript"],
      "integrity": "sha512-..."
    },
    "@lezer/javascript": {
      "version": "1.5.4",
      "dependencies": [],
      "integrity": "sha512-..."
    }
  }
}
```
