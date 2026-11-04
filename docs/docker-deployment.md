# Docker 部署指南

脚手架用一份 Compose 文件编排四个服务：前端 nginx（静态托管 + `/api` 反向代理 + SPA 回退）、后端 Spring Boot jar、Redis、以及可选的内置 MySQL。默认面向单台 Linux/macOS 主机自托管；数据库也可复用宿主机已有实例。

本指南的镜像构建、两种启动模式与文末验证命令均在 macOS（Docker Desktop 29.5.3 / Compose v5.1.4，apple silicon）实测通过，验证记录见文末。

相关文件：

| 文件 | 中文说明 |
| --- | --- |
| `docker-compose.yml` | 服务编排；MySQL 在 `mysql` profile 中，默认不启动 |
| `.env.example` | 环境变量模板，复制为 `.env` 后修改（`.env` 已被 git 忽略） |
| `deploy/mysql-client.cnf` | 内置 MySQL 导入初始化 SQL 时的客户端字符集配置 |
| `scaffold-system/Dockerfile` | 后端镜像：Maven 多阶段构建 → `eclipse-temurin:17-jre` 运行 |
| `scaffold-vue/Dockerfile` | 前端镜像：`node:22-alpine` 构建 → `nginx:1.27-alpine` 托管。平台自身的构建上下文是仓库根（npm workspaces 要把根清单与 `packages/scaffold-ui` 一起交给构建，compose 里写作 `context: .` + `dockerfile: scaffold-vue/Dockerfile`）；交付项目的上下文是 `scaffold-vue` 目录，组件与主题来自 npm 依赖 `@yuanda-lowcode-ai/scaffold-ui` |
| `.dockerignore`（仓库根）与 `scaffold-vue/.dockerignore` | 排除 `node_modules`、`dist`、`target`、`.git`、`lowcode-workspace` 等。Docker 只读「上下文根」的那一份，所以两份各管一边 |
| `package.json` / `package-lock.json`（仓库根） | 平台侧 npm workspaces 声明（`packages/*` + `scaffold-vue`）与锁文件；`@yuanda-lowcode-ai/scaffold-ui` 的物料源码在 `packages/scaffold-ui`，镜像内 `npm ci` 会把它软链进 `node_modules`。这两份清单属平台，不进交付项目 |
| `scaffold-vue/.npmrc` | 前端项目的 npm 配置：缓存目录、关 audit/fund，并把 `@yuanda-lowcode-ai` 这个 scope 钉在官方 registry —— 国内镜像同步新物料包有延迟，不钉住会 `ETARGET` 装不上 |
| `scaffold-vue/nginx.conf` | 静态资源缓存、SPA 回退与 `/api` 反代配置 |

## 前置条件

- Docker Engine（或 Docker Desktop）+ Docker Compose v2，磁盘约 2 GB。
- `sql/` 顶层的初始化 SQL 不写 `CHARSET` / `COLLATE`，建表结果继承数据库默认字符集：目标库必须是 utf8mb4（MySQL 5.7 默认可能是 latin1，会让中文直接乱码）。内置 `mysql` 服务已通过启动参数固定 `utf8mb4` / `utf8mb4_unicode_ci`；复用外部实例时先确认库字符集，必要时 `CREATE DATABASE ... CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`。
- 生成 JWT 密钥：`openssl rand -hex 32`（UTF-8 至少 32 字节且不能全为空白）。

## 快速开始（内置 MySQL）

```bash
cp .env.example .env
# 编辑 .env：至少填入 JWT_SECRET，并把 DB_PASSWORD 改成非默认值
docker compose --profile mysql up -d --build
```

`backend` 对 `mysql` 是**可选依赖**（`depends_on` + `required: false`）：启用 profile 时后端会等 MySQL 健康检查通过再启动，顺序确定；不带 profile 复用外部 MySQL 时该依赖自动跳过，不会因为拉起跑一个多余的数据库。Redis 是硬性健康依赖，前端又等后端 `/api/health` 通过才对外发布，因此这条命令返回即代表可访问。浏览器打开 `http://<主机地址>`（端口由 `WEB_PORT` 控制，默认 80）。

数据持久化在命名卷中，卷名格式是 `<project>_<卷名>`，project 默认取所在目录名（在本目录下即 `scaffold-master_mysql-data`、`scaffold-master_redis-data`）。`down` 保留卷，`down -v` 会连数据一起删，不可恢复。

内置 MySQL 仅首次创建卷时导入 `scaffold-system/sql` **顶层**的 `V1`（建表）→ `V2`（种子数据）→ `V3`（资源监测菜单）
<!-- scaffold:platform-only:start -->
加上平台自身的 `V4`（租户参数与平台管理菜单）、`V5`（项目表与低代码菜单）、`V6`（模型与字段表）、`V7`（工作区状态与同步权限）
<!-- scaffold:platform-only:end -->
，按文件名排序执行；`sql/verify/` 子目录不会被递归导入（双租户验收数据要手工执行）。已有卷不会重跑。

`deploy/mysql-client.cnf` 是必需的：官方 mysql 镜像容器内 locale 为 `C`，导入 SQL 的客户端默认按 latin1 解析 UTF-8 字节，种子中文会被二次编码存库（页面显示成「å‘¨æ˜Žè¿œ」）。这份配置把 `[client]` 字符集固定为 utf8mb4，与 README 要求的 `--default-character-set=utf8mb4` 口径一致。

## 复用宿主机已有 MySQL

已有初始化好的库时，不带 profile 启动，只跑 Redis + 后端 + 前端：

```bash
# .env 中设置：
#   DB_HOST=host.docker.internal   已为 backend 配好 host-gateway 映射，Linux 同样适用
#   DB_NAME / DB_USERNAME / DB_PASSWORD 按实际库配置
docker compose up -d --build
```

外部 MySQL 只监听 127.0.0.1 时容器访问不到，需要先放开监听地址或改走宿主机局域网 IP。

`DB_HOST=host.docker.internal` 与 `DB_PORT` 必须成对设置：`DB_PORT` 是后端实际连接的端口，漏改时它会退回默认 3306，打到宿主机上另一个 MySQL 实例（表现为登录报「系统内部错」而不是连不上内置库）。内置 MySQL 的宿主机映射端口是另一个变量 `MYSQL_HOST_PORT`，走外部模式时两者要指到同一个数字。

外部库不会被自动初始化：需先按 README 的口径导入 `sql/` 顶层的三份 SQL（交付项目只用 `V1`–`V3`；平台自身再加 `V4`–`V7`）（客户端带 `--default-character-set=utf8mb4`、会话 `SET time_zone='+08:00'`），或直接用 `scaffold-admin` 里的 `com.scaffold.admin.util.DbInit`（读同一组 `DB_*` 变量）。自动导入只在内置 `mysql` 服务首次建卷时发生。**升级已有部署**时（例如 PR 2 的 `V4`：新增 `lc_tenant_config`、平台管理菜单，并把 `sys_menu.id` 改成自增），必须手工对目标库执行新增的 `V*.sql` —— 脚本按幂等口径写（`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`，重复执行安全），不执行就会出现表缺失或菜单不显示。

## 环境变量

| 变量 | 中文说明 | 默认值 |
| --- | --- | --- |
| `JWT_SECRET` | JWT 签名密钥，UTF-8 至少 32 字节且不能全为空白；换密钥会使旧 token 失效 | 必填，无默认值 |
| `WEB_PORT` | 前端站点在宿主机暴露的端口 | `80` |
| `DB_HOST` | MySQL 地址；内置服务名为 `mysql`，外部实例填 `host.docker.internal` | `mysql` |
| `DB_PORT` | 后端连接 MySQL 的端口；内置服务填 `3306`，复用外部实例时填该实例真实端口 | `3306` |
| `MYSQL_HOST_PORT` | 内置 MySQL 映射到宿主机的端口，本机 3306 被占用时只改这一项 | `3306` |
| `DB_NAME` | 数据库名称 | `scaffold_system` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 / 密码 | `root` / `root`，务必按环境修改 |
| `DB_TIMEZONE` | 数据库连接与会话时区 | `+08:00` |
| `JSON_TIMEZONE` | JSON 序列化时区，须与 `DB_TIMEZONE` 对齐，否则日期整体偏移 | `GMT+8` |
| `CAPTCHA_ENABLED` | 登录图形验证码开关；生产保持 `true` | `true` |

<!-- scaffold:platform-only:start -->
### 低代码平台侧变量

交付项目用不到这几项，它们只随平台底座部署：

| 变量 | 中文说明 | 默认值 |
| --- | --- | --- |
| `LOWCODE_WORKSPACE_ROOT` | 项目工作区根目录（容器内路径），落在 `lowcode-workspace` 卷上 | `/var/lib/yuanda/workspace` |
| `LOWCODE_SCAFFOLD_SOURCE_ROOT` | 底座源码 checkout 在容器内的路径，交付快照按裁剪清单从这份代码读 | `/var/lib/yuanda/scaffold-src` |
| `LOWCODE_SSH_DIR` | 宿主机上存放部署密钥与 `known_hosts` 的目录，只读挂进后端容器 | `/root/.ssh` |
| `LOWCODE_SCAFFOLD_SRC` | 宿主机上的本仓库 checkout 路径，挂成容器里的底座源码 | compose 文件所在目录 |
| `LOWCODE_GIT_SSH_HOME` | 后端进程读 SSH 配置的主目录 | `/root` |
<!-- scaffold:platform-only:end -->

后端与 Redis 的连接在 compose 内固定为 `REDIS_HOST=redis`、`REDIS_PORT=6379`，Redis 未设密码（`REDIS_PASSWORD` 留空），因此 Redis 端口不对宿主机暴露。

## 日常运维

```bash
docker compose ps                        # 各服务与健康检查状态
docker compose logs -f backend           # 跟踪后端日志
docker compose up -d --build             # 代码更新后重建并滚动发布
docker compose down                      # 停止并移除容器，保留数据卷
docker compose --profile mysql down -v   # 连 MySQL/Redis 数据卷一起删除（不可恢复）
```

## 本地直接跑进程（不起容器）的几条事实

开发期常见的是 `java -jar` 起后端 + `npm run dev` 起前端，下面几条都是真踩过的，写下来省得再查一轮：

- **重打 jar 必须 `clean package`**：`mvn -DskipTests package` 走增量构建时可能几百毫秒就"成功"，fat jar 里还是旧字节码，重启后行为没变还以为是别的问题。怀疑没打进去了就核对一眼：`unzip -l scaffold-admin/target/scaffold-admin-0.1.0-SNAPSHOT.jar | grep <关键依赖或类>`。
- **`JWT_SECRET` 一变，所有已登录会话立刻 401**。这不是 bug：令牌签名校验不过就是未认证。所以重启后端前先确认用的是同一份密钥。
- **别用 `ps eww` 抓旧进程环境变量再 `env $vars` 重启**：值里含空格会被拆坏，密钥被截断后签名校验自然全挂 ——
  本仓库开发时就因此把所有登录会话刷没了一次，`/api/*` 全部 401，只能重新走验证码登录。
  正确做法是把启动变量固化成一份**不入库**的本地文件，例如 `scaffold-system/scaffold-admin/.env.local`
  （`.gitignore` 的 `**/.env.*` 已经覆盖它，`!**/.env.*.example` 那条只放行模板），启动时加载：

  ```bash
  cd scaffold-system/scaffold-admin
  set -a; . ./.env.local; set +a
  java -jar target/scaffold-admin-0.1.0-SNAPSHOT.jar
  ```

  需要给人抄的模板放 `.env.example`（入库），真实值永远只在 `.env.local` 里。
- **本机端口归属**：`3306` / `6379` 是业务库与业务 Redis，本脚手架的测试与本地实例用 `3316` / `6380`（容器 `scaffold-it-mysql` / `scaffold-it-redis`）。跑集成测试前先确认端口归属，别把业务库当测试库写进去。登录验证码答案与会话状态都在这个 Redis 里，清了 Redis 等于所有人重新登录、验证码全部作废。
- **前端命令一律从仓库根跑**（`npm run dev|build|test:unit`）。宿主 `scaffold-vue` 与物料包 `packages/*` 是 npm workspaces，`node_modules` 里那个包是指向 `packages/...` 的软链：**改包内源码宿主即时生效，不用重装**；只有改了依赖清单（`package.json`）才需要再 `npm install` 一次重建软链与锁文件。

<!-- scaffold:platform-only:start -->
## 低代码平台的工作区与仓库凭据

平台要往租户自己的 Git 仓库推固定代码，容器需要三样东西，缺一样就会在「初始化固定代码」这一步报错，
而且报错原因会写进项目的「最近一次动作」，不用翻容器日志。第 4 条不是容器的事，是项目仓库侧的权限：
缺它不会让初始化失败，只会留下告警。

1. **工作区与构建缓存卷**：`lowcode-workspace`（各项目 clone 目录）、`maven-repo`、`npm-cache`。
   工作区只是缓存，可以从「clone + 按裁剪清单全量重生成」重建；构建缓存卷一旦没有，每次生成都要全量重下依赖。
   清空某项目的工作区在「项目管理 → 仓库与工作区 → 清空工作区」里做，
   本地有未提交改动或未推送提交时后端会拒绝执行，平台不会静默丢代码。
   - **工作区路径是相对进程工作目录的**（`scaffold.lowcode.workspace-root` 默认 `./lowcode-workspace`）：
     换个目录启动后端，读到的就是另一个空目录，项目状态却还记着「就绪」，看起来像工作区丢了。
     这种情况点「清空工作区」把状态清回未初始化即可 —— 目录不在时它只重置状态，不碰远端分支；
     随后重新「初始化固定代码」会按当前底座重新全量生成。别手工去改库里的 `sync_status`。
   - **全量初始化会把发布分支上「快照之外」的旧文件删掉**（组件抽成 npm 包之后必须这样，否则项目里
     同时存在包里的组件和上一版拷进去的源码），删除数量出现在「最近一次动作」里；
     主干分支上人工写的东西不在此列，平台只写发布分支。
2. **底座源码 checkout**：容器要能读到本仓库的一份代码（`LOWCODE_SCAFFOLD_SRC` 挂成只读）。
   发布产物就是从这份 checkout 按裁剪清单读出来的，所以它必须是一个 git 工作副本，
   平台的 `base_scaffold_sha` 记的就是它当时的提交号。
   - **必须带 `.git`**：只拷源码树（tarball、`docker cp`）时读不到提交号，`base_scaffold_sha` 为空、
     底座标记 tag 打不出来，这个项目以后做不了「底座升级」的三方合并。平台不静默容忍：初始化照做，
     但会把原因写进项目的「最近一次动作」，项目抽屉的「底座标记 tag」一行也显示未记录。
     补救是挂一份带 `.git` 的 checkout，清空工作区后重新初始化。
   - **只读挂载意味着平台不会 fetch/pull**：下发的一直是这份 checkout 的**当前 HEAD**。
     要下发新版本，由人在宿主机上 `git pull` 到目标提交，平台不代劳，也不该改宿主的代码副本。
   - **动了底座就要重建镜像**：裁剪清单在后端代码里，底座源码在 checkout 里。两者版本错开时，
     交付物是「按旧清单裁新源码」，而 tag 记的是新源码的提交号，base 就成了假话。
     两件事必须成对做，当前没有自动核对（PR 8 可加构建期烘进镜像的 sha 与 checkout HEAD 比对）。
3. **主机 SSH 密钥与 `known_hosts`**：库里只有 `repo_url` 与分支名，不存任何 token。
   私钥必须无口令（或容器内起 ssh-agent），并且目标主机指纹要预先登记：

   ```bash
   # 在部署主机上执行一次，之后 compose 里的 LOWCODE_SSH_DIR 就指这个目录
   mkdir -p /root/.ssh && ssh-keyscan github.com >> /root/.ssh/known_hosts
   ssh -T git@github.com            # 确认部署密钥可用
   ```

   两条真实踩过的坑，报错都不指向真因，先按这里对照：

   - **`publickey: no keys to try` 未必是密钥没配**：Apache SSHD 解析 ed25519 私钥要 BouncyCastle，
     缺依赖时就是这个表现。后端 jar 里应能看到 `BOOT-INF/lib/bcprov-jdk18on-*.jar`
     （`unzip -l target/scaffold-admin-*.jar | grep bcprov`）。
   - **`Session is being closed` 多是代理劫持**：这台机器开着本机 HTTP 代理时，git over ssh 也不该走它 ——
     平台固定直连（与 `ssh` 命令一致）。确实需要代理的，就在 `~/.ssh/config` 里给该主机写 `ProxyCommand`。

   HTTPS 地址（`https://github.com/...`）本期不带 token：匿名读可以，推送会被远端拒（回显
   `Authentication is required...`）。要推 HTTPS 得让平台从主机环境变量读凭据，属未交付的能力。
   推送统一建议 `git@host:org/repo.git`。平台永不 `force-push`，分支保护导致的拒绝会原样回显。
4. **项目仓库要允许推 tag**：平台每交付一份完整的固定代码快照，就给那个提交打附注 tag
   `yuanda/platform-base/{底座提交号}` 并推到项目仓库，它是后续「底座升级」三方合并的 base 侧
   （机制见 `docs/lowcode-platform-proposal.md` §5.2）。仓库侧开了 tag 保护时这一步会被拒：
   平台只在「最近一次动作」里留告警，不回滚已经推好的分支；但补上 tag 之前该项目做不了底座升级，
   需要在仓库侧放行后重新初始化，或由人手工补 tag。平台自己永不移动或删除这个 tag。

初始化与推送是同步执行的 HTTP 请求，仓库较大时可能几十秒。nginx 反代默认 60 秒读超时，
如果超时就给 `/api` 这个 location 加 `proxy_read_timeout 300s;`（`scaffold-vue/nginx.conf`），
不要为了让它过去而改成异步任务——那会引入一套工作区状态需要持久化。
<!-- scaffold:platform-only:end -->

## 验证方法

```bash
curl http://localhost/api/health                       # {"code":200,...,"data":{"status":"UP",...}}
curl -s http://localhost/api/auth/captcha | head -c 80 # captchaEnabled / uuid / img（base64 PNG）
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/system/users   # SPA 深链接回退 200
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/api/system/users  # 无令牌 401
docker exec scaffold-master-mysql-1 mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 \
  -e "SELECT nickname FROM $DB_NAME.sys_user WHERE username='admin';"        # 应为「周明远」而非乱码
```

浏览器访问 `http://localhost`，用 `admin` / 种子密码登录后应能看到工作台侧栏（工作台、系统管理、系统监控、系统工具）与列表数据。

## 注意事项

- 种子管理员 `admin/admin123` 仅供本地开发；对外暴露前必须改密码，`.env` 里的 `DB_PASSWORD`、`JWT_SECRET` 也不要沿用默认值。
- 内置 MySQL 的健康检查要登录，`docker inspect scaffold-master-mysql-1` 能看到 `DB_PASSWORD`，同机其他用户也能读；本机自托管没问题，多人共享的主机请改用外部 MySQL（不带 profile），健康检查就不会带凭据。
- 前端登录页的验证码答案只存 Redis、不回传明文，自动化验证需要绕过时把 `CAPTCHA_ENABLED=false`（后端集成测试即以此关闭），验完记得改回 `true`。
- 80 端口被本机其他 Web 服务占用时改 `WEB_PORT`；Linux 上非 root 用户默认不能绑定 1024 以下端口。
- 前端镜像构建内置 `vue-tsc` 类型检查，类型错误会导致构建失败，这是预期行为。
- `restart: unless-stopped` 让宿主机重启后服务自动恢复；Docker Desktop 需设置为开机自启才会拉起整栈。
- `/api/health` 只证明后端进程活着，**不探测数据库与 Redis**，所以 healthy 不等于数据链路可用。内置 MySQL 模式下启动顺序已由可选依赖保证；外部模式下若数据库比后端晚就绪，或数据库中途重启，最早的几个业务请求会拿到 500，Hikari 是懒初始化、随后自动恢复，排查时看 `docker compose logs backend` 里的 `HikariPool-1 - Start completed`。
- 本指南覆盖单主机自托管，不含域名、TLS 与多副本编排；生产环境建议前面再加一层带证书的 nginx/负载均衡。

## 受限网络下的构建（本机等）

Docker 守护进程不读宿主机 shell 的代理变量，`docker build` 拉基础镜像可能直连失败。两条出路：

1. 配国内加速器：在 `~/.docker/daemon.json` 加 `"registry-mirrors": ["https://docker.m.daocloud.io", "https://docker.1ms.run"]`，然后 `docker desktop restart` 生效。
2. 在 Docker Desktop → Settings → Resources → Proxies 填本机代理（`ProxyHTTPMode` 需为 `manual`，仅写 `HttpProxy` 不生效）。

## 验证记录（2026-11-01）

全部用独立 project name、独立卷、错开端口（`WEB_PORT=80/8181/8182/8183/8186`、`MYSQL_HOST_PORT=3316~3322`）跑通，**未读写本机业务库 `scaffold_system`**；结束后已 `down -v` 清理容器与卷，本机 `scaffold-mysql`、`dsh` 容器状态正常。

- 两个镜像构建成功：后端 471 MB、前端 77 MB；`docker compose config` 在带 / 不带 `mysql` profile 下均校验通过。
- **按本指南原文跑一遍**：`cp .env.example .env` + 改 `.env` + `docker compose --profile mysql up -d --build`（默认 project name、默认卷名 `scaffold-master_mysql-data`），四个容器全 healthy，登录与列表查询通过，文末「验证方法」的每条命令逐条照做均得到预期输出。
- **文档默认形态**（`--profile mysql` + `WEB_PORT=80`）整套拉起：mysql / redis healthy → backend healthy（`restarts=0`，日志零条 `Connection refused`）→ frontend 绑定 80 端口 running。
- **外部 MySQL 形态**（`DB_HOST=host.docker.internal`、`DB_PORT=3321` 配 `MYSQL_HOST_PORT=3321`）在容器内直接调用登录接口通过，`sys_login_log` 真实落库，证明两个端口变量成对设置后连接路径正确；不带 profile 单起 `redis backend` 时 MySQL 不会被可选依赖反向拉起。
- `/api/health` 返回 `status=UP` 且时间为 `+08:00`；`/api/auth/captcha` 返回 `captchaEnabled=true` 与 base64 PNG；缺验证码登录被拒（`code=400 验证码错误或已过期`）；无令牌访问列表接口 401；SPA 深链接 `/system/user`、`/tool/generator`、`/monitor/operlog` 均回退 200。
- 关掉验证码后完成真实登录：取到 token、`/api/auth/me` 返回 35 条权限、`/api/auth/routes` 返回 `/dashboard`、`/system`、`/monitor`、`/tool` 四个顶级路由、`/api/system/users` 返回 3 条种子用户、导出接口返回 `text/csv` 带中文表头与中文数据。
- 浏览器实测：登录页填账号密码 → 工作台侧栏渲染出全部菜单与「中午好，周明远」→ 用户管理表格渲染「周明远 / 平台架构部 / 超级管理员 / 2026-11-01」，控制台无任何报错。验证码开启形态下另测登录页，`img` 元素实际渲染出 130×44 的 `data:image/png` 验证码图。
- 字符集回归：加 `deploy/mysql-client.cnf` 前 `sys_user.nickname` 入库为 `C3A5E28098…`（双重编码，页面显示乱码）；加后为 `E591A8E6988EE8BF9C`，与「周明远」的 UTF-8 字节完全一致，`@@character_set_database=utf8mb4`、排序规则 `utf8mb4_unicode_ci`，三份 SQL 导入后 `sys_menu` 39 行、`sys_dict_data` 7 行。
