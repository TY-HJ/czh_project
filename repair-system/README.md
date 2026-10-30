# 宿舍/社区报修工单管理系统

基于 **Spring Boot 3 + Vue 3** 前后端分离的报修工单管理系统。

## 一、系统简介

解决宿舍/社区报修"靠微信群吼、没人跟进、修完没验收、数据没统计"的痛点，核心业务是完整的**工单状态机流转**：

```
[0 待派单] --管理员派单--> [1 处理中] --维修工完成--> [2 待验收] --验收通过--> [3 已完成]
     |                        ^            |  └--验收不通过(退回重修)--┘
     |                        └------------重走处理流程
     └--住户撤销 / 管理员关闭--> [4 已关闭]
```

**亮点功能**（区别于普通增删改查）：
- 工单状态机流转 + 每一步写入流转日志，前端展示"处理进度时间线"
- 管理员派单、住户验收评价（1~5星）、验收不通过自动退回
- 定时任务自动催办：待派单超24小时 / 处理中超48小时，自动给管理员/维修工发站内消息
- ECharts 统计：类型分布饼图、月度趋势折线图、维修工评分排行、平均处理时长
- 三角色权限：住户(RESIDENT) / 维修工(WORKER) / 管理员(ADMIN)，JWT + Spring Security

## 二、技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.3、Spring Security + JWT、MyBatis-Plus、Spring Task |
| 前端 | Vue 3（Composition API）、Vite、Element Plus、Pinia、Axios、ECharts |
| 数据库 | MySQL 8.0 |

## 三、环境准备

- JDK 17+（本项目在 JDK 21 下验证）
- Maven 3.8+
- Node.js 18+（首次运行前请在 `frontend` 目录执行 `npm install`）
- MySQL 8.0

## 四、启动步骤

### 1. 初始化数据库

```bash
mysql -uroot -p < sql/init.sql
```

> 脚本会创建 `repair_system` 库、示例数据和测试账号。
> 数据库连接口令配置在 `backend/src/main/resources/application.yml`（本地为 root/1234，按需修改，请勿提交真实口令到仓库）。

### 2. 启动后端（端口 8080）

用 IDEA 打开 `backend` 目录（或项目根目录），找到入口类
`backend/src/main/java/com/repair/RepairSystemApplication.java`，直接运行 main 方法即可。

> 也可命令行启动: `cd backend && mvn spring-boot:run`

### 3. 启动前端（端口 5173）

```bash
cd frontend
npm install   # 首次需要
npm run dev
```

浏览器访问 http://localhost:5173

### 4. 测试账号（密码均为 `123456`）

| 账号 | 角色 | 说明 |
|---|---|---|
| `admin` | 管理员 | 派单、用户/字典/公告管理、统计大屏 |
| `zhangsan` | 住户 | 提交报修、验收评价、催办 |
| `zhaoliu` | 住户 | 第二个住户，可演示多住户数据隔离 |
| `sunxiaoxiao` | 住户 | 第三个住户，可演示多住户数据隔离 |
| `lisi` | 维修工 | 主修水电/门禁/公共照明 |
| `wangwu` | 维修工 | 主修门窗/电梯/其他 |
| `zhoulei` | 维修工 | 网络故障专职（只接网络类工单，用于演示技能专精派单） |
| `qiancheng` | 维修工 | 水电/门窗/其他（与李、王重叠，用于演示竞争派单与负荷均衡） |

### 5. 辅助填写（本地词库，不联网）

报修和完工两处都支持"少打字"，**全部在前端本地完成，不调用任何接口，断网也能用**：

| 位置 | 做法 |
|---|---|
| 住户提交报修 | 选定「报修类型」后自动预填一段描述；下方「常用写法」标签可一键换成该类型的其他例句，填入后可随意修改 |
| 维修工完成维修 | 弹窗里勾选「故障原因 / 处理方式 / 结果确认」，自动拼成一句处理说明，可再手写补充 |

**改错了能撤回**：点新例句会直接替换，你手改过的内容自动暂存，文本框下方出现一行「撤销」，点一下即可恢复原样 —— 不弹窗、不打断；完工侧勾选重写说明同理。一旦你重新开始手打，暂存自动作废。

词库在 `frontend/src/utils/suggest.js`，按 7 个报修类型分组，直接增删中文文案即可，不用改页面逻辑。

#### 5.1 大模型接入（预留，当前未启用）

后端保留 `/api/ai/**` 接口与调用逻辑，但**前端入口未接、配置项默认为关闭**，当前版本不使用大模型。
这样做是为了避免演示环境断网或未配密钥时点出错误提示。

将来若要启用（三步）：

1. 把 `application.yml` 里的 `ai.enabled` 改为 `true`；
2. IDEA：Run → Edit Configurations → 选中 `RepairSystemApplication` → Environment variables 填
   `AI_API_KEY=sk-你的密钥`（到 <https://platform.deepseek.com> 申请）；
3. 在前端两个表单里加入调用按钮。

> ⚠️ 密钥永远不要写进 `application.yml` 或提交到 GitHub。调用链为「浏览器 → 本项目后端 → 大模型」，
> 密钥只出现在服务端的出站请求头里，前端源码和浏览器网络面板都看不到。



> 三个住户各自只能看到自己的工单，管理员能看到全部，可用于演示数据隔离与派单差异。
> 派单规则（何时自动派、何时转人工）见 `docs/项目交接备忘.md` 第 6.1 节；升级脚本 `sql/upgrade_v31_dispatch.sql`。
> 管理员点「派单」后会列出所有在岗师傅的**当前在办 / 平均评分 / 本栋完成数 / 综合得分**，点行即可指定，第一行标「推荐」；
> 若该师傅没配这类技能会显示「缺技能」，确认后仍可强制派出（避免某种故障只有一个人会修、他又休假时工单卡死）。

**演示数据说明**（两个脚本按需选一个，都可重复执行）：

| 脚本 | 作用 | 什么时候用 |
|---|---|---|
| `sql/restore_seed.sql` | 只留 4 条示例单（1/2 已完成、3 待验收、4 待派单） | 只想演示一条完整流程 |
| `sql/demo_data.sql` | **10 条工单** + 42 条流转日志 + 4 条评价，五种状态齐全 | **默认用这个**，列表干净、图表也有内容 |

```bash
mysql -uroot -p repair_system < sql/demo_data.sql
```

这 10 条不是随便写的：7 种报修类型全覆盖（水电 3、网络 2，其余各 1）、三个住户各有单（4/3/3）、
四个师傅都有完成单和评分（5.0/5.0/4.0/4.0）、五种状态各占一些（待派单 1 / 处理中 2 / 待验收 2 / 已完成 4 / 已关闭 1）。

> 处理中、待验收的时间都贴近当天，刚跑完不会立刻被定时任务改派或催办。**放几天后再演示，重跑一次即可把时间刷新为"刚刚"**。

**建议按此流程体验完整闭环**：住户提交报修 → 管理员派单给李师傅 → 李师傅完成维修 → 住户验收打分 → 管理员查看统计大屏。

## 五、项目结构

```
repair-system/
├── sql/init.sql                    # 建库建表 + 初始数据
├── backend/                        # Spring Boot 后端
│   └── src/main/java/com/repair/
│       ├── controller/             # 接口层(8个Controller)
│       ├── service/                # 业务层(OrderService为状态机核心)
│       ├── mapper/                 # MyBatis-Plus 数据层
│       ├── entity/ dto/ vo/        # 实体/入参/出参
│       ├── security/               # JWT工具 + 认证过滤器
│       ├── config/                 # Security/MybatisPlus/CORS/上传映射
│       ├── common/                 # Result统一返回/全局异常
│       └── task/                   # 超时催办定时任务
└── frontend/                       # Vue3 前端
    └── src/
        ├── api/                    # 接口封装
        ├── views/                  # 页面(login/student/worker/admin/common)
        ├── layout/Layout.vue       # 布局(按角色显示菜单)
        ├── components/             # StatusTag/OrderDetailDialog
        ├── router/                 # 路由+角色守卫
        └── stores/                 # Pinia用户状态
```

## 六、接口一览（核心）

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | /api/auth/login | 公开 | 登录 |
| POST | /api/auth/register | 公开 | 住户注册 |
| POST | /api/orders | 住户 | 提交报修 |
| PUT | /api/orders/{id}/cancel | 住户 | 撤销(仅待派单) |
| PUT | /api/orders/{id}/acceptance | 住户 | 验收(通过/退回+评分) |
| PUT | /api/orders/{id}/assign | 管理员 | 派单(技能不匹配时可强制派出) |
| PUT | /api/orders/{id}/close | 管理员 | 强制关闭 |
| PUT | /api/orders/{id}/finish | 维修工 | 完成维修 |
| PUT | /api/orders/{id}/urge | 报修人/管理员 | 催办 |
| GET | /api/orders/{id}/logs | 登录用户 | 流转日志(时间线) |
| DELETE | /api/messages/{id} | 登录用户 | 删除自己的一条站内消息 |
| DELETE | /api/messages/clear-read | 登录用户 | 清空自己的已读消息(未读保留) |
| GET | /api/stats/* | 管理员 | 统计数据 |

## 七、常见问题

1. **启动报数据库连接失败**：确认 MySQL 已启动、init.sql 已执行、application.yml 密码正确。
2. **前端登录后 403**：确认访问的是 `/api/**` 且后端已启动（vite 已配置代理）。
3. **上传的图片在哪**：保存在后端运行目录的 `uploads/` 下，通过 `/uploads/文件名` 访问。
4. **JWT 密钥**：生产环境请修改 `application.yml` 中的 `jwt.secret`。
