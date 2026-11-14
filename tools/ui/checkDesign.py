#!/usr/bin/env python3
"""Design-system gate.

Enforces the rules of docs/03-design-system.md and docs/08-ux-guidelines.md so they cannot
rot away from the code:

  1. theme-light.css and theme-dark.css declare exactly the same tokens;
  2. every pair in src/main/resources/css/contrast-pairs.txt meets its WCAG threshold;
  3. app.css contains no colour literal - colours come from the token files only;
  4. app.css refers to no token the themes do not declare;
  5. every font size is >= 11 px and belongs to the type scale (em is allowed only inside
     the Arabic block, where it scales from the root size);
  6. the token table of docs/03-design-system.md matches the real theme files;
  7. no Java source and no FXML file hardcodes a colour or a font size - code reaches colours
     through ThemePalette and sizes through the classes of app.css;
  8. no FXML file carries an inline style, and the single setStyle of the project is the
     documented table-alignment exception in DataTable.

Exit code 0 when everything holds, 1 otherwise. Run before every push (devRules section 8).
"""
from __future__ import annotations

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CSS = os.path.join(ROOT, "src", "main", "resources", "css")
APP = os.path.join(CSS, "app.css")
LIGHT = os.path.join(CSS, "theme-light.css")
DARK = os.path.join(CSS, "theme-dark.css")
PAIRS = os.path.join(CSS, "contrast-pairs.txt")
DOC = os.path.join(ROOT, "docs", "03-design-system.md")
JAVA_ROOT = os.path.join(ROOT, "src", "main", "java")
FXML_ROOT = os.path.join(ROOT, "src", "main", "resources", "fxml")

# The nine-step type scale of the design system. Nothing smaller than 11 px ships.
SCALE = [11.0, 11.5, 12.5, 13.5, 15.0, 17.0, 20.0, 22.0, 27.0]

TOKEN_DEF = re.compile(r"^\s*(-c-[a-z0-9-]+)\s*:\s*([^;]+);", re.M)
TOKEN_USE = re.compile(r"-c-[a-z0-9-]+")
HEX = re.compile(r"#[0-9a-fA-F]{3,8}\b")
FUNC_COLOUR = re.compile(r"\b(rgba?|hsla?)\s*\(")
NAMED = re.compile(r"(?<![\w-])(white|black|red|green|blue|yellow|orange|grey|gray|silver|maroon|navy|teal|purple|olive|lime|aqua|fuchsia)(?![\w-])")
FONT_SIZE = re.compile(r"-fx-font-size\s*:\s*([0-9.]+)\s*(px|em)\s*;")
INLINE_STYLE = re.compile(r"\sstyle\s*=\s*\"")
SET_STYLE = re.compile(r"\.setStyle\s*\(")
SET_STYLE_ALLOWANCE = ("DataTable.java",)


def read(path: str) -> str:
    return io.open(path, encoding="utf-8").read()


def parse_tokens(path: str) -> dict[str, str]:
    text = read(path)
    body = text[text.index(".root {"):]
    return {name: value.strip() for name, value in TOKEN_DEF.findall(body)}


def hex_rgb(value: str):
    value = value.strip().lstrip("#")
    if len(value) == 3:
        value = "".join(c * 2 for c in value)
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def luminance(rgb) -> float:
    def channel(c: float) -> float:
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(a: str, b: str) -> float:
    la, lb = luminance(hex_rgb(a)), luminance(hex_rgb(b))
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def strip_comments(css: str) -> str:
    return re.sub(r"/\*.*?\*/", "", css, flags=re.S)


def check_parity(light, dark, problems):
    only_light = sorted(set(light) - set(dark))
    only_dark = sorted(set(dark) - set(light))
    for name in only_light:
        problems.append(f"theme-dark.css is missing {name}")
    for name in only_dark:
        problems.append(f"theme-light.css is missing {name}")


def check_contrast(light, dark, problems):
    pairs = []
    for line in read(PAIRS).splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        fg, bg, minimum = line.split()
        pairs.append((fg, bg, float(minimum)))
    for name, tokens in (("light", light), ("dark", dark)):
        for fg, bg, minimum in pairs:
            for token in (fg, bg):
                if token not in tokens:
                    problems.append(f"contrast pair references undeclared token {token}")
                    break
            else:
                fg_value, bg_value = tokens[fg], tokens[bg]
                if not fg_value.startswith("#") or not bg_value.startswith("#"):
                    problems.append(f"{name}: pair {fg} on {bg} must use solid hex tokens, not {fg_value} / {bg_value}")
                    continue
                ratio = contrast(fg_value, bg_value)
                if ratio + 1e-9 < minimum:
                    problems.append(f"{name}: {fg} on {bg} = {ratio:.2f}, below {minimum:.2f}")
    return pairs


def check_no_literals(css, problems):
    body = strip_comments(css)
    for match in HEX.finditer(body):
        problems.append(f"app.css hardcodes the colour {match.group(0)} - use a token")
    for match in FUNC_COLOUR.finditer(body):
        problems.append(f"app.css hardcodes a colour function {match.group(0)}...) - use a token")
    for match in NAMED.finditer(body):
        problems.append(f"app.css hardcodes the colour keyword {match.group(1)} - use a token")


def check_token_use(css, light, problems):
    body = strip_comments(css)
    for name in sorted(set(TOKEN_USE.findall(body))):
        if name not in light:
            problems.append(f"app.css uses {name}, which no theme declares")


def check_font_sizes(css, problems):
    body = strip_comments(css)
    # split on rules so we know which declarations sit inside the Arabic block
    for rule in re.finditer(r"([^{}]+)\{([^{}]*)\}", body):
        selector, declarations = rule.group(1).strip(), rule.group(2)
        arabic = ".lang-ar" in selector
        for value, unit in FONT_SIZE.findall(declarations):
            size = float(value)
            if unit == "em":
                if not arabic:
                    problems.append(f"{selector}: em font size outside the Arabic block")
                continue
            if size < 11:
                problems.append(f"{selector}: font size {size}px is below the 11 px floor")
            elif size not in SCALE:
                problems.append(f"{selector}: font size {size}px is outside the type scale {SCALE}")


def check_sources(problems):
    """Code must not carry a colour or a font size of its own."""
    for folder, suffix in ((JAVA_ROOT, ".java"), (FXML_ROOT, ".fxml")):
        for base, _dirs, files in os.walk(folder):
            for name in sorted(files):
                if not name.endswith(suffix):
                    continue
                path = os.path.join(base, name)
                text = strip_comments(read(path))
                relative = os.path.relpath(path, ROOT).replace(os.sep, "/")
                for match in HEX.finditer(text):
                    problems.append(f"{relative} hardcodes the colour {match.group(0)} - "
                                    "read it from the theme tokens (ThemePalette) or a style class")
                for match in FUNC_COLOUR.finditer(text):
                    problems.append(f"{relative} hardcodes a colour function {match.group(0)}...) - use a token")
                for match in FONT_SIZE.finditer(text):
                    problems.append(f"{relative} sets a font size inline ({match.group(0).strip()}) - "
                                    "use one of the type-scale classes of app.css")
                for _match in INLINE_STYLE.finditer(text):
                    problems.append(f"{relative} carries an inline style attribute - declare the rule in app.css")
                if suffix == ".java":
                    for _match in SET_STYLE.finditer(text):
                        if not relative.endswith(SET_STYLE_ALLOWANCE[0]):
                            problems.append(f"{relative} calls setStyle - use a style class "
                                            "(the only allowed setStyle is the table alignment in DataTable)")


def check_doc(light, dark, problems):
    doc = read(DOC)
    rows = re.findall(r"^\|\s*`(-c-[a-z0-9-]+)`\s*\|\s*`([^`]+)`\s*\|\s*`([^`]+)`\s*\|", doc, re.M)
    if len(rows) < 20:
        problems.append("docs/03-design-system.md no longer documents the token table")
        return 0
    for token, light_value, dark_value in rows:
        if token not in light or token not in dark:
            problems.append(f"doc lists {token}, which the themes do not declare")
        elif light_value.strip() != light[token] or dark_value.strip() != dark[token]:
            problems.append(f"doc value for {token} is {light_value}/{dark_value}, "
                            f"the themes say {light[token]}/{dark[token]}")
    return len(rows)


def main() -> int:
    problems: list[str] = []
    light, dark = parse_tokens(LIGHT), parse_tokens(DARK)
    css = read(APP)
    check_parity(light, dark, problems)
    pairs = check_contrast(light, dark, problems)
    check_no_literals(css, problems)
    check_token_use(css, light, problems)
    check_font_sizes(css, problems)
    check_sources(problems)
    documented = check_doc(light, dark, problems)

    limit = 0
    for problem in problems[:limit or len(problems)]:
        print("  -", problem)
    if problems:
        print(f"design system: {len(problems)} problem(s)")
        return 1
    print(f"design system: {len(light)} tokens in both themes, {len(pairs)} contrast pairs, "
          f"{documented} documented rows, 0 problems")
    return 0


if __name__ == "__main__":
    sys.exit(main())
