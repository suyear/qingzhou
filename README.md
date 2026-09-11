# 轻舟（qingzhou）

低代码集成调度中台：接口组件 → 工作流编排 → 试运行 → 定时调度 → 开放平台调用 → 执行记录。

- 前端：`web/`（Vue 3 + Element Plus + Vite）
- 后端：`server/`（Spring Boot 3 / Java 21）
- 数据库脚本：`sql/`

## 环境

- JDK 21、Maven 3.9+
- Node.js 20+（前端）
- MySQL 8.0、Redis

数据库与 Redis 通过环境变量覆盖，勿把真实密码提交进仓库：

```bash
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=qingzhou
export DB_USERNAME=root
export DB_PASSWORD=your-password
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_PASSWORD=
export AES_KEY=qingzhou-aes256-secret-key-00001
```

## 数据库迁移

按文件名顺序执行：

1. **新库**：`sql/V1__init_schema.sql`（已含调度类型字段）→ `sql/V2__seed_wecom_components.sql`
2. **旧库升级**（`qz_schedule_job` 还没有 `schedule_type` / `trigger_input` 等列）：再执行 `sql/V3__schedule_types.sql`  
   若 V1 已是当前版本，**不要重复执行 V3**，否则会因列已存在而失败。

V3 补齐的能力：固定间隔 / 每天 / 每周 / 一次性调度，以及调度触发入参。

## 启动

```bash
# 后端，默认 http://127.0.0.1:18080
cd server
mvn spring-boot:run

# 前端，默认 http://127.0.0.1:5173（/api 代理到 18080）
cd web
npm install
npm run dev
```

后端未启动时，控制台会提示无法连接服务，而不是把空列表当成「没有数据」。

## 编译注意

Java 21 下 `server/pom.xml` 已为 `maven-compiler-plugin` 配置 Lombok `annotationProcessorPaths`。若本地覆盖了 compiler 插件配置，请保留该段，否则会出现找不到 getter/setter 的编译错误。
