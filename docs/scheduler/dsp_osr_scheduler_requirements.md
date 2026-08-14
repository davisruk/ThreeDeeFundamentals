# DSP Simulation Requirements (Consolidated)

This document defines the requirements for a simulation of the DSP (Dispensing Support Pharmacy) warehouse, consolidating:

- TDP-182 (logical model)
- CPAS Interface Specification (message & order model)
- KNAPP General Specification PD (physical flow, stations, start logic)

---

# 1. Terminology Standardisation

Multiple source documents use different terms for similar or overlapping concepts. This simulation shall adopt a single consistent vocabulary and map all known equivalents.

## 1.1 Standard Terms Used in This Document

| Standard Term | Equivalent Terms | Source | Meaning |
|--------------|-----------------|--------|--------|
| **Notional Tote** | Order Group, POG | TDP-182 | Logical grouping of items for a store / dispatch |
| **Physical Tote (Load Unit)** | Tote, Load Unit | KNAPP | Physical container moving through system |
| **Order Type: ADAPTED** | ASSOCIATED (historical overlap), Adapted Order | Interface / TDP | Pre-processing / staging of items |
| **Order Type: ASSOCIATED** | ASSOCIATED (in some contexts), Constituent | Interface / TDP | Main consolidation order |
| **Order Type: FULL_PACK** | FULL_PACK | TDP | Fully automated order |
| **Order Type: EMPTY** | Empty Tote Order | Interface | Order that creates a tote at AV02 |
| **Manual Flow** | MANUAL_FLOW | TDP | Manual processing path |

---

## 1.2 Important Clarifications

### ASSOCIATED vs ASSOCIATED

- In TDP-182, **ASSOCIATED** often refers to a tote carrying mixed or constituent items
- In the Interface Spec, this behaviour aligns most closely with **ASSOCIATED orders**

Simulation rule:

> ASSOCIATED (TDP term) shall be treated as ASSOCIATED (order type) unless explicitly modelling tote subtype

---

### FULL_PACK vs FULL_PACK

- FULL_PACK (TDP) = Full Pack

Simulation rule:

> FULL_PACK shall be represented as FULL_PACK

---

### MANUAL_FLOW vs MANUAL

- MANUAL_FLOW represents manual tote flow

Simulation rule:

> MANUAL_FLOW shall be represented as MANUAL flow, not an order type

---

### EMPTY

- Only defined in Interface + KNAPP
- Not present in TDP-182

Simulation rule:

> EMPTY is a first-class order type and must not be conflated with ASSOCIATED

---

## 1.3 Naming Convention Rules

To avoid ambiguity, the simulation shall use the following conventions consistently:

- **Use OrderType for behaviour**
  - Allowed values: `ADAPTED`, `ASSOCIATED`, `FULL_PACK`, `EMPTY`

- **Use MANUAL_FLOW to describe manual processing paths**
  - This is a flow characteristic, not an OrderType

- **Use Physical Tote / Load Unit for the container**
  - Do not encode order lifecycle behaviour in the tote name
  - `ToteType` describes physical carrier role / capability only

- **Do NOT use legacy terms in the main model**
  - Disallowed in code and requirements: `CPC`, `CPF`, `CPNA`
  - These may appear only in the terminology mapping table for reference

- **Mapping guidance (for reference only)**
  - `CPC` → `ASSOCIATED`
  - `CPF` → `FULL_PACK`
  - `CPNA` → `MANUAL_FLOW`

- **Separation of concerns**
  - *OrderType* defines how work starts and flows
  - *12N line type* defines order-specific processing intent
  - *Product master data* defines Third Party location and physical dimensions
  - *Route requirements* define which stations are visited

These rules are mandatory for all subsequent sections of this document.

---

# 2. Core Concepts

## 1.1 Notional Tote

A **Notional Tote** is a logical grouping of items that belong together for a store (and typically multiple patients).

It is used to:
- group work across systems
- correlate multiple physical processes
- enable consolidation of items into dispatchable units

Key properties:

- May span multiple physical totes
- May involve multiple order types
- Is the primary correlation key across flows

---

## 1.2 Physical Tote (Load Unit)

A **Load Unit (Tote)** is a physical container used in the warehouse.

From KNAPP:
- A load unit consists of container + contents + identifiers
- The authoritative `ToteType` model is defined in section 2.

---

# 2. Order vs Tote Model

This section clarifies the distinction between **OrderType** (process intent) and **ToteType** (physical carrier), and defines how they interact.

## 2.1 Definitions

- **OrderType** defines *what work is performed* and *how it flows through the system*.
- **ToteType** defines *the physical container* used to carry items through the system.

```java
enum OrderType {
    ADAPTED,
    EMPTY,
    ASSOCIATED,
    FULL_PACK
}

// Physical/container classification (capability/role)
enum ToteType {
    ASSOCIATED,   // carrier for mixed/consolidated flows
    FULL_PACK,    // carrier for fully automated flows
    MANUAL_FLOW   // carrier for manual-only handling
}
```

## 2.2 Responsibilities

### OrderType controls:
- Start location (OSR vs AV02)
- Dependencies (e.g. ADAPTED completion)
- High-level routing behaviour
- Lifecycle (creation, processing, completion)

### ToteType controls:
- Physical movement through stations
- What kinds of items can be carried
- Historical physical-carrier role; active MANUAL processing is excluded
- Capacity and queueing behaviour

## 2.3 Relationship

- Orders may **create** or **use** totes
- Totes are **allocated to orders** during execution

```text
Order ──creates/uses──► Tote
```

## 2.4 Typical (Non-Strict) Mapping

| OrderType   | Typical ToteType |
|-------------|------------------|
| ADAPTED     | (no dedicated tote; staging flow) |
| ASSOCIATED  | ASSOCIATED |
| FULL_PACK   | FULL_PACK |
| EMPTY       | ASSOCIATED (after allocation) |

> Note: This mapping is **not enforced**; OrderType and ToteType are intentionally decoupled.

## 2.5 Lifecycle Examples

### ASSOCIATED
```text
OSR → retrieve tote → process → dispatch
```

### EMPTY
```text
AV02 → allocate empty tote → process (as ASSOCIATED) → dispatch
```

### ADAPTED
```text
OSR → process items → stage → complete (no tote dependency)
```

## 2.6 Design Rules

- Do not conflate OrderType with ToteType
- Do not enforce 1:1 mapping
- Model EMPTY as a start mechanism that **creates** a tote
- Treat ADAPTED as a process stage, not a tote-driven flow

---

# 3. Order Types (Authoritative Model)

From Interface Spec + KNAPP:

```java
enum OrderType {
    ADAPTED,     // 02
    EMPTY,       // 03
    ASSOCIATED,  // 04
    FULL_PACK    // 05
}
```

---

## 2.1 Critical Behaviour

### ADAPTED
- Prepares items (sorting / staging)
- Must complete before ASSOCIATED / EMPTY

### ASSOCIATED
- Main consolidation flow
- Starts from OSR
- Combines:
  - adapted items
  - automated items
  - 3rd party items

### FULL_PACK
- Fully automated flow
- Starts from OSR
- Goes through P2P as the main automated tote-to-bag flow
- No manual merge or adapted-line consolidation

### EMPTY (CRITICAL)

From KNAPP:
- Starts at AV02
- Allocates a physical empty tote

Definition:

> EMPTY ORDER = an order that creates a tote and then behaves like an associated order

Key rules:

- Starts WITHOUT a tote
- Requires empty tote supply at AV02
- After start → behaves like ASSOCIATED

---

# 3. Order Start Model

## 3.1 Start Locations

```java
enum StartLocation {
    OSR,
    AV02
}
```

### Mapping

| Order Type | Start Location |
|------------|----------------|
| ADAPTED    | OSR |
| ASSOCIATED | OSR |
| FULL_PACK  | OSR |
| EMPTY      | AV02 |

---

## 3.2 Start Behaviour

### OSR Start
- Tote already exists
- Retrieved from buffer

### AV02 Start
- Tote must be created
- Empty tote allocated

Simulation rule:

```java
if (order.type == EMPTY) {
    allocateEmptyTote();
}
```

---

# 4. Product Master And Order-Line Processing

Product location/capability data and order-specific processing instructions have different sources of truth.

## 4.1 Product master

The DSP product-master export is loaded independently from 12N JSON. The current source is:

`app/md/product_automation.csv`

The 12N `productId` joins to `dispensingProductPackColumbusCode`.

Initially useful master-data fields are:

- product id
- display name
- optional `thirdPartyLocation`
- physical length, width, and height

A nonblank `thirdPartyLocation` means the product is sourced from the Third Party Area. Retain the location; do not reduce it permanently to a boolean.

The approximately 5,500 records should be held in an in-memory repository. A relational database is not required.

## 4.2 Order-specific processing

12N line type is authoritative for processing required by that order line:

- `05` / `FULL_PACK`: the line can use the P2P labelling flow for this order.
- `02` / `ADAPTED`: the line requires adapting/preparation for this order.
- `01` / `MANUAL`: excluded from the active simulator scope.

Do not derive this processing decision from a fixed product category. Columbus may place an otherwise automatable product into an ADAPTED flow because order-specific label content cannot fit on a P2P label.

Product master answers where the product is sourced and supplies dimensions. The 12N line answers how it is processed for this order.

---

# 4A. 12N Line-Level Destination And Prepared Work

The 12N examples show that destination and preparation readiness must be modelled at line level.

Rules:

- `pharmacyId` is an order-line field, not an order-level field.
- Final dispatch totes/bags must not contain bags for different pharmacies.
- `ASSOCIATED`, `EMPTY`, and `FULL_PACK` dispatch orders must therefore be pharmacy-pure when their order lines are considered together.
- Manual tote order examples are always pharmacy-pure.
- `ADAPTED` tote orders are preparation batches and may contain lines for multiple pharmacies.
- `ADAPTED` must not be treated as a notional-tote-pure dispatch carrier.
- The 12N line reference is globally distinct and precisely correlates an ADAPTED preparation line with its target ASSOCIATED line.
- `referenceOrderId` identifies/groups the target ASSOCIATED order.
- `referenceSheetNumber` is retained for protocol fidelity but is always `001` and must not be used as a meaningful identity discriminator.
- Prepared-line identity is `target order id + line reference`.
- The 12N header `sheetNumber` remains the real sheet number for order sequencing.
- Scheduler readiness must be based on terminal prepared-line outcomes, not on a whole adapted notional tote being complete.

Implication:

```text
serviceCentreId = release-window grouping
pharmacyId = final dispatch/store purity, line-level
notionalToteId = dispatch/consolidation grouping
adapted order = preparation source, may unlock target dispatch lines
```

---

# 5. Routing Model

## 5.1 Item-driven Routing

Stations are driven by item requirements.

---

## 5.2 Stations

From KNAPP:

- OSR Buffer
- 3rd Party Area
- Adapting Area
- P2P
- Error Handling
- Dispatch
- AV02 (order start)

---

## 5.3 Routing Rules

### Third Party

Third Party work is line- and lifecycle-specific:

- an ADAPTED source line requires Third Party when its product has a Third Party location and outstanding quantity;
- a `FULL_PACK` line in a `FULL_PACK`, `ASSOCIATED`, or `EMPTY` fulfilment order requires a direct Third Party pick when its product has a Third Party location and outstanding quantity;
- an ADAPTED line in an ASSOCIATED or EMPTY order is collected through Adapting and must not be repicked at Third Party;
- completed Third Party work must not cause a revisit.

### Adapting

- ADAPTED preparation orders visit Adapting after any required Third Party pick.
- ASSOCIATED and EMPTY orders with adapted dependencies collect terminal preparation outcomes through Adapting.
- FULL_PACK orders never collect adapted lines.

### Manual

Manual work is no longer in active simulator scope. Ignore MANUAL preparation messages and MANUAL lines, report them during ingestion, and derive no manual route or merge requirement.

---

# 6. Manual Data Exclusion

Historical production datasets contain MANUAL work, but the production DSP is ceasing that flow and it is not simulated.

Rules:

- Ignore MANUAL preparation messages.
- Remove MANUAL lines from mixed dispatch messages.
- Omit dispatch orders that contain no simulated lines after filtering.
- Report ignored messages, lines, and orders.
- Sequence retained simulated sheets without allowing an omitted manual-only sheet to block later retained work.

---

# 7. Empty Orders in Flow

## 7.1 Behaviour

```text
EMPTY → AV02 → allocate tote → behave like ASSOCIATED
```

## 7.2 Constraints

- Not buffered in OSR
- Requires physical tote supply

---

# 8. Order Dependencies

From KNAPP:

- Adapted must complete first
- Associated / Empty depend on Adapted

---

# 9. Scheduler Requirements

## 9.1 Ordering Dimensions

The scheduler shall consider multiple independent ordering dimensions:

### Service Centre Ordering

- Orders are grouped by **Service Centre**
- Service Centres shall be processed in a deterministic sequence
- Example strategy:

```text
Round-robin across service centres
or
Priority-based ordering
```

---

### Sheet Ordering

- Each Notional Tote may be split into **multiple sheets**
- Sheets must be processed in sequence:

```text
Sheet 1 → Sheet 2 → Sheet 3
```

- A later sheet must not be released before an earlier sheet

---

## 9.2 Dependency Types

Dependencies shall be explicitly modelled.

### Types of Dependency

```java
enum DependencyType {
    ADAPTED_COMPLETION,
    SHEET_SEQUENCE,
    SERVICE_CENTRE_ORDER
}
```

### Rules

- ASSOCIATED / EMPTY orders depend on every required adapted line reaching a terminal preparation outcome
- Phase 1 adapting/Third Party work produces COMPLETE outcomes only
- Future Exception work adds INCOMPLETE as another dependency-resolving outcome; only COMPLETE adds a physical pack
- Sheets depend on previous sheet completion

---

## 9.3 Starvation / Deadlock Avoidance

The scheduler shall prevent starvation and deadlock.

### Starvation Avoidance

- No Service Centre shall be indefinitely blocked
- Implement fairness:

```text
Max consecutive releases per Service Centre
```

---

### Deadlock Avoidance

Deadlock scenarios:

- Waiting for preparation outcomes that are never produced
- Waiting for downstream capacity while upstream is blocked

Mitigation:

- Detect circular wait conditions
- Allow override release after timeout

---

## 9.4 Capacity Constraints (Detailed)

Each station shall define capacity:

```java
class StationCapacity {
    int maxInProgress;
    int queueLimit;
}
```

### Behaviour

- If queue is full → block upstream release
- If processing full → hold at previous station

### Stations to model capacity for:

- Third Party Area
- Adapting Area
- P2P
- Exception Area when implemented

---

## 9.5 Release Strategy

Release priority:

1. ADAPTED
2. ASSOCIATED / EMPTY
3. FULL_PACK

Within priority:

- Service Centre ordering
- Sheet ordering
- FIFO

---

# 10. Renderable Lifecycle / Performance Constraint

The scheduler may load a full day of order and pack data. A production-scale run may contain around 110,000 packs, but only a small fraction should be represented by active renderables at any point in time.

Simulation rule:

> Loaded order/item data is not the same thing as active simulation objects or renderable objects.

## 10.1 Lifecycle Rules

- Orders waiting in OSR are data only.
- Packs inside unreleased OSR orders are data only.
- A physical tote renderable is created when:
  - an OSR order is released, or
  - an EMPTY order allocates a tote at AV02.
- Pack renderables are created only for packs in released / active totes.
- Pack renderables are visible only when:
  - the containing tote lid is open, or
  - the pack has left tote containment and is moving through active machine flow.
- When the tote lid is closed, contained pack renderables should be hidden or skipped rather than evaluated.
- Once packs enter a bag / bagger black-box stage, individual pack renderables may be hidden, retired, or returned to a pool while logical pack contents remain in domain state.
- Bag renderables represent bagged contents; bagged packs do not normally need individual renderables.

## 10.2 Visibility Flag

Renderable objects should support a cheap visibility flag or equivalent mechanism.

Rules:

- Hidden renderables must be skipped early in update/render traversal.
- Visibility should be toggled for common state changes such as tote lid open/closed.
- Avoid repeated allocation and destruction inside the main tick loop where a stable renderable can be hidden and later shown.
- Do not create renderables for all loaded order data up front.

This rule applies to:

- totes
- contained packs
- free-moving packs
- bags
- optional debug labels / overlays

---

# 11. Key Principles

1. Notional Tote = logical grouping
2. Order Type defines start + behaviour
3. EMPTY = creation of tote
4. Routing = line-lifecycle plus product-location driven
5. Product master supplies Third Party location and dimensions
6. 12N line type supplies order-specific processing intent
7. MANUAL work is excluded from active simulation

---

# 12. Final Model Summary

```text
Order arrives

IF EMPTY:
    start at AV02
    allocate tote
ELSE:
    retrieve from OSR

Process:
    Adapted -> terminal prepared-line outcome
    Associated/Empty -> conservative release after dependencies resolve

Routing:
    Third Party where line lifecycle and product bin location require it
    Adapting for preparation/store or collection

P2P:
    create bags

Dispatch
```

---

This model aligns:
- Logical (TDP-182)
- Interface (order types)
- Physical (KNAPP system behaviour)
