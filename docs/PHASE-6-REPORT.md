# Phase 6 — Customers, sales, distribution and printable documents

> Scope: the customer file and its credit guard, the commercial chain quotation → order → delivery
> note → invoice → payment → credit note, the fleet and its daily tours with proof of delivery, and
> the printable documents in French, English and Arabic — on both halves (PHP API + JavaFX desktop),
> verified in CI against a real MySQL 8 database.

**Guiding rule:** a sale is a chain of signed facts, not a form anyone can rewrite. Stock leaves when
a delivery note is prepared, and the note names the exact batches; money is owed when an invoice is
issued, and an issued invoice is frozen — only a payment or a credit note can change what the
customer owes. The application never invents a price, a batch or a quantity.

---

## 1. Data model used

The tables existed since Phase 2; this phase gives them behaviour. **No schema change**: 68 tables,
schema checker still reports 0 problems.

| Table | Role in this phase |
| --- | --- |
| `customers` | `creditLimit`, `balance`, `priceSegment` (`RETAIL \| WHOLESALE \| SPECIAL`), `type`, `isActive`; unlimited credit is `creditLimit = 0` |
| `quotations` / `quotationItems` | `DV-00001` numbering, `DRAFT → SENT → ACCEPTED \| REJECTED \| EXPIRED`, price snapshot per line |
| `salesOrders` / `salesOrderItems` | `SO-00001`, `DRAFT → CONFIRMED → RESERVED \| PARTIAL → DELIVERED → INVOICED \| CANCELLED`, per-line `deliveredQty` / `invoicedQty` |
| `deliveryNotes` / `deliveryItems` | `BL-00001`, `PREPARED → LOADED → IN_TRANSIT → DELIVERED \| RETURNED`, one row per batch shipped, `signedBy`, `signaturePath`, `routeId` |
| `invoices` / `invoiceItems` | `FA-00001`, `DRAFT → ISSUED → PARTIAL → PAID \| CANCELLED`, `finalizedAt` is the wall, `dueDate`, `printedCount`, batch number per line |
| `payments` | `ENC-00001`, against an invoice or on the customer's account, always on a `cashAccount` |
| `creditNotes` | `AV-00001`, `ISSUED → APPLIED \| CANCELLED`, the only way to correct an issued invoice |
| `cashAccounts` | `CAISSE`, `BANQUE` — where a settlement physically lands |
| `vehicles` / `deliveryRoutes` / `routeStops` | `19-2145-116`, `TR-00001`, `PLANNED → LOADING → IN_TRANSIT → COMPLETED \| CANCELLED`; a stop is `PENDING → DELIVERED \| PARTIAL \| REFUSED` |
| `productBatches` / `stockMovements` | `SALES_OUT` leaves the released batch the note names, in the same transaction |
| `auditLogs` | every state change is logged with its document number |

## 2. API — 51 new routes (122 in total)

| Area | Routes | Permission |
| --- | --- | --- |
| Customers | `GET /api/customers`, `/selectable`, `/{id}`, `/{id}/statement`, `POST`, `PUT /{id}`, `DELETE /{id}` | `CUSTOMERS_VIEW` / `CUSTOMERS_MANAGE` |
| Quotations | `GET /api/quotations`, `/{id}`, `POST`, `/{id}/send`, `/{id}/accept`, `/{id}/reject` | `SALES_VIEW` / `SALES_CREATE` |
| Orders | `GET /api/salesOrders`, `/{id}`, `POST`, `/{id}/confirm`, `/{id}/cancel`, `/{id}/deliveries` | `SALES_VIEW` / `SALES_CREATE` |
| Delivery notes | `GET /api/deliveries`, `/{id}`, `/{id}/load`, `/{id}/deliver`, `/{id}/return` | `DISTRIBUTION_VIEW` / `DISTRIBUTION_MANAGE` |
| Invoices | `GET /api/invoices`, `/{id}`, `POST`, `PUT /{id}`, `/{id}/issue`, `/{id}/cancel`, `/{id}/creditNotes` | `SALES_VIEW` / `SALES_INVOICE` |
| Settlements | `GET /api/payments`, `POST /api/payments`, `GET /api/cashAccounts` | `FINANCE_VIEW` / `FINANCE_PAYMENT` |
| Credit notes | `GET /api/creditNotes`, `/{id}`, `/{id}/apply`, `/{id}/cancel` | `FINANCE_VIEW` / `FINANCE_PAYMENT` |
| Fleet | `GET /api/vehicles`, `POST /api/vehicles`, `PUT /api/vehicles/{id}`, `GET /api/drivers` | `DISTRIBUTION_VIEW` / `DISTRIBUTION_MANAGE` |
| Tours | `GET /api/routes`, `/{id}`, `POST`, `/{id}/load`, `/{id}/start`, `/{id}/stops/{stopId}/deliver`, `/{id}/complete`, `/{id}/cancel` | `DISTRIBUTION_VIEW` / `DISTRIBUTION_MANAGE` |
| Documents | `GET /api/documents/{type}/{id}?lang=fr\|en\|ar`, `type` ∈ `quotation, order, delivery, invoice, statement` | `SALES_VIEW` |

### Rules enforced in the repository

1. **Credit is a limit, not a hint.** Confirming an order whose total pushes the customer's balance
   above its `creditLimit` fails with `409 CREDIT_LIMIT_EXCEEDED`; `creditLimit = 0` means unlimited.
   A customer with a non-zero balance cannot be closed (`CUSTOMER_HAS_BALANCE`).
2. **A price is copied, never recomputed.** A quotation line stores the segment price and the discount
   it was quoted at; the order created from an accepted quotation keeps them. A later catalogue
   change does not rewrite history.
3. **Stock leaves through the batches the note names.** Preparing a delivery walks the released
   (`qualityStatus = RELEASED`) batches of the product in FEFO order
   (`GET /api/inventory/fefo` uses the same ordering), writes one `deliveryItems` row per batch,
   decrements `productBatches` and appends a `SALES_OUT` movement — all in one transaction. A
   shortage answers `409 SALES_STOCK_INSUFFICIENT` with what is missing; nothing is shipped partially
   behind the user's back.
4. **A delivery never exceeds its order.** Requesting more than the line's remaining quantity fails
   with `409 DELIVERY_EXCEEDS_ORDER`; an order with nothing left to ship answers `NOTHING_TO_DELIVER`.
5. **Issuing an invoice freezes it.** `finalizedAt` is set, the total is added to `customers.balance`
   and the same transaction writes the audit entry. Editing a finalized invoice fails with
   `409 INVOICE_FINALIZED`; invoicing an order with nothing to bill fails with `NOTHING_TO_INVOICE`.
6. **Money only moves in ways the books can follow.** A payment cannot exceed what is still due
   (`PAYMENT_EXCEEDS_BALANCE`), requires an active cash account, and always decrements the customer's
   balance; a payment on account carries no invoice. A credit note cannot exceed the invoice's
   remaining amount (`CREDIT_EXCEEDS_BALANCE`), applies as a balance decrease, and an invoice that is
   neither issued nor payable answers `INVOICE_NOT_PAYABLE` / `INVALID_STATUS`.
7. **A tour moves its notes with it.** A tour is built from `PREPARED` notes that are not yet planned
   (`NOTE_ALREADY_PLANNED`, `NOTE_NOT_PLANNABLE`); loading puts the tour in `LOADING`, departure in
   `IN_TRANSIT` and every loaded note in transit with it. Signing a stop signs the delivery note for
   the customer; refusing it requires a remark and keeps the note alive. Closing the day returns the
   notes nobody settled to `PREPARED` so they can be planned again, and books the fuel on the tour.
   A stop already settled answers `409 STOP_SETTLED`.
8. **Documents are rendered by the server.** `POST`-free, one self-contained HTML page per document
   with no external CSS or JavaScript, so a printed invoice looks the same everywhere. Arabic gets a
   right-to-left page (`dir="rtl"`) and the Arabic product names the catalogue carries; the company
   identity comes from Settings. Printing an invoice increments `printedCount` and is audited.

## 3. Desktop client — three screens

| Screen | What it does |
| --- | --- |
| **Customers** (`customers.fxml`) | list with price segment, credit limit, balance and available credit; detail panel with utilisation, invoice and order counts; create/edit dialog; statement dialog with debit/credit/balance lines and its printable version. The close button is disabled while the account still owes money |
| **Sales** (`sales.fxml`) | one screen for the four commercial documents — quotations, orders, delivery notes, invoices — with a status filter, a search, and the actions the selected document's state allows. An order shows its delivery and invoicing progress and lists the notes and invoices attached to it; a delivery note lists the batch numbers that left the warehouse; an invoice lists its payments and credit notes, and says in words that it is frozen once issued |
| **Distribution** (`distribution.fxml`) | two views of the same screen: the day's tours (stops served, loaded quantity, fuel) and the fleet (plate, model, payload, driver, open tours). A tour in transit shows its stops with a **Sign** and a **Refuse** button, disabled for stops already settled |

The client never computes a total, a credit position or a batch: the header, the credit figures, the
progress percentages and the batch numbers all come from the server, and every action re-reads the
document it changed.

Interaction rules the screens follow (from `docs/08-ux-guidelines.md`): a disabled button says why by
being disabled — the state the server would refuse is not offered; destructive transitions
(cancelling an order or a tour, issuing an invoice) ask a confirmation that names the document;
every list states how many rows exist and what an empty result means.

## 4. Printable documents

| Document | Markers in CI |
| --- | --- |
| Quotation | `DEVIS`, validity date, lines, totals |
| Order | `BON DE COMMANDE`, delivery date, lines |
| Delivery note | `BON DE LIVRAISON`, batch numbers, receiver's name, delivered date |
| Invoice | `FACTURE`, due date, payments, remaining |
| Statement | `RELEVÉ DE COMPTE`, debit / credit / running balance |
| Arabic | `فاتورة` and `dir="rtl"` on the page |

The desktop opens the page with the system browser/printer through `DocumentPrinter`, which downloads
the HTML from `/api/documents/{type}/{id}` in the language currently selected. In demo mode (no API)
it says so instead of opening an empty page.

**Known limit, stated on purpose:** proof of delivery is the receiver's name typed at the stop (and
stored in `deliveryNotes.signedBy`). A drawn signature has a column ready (`signaturePath`) but the
capture pad is not part of this phase — the roadmap's "signature capture" is delivered as a recorded
name, not a bitmap.

## 5. Verification

| Check | Result |
| --- | --- |
| PHP API — integration suite on MySQL 8 | **347 checks passed** (was 241 after Phase 5) |
| PHP API — unit tests | 25 passed |
| PHP lint (`php -l` on every file) | clean |
| JavaFX client — desktop tests | **79 passed**, 22 of them driven by **TestFX** through the real interface |
| FXML checker (`tools/fxml_check.py`) | 1 pre-existing, unrelated problem (`inventory.fxml` `fx:id="tabs"`) |
| i18n bundles | 1082 keys × 3 languages, 0 missing (`tools/i18n/audit.py`) |
| Design system (`tools/ui/checkDesign.py`) | unchanged, still green |
| CI run | **43593030825** (green), commit `6ed6ad2` |

New TestFX coverage: the customer file and its credit guard (3 tests), the commercial pipeline and
the frozen invoice (3 tests), the tours and their stops (2 tests). The API contract test now also
proves that `/api/documents/{type}/{id}` exists on the server, and the endpoints added to
`ApiEndpoints` are checked against `routes.php` on every build.

Error codes exercised by the suites: `CREDIT_LIMIT_EXCEEDED`, `CUSTOMER_HAS_BALANCE`,
`SALES_STOCK_INSUFFICIENT`, `DELIVERY_EXCEEDS_ORDER`, `NOTHING_TO_DELIVER`, `NOTHING_TO_INVOICE`,
`INVOICE_FINALIZED`, `INVOICE_NOT_PAYABLE`, `PAYMENT_EXCEEDS_BALANCE`, `CREDIT_EXCEEDS_BALANCE`,
`VEHICLE_EXISTS`, `NOTE_ALREADY_PLANNED`, `NOTE_NOT_PLANNABLE`, `STOP_SETTLED`, `ORDER_HAS_DELIVERIES`,
`INVALID_STATUS`.

## 6. Demo data

`database/seed/07_sales.sql` drives the whole story: five customers (`CL-0001` … `CL-0005`, one with
unlimited credit), a quotation that can be sent, one that can be accepted into an order, a draft
order, a partially shipped order, an invoiced order, two vehicles (`19-2145-116`, `19-3320-117`), two
cash accounts (`CAISSE`, `BANQUE`) and the three tours the distribution screen shows. The desktop
mirrors the same data in `MockSalesService` / `MockDistributionService`, so the demo mode can be
walked from a quotation to a signed stop without a database.

## 7. What Phase 7 inherits

* `payments` and `creditNotes` already carry the cash account and the customer, so the cash book of
  Phase 7 can be built from them without a migration.
* `customers.balance` is the truth for the receivable; the statement (`/api/customers/{id}/statement`)
  is already a ledger, and Phase 7's financial statements can page it instead of recomputing it.
* `deliveryRoutes.fuelCost` is the first vehicle expense; the expense module of Phase 7 books it.
* Open items for Phase 8: drawn signatures, PDF templates, and the report centre's exports of the
  documents this phase renders as HTML.
