# -*- coding: utf-8 -*-
"""يولد strings.xml من tools/strings_ar.py ويتحقق أن كل مرجع R.string في الكود معرّف."""
import glob, os, re, sys
from xml.sax.saxutils import escape
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(root, "tools"))
from strings_ar import S, ARRAYS, PLURALS

def esc(v):
    v = escape(v).replace("'", "\\'").replace('"', '\\"')
    if v.startswith("@") or v.startswith("?"):
        v = "\\" + v
    return v

src = "\n".join(open(f, encoding="utf-8").read() for f in glob.glob(os.path.join(root, "app/src/main/java/**/*.kt"), recursive=True))
used = set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", src))
missing = sorted(used - set(S))
unused = sorted(set(S) - used - {"app_name"})
out = ['<?xml version="1.0" encoding="utf-8"?>', '<resources xmlns:tools="http://schemas.android.com/tools" tools:locale="ar">']
for k in sorted(S):
    attr = ' translatable="false"' if k == "app_name" else ""
    fmt = ' formatted="false"' if ("%" in S[k] and not re.search(r"%\d\$", S[k])) else ""
    out.append(f'    <string name="{k}"{attr}{fmt}>{esc(S[k])}</string>')
for k, items in ARRAYS.items():
    out.append(f'    <string-array name="{k}">')
    out += [f"        <item>{esc(i)}</item>" for i in items]
    out.append("    </string-array>")
for k, q in PLURALS.items():
    out.append(f'    <plurals name="{k}">')
    out += [f'        <item quantity="{qq}">{esc(t)}</item>' for qq, t in q.items()]
    out.append("    </plurals>")
out.append("</resources>")
path = os.path.join(root, "app/src/main/res/values/strings.xml")
os.makedirs(os.path.dirname(path), exist_ok=True)
open(path, "w", encoding="utf-8").write("\n".join(out) + "\n")
print(f"written {len(S)} strings; missing={missing}; unused={unused}")
sys.exit(1 if missing else 0)
