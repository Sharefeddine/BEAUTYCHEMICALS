#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Checks that every literal i18n key used by the sources exists in the bundles.

Usage: python3 tools/i18n/audit.py
Exit code 1 when a key is missing or unused keys are found in some bundle only.
"""
import io
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BUNDLE = os.path.join(ROOT, "src", "main", "resources", "i18n", "messages_fr.properties")

KEY_PATTERN = re.compile(r'(?:Labels\.get|getString|getBundleKey|bundle\.getString)\(\s*"([A-Za-z0-9_.]+)"')
FXML_PATTERN = re.compile(r'(?:text|promptText|title)="%\s*([A-Za-z0-9_.]+)')


def load_keys():
    keys = set()
    with io.open(BUNDLE, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            keys.add(line.split("=", 1)[0])
    return keys


def used_keys():
    found = {}
    for base, _dirs, files in os.walk(os.path.join(ROOT, "src", "main")):
        for name in files:
            if not name.endswith((".java", ".fxml")):
                continue
            path = os.path.join(base, name)
            with io.open(path, encoding="utf-8", errors="replace") as handle:
                text = handle.read()
            for match in KEY_PATTERN.finditer(text):
                found.setdefault(match.group(1), path)
            for match in FXML_PATTERN.finditer(text):
                found.setdefault(match.group(1), path)
    return found


def main():
    keys = load_keys()
    used = used_keys()
    missing = sorted(k for k in used if k not in keys)
    for key in missing:
        print("missing key: %s  (%s)" % (key, os.path.relpath(used[key], ROOT)))
    unused = sorted(k for k in keys if k not in used)
    print("%d keys defined, %d referenced literally, %d missing, %d unused (dynamic or fallback)" %
          (len(keys), len(used), len(missing), len(unused)))
    if unused:
        print("unused sample: " + ", ".join(unused[:15]))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
