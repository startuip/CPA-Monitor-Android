# CPA Monitor

CPA Monitor 是面向个人侧载的 CPAMP 只读 Android 客户端，兼容 CPA-Manager-Plus v1.12.6，支持 Android 8.0（API 26）及以上版本。

## 功能

- 首次连接验证 Manager Server、Admin Key 与监控能力，验证全部通过后才保存配置。
- 总览今日调用、Token、估算费用、成功率、RPM/TPM、流量、模型排行、最近失败与采集器状态。
- 用量分析支持今日、7 天、30 天、自定义日期和 Provider、模型、账号、失败状态筛选。
- 请求明细按 50 条分页，使用 CPAMP 的 `before_ms + before_id` 双游标。
- Auth Files 账号按 Provider 分组，展示调用统计和已有的配额快照、过期状态及重置倒计时。
- 聚合与配额使用 Android Keystore 加密后缓存到 Room；请求明细只保留在内存。
- WorkManager 机会性检查低配额、失败率、连接/鉴权和采集器错误，并按规则去重。

## 安全边界

客户端只允许以下操作：

```text
GET  /usage-service/info
GET  /status
GET  /v0/management/dashboard/summary
POST /v0/management/monitoring/analytics
GET  /v0/management/auth-files
POST /v0/management/quota-snapshots/query
GET  /v0/management/model-prices
```

OkHttp 拦截器会拒绝白名单外的路径或方法，并禁止携带凭据自动跟随重定向。Admin Key 与离线缓存由 Android Keystore 中的 AES-GCM 密钥加密后存储；应用禁止截屏、明文 HTTP、备份和网络正文日志。不支持跳过 TLS 验证或信任自签名证书。

## 构建

需要 JDK 17 和 Android SDK Platform 37：

```bash
./gradlew test assembleDebug assembleRelease
./gradlew lint
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

Release 签名可通过 `CPA_MONITOR_STORE_FILE`、`CPA_MONITOR_STORE_PASSWORD`、`CPA_MONITOR_KEY_ALIAS`、`CPA_MONITOR_KEY_PASSWORD` 环境变量，或未提交的 `keystore.properties` 提供。
