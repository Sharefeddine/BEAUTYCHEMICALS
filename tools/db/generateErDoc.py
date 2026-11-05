#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generates docs/02-er-diagram.md from the real DDL in database/schema/.

Editing the schema and running this script keeps the documentation in sync - the ER model can
no longer drift from the files that build the database.

Usage: python3 tools/db/generateErDoc.py [--check]
       --check exits 1 when the committed document is stale (used by CI).
"""
from __future__ import annotations

import io
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SCHEMA_DIR = os.path.join(ROOT, "database", "schema")
TARGET = os.path.join(ROOT, "docs", "02-er-diagram.md")

MODULE_TITLES = {
    "01_core.sql": "Core - company, people, security",
    "02_organisation.sql": "Organisation - treasury and staff",
    "03_catalogue.sql": "Catalogue - products, materials, stock",
    "04_production.sql": "Production - formulas, orders, quality",
    "05_commerce.sql": "Commerce - purchasing, sales, distribution",
    "06_finance.sql": "Finance - cash, expenses, payroll",
    "07_safety.sql": "Chemical safety",
    "08_system.sql": "System - settings, numbering, views",
}

INTRO = """# ChemManager ERP — Entity / Relationship Model

The relational model is implemented in `database/schema/` (MySQL 8, `InnoDB`, `utf8mb4_unicode_ci`).
**This document is generated from those files** (`python3 tools/db/generateErDoc.py`), so every table
and column below exists in the DDL.

## Conventions

| Rule | Detail |
| --- | --- |
| Names | **lowerCamelCase, no underscore** — tables, columns, indexes, constraints, triggers and views |
| Primary keys | `id BIGINT UNSIGNED AUTO_INCREMENT`; business codes also unique |
| Foreign keys | `<entity>Id` (`customerId`, `productBatchId`) |
| Audit | `createdAt`, `updatedAt` (`DATETIME`), `createdBy`, `updatedBy` (`users.id`) |
| Soft delete | `deletedAt DATETIME NULL` and/or `isActive TINYINT(1)` — documents and stock history are never deleted |
| Numbers | money `DECIMAL(15,2)`, quantities `DECIMAL(15,3)`, rates `DECIMAL(7,4)`, currency `CHAR(3)` default `DZD` |
| Status | `ENUM` mirrored by a Java/PHP constant list |
| Indexes | `idx<Table><Purpose>`, unique `uq<Table><Purpose>`, `fk<Table><Target>`, `ck<Table><Rule>` |
| Views | `vw<Name>` |

## 1. Overview diagram

```mermaid
erDiagram
    USERS ||--o{ USERROLES : has
    ROLES ||--o{ USERROLES : granted
    ROLES ||--o{ ROLEPERMISSIONS : contains
    PERMISSIONS ||--o{ ROLEPERMISSIONS : in
    USERS ||--o{ APITOKENS : owns
    USERS ||--o{ AUDITLOGS : writes
    USERS ||--o{ NOTIFICATIONS : receives
    DEPARTMENTS ||--o{ EMPLOYEES : employs

    PRODUCTCATEGORIES ||--o{ PRODUCTS : classifies
    RAWMATERIALCATEGORIES ||--o{ RAWMATERIALS : classifies
    SUPPLIERS ||--o{ RAWMATERIALS : supplies
    SUPPLIERS ||--o{ MATERIALBATCHES : delivers
    PRODUCTS ||--o{ PRODUCTBATCHES : "produced as"
    RAWMATERIALS ||--o{ MATERIALBATCHES : "held as"
    WAREHOUSES ||--o{ MATERIALBATCHES : stores
    WAREHOUSES ||--o{ PRODUCTBATCHES : stores
    MATERIALBATCHES ||--o{ STOCKMOVEMENTS : moves
    PRODUCTBATCHES ||--o{ STOCKMOVEMENTS : moves

    FORMULATIONS ||--o{ FORMULATIONVERSIONS : versions
    FORMULATIONVERSIONS ||--o{ FORMULATIONITEMS : "composed of"
    RAWMATERIALS ||--o{ FORMULATIONITEMS : "used in"
    PRODUCTS ||--o{ FORMULATIONS : "manufactured by"
    PRODUCTIONORDERS ||--o{ PRODUCTIONMATERIALS : consumes
    PRODUCTIONORDERS ||--o{ PRODUCTIONOUTPUTS : yields
    PRODUCTIONORDERS ||--o{ QUALITYCHECKS : "checked by"
    PRODUCTIONORDERS ||--o{ PRODUCTBATCHES : produces

    PURCHASEORDERS ||--o{ PURCHASEORDERITEMS : contains
    PURCHASEORDERS ||--o{ GOODSRECEIPTS : received
    GOODSRECEIPTS ||--o{ GOODSRECEIPTITEMS : contains
    SALESORDERS ||--o{ SALESORDERITEMS : contains
    SALESORDERS ||--o{ DELIVERYNOTES : shipped
    DELIVERYNOTES ||--o{ DELIVERYITEMS : contains
    SALESORDERS ||--o{ INVOICES : billed
    INVOICES ||--o{ INVOICEITEMS : contains
    INVOICES ||--o{ CREDITNOTES : corrected
    CUSTOMERS ||--o{ SALESORDERS : places
    CUSTOMERS ||--o{ INVOICES : owes
    CUSTOMERS ||--o{ PAYMENTS : pays
    DELIVERYROUTES ||--o{ ROUTESTOPS : orders
    VEHICLES ||--o{ DELIVERYROUTES : serves

    CASHACCOUNTS ||--o{ CASHTRANSACTIONS : books
    CASHACCOUNTS ||--o{ PAYMENTS : receives
    CASHACCOUNTS ||--o{ SUPPLIERPAYMENTS : pays
    EXPENSECATEGORIES ||--o{ EXPENSES : classifies
    EMPLOYEES ||--o{ SALARYPAYMENTS : earns
    EMPLOYEES ||--o{ ATTENDANCE : checks
    ACCOUNTINGPERIODS ||--o{ CASHTRANSACTIONS : groups

    RAWMATERIALS ||--o| SAFETYINSTRUCTIONS : documented
    RAWMATERIALS ||--o{ SAFETYDATASHEETS : "has SDS"
    RAWMATERIALS ||--o{ MATERIALPICTOGRAMS : labelled
    SAFETYPICTOGRAMS ||--o{ MATERIALPICTOGRAMS : labels
```

## 2. Modules and tables

"""

OUTRO = """## 3. Rules enforced by the database itself

| Rule | Mechanism |
| --- | --- |
| A stock quantity is never negative | `CHECK` constraints + `trgMaterialBatchesNoNegative`, `trgProductBatchesNoNegative` |
| An approved formulation version is immutable | `trgFormulationVersionsLocked` (any `UPDATE` aborts) |
| An issued invoice is immutable | `trgInvoicesFinalized`, `trgInvoiceItemsImmutable` — corrections go through `creditNotes` |
| A stock movement touches exactly one batch kind | `ckStockMovementsTarget`, `CHECK (quantity > 0)` |
| Traceability is complete | every `invoiceItems` row keeps its `productBatchId`; every `productBatches` row keeps its `productionOrderId` |
| Financial history is append-only | `cashTransactions` has no update path; the app account has no `DELETE` grant |
| Numbering is gapless | `documentSequences` locked with `SELECT … FOR UPDATE` inside the transaction |
| A closed period rejects postings | `accountingPeriods.status` checked by the API before any insert |

## 4. Reporting views

| View | Purpose |
| --- | --- |
| `vwStockBalances` | released stock per product/material with minimum level and nearest expiry |
| `vwLowStock` | rows of `vwStockBalances` at or below `minStock` (dashboard alert card, restock report) |

---

*Generated from `database/schema/*.sql` by `tools/db/generateErDoc.py` — do not edit by hand.*
"""


def strip_comments(sql: str) -> str:
    sql = re.sub(r"/\*.*?\*/", "", sql, flags=re.S)
    return re.sub(r"--[^\n]*", "", sql)


def split_top_level(body: str) -> list[str]:
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


def table_body(statement: str) -> str:
    start = statement.find("(")
    depth = 0
    for index in range(start, len(statement)):
        if statement[index] == "(":
            depth += 1
        elif statement[index] == ")":
            depth -= 1
            if depth == 0:
                return statement[start + 1:index]
    return ""


NON_COLUMN = ("PRIMARY KEY", "UNIQUE KEY", "KEY ", "INDEX ", "CONSTRAINT", "FOREIGN KEY", "CHECK")


def parse() -> list[dict]:
    modules = []
    for filename in sorted(os.listdir(SCHEMA_DIR)):
        if not filename.endswith(".sql"):
            continue
        sql = strip_comments(io.open(os.path.join(SCHEMA_DIR, filename), encoding="utf-8").read())
        tables, views, triggers = [], [], []
        for statement in re.split(r";\s*\n(?=CREATE|ALTER)", sql):
            statement = statement.strip()
            header = statement.upper()
            if header.startswith("CREATE TABLE"):
                name = re.match(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?", statement, re.I).group(1)
                comment = re.search(r"COMMENT\s*=\s*'((?:[^']|'')*)'\s*;?\s*$", statement)
                columns, keys, references = [], [], []
                for definition in split_top_level(table_body(statement)):
                    upper = definition.upper()
                    if upper.startswith(NON_COLUMN):
                        key_match = re.match(r"(?:PRIMARY\s+KEY|UNIQUE\s+KEY|KEY)\s+`?(\w+)`?", definition, re.I)
                        if key_match:
                            keys.append(key_match.group(1))
                        source = re.search(r"FOREIGN\s+KEY\s*\(\s*`?(\w+)`?\s*\)", definition, re.I)
                        for target, column in re.findall(
                                r"REFERENCES\s+`?(\w+)`?\s*\(\s*`?(\w+)`?", definition, re.I):
                            references.append((source.group(1) if source else "?", target, column))
                        continue
                    match = re.match(r"`?(\w+)`?\s+([A-Za-z]+(?:\([^)]*\))?(?:\s+UNSIGNED)?)", definition)
                    if not match:
                        continue
                    column = match.group(1)
                    kind = re.sub(r"\s+", " ", match.group(2)).upper()
                    generated = "GENERATED ALWAYS" in upper
                    columns.append({
                        "name": column,
                        "type": kind,
                        "notNull": "NOT NULL" in upper,
                        "default": re.search(r"DEFAULT\s+('(?:[^']|'')*'|[\w.]+)", definition, re.I),
                        "generated": generated,
                        "comment": (re.search(r"COMMENT\s+'((?:[^']|'')*)'", definition).group(1)
                                    if re.search(r"COMMENT\s+'((?:[^']|'')*)'", definition) else ""),
                    })
                tables.append({
                    "name": name,
                    "comment": comment.group(1) if comment else "",
                    "columns": columns,
                    "keys": keys,
                    "references": references,
                })
            elif header.startswith("CREATE TRIGGER"):
                match = re.match(r"CREATE\s+TRIGGER\s+`?(\w+)`?", statement, re.I)
                target = re.search(r"ON\s+`?(\w+)`?", statement, re.I)
                if match:
                    triggers.append("%s (%s)" % (match.group(1), target.group(1) if target else "?"))
            elif "VIEW" in header[:40]:
                match = re.search(r"VIEW\s+`?(\w+)`?", statement, re.I)
                if match:
                    views.append(match.group(1))
        modules.append({"file": filename, "tables": tables, "views": views, "triggers": triggers})
    return modules


def render(modules: list[dict]) -> str:
    lines = [INTRO]
    total = sum(len(module["tables"]) for module in modules)
    lines.append("| # | File | Module | Tables |\n| --- | --- | --- | --- |")
    for index, module in enumerate(modules, start=1):
        title = MODULE_TITLES.get(module["file"], module["file"])
        lines.append("| %d | `%s` | %s | %d |" % (index, module["file"], title, len(module["tables"])))
    lines.append("\n**%d tables** in total.\n" % total)

    for module in modules:
        lines.append("### %s\n" % module["file"].replace(".sql", "").replace("_", " - ") + "")
        lines[-1] = "### %s — %s\n" % (module["file"], MODULE_TITLES.get(module["file"], ""))
        for table in module["tables"]:
            lines.append("#### `%s`" % table["name"])
            if table["comment"]:
                lines.append("*%s*" % table["comment"].replace("''", "'"))
            lines.append("")
            rendered = []
            for column in table["columns"]:
                bits = ["`%s`" % column["name"], "`%s`" % column["type"]]
                if column["name"] == "id":
                    bits.append("**PK**")
                if column["generated"]:
                    bits.append("computed")
                elif not column["notNull"]:
                    bits.append("NULL")
                rendered.append(" ".join(bits))
            lines.append("- " + ", ".join(rendered))
            if table["keys"]:
                lines.append("- Keys: %s" % ", ".join("`%s`" % key for key in table["keys"]))
            if table["references"]:
                lines.append("- References: %s" % ", ".join(
                    "`%s` → `%s.%s`" % (source, target, column)
                    for source, target, column in table["references"]))
            lines.append("")
        if module["views"]:
            lines.append("Views: %s\n" % ", ".join("`%s`" % view for view in module["views"]))
        if module["triggers"]:
            lines.append("Triggers: %s\n" % ", ".join(module["triggers"]))
    lines.append(OUTRO)
    return "\n".join(lines)


def main() -> int:
    content = render(parse())
    if "--check" in sys.argv:
        current = io.open(TARGET, encoding="utf-8").read() if os.path.isfile(TARGET) else ""
        if current != content:
            print("docs/02-er-diagram.md is stale: run python3 tools/db/generateErDoc.py")
            return 1
        print("docs/02-er-diagram.md is up to date")
        return 0
    io.open(TARGET, "w", encoding="utf-8").write(content)
    print("written: docs/02-er-diagram.md (%d lines)" % len(content.splitlines()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
