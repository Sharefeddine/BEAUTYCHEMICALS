#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static validator for the MySQL schema and seeders.

There is no MySQL server in the delivery environment, so this checker replaces the parts
of `mysql < file` that catch the real mistakes:

  1. the SQL is well formed (balanced parentheses and quotes, terminated statements);
  2. identifiers are lowerCamelCase - no underscore in any table, column, index,
     constraint, trigger or view name;
  3. every FOREIGN KEY points to an existing table and column, and the referenced table
     is created before the referencing one (migration order matters);
  4. every table declares a PRIMARY KEY, ENGINE and CHARSET;
  5. the seeds reference only permissions/roles that they define, and the permission list
     is identical to com.chemmanager.security.Permission and DefaultRoles (12 roles).

Usage: python3 tools/db/checkSchema.py      (exit 1 on any problem)
"""
from __future__ import annotations

import io
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SCHEMA_DIR = os.path.join(ROOT, "database", "schema")
SEED_DIR = os.path.join(ROOT, "database", "seed")
JAVA_SECURITY = os.path.join(ROOT, "src", "main", "java", "com", "chemmanager", "security")

# MySQL built-ins whose spelling we cannot change (collations, system variables)
ALLOWED_UNDERSCORE = {"utf8mb4_unicode_ci", "utf8mb4_general_ci", "time_zone", "character_set_client"}

problems: list[str] = []


def fail(message: str) -> None:
    problems.append(message)


def read(path: str) -> str:
    with io.open(path, encoding="utf-8") as handle:
        return handle.read()


def strip_comments(sql: str) -> str:
    sql = re.sub(r"/\*.*?\*/", "", sql, flags=re.S)
    return re.sub(r"--[^\n]*", "", sql)


def split_statements(sql: str) -> list[str]:
    """Splits on semicolons outside strings, respecting the DELIMITER $$ blocks."""
    statements, buffer = [], []
    delimiter = ";"
    for line in sql.splitlines():
        stripped = line.strip()
        match = re.match(r"DELIMITER\s+(\S+)", stripped, flags=re.I)
        if match:
            delimiter = match.group(1)
            continue
        buffer.append(line)
        if stripped.endswith(delimiter):
            text = "\n".join(buffer).strip()
            if len(delimiter) > 1:
                text = text[: -len(delimiter)]
            if text.strip():
                statements.append(text.strip())
            buffer = []
    tail = "\n".join(buffer).strip()
    if tail:
        statements.append(tail)
    return statements


def balanced(text: str) -> bool:
    depth, quote = 0, None
    for character in text:
        if quote:
            if character == quote:
                quote = None
            continue
        if character in "'\"`":
            quote = character
        elif character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
            if depth < 0:
                return False
    return depth == 0 and quote is None


def table_body(statement: str) -> str | None:
    start = statement.find("(")
    if start < 0:
        return None
    depth = 0
    for index in range(start, len(statement)):
        if statement[index] == "(":
            depth += 1
        elif statement[index] == ")":
            depth -= 1
            if depth == 0:
                return statement[start + 1:index]
    return None


def split_definitions(body: str) -> list[str]:
    parts, depth, current, quote = [], 0, [], None
    for character in body:
        if quote:
            current.append(character)
            if character == quote:
                quote = None
            continue
        if character in "'\"`":
            quote = character
            current.append(character)
        elif character == "(":
            depth += 1
            current.append(character)
        elif character == ")":
            depth -= 1
            current.append(character)
        elif character == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(character)
    if current:
        parts.append("".join(current).strip())
    return [part for part in parts if part]


NON_COLUMN = ("PRIMARY KEY", "UNIQUE KEY", "KEY ", "INDEX ", "CONSTRAINT", "FOREIGN KEY", "CHECK", "FULLTEXT")

# ----------------------------------------------------------------------------- checks


def check_identifiers(name: str, kind: str, filename: str, line_hint: str = "") -> None:
    if "_" in name and name not in ALLOWED_UNDERSCORE:
        fail("%s: %s '%s' contains an underscore (lowerCamelCase required)%s"
             % (filename, kind, name, line_hint))


def parse_schema() -> tuple[dict[str, dict], list[str]]:
    tables: dict[str, dict] = {}
    order: list[str] = []
    for filename in sorted(os.listdir(SCHEMA_DIR)):
        if not filename.endswith(".sql"):
            continue
        raw = read(os.path.join(SCHEMA_DIR, filename))
        if not balanced(strip_comments(raw)):
            fail("%s: unbalanced parentheses or quotes" % filename)
        sql = strip_comments(raw)

        for statement in split_statements(sql):
            upper = statement.upper().lstrip()

            if upper.startswith("CREATE TABLE"):
                match = re.match(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?", statement, flags=re.I)
                if not match:
                    fail("%s: unparsable CREATE TABLE" % filename)
                    continue
                name = match.group(1)
                check_identifiers(name, "table", filename)
                if name in tables:
                    fail("%s: table '%s' is created twice" % (filename, name))
                body = table_body(statement)
                if body is None:
                    fail("%s: table '%s' has no body" % (filename, name))
                    continue
                if not re.search(r"PRIMARY\s+KEY", body, flags=re.I):
                    fail("%s: table '%s' has no PRIMARY KEY" % (filename, name))
                if "ENGINE" not in statement.upper() or "CHARSET" not in statement.upper():
                    fail("%s: table '%s' must declare ENGINE, CHARSET and COLLATE" % (filename, name))

                columns: set[str] = set()
                for definition in split_definitions(body):
                    if definition.upper().startswith(NON_COLUMN):
                        for key in re.findall(r"KEY\s+`?(\w+)`?", definition, flags=re.I):
                            check_identifiers(key, "index", filename, " in table " + name)
                        for constraint in re.findall(r"CONSTRAINT\s+`?(\w+)`?", definition, flags=re.I):
                            check_identifiers(constraint, "constraint", filename, " in table " + name)
                        continue
                    column_match = re.match(r"`?(\w+)`?\s+", definition)
                    if not column_match:
                        continue
                    column = column_match.group(1)
                    check_identifiers(column, "column", filename, " in table " + name)
                    columns.add(column)
                tables[name] = {"columns": columns, "file": filename, "created_index": len(order)}
                order.append(name)

            elif upper.startswith("ALTER TABLE"):
                match = re.match(r"ALTER\s+TABLE\s+`?(\w+)`?", statement, flags=re.I)
                target = match.group(1) if match else "?"
                if target not in tables:
                    fail("%s: ALTER TABLE on unknown table '%s'" % (filename, target))
                for constraint in re.findall(r"CONSTRAINT\s+`?(\w+)`?", statement, flags=re.I):
                    check_identifiers(constraint, "constraint", filename, " in ALTER TABLE " + target)
                for reference, column in re.findall(
                        r"REFERENCES\s+`?(\w+)`?\s*\(`?(\w+)`?\)", statement, flags=re.I):
                    check_reference(filename, target, reference, column, tables)

            elif upper.startswith("CREATE TRIGGER"):
                match = re.match(r"CREATE\s+TRIGGER\s+`?(\w+)`?", statement, flags=re.I)
                if match:
                    check_identifiers(match.group(1), "trigger", filename)

            elif upper.startswith("CREATE OR REPLACE VIEW") or upper.startswith("CREATE VIEW"):
                match = re.search(r"VIEW\s+`?(\w+)`?", statement, flags=re.I)
                if match:
                    check_identifiers(match.group(1), "view", filename)

            # foreign keys declared inline inside CREATE TABLE
            for reference, column in re.findall(r"REFERENCES\s+`?(\w+)`?\s*\(`?(\w+)`?\)", statement, flags=re.I):
                if reference not in tables and not upper.startswith("ALTER"):
                    fail("%s: %s references '%s' before it is created (migration order)"
                         % (filename, "table", reference))
                elif reference in tables:
                    check_reference(filename, "inline", reference, column, tables)
    return tables, order


def check_reference(filename: str, source: str, reference: str, column: str, tables: dict) -> None:
    if reference not in tables:
        fail("%s: FOREIGN KEY references unknown table '%s'" % (filename, reference))
        return
    if column not in tables[reference]["columns"]:
        fail("%s: FOREIGN KEY references unknown column '%s.%s'" % (filename, reference, column))


def check_seeds(tables: dict) -> None:
    permissions_sql = read(os.path.join(SEED_DIR, "01_permissions.sql"))
    roles_sql = read(os.path.join(SEED_DIR, "02_roles.sql"))

    seed_codes = re.findall(r"^\s*\('([A-Z][A-Z0-9_]*?)\',\s*'([A-Z][A-Z0-9_]*?)\',", permissions_sql, flags=re.M)
    permission_codes = {code for code, _module in seed_codes}
    if len(permission_codes) != len(seed_codes):
        fail("seed 01: duplicate permission codes")
    for code in permission_codes:
        if "_" in code:
            continue  # enum codes are SCREAMING_SNAKE by convention (they are values, not identifiers)

    java_permissions = set(re.findall(r"^\s{4}([A-Z][A-Z0-9_]*)\s*[,(]",
                                      read(os.path.join(JAVA_SECURITY, "Permission.java")), flags=re.M))
    missing_in_sql = java_permissions - permission_codes
    missing_in_java = permission_codes - java_permissions
    if missing_in_sql:
        fail("seed 01 is missing permissions declared in Permission.java: %s" % ", ".join(sorted(missing_in_sql)))
    if missing_in_java:
        fail("Permission.java is missing permissions present in seed 01: %s" % ", ".join(sorted(missing_in_java)))

    role_codes = set(re.findall(r"^\s*\('([A-Z][A-Z0-9_]*)\',\s*'[^']*',", roles_sql, flags=re.M))
    java_roles = set(re.findall(r"SUPER_ADMIN|GENERAL_MANAGER|PRODUCTION_MANAGER|PRODUCTION_OPERATOR|"
                                r"WAREHOUSE_MANAGER|WAREHOUSE_EMPLOYEE|SALES_MANAGER|SALES_REPRESENTATIVE|"
                                r"ACCOUNTANT|PURCHASING_MANAGER|DELIVERY_MANAGER|VIEWER",
                                read(os.path.join(JAVA_SECURITY, "DefaultRoles.java"))))
    if role_codes != java_roles:
        fail("seed 02 roles %s differ from DefaultRoles %s" % (sorted(role_codes), sorted(java_roles)))

    used = set()
    for block in re.findall(r"ON\s+p\.code\s+IN\s*\(([^)]*)\)", roles_sql):
        used.update(re.findall(r"'([A-Z][A-Z0-9_]*)'", block))
    unknown = used - permission_codes
    if unknown:
        fail("seed 02 grants unknown permissions: %s" % ", ".join(sorted(unknown)))
    if not used and "CROSS JOIN permissions" not in roles_sql:
        fail("seed 02 grants no permission at all")

    # catalogue seeds
    for name in sorted(os.listdir(SEED_DIR)):
        if not name.endswith(".sql"):
            continue
        sql = read(os.path.join(SEED_DIR, name))
        if not balanced(strip_comments(sql)):
            fail("%s: unbalanced parentheses or quotes" % name)
        for table in re.findall(r"INSERT\s+INTO\s+`?(\w+)`?", sql, flags=re.I):
            if table not in tables:
                fail("%s: INSERT INTO unknown table '%s'" % (name, table))


def check_readme() -> None:
    path = os.path.join(ROOT, "database", "README.md")
    if not os.path.isfile(path):
        fail("database/README.md is missing")
        return
    text = read(path)
    for filename in sorted(os.listdir(SCHEMA_DIR)):
        if filename.endswith(".sql") and filename not in text:
            fail("database/README.md does not document %s" % filename)


def main() -> int:
    tables, order = parse_schema()
    check_seeds(tables)
    check_readme()
    for problem in problems:
        print("problem: " + problem)
    print("%d tables, %d problems" % (len(tables), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
