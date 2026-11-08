"""Check all app-local references in Android resource XML and Manifest.

Run: python verification/test_android_resource_links.py
"""
from pathlib import Path
from xml.etree import ElementTree as ET
import re

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT / "app/src/main/res"
SRC=ROOT / "app/src/main"
found=set()
for file in RES.rglob("*.xml"):
    ET.parse(file)
    kind=file.parent.name.split("-", 1)[0]
    if kind != "values":
        found.add((kind,file.stem))
    else:
        for child in ET.parse(file).getroot():
            name=child.get("name")
            if name:
                found.add((child.get("type") or child.tag,name))
ET.parse(SRC / "AndroidManifest.xml")
missing={}
for file in [*RES.rglob("*.xml"),SRC / "AndroidManifest.xml"]:
    text=file.read_text(encoding="utf-8")
    for kind,name in re.findall(r"(?<!android:)@([a-z]+)/(\w[\w.]*)", text):
        if kind == "id":
            continue
        if (kind,name) not in found:
            missing.setdefault((kind,name), []).append(str(file.relative_to(ROOT)))
assert not missing, f"Missing Android resources: {missing}"
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    icon=RES / "mipmap-anydpi-v26" / name
    assert "@color/ic_launcher_background" in icon.read_text(encoding="utf-8")
assert ("color","ic_launcher_background") in found
print("ANDROID_RESOURCES_XML_REFS_PASS")
