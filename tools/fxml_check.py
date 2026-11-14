#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static checker for the FXML screens.

Catches, without a running JVM, the mistakes that only appear at runtime:
  * an fx:id that no @FXML field declares (or the reverse), unless the Java code
    reaches it by selector (lookup("#id")), which is how the UI tests drive a screen,
  * a missing or duplicated fx:controller,
  * an event handler (#method) that does not exist in the controller,
  * a %key that is missing from the bundles,
  * a style class that no stylesheet defines,
  * a controller referenced by the FXML that does not exist.

Usage: python3 tools/fxml_check.py   (exit 1 on any problem)
"""
import io
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
FXML_DIR = os.path.join(ROOT, "src", "main", "resources", "fxml")
JAVA_DIR = os.path.join(ROOT, "src", "main", "java")
TEST_DIR = os.path.join(ROOT, "src", "test", "java")
CSS_DIR = os.path.join(ROOT, "src", "main", "resources", "css")
I18N = os.path.join(ROOT, "src", "main", "resources", "i18n", "messages_fr.properties")

FXID = "{http://javafx.com/javafx/21}id"
FXID_ALT = "fx:id"
CONTROLLER = "{http://javafx.com/javafx/21}controller"

problems = []


def read(path):
    with io.open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read()


def bundle_keys():
    keys = set()
    for line in read(I18N).splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            keys.add(line.split("=", 1)[0])
    return keys


def css_classes():
    classes = set()
    for name in os.listdir(CSS_DIR):
        if name.endswith(".css"):
            classes.update(re.findall(r"\.([A-Za-z][A-Za-z0-9_-]*)", read(os.path.join(CSS_DIR, name))))
    return classes


def tokenize_styles(value):
    return [token.strip() for token in re.split(r"[,\s]+", value) if token.strip()]


def annotated_fields(java):
    fields = set(re.findall(r"@FXML\s+(?:private|protected|public)?\s*[\w<>,\[\]\. ]+?\s+(\w+)\s*(?:=|;)", java))
    parent = re.search(r"class\s+\w+\s+extends\s+([\w\.]+)", java)
    if parent:
        parent_source = controller_source(parent.group(1))
        if parent_source:
            fields |= annotated_fields(read(parent_source))
    return fields


def controller_source(class_name):
    if "." not in class_name:
        for base, _dirs, files in os.walk(JAVA_DIR):
            candidate = className_file(class_name, files)
            if candidate:
                return os.path.join(base, candidate)
        return None
    package_path = class_name.replace(".", os.sep) + ".java"
    path = os.path.join(JAVA_DIR, package_path)
    return path if os.path.isfile(path) else None


def selected_ids():
    """fx:id values the Java code (main and tests) looks up by CSS selector."""
    ids = set()
    for base_dir in (JAVA_DIR, TEST_DIR):
        for base, _dirs, files in os.walk(base_dir):
            for name in files:
                if name.endswith(".java"):
                    ids.update(re.findall(r'"#([A-Za-z_]\w*)"',
                                          read(os.path.join(base, name))))
    return ids


def className_file(class_name, files):
    name = class_name + ".java"
    return name if name in files else None


def check():
    keys = bundle_keys()
    classes = css_classes()
    selected = selected_ids()
    for name in sorted(os.listdir(FXML_DIR)):
        if not name.endswith(".fxml"):
            continue
        path = os.path.join(FXML_DIR, name)
        text = read(path)
        try:
            tree = ET.parse(path)
        except ET.ParseError as ex:
            problems.append("%s: invalid XML (%s)" % (name, ex))
            continue
        root = tree.getroot()

        controllers = re.findall(r'fx:controller="([^"]+)"', text)
        if len(controllers) != 1:
            problems.append("%s: expected exactly one fx:controller, found %d" % (name, len(controllers)))
            continue
        controller = controllers[0]
        source = controller_source(controller)
        if source is None:
            problems.append("%s: controller %s does not exist" % (name, controller))
            continue
        java = read(source)

        ids = set(re.findall(r'fx:id="([^"]+)"', text))
        annotated = annotated_fields(java)
        handlers = set(re.findall(r'#([A-Za-z_]\w*)', text))

        for fx_id in sorted(ids - annotated - selected):
            problems.append("%s: fx:id '%s' has no @FXML field in %s" % (name, fx_id, controller))
        for field in sorted(a for a in annotated if a not in ids and a != "initialize"):
            problems.append("%s: @FXML field '%s' is declared but not bound in the FXML" % (name, field))
        for handler in sorted(handlers):
            if not re.search(r"(?:private|protected|public)?\s*void\s+" + re.escape(handler) + r"\s*\(", java):
                problems.append("%s: handler '#%s' not found in %s" % (name, handler, controller))

        for key in sorted(set(re.findall(r'"%\s*([A-Za-z0-9_.]+)"', text))):
            if key not in keys:
                problems.append("%s: i18n key '%s' is missing from the bundles" % (name, key))

        for style in sorted(set(re.findall(r'styleClass="([^"]+)"', text))):
            for token in tokenize_styles(style):
                if not token.startswith("$") and token not in classes:
                    problems.append("%s: style class '%s' is not defined in the stylesheets" % (name, token))

        if "xmlns:fx" not in text:
            problems.append("%s: missing fx namespace declaration" % name)
    return problems


if __name__ == "__main__":
    issues = check()
    for issue in issues:
        print(issue)
    print("%d problem(s) found" % len(issues))
    sys.exit(1 if issues else 0)
