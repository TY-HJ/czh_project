# -*- coding: utf-8 -*-
"""
repair-system API 级端到端测试（零第三方依赖，仅 Python 标准库）

覆盖范围:
  1. 认证: 三角色登录 / 登录失败 / 注册 / 重复注册 / me / 未登录拦截
  2. 字典: 报修类型 / 位置
  3. 参数校验 / 角色越权 / 数据越权
  4. 工单状态机完整流转(V2.0 自动派单):
     提交 -> 自动派单(处理中) -> 接单 -> 完成 -> 验收(退回重修 -> 再完成 -> 通过)
  5. 关闭自动派单后的分支: 待派单 -> 住户撤销 / 管理员关闭
  6. 统计大屏 6 个接口 + 智能派单候选/规则
"""
import json
import sys
import time
import urllib.request
import urllib.error
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE_URL = "http://localhost:8080"
PASSWORD = "123456"
TS = str(int(time.time()))
NEW_USER = "e2eu" + TS[-8:]  # 3~20 位

results = []


def api(method, path, token=None, payload=None):
    url = BASE_URL + path
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.status, (json.loads(body) if body else None)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"raw": body[:200]}
    except Exception as e:
        return -1, {"raw": str(e)}


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("  [PASS] " if ok else "  [FAIL] ") + name + (("  -> " + str(detail)) if (detail and not ok) else ""))


def expect_ok(name, resp):
    status, body = resp
    ok = status == 200 and isinstance(body, dict) and body.get("code") == 200
    check(name, ok, body)
    return body.get("data") if ok else None


def expect_fail(name, resp):
    status, body = resp
    ok = status >= 400 or (isinstance(body, dict) and body.get("code") != 200)
    check(name, ok, body if ok else "意外成功: " + json.dumps(body, ensure_ascii=False)[:150])


def find_order(token, keyword, exclude=()):
    page = expect_ok("检索工单(keyword=%s)" % keyword,
                     api("GET", "/api/orders/page?keyword=" + keyword + "&current=1&size=10", token=token))
    if page and page.get("records"):
        for r in page["records"]:
            if r["id"] not in exclude:
                return r
    return None


def summary():
    passed = sum(1 for _, ok in results if ok)
    failed = len(results) - passed
    print("\n" + "=" * 60)
    print("总计: %d  通过: %d  失败: %d" % (len(results), passed, failed))
    if failed:
        print("\n失败用例:")
        for name, ok in results:
            if not ok:
                print("  - " + name)
    print("=" * 60)
    return 0 if failed == 0 else 1


# ==================================================================
print("=" * 60)
print("repair-system 端到端测试 (API 级)  " + time.strftime("%Y-%m-%d %H:%M:%S"))
print("=" * 60)

# ---------- 1. 认证模块 ----------
print("\n[模块1] 认证")
s, b = api("POST", "/api/auth/login", payload={"username": "admin", "password": PASSWORD})
admin = expect_ok("管理员 admin 登录", (s, b))
check("admin 角色 = ADMIN", bool(admin) and admin.get("role") == "ADMIN", admin)
admin_token = admin["token"] if admin else None

s, b = api("POST", "/api/auth/login", payload={"username": "zhangsan", "password": PASSWORD})
zs = expect_ok("住户 zhangsan 登录", (s, b))
check("zhangsan 角色 = RESIDENT", bool(zs) and zs.get("role") == "RESIDENT", zs)
zs_token = zs["token"] if zs else None
zs_id = zs.get("id") if zs else None

s, b = api("POST", "/api/auth/login", payload={"username": "lisi", "password": PASSWORD})
lisi = expect_ok("维修工 lisi 登录", (s, b))
check("lisi 角色 = WORKER", bool(lisi) and lisi.get("role") == "WORKER", lisi)
lisi_token = lisi["token"] if lisi else None
lisi_id = lisi.get("id") if lisi else None

s, b = api("POST", "/api/auth/login", payload={"username": "wangwu", "password": PASSWORD})
ww = expect_ok("维修工 wangwu 登录", (s, b))
ww_token = ww["token"] if ww else None

expect_fail("错误密码登录被拒", api("POST", "/api/auth/login", payload={"username": "admin", "password": "wrong"}))
expect_fail("不存在用户登录被拒", api("POST", "/api/auth/login", payload={"username": "no_such_user", "password": PASSWORD}))

s, b = api("POST", "/api/auth/register", payload={
    "username": NEW_USER, "password": "e2e123456", "realName": "端到端测试用户", "phone": "13800001234"})
expect_ok("新住户注册", (s, b))
expect_fail("重复用户名注册被拒", api("POST", "/api/auth/register", payload={
    "username": NEW_USER, "password": "e2e123456", "realName": "重复", "phone": "13800001234"}))

s, b = api("POST", "/api/auth/login", payload={"username": NEW_USER, "password": "e2e123456"})
nu = expect_ok("新住户登录", (s, b))
nu_token = nu["token"] if nu else None

me = expect_ok("GET /auth/me (zhangsan)", api("GET", "/api/auth/me", token=zs_token))
check("me 返回正确用户名", bool(me) and me.get("username") == "zhangsan", me)
expect_fail("未携带 token 访问 /auth/me 被拒", api("GET", "/api/auth/me"))

# ---------- 2. 字典模块 ----------
print("\n[模块2] 字典")
types = expect_ok("报修类型列表", api("GET", "/api/dict/types", token=zs_token))
locs = expect_ok("位置列表", api("GET", "/api/dict/locations", token=zs_token))
type_id = types[0]["id"] if types else None
loc_id = locs[0]["id"] if locs else None
check("字典数据非空(可建单)", bool(type_id and loc_id), {"typeId": type_id, "locationId": loc_id})

# ---------- 3. 参数校验 ----------
print("\n[模块3] 参数校验")
expect_fail("建单缺少 typeId 被拒", api("POST", "/api/orders", token=zs_token,
                                       payload={"locationId": loc_id, "title": "缺类型"}))
expect_fail("建单标题为空被拒", api("POST", "/api/orders", token=zs_token,
                                  payload={"typeId": type_id, "locationId": loc_id, "title": ""}))

# ---------- 4. 权限拦截(角色越权) ----------
print("\n[模块4] 权限拦截(角色越权)")
expect_fail("住户访问管理员工单分页被拒", api("GET", "/api/orders/page", token=zs_token))
expect_fail("维修工访问管理员工单分页被拒", api("GET", "/api/orders/page", token=lisi_token))
expect_fail("住户访问统计大屏被拒", api("GET", "/api/stats/overview", token=zs_token))
expect_fail("住户调用派单接口被拒", api("PUT", "/api/orders/1/assign", token=zs_token, payload={"workerId": lisi_id}))
expect_fail("住户访问智能派单候选被拒", api("GET", "/api/dispatch/candidates/1", token=zs_token))

# ---------- 5. 工单状态机完整流转(V2.0 自动派单) ----------
print("\n[模块5] 工单状态机完整流转(自动派单)")
expect_ok("住户提交报修工单(URGENT)", api("POST", "/api/orders", token=zs_token, payload={
    "typeId": type_id, "locationId": loc_id, "title": "水龙头漏水E2E-A" + TS,
    "description": "端到端自动化测试创建的工单", "priority": "URGENT"}))

order = find_order(admin_token, "E2E-A" + TS)
check("管理员能检索到新工单", bool(order), None)
if not order:
    sys.exit(summary())
oid = order["id"]
check("提交后即自动派单,状态 = 1 处理中", order.get("status") == 1, order.get("status"))
check("已自动分配维修工", bool(order.get("workerId")), order.get("workerId"))
check("派单方式 = AUTO", order.get("dispatchType") == "AUTO", order.get("dispatchType"))

expect_fail("派单不传维修工被拒", api("PUT", "/api/orders/%d/assign" % oid, token=admin_token, payload={}))
expect_fail("派单给住户被拒(维修工不存在)", api("PUT", "/api/orders/%d/assign" % oid, token=admin_token, payload={"workerId": zs_id}))
# V3.1/A2: 处理中再次派单=改派, 但派给当前同一个人应被拒
expect_fail("派给当前维修工被拒(无需重复派单)", api("PUT", "/api/orders/%d/assign" % oid, token=admin_token,
                                            payload={"workerId": order.get("workerId")}))
# V3.1/A2: 改派给其他师傅 -> 再改派回来, 验证改派通道可用且不破坏后续接单流程
if ww and ww.get("id") != order.get("workerId"):
    expect_ok("处理中工单可改派给其他维修工", api("PUT", "/api/orders/%d/assign" % oid, token=admin_token,
                                        payload={"workerId": ww.get("id")}))
    re_assigned = find_order(admin_token, "E2E-A" + TS)
    check("改派后维修工已变更", bool(re_assigned) and re_assigned.get("workerId") == ww.get("id"),
          re_assigned and re_assigned.get("workerId"))
    check("改派后接单时间被清空(需新师傅重新接单)",
          bool(re_assigned) and not re_assigned.get("workerAcceptTime"), re_assigned and re_assigned.get("workerAcceptTime"))
    expect_ok("改派回原维修工", api("PUT", "/api/orders/%d/assign" % oid, token=admin_token,
                              payload={"workerId": order.get("workerId")}))
# V3.1/A2: 拒单接口的校验分支(不改状态)
expect_fail("住户调用拒单被拒(角色越权)", api("PUT", "/api/orders/%d/refuse" % oid, token=zs_token,
                                       payload={"reason": "越权测试"}))
expect_fail("拒单不填原因被拒", api("PUT", "/api/orders/%d/refuse" % oid, token=lisi_token, payload={"reason": ""}))

expect_fail("他人住户查看工单详情被拒(数据越权)", api("GET", "/api/orders/%d" % oid, token=nu_token))
d = expect_ok("报修人查看工单详情", api("GET", "/api/orders/%d" % oid, token=zs_token))
logs = expect_ok("报修人查看流转日志", api("GET", "/api/orders/%d/logs" % oid, token=zs_token))
check("日志记录了创建动作", bool(logs), logs)

expect_fail("非报修人催办被拒", api("PUT", "/api/orders/%d/urge" % oid, token=ww_token))
expect_ok("报修人催办", api("PUT", "/api/orders/%d/urge" % oid, token=zs_token))
expect_fail("已派单工单被住户撤销被拒", api("PUT", "/api/orders/%d/cancel" % oid, token=zs_token))

wpage = expect_ok("维修工查询自己的工单", api("GET", "/api/orders/worker?status=1", token=lisi_token))
check("维修工工单列表包含该单", bool(wpage and any(r["id"] == oid for r in wpage.get("records", []))), wpage)

expect_fail("住户在处理中验收被拒(非法流转)", api("PUT", "/api/orders/%d/acceptance" % oid, token=zs_token,
                                                payload={"pass": True, "score": 5}))
expect_ok("维修工接单确认", api("PUT", "/api/orders/%d/accept" % oid, token=lisi_token))
expect_fail("已接单后拒单被拒(需走管理员改派)", api("PUT", "/api/orders/%d/refuse" % oid, token=lisi_token,
                                           payload={"reason": "接到一半想推掉"}))
expect_ok("维修工完成维修", api("PUT", "/api/orders/%d/finish" % oid, token=lisi_token,
                              payload={"handleNote": "更换阀芯并测试正常"}))
d = expect_ok("完成后查看详情", api("GET", "/api/orders/%d" % oid, token=zs_token))
check("完成后状态 = 2 待验收", bool(d) and d.get("status") == 2, d and d.get("status"))

expect_fail("维修工重复完成被拒", api("PUT", "/api/orders/%d/finish" % oid, token=lisi_token,
                                    payload={"handleNote": "again"}))
expect_fail("验收通过但不打分被拒", api("PUT", "/api/orders/%d/acceptance" % oid, token=zs_token,
                                     payload={"pass": True}))
expect_ok("验收不通过 -> 退回重修", api("PUT", "/api/orders/%d/acceptance" % oid, token=zs_token,
                                     payload={"pass": False, "content": "还有渗水"}))
d = expect_ok("退回后查看详情", api("GET", "/api/orders/%d" % oid, token=zs_token))
check("退回后状态回到 1 处理中", bool(d) and d.get("status") == 1, d and d.get("status"))

expect_ok("维修工二次完成", api("PUT", "/api/orders/%d/finish" % oid, token=lisi_token,
                              payload={"handleNote": "二次维修:更换整个水龙头"}))
expect_ok("验收通过并打5星", api("PUT", "/api/orders/%d/acceptance" % oid, token=zs_token,
                              payload={"pass": True, "score": 5, "content": "修得很快,好评"}))
d = expect_ok("验收后查看详情", api("GET", "/api/orders/%d" % oid, token=zs_token))
check("验收通过后状态 = 3 已完成", bool(d) and d.get("status") == 3, d and d.get("status"))
check("工单评分 = 5", bool(d) and d.get("score") == 5, d and d.get("score"))

logs = expect_ok("完成后查看完整流转日志", api("GET", "/api/orders/%d/logs" % oid, token=zs_token))
check("流转日志 >= 8 条", bool(logs) and len(logs) >= 8, len(logs) if logs else logs)

expect_fail("对已完成工单关闭被拒", api("PUT", "/api/orders/%d/close" % oid, token=admin_token))
expect_fail("对已完成工单催办被拒", api("PUT", "/api/orders/%d/urge" % oid, token=zs_token))

# ---------- 6. 关闭自动派单: 待派单分支(撤销/关闭/手动派单) ----------
print("\n[模块6] 关闭自动派单后的待派单分支")
orig_rule = expect_ok("读取原始派单规则", api("GET", "/api/dispatch/rule", token=admin_token))
rule_off = dict(orig_rule) if orig_rule else {"autoDispatchEnabled": False}
rule_off["autoDispatchEnabled"] = False
expect_ok("临时关闭自动派单开关", api("PUT", "/api/dispatch/rule", token=admin_token, payload=rule_off))

try:
    expect_ok("住户再建一单(开关已关)", api("POST", "/api/orders", token=zs_token, payload={
        "typeId": type_id, "locationId": loc_id, "title": "待撤销工单E2E-B" + TS, "priority": "NORMAL"}))
    o2 = find_order(admin_token, "E2E-B" + TS, exclude=(oid,))
    check("新工单状态 = 0 待派单(未自动派单)", bool(o2) and o2.get("status") == 0,
          o2 and o2.get("status"))
    oid2 = o2["id"] if o2 else None

    if oid2:
        expect_fail("住户调用关闭接口被拒(角色越权)", api("PUT", "/api/orders/%d/close" % oid2, token=zs_token))
        expect_ok("住户撤销待派单工单", api("PUT", "/api/orders/%d/cancel" % oid2, token=zs_token))
        d = expect_ok("撤销后查看详情", api("GET", "/api/orders/%d" % oid2, token=zs_token))
        check("撤销后状态 = 4 已关闭", bool(d) and d.get("status") == 4, d and d.get("status"))
        expect_fail("对已关闭工单再次撤销被拒", api("PUT", "/api/orders/%d/cancel" % oid2, token=zs_token))

    expect_ok("住户建第三单(供管理员关闭)", api("POST", "/api/orders", token=zs_token, payload={
        "typeId": type_id, "locationId": loc_id, "title": "管理员关闭用E2E-C" + TS, "priority": "NORMAL"}))
    o3 = find_order(admin_token, "E2E-C" + TS, exclude=(oid, oid2))
    oid3 = o3["id"] if o3 else None
    check("检索到第三张工单(待派单)", bool(o3) and o3.get("status") == 0, o3 and o3.get("status"))

    if oid3:
        expect_ok("管理员手动派单给 lisi(开关关闭场景)", api("PUT", "/api/orders/%d/assign" % oid3,
                                                     token=admin_token, payload={"workerId": lisi_id}))
        d = expect_ok("手动派单后查看详情", api("GET", "/api/orders/%d" % oid3, token=zs_token))
        check("手动派单后状态 = 1 处理中,方式 MANUAL", bool(d) and d.get("status") == 1
              and d.get("dispatchType") == "MANUAL", d and (d.get("status"), d.get("dispatchType")))
        # 再建一单供管理员直接关闭(保持待派单)
        expect_ok("住户建第四单(供管理员关闭)", api("POST", "/api/orders", token=zs_token, payload={
            "typeId": type_id, "locationId": loc_id, "title": "关闭用E2E-D" + TS, "priority": "NORMAL"}))
        o4 = find_order(admin_token, "E2E-D" + TS, exclude=(oid, oid2, oid3))
        if o4 and o4.get("status") == 0:
            expect_ok("管理员关闭待派单工单", api("PUT", "/api/orders/%d/close" % o4["id"], token=admin_token))
            d = expect_ok("关闭后查看详情", api("GET", "/api/orders/%d" % o4["id"], token=zs_token))
            check("关闭后状态 = 4 已关闭", bool(d) and d.get("status") == 4, d and d.get("status"))
        else:
            check("第四单为待派单状态", False, o4)
    else:
        check("第三张工单存在", False, None)
finally:
    if orig_rule is not None:
        expect_ok("恢复原始派单规则", api("PUT", "/api/dispatch/rule", token=admin_token, payload=orig_rule))
rule_now = api("GET", "/api/dispatch/rule", token=admin_token)
check("派单规则已恢复", rule_now[1] and rule_now[1].get("data") == orig_rule, rule_now[1])

# ---------- 7. 统计大屏 ----------
print("\n[模块7] 统计大屏(管理员)")
for path, name in [("/api/stats/overview", "总览"), ("/api/stats/type-distribution", "类型分布"),
                   ("/api/stats/monthly-trend", "月度趋势"), ("/api/stats/worker-ranking", "维修工排行"),
                   ("/api/stats/heatmap", "楼栋热力"), ("/api/stats/trend-warning", "趋势预警")]:
    r = expect_ok("统计-" + name, api("GET", path, token=admin_token))
    check("统计-" + name + " 有数据", r is not None, r)

# ---------- 8. 智能派单 ----------
print("\n[模块8] 智能派单")
cands = expect_ok("管理员查看派单候选", api("GET", "/api/dispatch/candidates/%d" % oid, token=admin_token))
check("候选列表包含 lisi", bool(cands) and any(
    (c.get("workerId") == lisi_id or c.get("id") == lisi_id) for c in cands), cands)
expect_ok("查看派单规则", api("GET", "/api/dispatch/rule", token=admin_token))

sys.exit(summary())
