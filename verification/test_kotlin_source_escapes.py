"""Offline regression check for unsupported escape sequences in Kotlin strings."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
valid = set("tbnr'\"\\$")
problems = []
for file in (root / "app/src/main/java").rglob("*.kt"):
    src = file.read_text(encoding="utf-8-sig")
    i, line, mode = 0, 1, "code"
    while i < len(src):
        ch = src[i]
        if ch == "\n":
            line += 1
        if mode == "code":
            if src.startswith("//", i):
                mode, i = "line_comment", i + 2
                continue
            if src.startswith("/*", i):
                mode, i = "block_comment", i + 2
                continue
            if src.startswith('"""', i):
                mode, i = "raw", i + 3
                continue
            if ch == '"':
                mode = "string"
            elif ch == "'":
                mode = "char"
        elif mode == "line_comment":
            if ch == "\n":
                mode = "code"
        elif mode == "block_comment":
            if src.startswith("*/", i):
                mode, i = "code", i + 2
                continue
        elif mode == "raw":
            if src.startswith('"""', i):
                mode, i = "code", i + 3
                continue
        elif mode in ("string", "char"):
            if ch == "\\":
                next_char = src[i + 1] if i + 1 < len(src) else ""
                if next_char == "u" and re.fullmatch(r"[0-9a-fA-F]{4}", src[i+2:i+6]):
                    i += 6
                    continue
                if next_char not in valid:
                    problems.append(f"{file.relative_to(root)}:{line}: invalid escape \\{next_char}")
                i += 2
                continue
            if (ch == '"' and mode == "string") or (ch == "'" and mode == "char"):
                mode = "code"
        i += 1

shell = (root / "app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
assert 'ip.contains(Regex("""[\\s/:]"""))' in shell, "Correct raw-string regex missing"
assert not problems, "\n".join(problems)
print("KOTLIN_ESCAPE_SEQUENCE_SCAN_PASS")
