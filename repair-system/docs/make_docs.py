# -*- coding: utf-8 -*-
"""生成《报修工单管理系统》需求文档与实施计划 docx"""
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn

DOCS_DIR = r"D:\Workbuddy\work_space\repair-system\docs"

# ---------------- 通用工具 ----------------

def set_run(run, cn="宋体", en="Times New Roman", size=12, bold=False, color=None):
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.name = en
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn('w:rFonts'))
    if rFonts is None:
        rFonts = rPr.makeelement(qn('w:rFonts'), {})
        rPr.append(rFonts)
    rFonts.set(qn('w:eastAsia'), cn)
    if color:
        run.font.color.rgb = RGBColor(*color)

def title(doc, text, sub=None):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_run(p.add_run(text), cn="黑体", size=22, bold=True)
    if sub:
        p2 = doc.add_paragraph()
        p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_run(p2.add_run(sub), size=14)

def h1(doc, text):
    p = doc.add_heading(level=1)
    set_run(p.add_run(text), cn="黑体", size=16, bold=True, color=(0, 0, 0))

def h2(doc, text):
    p = doc.add_heading(level=2)
    set_run(p.add_run(text), cn="黑体", size=14, bold=True, color=(0, 0, 0))

def h3(doc, text):
    p = doc.add_heading(level=3)
    set_run(p.add_run(text), cn="黑体", size=12.5, bold=True, color=(0, 0, 0))

def para(doc, text, indent=True):
    p = doc.add_paragraph()
    if indent:
        p.paragraph_format.first_line_indent = Pt(24)
    set_run(p.add_run(text), size=12)
    return p

def bullet(doc, text):
    p = doc.add_paragraph(style='List Bullet')
    set_run(p.add_run(text), size=12)

def table(doc, headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = 'Table Grid'
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, htext in enumerate(headers):
        cell = t.rows[0].cells[i]
        cell.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_run(cell.paragraphs[0].add_run(htext), cn="黑体", size=11, bold=True)
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            set_run(cells[i].paragraphs[0].add_run(str(val)), size=11)
    if widths:
        for i, w in enumerate(widths):
            for r in t.rows:
                r.cells[i].width = Cm(w)
    doc.add_paragraph()
    return t

def new_doc():
    doc = Document()
    for section in doc.sections:
        section.top_margin = Cm(2.5)
        section.bottom_margin = Cm(2.5)
        section.left_margin = Cm(2.8)
        section.right_margin = Cm(2.8)
    return doc

# ============================================================
# 文档一：需求规格说明书
# ============================================================

doc = new_doc()
title(doc, "宿舍/社区报修工单管理系统", "需求规格说明书  V1.0")
table(doc, ["项目名称", "技术架构", "文档版本", "编写日期"],
      [["宿舍/社区报修工单管理系统", "Spring Boot 3 + Vue 3 + MySQL 8", "V1.0", "2026-10-17"]], [4, 6, 3, 3])

h1(doc, "1  引言")
h2(doc, "1.1  编写目的")
para(doc, "本文档描述宿舍/社区报修工单管理系统（以下简称“本系统”）的功能需求与非功能需求，作为系统设计、开发、测试与验收的依据，同时作为毕业设计（课程设计）的需求分析材料。预期读者为开发者、指导教师及测试人员。")
h2(doc, "1.2  项目背景")
para(doc, "在宿舍与社区日常生活中，水电故障、门窗损坏、家具破损等设施问题频发。目前的报修方式多依赖微信群、口头转告等非正式渠道，普遍存在以下痛点：")
bullet(doc, "报修入口分散：没有统一的提交渠道，信息容易遗漏、描述不规范，缺乏照片佐证；")
bullet(doc, "进度不透明：报修后无法知晓“有没有人管、修到哪一步”，只能反复追问；")
bullet(doc, "派单靠人工：管理员需要自行记忆和协调维修工，任务分配无记录、无提醒；")
bullet(doc, "修完无验收：维修是否彻底解决没有确认环节，服务质量无法量化；")
bullet(doc, "数据无沉淀：报修类型、频次、处理时长等数据散失，无法支撑管理决策。")
para(doc, "本系统以“工单”为核心载体，将报修、派单、维修、验收、评价全流程线上化、可追溯化，并通过超时催办与统计报表提升管理效率与服务质量。")
h2(doc, "1.3  术语定义")
table(doc, ["术语", "定义"],
      [["工单（Repair Order）", "一次报修请求的载体，记录报修人、类型、位置、状态、处理过程与结果"],
       ["状态机", "工单在“待派单→处理中→待验收→已完成/已关闭”之间的受控流转规则"],
       ["流转日志", "工单每次状态变更时自动记录的操作人、动作与备注，构成处理时间线"],
       ["派单", "管理员将待派单工单指派给指定维修工的动作"],
       ["催办", "报修人或管理员对处理缓慢的工单发出的提醒，系统记录催办次数"],
       ["BCrypt", "密码单向加密算法，用于用户口令的安全存储"]], [4, 13])
h2(doc, "1.4  参考资料")
bullet(doc, "GB/T 8567-2006《计算机软件文档编制规范》")
bullet(doc, "GB/T 9385-2008《计算机软件需求规格说明规范》")
bullet(doc, "Spring Boot / MyBatis-Plus / Vue 3 官方文档")

h1(doc, "2  项目概述")
h2(doc, "2.1  项目目标")
para(doc, "构建一个前后端分离的报修工单管理系统，实现报修全流程的线上闭环管理，达到以下目标：")
bullet(doc, "统一报修入口，报修信息结构化（类型、位置、照片），可检索、可追溯；")
bullet(doc, "工单状态受控流转，每一步变更留痕，处理进度对报修人实时可见；")
bullet(doc, "超时自动催办，减少“无人管”的搁置工单；")
bullet(doc, "验收评价机制形成服务质量数据，沉淀为维修工评分排行；")
bullet(doc, "统计报表直观呈现报修分布与处理效率，辅助管理决策。")
h2(doc, "2.2  用户角色")
table(doc, ["角色", "角色标识", "主要职责"],
      [["学生/住户", "STUDENT", "提交报修、上传照片、查询进度、催办、撤销、验收维修结果并评分"],
       ["维修工", "WORKER", "接收维修任务、处理故障并填写处理说明、查看历史任务与个人评分"],
       ["管理员", "ADMIN", "工单派单与关闭、用户管理、维修工管理、字典维护、公告发布、统计查看"]], [3, 3, 11])
h2(doc, "2.3  运行环境")
table(doc, ["类别", "要求"],
      [["服务端", "JDK 17+，内存 2GB 以上，支持 Windows/Linux"],
       ["数据库", "MySQL 8.0，字符集 utf8mb4"],
       ["客户端", "Chrome / Edge / Firefox 等现代浏览器，建议分辨率 1366×768 以上"],
       ["开发环境", "IntelliJ IDEA / VS Code，Maven 3.8+，Node.js 18+"]], [3, 13])
h2(doc, "2.4  设计与实现约束")
bullet(doc, "前后端分离：后端仅提供 RESTful JSON 接口，前端通过 Axios 访问；")
bullet(doc, "认证采用 JWT 无状态令牌，接口权限基于角色注解控制；")
bullet(doc, "图片文件存储于服务器本地磁盘（uploads 目录），通过静态资源映射访问；")
bullet(doc, "工单状态流转必须在 Service 层统一校验，禁止非法状态跳变。")

h1(doc, "3  功能需求")
h2(doc, "3.1  功能模块总览")
table(doc, ["模块编号", "功能模块", "使用角色", "优先级"],
      [["M01", "认证与账号", "全部角色", "高"],
       ["M02", "报修提交", "学生", "高"],
       ["M03", "工单状态机（派单/完成/验收/退回/关闭/撤销/催办）", "全部角色", "高"],
       ["M04", "工单进度时间线", "全部角色", "高"],
       ["M05", "站内消息与超时催办", "全部角色", "中"],
       ["M06", "维修工任务台", "维修工", "高"],
       ["M07", "用户管理", "管理员", "中"],
       ["M08", "字典管理（报修类型/位置）", "管理员", "中"],
       ["M09", "公告管理", "管理员/全部", "低"],
       ["M10", "数据统计可视化", "管理员", "中"]], [2.5, 8, 3, 2.5])

h2(doc, "3.2  M01 认证与账号")
bullet(doc, "FR-M01-1 登录：用户以用户名+密码登录，密码校验采用 BCrypt 比对；登录成功签发 24 小时有效的 JWT 令牌，返回用户基本信息与角色。")
bullet(doc, "FR-M01-2 注册：开放学生（STUDENT）自助注册，用户名全局唯一；维修工与管理员账号仅由管理员创建。")
bullet(doc, "FR-M01-3 权限控制：所有业务接口按角色鉴权，越权访问返回 403；被禁用账号登录时明确提示。")
bullet(doc, "FR-M01-4 用户管理：管理员可分页查询用户（按角色、姓名/用户名关键字过滤），新增/编辑用户、重置密码、启用/禁用、删除账号。")

h2(doc, "3.3  M02 报修提交")
bullet(doc, "FR-M02-1 学生选择报修类型（字典）、故障位置（楼栋/楼层/房间，字典）、填写标题与详细描述，可选择“普通/紧急”优先级。")
bullet(doc, "FR-M02-2 支持上传 0~N 张故障照片（单张≤10MB，仅限 jpg/png/gif/webp/bmp），照片随工单保存并在详情中查看。")
bullet(doc, "FR-M02-3 提交成功后系统自动生成唯一工单号（规则：yyyyMMdd + 当日4位流水号），状态置为“待派单”，并写入第一条流转日志、通知全部管理员。")

h2(doc, "3.4  M03 工单状态机（核心）")
para(doc, "工单状态定义如下：0-待派单、1-处理中、2-待验收、3-已完成、4-已关闭。允许的状态流转及触发条件如下：")
table(doc, ["当前状态", "触发动作", "操作角色", "目标状态", "约束与联动"],
      [["待派单", "派单（选择维修工）", "管理员", "处理中", "记录派单人与派单时间，站内消息通知维修工"],
       ["待派单", "撤销", "报修人", "已关闭", "仅报修人本人可撤销"],
       ["待派单/处理中/待验收", "关闭", "管理员", "已关闭", "强制终结，通知报修人"],
       ["处理中", "完成维修（填写处理说明）", "维修工", "待验收", "处理说明必填，记录完成时间，通知报修人"],
       ["待验收", "验收通过（评分1~5星+评语）", "报修人", "已完成", "评分必填，生成评价记录关联维修工，通知维修工"],
       ["待验收", "验收不通过（说明原因）", "报修人", "处理中", "退回维修工重新处理，清空完成时间，通知维修工"],
       ["待派单/处理中", "催办", "报修人/管理员", "状态不变", "催办次数+1，写入日志并通知对方"]], [3, 4.2, 2.6, 2.4, 6])
para(doc, "约束：任何不满足上表的状态变更请求必须被拒绝并给出明确错误提示；每次成功流转均自动写入流转日志（操作人、动作、备注、时间）。")

h2(doc, "3.5  M04 工单进度时间线")
bullet(doc, "FR-M04-1 工单详情页按时间正序展示全部流转日志（动作中文名、操作人、备注、时间），形成可视化时间线。")
bullet(doc, "FR-M04-2 列表页以不同颜色标签区分工单状态，管理员列表展示催办次数角标。")

h2(doc, "3.6  M05 站内消息与超时催办")
bullet(doc, "FR-M05-1 关键节点自动产生站内消息：新工单通知管理员、派单通知维修工、完成通知报修人、验收结果通知维修工、退回/关闭/催办通知相关人。")
bullet(doc, "FR-M05-2 前端定时展示未读消息数，支持一键全部已读与逐条已读。")
bullet(doc, "FR-M05-3 超时催办定时任务：待派单超过 24 小时→通知管理员；已派单超过 48 小时未完成→通知维修工与管理员。任务每 30 分钟执行一次。")

h2(doc, "3.7  M06 维修工任务台")
bullet(doc, "FR-M06-1 维修工查看分派给自己的任务，默认展示处理中与待验收的未完结工单，可按状态筛选。")
bullet(doc, "FR-M06-2 对“处理中”工单填写处理说明后标记完成；可查看已完成工单的历史与获得评分。")

h2(doc, "3.8  M07~M09 管理端基础模块")
bullet(doc, "FR-M07 用户管理：见 FR-M01-4。")
bullet(doc, "FR-M08 字典管理：维护报修类型（名称、排序）与位置（楼栋、楼层、房间）的增删改查，供报修提交时下拉选择。")
bullet(doc, "FR-M09 公告管理：管理员发布、编辑、删除公告；全部用户可在公告列表查看。")

h2(doc, "3.9  M10 数据统计可视化（管理员）")
table(doc, ["统计项", "口径", "呈现形式"],
      [["总览指标卡", "工单总数、待派单数、处理中数、待验收数、已完成数", "数字卡片"],
       ["报修类型分布", "各报修类型的工单数量占比", "饼图"],
       ["月度工单趋势", "近几个月每月提交的工单数量", "折线图"],
       ["维修工评分排行", "各维修工收到的平均星级与评价数", "条形图"],
       ["平均处理时长", "已完成工单从提交到完成的平均小时数", "指标卡"]], [4, 8.5, 3.5])

h1(doc, "4  非功能需求")
table(doc, ["类别", "需求描述"],
      [["性能", "常规列表查询响应时间 ≤ 1s（万级数据量）；分页查询默认每页 10 条"],
       ["安全", "密码 BCrypt 加密存储；JWT 签名防篡改；接口按角色鉴权；上传文件限制类型与大小；SQL 全部经 MyBatis-Plus 参数化防注入"],
       ["可用性", "统一响应结构（code/msg/data）与全局异常处理，前端以 Toast 明确反馈错误原因"],
       ["兼容性", "主流现代浏览器；后端接口前后端独立演进，仅以 REST 契约耦合"],
       ["可维护性", "分层架构（Controller-Service-Mapper），状态流转集中于 Service；统一日志与异常处理"],
       ["数据完整性", "关键业务（状态流转、评价写入）使用事务保证一致性；工单号唯一约束"]], [3, 13.5])

h1(doc, "5  数据需求")
para(doc, "数据库共 8 张核心表，主要结构如下（详细字段见 sql/init.sql）：")
table(doc, ["表名", "说明", "关键字段"],
      [["user", "用户表", "username(唯一)、password(BCrypt)、real_name、phone、role、status"],
       ["repair_type", "报修类型字典", "type_name、sort"],
       ["location", "位置字典", "building、floor、room"],
       ["repair_order", "报修工单（核心）", "order_no(唯一)、user_id、type_id、location_id、status、worker_id、priority、urge_count、handle_note、submit/assign/finish/accept_time"],
       ["order_log", "工单流转记录", "order_id、operator_id、action、remark"],
       ["evaluation", "验收评价", "order_id、worker_id、user_id、score(1~5)、content"],
       ["notice", "公告", "title、content、admin_id"],
       ["message", "站内消息", "receiver_id、content、is_read"]], [3, 4.5, 9])

h1(doc, "6  接口需求（核心）")
table(doc, ["方法", "路径", "角色", "说明"],
      [["POST", "/api/auth/login", "公开", "登录，返回JWT"],
       ["POST", "/api/auth/register", "公开", "学生注册"],
       ["POST", "/api/orders", "学生", "提交报修"],
       ["PUT", "/api/orders/{id}/cancel", "学生", "撤销（仅待派单）"],
       ["PUT", "/api/orders/{id}/acceptance", "学生", "验收（通过/退回+评分）"],
       ["PUT", "/api/orders/{id}/assign", "管理员", "派单"],
       ["PUT", "/api/orders/{id}/close", "管理员", "强制关闭"],
       ["PUT", "/api/orders/{id}/urge", "报修人/管理员", "催办"],
       ["PUT", "/api/orders/{id}/finish", "维修工", "完成维修"],
       ["GET", "/api/orders/{id}/logs", "登录用户", "流转日志时间线"],
       ["GET", "/api/stats/*", "管理员", "统计数据"],
       ["POST", "/api/files/upload", "登录用户", "报修图片上传"]], [1.8, 6.5, 3.5, 5])

h1(doc, "7  验收标准")
bullet(doc, "三个角色按“提交报修→派单→完成→验收→评价”完整走通，状态流转与日志记录准确无误；")
bullet(doc, "非法状态操作（如重复派单、非报修人验收）被正确拒绝并提示；")
bullet(doc, "超时工单能被定时任务识别并产生催办消息；")
bullet(doc, "统计图表数据与库内数据一致；")
bullet(doc, "所有列表支持分页，图片上传与回显正常。")

doc.save(DOCS_DIR + r"\报修工单管理系统-需求规格说明书.docx")
print("DOC1 OK")

# ============================================================
# 文档二：项目实施计划
# ============================================================

doc2 = new_doc()
title(doc2, "宿舍/社区报修工单管理系统", "项目实施计划  V1.0")
table(doc2, ["项目名称", "技术架构", "文档版本", "编写日期"],
      [["宿舍/社区报修工单管理系统", "Spring Boot 3 + Vue 3 + MySQL 8", "V1.0", "2026-10-17"]], [4, 6, 3, 3])

h1(doc2, "1  项目概述与范围")
para(doc2, "本计划规划“宿舍/社区报修工单管理系统”从环境搭建、迭代开发、测试到交付的全过程。项目范围以《需求规格说明书 V1.0》为准，包含 M01~M10 共十个功能模块；WebSocket 实时推送、Excel 导出、RBAC 权限重构、智能派单等列入二期优化范围，本期不实施。")

h1(doc2, "2  技术选型")
table(doc2, ["层次", "技术", "选型理由"],
      [["后端框架", "Spring Boot 3.3", "生态成熟、自动装配，适合快速构建 REST 服务"],
       ["ORM", "MyBatis-Plus", "通用 CRUD 零 SQL，复杂查询用注解 SQL，开发效率高"],
       ["认证授权", "Spring Security + JWT", "无状态令牌适配前后端分离，注解式角色控制简洁"],
       ["定时任务", "Spring Task", "内置支持，满足超时催办这类轻量调度，无需引入 MQ"],
       ["前端框架", "Vue 3 + Vite", "Composition API 组合式开发，Vite 冷启动快"],
       ["UI 组件", "Element Plus", "组件丰富（表格/表单/弹窗/消息），中后台标准方案"],
       ["状态管理", "Pinia", "Vue3 官方推荐，管理登录态"],
       ["图表", "ECharts 5", "统计可视化能力全面，文档丰富"],
       ["数据库", "MySQL 8.0（utf8mb4）", "开源稳定，InnoDB 事务保证状态流转一致性"]], [2.8, 4.2, 9.5])

h1(doc2, "3  开发模式与里程碑总览")
para(doc2, "项目采用迭代增量开发，每个里程碑结束后进行阶段验证（可运行、可演示），总体分七个阶段。以单人全职投入估算，总工期约 15 个工作日；可根据实际并行压缩。")
table(doc2, ["阶段", "里程碑", "主要产出物", "工期", "完成判据"],
      [["S1", "环境与骨架", "前后端工程初始化、数据库建库建表、登录接口前后端联通", "2天", "浏览器可登录并进入布局页"],
       ["S2", "认证与用户管理", "JWT登录/注册、角色路由守卫、管理员用户管理", "2天", "三角色登录后菜单各不相同，越权被拦截"],
       ["S3", "字典与报修提交", "类型/位置字典管理、报修提交（含图片上传）", "2天", "学生可完整提交一条带图报修"],
       ["S4", "工单状态机（核心）", "派单/完成/验收/退回/撤销/关闭、流转日志、进度时间线", "3天", "全流程走通，非法流转被拒绝"],
       ["S5", "消息与定时催办", "站内消息、未读角标、超时催办定时任务", "2天", "超时工单自动产生催办消息"],
       ["S6", "统计可视化", "指标卡+四类 ECharts 图表", "2天", "图表数据与数据库一致"],
       ["S7", "测试与交付", "功能测试用例执行、缺陷修复、部署说明、答辩演示脚本", "2天", "验收标准全部通过"]], [1.5, 3.4, 6.3, 1.5, 4.3])

h1(doc2, "4  详细任务分解（WBS）")
h2(doc2, "4.1  S1 环境与骨架")
bullet(doc2, "1.1 安装配置 JDK17+/Maven/Node/MySQL，执行 sql/init.sql 建库；")
bullet(doc2, "1.2 初始化 Spring Boot 工程（依赖、application.yml、统一返回 Result、全局异常处理）；")
bullet(doc2, "1.3 初始化 Vue3 工程（Vite、Element Plus、Axios 封装、路由骨架、Pinia）；")
bullet(doc2, "1.4 配置 CORS 与 /uploads 静态映射，跑通一个受 token 保护的测试接口。")
h2(doc2, "4.2  S2 认证与用户管理")
bullet(doc2, "2.1 JwtUtil 与认证过滤器、SecurityConfig（放行规则、BCrypt 注入）；")
bullet(doc2, "2.2 登录/注册接口与前端登录页、注册页；")
bullet(doc2, "2.3 路由守卫按角色拦截（meta.roles）；Layout 侧边菜单按角色渲染；")
bullet(doc2, "2.4 管理员用户管理页（分页/新增/编辑/禁用/删除）。")
h2(doc2, "4.3  S3 字典与报修提交")
bullet(doc2, "3.1 报修类型、位置字典的 CRUD 接口与管理页；")
bullet(doc2, "3.2 图片上传接口（类型白名单、大小限制、UUID 重命名）；")
bullet(doc2, "3.3 学生报修表单（字典级联下拉、多图上传、优先级选择）与提交事务。")
h2(doc2, "4.4  S4 工单状态机（关键路径，重点投入）")
bullet(doc2, "4.1 状态常量与流转规则表设计，Service 层统一校验方法；")
bullet(doc2, "4.2 派单、完成、验收通过、验收退回、撤销、关闭、催办七个动作接口；")
bullet(doc2, "4.3 流转日志写入与时间线查询接口；前端 OrderTimeline/StatusTag 组件；")
bullet(doc2, "4.4 三角色工单列表页（我的工单/维修任务/工单管理）与详情弹窗。")
h2(doc2, "4.5  S5 消息与定时催办")
bullet(doc2, "5.1 message 表读写服务、未读数接口、消息中心页；")
bullet(doc2, "5.2 各业务动作埋点发送站内消息；")
bullet(doc2, "5.3 OrderTimeoutTask 定时任务（24h/48h 规则）与日志观测。")
h2(doc2, "4.6  S6 统计可视化")
bullet(doc2, "6.1 统计 SQL（类型分布/月度趋势/评分排行/平均时长）与接口；")
bullet(doc2, "6.2 Dashboard 页：指标卡 + ECharts 图表联调。")
h2(doc2, "4.7  S7 测试与交付")
bullet(doc2, "7.1 按测试计划执行功能测试并修复缺陷；")
bullet(doc2, "7.2 编写 README（启动步骤、账号、常见问题）；")
bullet(doc2, "7.3 准备演示数据与答辩演示脚本。")

h1(doc2, "5  测试计划")
h2(doc2, "5.1  测试策略")
para(doc2, "以手工功能测试为主、接口测试为辅；核心状态机补充 JUnit 单元测试。每阶段完成即执行对应回归。")
h2(doc2, "5.2  核心测试用例（节选）")
table(doc2, ["编号", "用例", "前置条件", "步骤", "预期结果"],
      [["TC01", "正常登录", "账号存在且启用", "输入正确用户名密码登录", "返回token，跳转对应角色首页"],
       ["TC02", "错误密码登录", "账号存在", "输入错误密码", "提示“用户名或密码错误”"],
       ["TC03", "提交带图报修", "学生已登录", "填写表单上传2张图片提交", "生成工单号，状态待派单，管理员收到消息"],
       ["TC04", "管理员派单", "存在待派单工单", "选择维修工确认派单", "状态变处理中，维修工收到消息"],
       ["TC05", "重复派单", "工单已派单", "再次调用派单接口", "拒绝并提示“该工单已派单”"],
       ["TC06", "完成维修", "维修工持有处理中工单", "填写处理说明提交", "状态变待验收，报修人收到消息"],
       ["TC07", "验收通过", "工单待验收", "打5星提交验收", "状态已完成，生成评价记录，维修工收到消息"],
       ["TC08", "验收退回", "工单待验收", "选择不通过并填原因", "状态回处理中，维修工收到退回通知"],
       ["TC09", "非本人撤销", "学生A的工单", "学生B调用撤销接口", "拒绝并提示无权限"],
       ["TC10", "超时催办", "存在提交超24h的待派单", "等待/触发定时任务", "管理员收到超时提醒消息"],
       ["TC11", "越权访问接口", "学生已登录", "学生调用管理员统计接口", "返回403"],
       ["TC12", "统计一致性", "库内有已知数据", "打开统计大屏", "各图表数值与SQL统计结果一致"]], [1.4, 3, 3, 4, 5])
h2(doc2, "5.3  缺陷管理")
para(doc2, "缺陷按“严重/一般/轻微”分级记录；严重缺陷（状态机错乱、权限绕过、数据不一致）必须清零方可交付。")

h1(doc2, "6  风险管理")
table(doc2, ["风险", "概率", "影响", "应对措施"],
      [["状态机分支遗漏导致流程漏洞", "中", "高", "先写流转规则表再编码；S4 阶段安排专项非法流转测试"],
       ["环境差异（JDK/MySQL版本、编码）", "中", "中", "统一 JDK17+/MySQL8；SQL 脚本固定 utf8mb4；README 写明环境要求"],
       ["图片上传的安全与磁盘占用", "低", "中", "类型白名单+大小限制+UUID重命名；预留切换 OSS 的接口层"],
       ["进度延期", "中", "中", "里程碑缓冲 10%；优先保障核心状态机，统计/公告可后置"],
       ["催办任务重复发送消息", "低", "低", "按状态+时间阈值筛选，任务幂等；必要时记录上次催办时间"]], [5.5, 1.8, 1.8, 7.4])

h1(doc2, "7  交付物清单")
bullet(doc2, "系统源码：backend/（Spring Boot 工程）、frontend/（Vue3 工程）；")
bullet(doc2, "数据库脚本：sql/init.sql（建库建表与初始数据）；")
bullet(doc2, "文档：需求规格说明书、项目实施计划、README（部署运行说明）；")
bullet(doc2, "测试记录：功能测试用例执行结果与缺陷清单；")
bullet(doc2, "演示材料：答辩演示脚本与演示数据。")

h1(doc2, "8  部署方案")
bullet(doc2, "开发环境：后端 mvn spring-boot:run（8080），前端 npm run dev（5173，Vite 代理 /api）；")
bullet(doc2, "生产环境：后端 mvn package 打 jar 包运行，前端 npm run build 产物交由 Nginx 托管并反向代理 /api；")
bullet(doc2, "数据库：执行 init.sql 初始化；生产环境修改 JWT 密钥与数据库口令。")

doc2.save(DOCS_DIR + r"\报修工单管理系统-项目实施计划.docx")
print("DOC2 OK")
