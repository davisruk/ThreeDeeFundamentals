# Adapting Station Requirements

This document defines the behaviour and lifecycle of the Adapting Station within the DSP simulation model.

The requirements are derived from:
- TDP-182
- CPAS Interface Specification
- KNAPP General Specification (GS_PD)

---

# 1. Purpose

The Adapting Station is responsible for:

- preparing products that require adaptation or manual labelling
- temporarily staging prepared products
- enabling downstream fulfilment orders to retrieve prepared products later

The Adapting Station acts as:

- a preparation area
- a temporary storage area
- a deferred fulfilment dependency

It is not a dispatch or final bagging area.

---

# 2. Core Behaviour

The Adapting Station supports two fundamentally different tote interactions.

| Interaction Type | Purpose |
|---|---|
| Preparation / Deposit | Deliver products for adaptation and staging |
| Fulfilment / Retrieval | Retrieve previously adapted products |

---

# 3. Preparation / Deposit Flow

## 3.1 Overview

An `ADAPTED` order may arrive at the Adapting Station carrying:

- automated full packs
- 3rd party items
- items requiring adaptation / relabelling

The tote acts as a:

```text
shared preparation transport
```

and may contain:

- multiple pharmacies
- multiple stores
- multiple downstream orders
- multiple patients

This behaviour is intentional and reflects warehouse optimisation.

---

## 3.2 Processing Behaviour

At the Adapting Station:

- products are identified
- labels may be applied
- products may be verified
- products are sorted into staging locations

The tote itself does not remain associated with the products after staging.

---

## 3.3 Staging Behaviour

Prepared products shall be staged using:

- `ReferenceOrderId`
- `ReferenceSheetNumber`

The staged products represent a deferred dependency for later fulfilment orders.

Example:

```text
ASSOCIATED order 90001 sheet 001
    waiting for:
        adapted products
```

---

## 3.4 Physical Interpretation

The ADAPTED tote is:

- a temporary transport mechanism
- not a dispatch grouping
- not store-pure

After deposit:

- the tote is removed/stored by the station
- for Phase 1, the tote can disappear after STORE processing
- the staged products remain

---

# 4. Fulfilment / Retrieval Flow

## 4.1 Overview

`ASSOCIATED` or `EMPTY` orders later retrieve prepared products from staging.

`FULL_PACK` orders do not retrieve adapted products.

These orders represent:

```text
store-specific fulfilment flows
```

---

## 4.2 Retrieval Rules

When a fulfilment tote arrives:

- staged products matching:
  - `ReferenceOrderId`
  - `ReferenceSheetNumber`
- shall be retrieved

The products are then:

- inserted into the fulfilment flow
- added to the collecting tote load plan
- carried onward toward:
  - P2P
  - manual merge
  - dispatch

---

## 4.3 Dependency Rule

A fulfilment order must not proceed past the dependency point until:

- all required adapted products are available
- or timeout / exception handling rules apply

---

# 5. Adapted Product Store

The simulation shall model adapting storage independently from totes.

Loaded ADAPTED 12N prepared-line data is source work for the station. It must not automatically make a target dispatch dependency ready. A prepared-line key becomes available to the scheduler after the adapting station has processed the source STORE visit and staged the line. Test fixtures may seed already-staged startup state explicitly where needed.

Example:

```java
class AdaptedProductStore {
    Map<OrderReference, List<PreparedItem>> stagedItems;
}

record OrderReference(
    String orderId,
    int sheetNumber
) {}
```

---

# 6. Multi-Store Behaviour

ADAPTED totes may contain products for:

- multiple pharmacies
- multiple stores
- multiple downstream orders

However:

- staged products must be separated logically
- retrieval is always performed against downstream order references

---

# 7. Lifecycle Summary

```text
ADAPTED tote arrives for STORE
    ↓
Products adapted / labelled
    ↓
Products staged in racks / bins
    ↓
ADAPTED tote is removed/stored
    ↓
ASSOCIATED / EMPTY order arrives later
    ↓
Prepared products retrieved
    ↓
Products continue through fulfilment flow
```

---

# 8. Key Simulation Principles

- ADAPTED flow is preparation-oriented
- ASSOCIATED / EMPTY flow is fulfilment-oriented
- Staged products outlive the tote that delivered them
- Physical totes and logical dependencies are decoupled
- Adapted staging behaves like deferred warehouse-managed inventory

---

# 9. Simulation Implications

The simulation should support:

- asynchronous preparation and retrieval
- multiple downstream dependencies
- staging capacity limits
- dependency-aware scheduling
- delayed fulfilment
- partial retrieval scenarios
- timeout / exception handling

The Adapting Station should therefore be treated as both:

- a processing station
- and a temporary inventory subsystem
