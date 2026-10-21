"""يقود التطبيق على المحاكي عبر adb لالتقاط لقطات شاشة حقيقية.
البيانات المُدخلة هنا بيانات اختبار على المحاكي فقط، وليست بيانات افتراضية في التطبيق."""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

OUT = sys.argv[1] if len(sys.argv) > 1 else "screenshots"
os.makedirs(OUT, exist_ok=True)
os.makedirs(os.path.join(OUT, "dumps"), exist_ok=True)
PKG = "com.hossammo.cash"
log = open(os.path.join(OUT, "drive.log"), "w", encoding="utf-8")

def p(*a):
    s = " ".join(str(x) for x in a); print(s); log.write(s + "\n"); log.flush()

def adb(*a, binary=False):
    r = subprocess.run(["adb", *a], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")

def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    x = adb("shell", "cat", "/sdcard/ui.xml")
    try:
        root = ET.fromstring(x[x.index("<?xml"):] if "<?xml" in x else x)
    except Exception:
        return [], x
    res = []
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) != 4: continue
        x1, y1, x2, y2 = map(int, b)
        res.append(dict(text=n.get("text", ""), desc=n.get("content-desc", ""), cls=n.get("class", ""),
                        x=(x1 + x2) // 2, y=(y1 + y2) // 2, y1=y1, y2=y2))
    return res, x

counter = [0]
def save_dump(tag):
    _, x = nodes()
    counter[0] += 1
    open(os.path.join(OUT, "dumps", f"{counter[0]:02d}_{tag}.xml"), "w", encoding="utf-8").write(x)

def find(label, exact=True, pick="first"):
    ns, _ = nodes()
    m = [n for n in ns if (n["text"] == label or n["desc"] == label) or
         (not exact and (label in n["text"] or label in n["desc"]))]
    if not m: return None
    if pick == "last": return max(m, key=lambda n: n["y"])
    if pick == "top": return min(m, key=lambda n: n["y"])
    return m[0]

def dismiss_system_dialogs():
    """يغلق نوافذ النظام مثل «Pixel Launcher isn't responding» التي تظهر على المحاكي البطيء."""
    ns, _ = nodes()
    texts = [n["text"] for n in ns]
    if any("isn't responding" in t or "keeps stopping" in t for t in texts):
        for n in ns:
            if n["text"] in ("Wait", "Close app", "OK"):
                adb("shell", "input", "tap", str(n["x"]), str(n["y"])); time.sleep(2); p("dismissed system dialog"); return True
    return False

def tap(label, exact=True, pick="first", timeout=25, scroll=False):
    end = time.time() + timeout
    swipes = 0
    while time.time() < end:
        dismiss_system_dialogs()
        n = find(label, exact, pick)
        if n:
            adb("shell", "input", "tap", str(n["x"]), str(n["y"])); time.sleep(1.5); return True
        if scroll:
            # ننزل أولًا ثم نصعد إن لم نجد العنصر
            if swipes < 5: swipe_up()
            else: swipe_down()
            swipes = (swipes + 1) % 10
        else: time.sleep(1)
    raise RuntimeError(f"not found: {label}")

def swipe_up():
    adb("shell", "input", "swipe", "540", "1600", "540", "1100", "300"); time.sleep(1)

def swipe_down():
    adb("shell", "input", "swipe", "540", "900", "540", "1500", "300"); time.sleep(1)

def back():
    adb("shell", "input", "keyevent", "4"); time.sleep(1.5)

def edits():
    ns, _ = nodes()
    return [n for n in ns if n["cls"] == "android.widget.EditText"]

def type_into(idx, value):
    e = edits()
    if len(e) <= idx: raise RuntimeError(f"no EditText #{idx} (have {len(e)})")
    adb("shell", "input", "tap", str(e[idx]["x"]), str(e[idx]["y"])); time.sleep(0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(15): adb("shell", "input", "keyevent", "KEYCODE_DEL")
    adb("shell", "input", "text", value); time.sleep(0.8)

def hide_kb():
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method"): back()

def shot(name):
    time.sleep(1.5)
    png = adb("exec-out", "screencap", "-p", binary=True)
    open(os.path.join(OUT, name + ".png"), "wb").write(png)
    p("shot", name, len(png))

def ensure_app():
    if PKG not in adb("shell", "dumpsys", "window", "displays").split("mCurrentFocus")[-1][:200]:
        adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity"); time.sleep(4)

def step(name, fn):
    dismiss_system_dialogs()
    ensure_app()
    try:
        fn(); p("OK  ", name)
    except Exception as e:
        p("FAIL", name, e); save_dump(name)

def home_tab(label):
    tap(label, pick="last")

months = ["يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"]
cur = int(adb("shell", "date", "+%m").strip() or "10")
prev = 12 if cur == 1 else cur - 1

time.sleep(30)  # يترك المحاكي يستقر بعد الإقلاع
dismiss_system_dialogs()
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity"); time.sleep(10)
save_dump("start")

def s_onboarding():
    for _ in range(5):
        n = find("تخطي")
        if not n: return
        p("tap skip at", n["x"], n["y"])
        adb("shell", "input", "tap", str(n["x"]), str(n["y"])); time.sleep(3)
        p("focus:", adb("shell", "dumpsys", "window").split("mCurrentFocus")[-1][:120])
    raise RuntimeError("onboarding did not close")
step("onboarding", s_onboarding)

def s_secure():
    home_tab("التقارير والإعدادات")
    tap("الأمان والخصوصية", scroll=True)
    tap("منع لقطات الشاشة", scroll=True)
    back()
step("disable_secure", s_secure)
step("shot_more", lambda: shot("08_reports_settings"))

def s_empty():
    home_tab("الرئيسية"); shot("01_dashboard_empty")
step("dashboard_empty", s_empty)

def open_months():
    home_tab("السيولة والمدخرات")
    tap("خيارات إضافية", pick="top"); tap("الأشهر")

def add_month(m, empty):
    tap("إضافة شهر", pick="last")
    if m != cur:
        tap(months[cur - 1], pick="last"); tap(months[m - 1], pick="last")
    if empty: tap("شهر فارغ")
    tap("إنشاء", pick="last"); time.sleep(2)

def s_prev():
    open_months(); add_month(prev, True)
    save_dump("month_entry_prev")
    type_into(0, "500000"); hide_kb()
    type_into(1, "431000"); hide_kb()
    shot("04_month_entry")
    tap("حفظ الكل"); time.sleep(2); back()
step("prev_month", s_prev)

def s_cur():
    add_month(cur, False)
    type_into(0, "519000"); hide_kb()
    tap("حفظ الكل"); time.sleep(2); back()
    shot("05_months")
step("cur_month", s_cur)

def s_liq():
    back(); home_tab("السيولة والمدخرات"); shot("03_liquidity")
step("liquidity", s_liq)

def s_dash():
    home_tab("الرئيسية"); shot("02_dashboard")
    swipe_up(); shot("02b_dashboard_charts")
step("dashboard", s_dash)

def s_charts():
    tap("كل الرسومات", scroll=True); time.sleep(2); shot("06_charts"); back()
step("charts", s_charts)

def s_debt():
    home_tab("الديون")
    tap("إضافة دين", pick="last")
    save_dump("debt_editor")
    type_into(0, "Car loan"); hide_kb()
    type_into(1, "1000000"); hide_kb()
    tap("حفظ", pick="last"); time.sleep(2)
    shot("07_debts")
step("debts", s_debt)

def s_var():
    home_tab("المدخرات المتغيرة"); shot("09_variable_savings")
step("variable", s_var)

def s_report():
    home_tab("التقارير والإعدادات")
    tap("تقرير الزيادة والانخفاض", scroll=True); time.sleep(3); shot("10_report"); back()
step("report", s_report)

def s_backup():
    tap("النسخ الاحتياطي والاستعادة", scroll=True); shot("11_backup"); back()
step("backup", s_backup)

def s_security():
    tap("الأمان والخصوصية", scroll=True); shot("12_security"); back()
step("security", s_security)
open(os.path.join(OUT, "logcat_errors.txt"), "w", encoding="utf-8").write(
    adb("logcat", "-d", "-b", "crash") + "\n=====\n" + adb("logcat", "-d", "*:W"))
p("done")
