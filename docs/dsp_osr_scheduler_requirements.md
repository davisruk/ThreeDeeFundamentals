# DSP OSR Scheduler Requirements (Simulation)

## 1. Purpose

This document defines the requirements and rules for a simulation scheduler responsible for releasing totes from an OSR (Order Storage and Retrieval) buffer in a manner broadly aligned with a modern DSP (Dispensing Support Pharmacy) environment.

The simulation is driven by simplified **12N messages** which represent executable tote instructions derived from upstream systems.

---

## 2. Core Concepts

### 2.1 Notional Tote

A **Notional Tote** is a logical grouping of work representing a single store delivery batch.

- It is a **correlation identifier**, not a physical object.
- Multiple physical totes may belong to a single Notional Tote.
- It is used to:
  - Link related totes
  - Manage dependencies
  - Support reconciliation of results

### 2.2 Physical Tote

A **Physical Tote** is the unit of work operated on by the simulation.

Each physical tote is derived from a 12N instruction and has:

- A Notional Tote ID
- A Tote Type
- A Sheet Number
- A Service Centre

The scheduler operates exclusively on **physical totes**.

### 2.3 12N Message (Simplified)

Each 12N message represents a single physical tote instruction.

Minimum required fields:

- `notionalToteId`
- `physicalToteId`
- `toteType`
- `sheetNumber`
- `serviceCentreId`

Optional fields:

- dependencies
- storeId

---

## 3. Tote Types

The simulation shall support the following tote types:

| Tote Type | Description | Leaves DSP |
|----------|-------------|------------|
| CPF | Full tote (automated flow) | Yes |
| CPC | Constituent tote (mixed items) | Yes |
| CPNA | Manual tote | Yes |

Notes:
- CPNA totes may represent manual-only work or standalone dispatch units.
- CPC totes represent the primary outbound flow for mixed items.

---

## 4. Stations Overview

The DSP simulation shall model a simplified set of stations to provide context for tote routing and processing. These stations do not need to model detailed behaviour, but must support routing decisions and sequencing.

### 4.1 OSR (Order Storage and Retrieval)

- Acts as the buffer for all incoming totes
- Scheduler releases totes from OSR
- No processing occurs here

---

### 4.2 Automated Line / P2P (Pack-to-Patient)

- Handles fully automatable items
- Creates patient bags for automated packs
- Final stage for CPF totes

---

### 4.3 3rd Party Station

- Handles picking of 3rd party stock
- Always requires human interaction
- May feed either:
  - CPC (sortable flow)
  - CPNA (manual flow)

---

### 4.4 Sortable / Preparation Station

- Handles items that cannot be labelled automatically
- Applies labels or performs verification
- Items remain within CPC flow
- Does not create separate totes

---

### 4.5 Manual Station

- Handles non-sortable manual items
- Processes CPNA totes
- Produces patient-level bag contents
- Feeds CPC merge or standalone dispatch

---

### 4.6 Manual Merge Point

- CPC totes may pass through this station
- Manual items are inserted into patient bags
- Represents consolidation of manual and automated flows

---

### 4.7 Dispatch / Exit

- Final stage of the DSP
- Completed totes leave the system
- Tote types that may exit:
  - CPF
  - CPC
  - CPNA (manual-only cases)

---

## 5. Routing Matrix

The following matrix defines the typical routing paths for each tote type within the DSP simulation.

```
CPF  → OSR → P2P → Dispatch

CPC  → OSR
      → (3rd Party Station, if required)
      → (Sortable / Preparation Station, if required)
      → (Manual Merge Point, if required)
      → P2P
      → Dispatch

CPNA → OSR
      → Manual Station
      → (Manual Merge Point, optional)
      → Dispatch
```

### 5.1 Routing Rules

- CPF totes follow a fully automated path with no manual intervention
- CPC totes may visit zero or more optional stations depending on item requirements
- CPNA totes always visit the Manual Station
- Manual Merge Point is only visited when CPC requires manual item consolidation
- Stations in parentheses are conditional and depend on tote contents

---

## 6. Route Requirements as Data

Each tote shall carry a set of **route requirements** derived from its contents. The scheduler and routing engine shall use these requirements to determine which stations the tote must visit.

### 6.1 Data Structure (Example)

```java
record ToteRouteRequirements(
    boolean requiresThirdPartyStation,
    boolean requiresSortablePreparation,
    boolean requiresManualMerge,
    boolean requiresManualStation,
    boolean isDispatchable
) {}
```

### 6.2 Default by Tote Type

| Tote Type | 3rd Party | Sortable Prep | Manual Station | Manual Merge | Dispatch |
|---|---:|---:|---:|---:|---:|
| CPF  | No  | No  | No  | No  | Yes |
| CPC  | Opt | Opt | No  | Opt | Yes |
| CPNA | Opt | No  | Yes | No  | Yes |

Notes:
- "Opt" (Optional) means the requirement is driven by tote contents.

### 6.3 Derivation Rules (Example)

```java
if (toteType == ToteType.CPC) {
    requiresThirdPartyStation = hasThirdPartyItems;
    requiresSortablePreparation = hasSortableItems;
    requiresManualMerge = hasManualDependencies;
}

if (toteType == ToteType.CPNA) {
    requiresManualStation = true;
    requiresThirdPartyStation = hasThirdPartyItems;
}

if (toteType == ToteType.CPF) {
    // no additional requirements
}
```

### 6.4 Example Instance

```json
{
  "notionalToteId": "TOTE66",
  "physicalToteId": "PHYS123",
  "toteType": "CPC",
  "sheetNumber": 1,
  "routeRequirements": {
    "requiresThirdPartyStation": true,
    "requiresSortablePreparation": true,
    "requiresManualMerge": false,
    "requiresManualStation": false,
    "isDispatchable": true
  }
}
```

### 6.5 Routing Interpretation

The routing engine shall translate requirements into a path:

```text
CPC with 3rd party + sortable:
OSR → 3rd Party Station → Sortable / Preparation Station → P2P → Dispatch
```

Key principle:

- `toteType` defines the **broad flow**
- `routeRequirements` refine the **exact path**

---

## 7. Scheduler Responsibilities

The scheduler is responsible for:

- Selecting which tote to release from OSR
- Enforcing service centre ordering
- Respecting tote-type priorities
- Ensuring dependencies are satisfied
- Avoiding starvation and deadlock
- Respecting downstream capacity constraints

---

## 5. Release Strategy

### 5.1 Service Centre Priority

Totes shall be grouped by `serviceCentreId`.

The scheduler shall process service centres in a defined priority order:

```
ServiceCentrePriority = [SC1, SC2, SC3, ...]
```

All release decisions shall first respect this ordering.

---

### 5.2 Tote Type Priority

Within a service centre, totes shall be released in the following order:

```
CPNA → CPF → CPC
```

Rationale:
- Manual work should be completed early where required
- Fully automated work can proceed independently
- CPC totes may depend on other work

---

### 5.3 Sheet Ordering

For totes belonging to the same:

- Notional Tote
- Tote Type

Sheets shall be released in ascending order:

```
001 → 002 → 003
```

---

## 6. Manual Integration Flow

### 6.1 Purpose

Manual items are handled separately from automated items, but may need to be combined with automated items before dispatch.

The simulation shall model this as a **bag-content merge**, not as a physical tote merge.

---

### 6.2 Manual Tote Processing

A CPNA tote represents manual work for one or more patients.

When released from OSR, a CPNA tote shall travel to the manual station.

At the manual station, the simulation shall:

- process the manual item instructions
- create patient-level manual bag contents
- associate those contents with:
  - `notionalToteId`
  - `patientId`
  - `storeId`
- mark the CPNA tote as complete

The CPNA tote itself does not merge with another tote.

---

### 6.3 Manual Bag Content Store

The simulation shall maintain a logical store of prepared manual bag contents.

Example structure:

```text
ManualBagContentStore:
  notionalToteId
    patientId
      manualItems[]
      status
```

This store acts as the hand-off point between CPNA processing and CPC consolidation.

---

### 6.4 CPC Manual Merge

A CPC tote may require manual items to be added to one or more patient bags.

When a CPC tote reaches the manual merge point, the simulation shall:

- identify required manual contents by `notionalToteId` and `patientId`
- retrieve the prepared manual contents
- add them to the corresponding patient bag
- mark the manual contents as consumed

After this step, the patient bag shall contain both automated and manual items.

---

### 6.5 Manual-Only Dispatch

If a CPNA tote represents manual-only work that does not need to merge into a CPC tote, it may be treated as a standalone dispatch tote.

In this case:

- the CPNA tote completes manual processing
- its patient bags are considered dispatch-ready
- no CPC dependency is required

---

### 6.6 Manual Integration Principles

- Manual items are prepared by CPNA totes
- CPC totes perform mixed-order consolidation
- Totes do not physically merge
- Patient bag contents may merge
- The Notional Tote ID provides the correlation between CPNA and CPC work

---

## 7. Dependency Rules

### 6.1 General

Totes may define dependencies on other totes.

Dependencies are always scoped within the same Notional Tote.

---

### 6.2 CPC Dependency Rule

A CPC tote may depend on CPNA work.

A CPC tote may be released only if:

- Required CPNA work is complete, OR
- The system allows CPC to proceed to a manual station later

---

### 6.3 Dependency Representation

Dependencies should be explicitly modelled:

```
Dependency:
  - notionalToteId
  - requiredToteType
  - requiredSheetNumber
  - dependencyType
```

Example dependency types:

- `MUST_COMPLETE_BEFORE_RELEASE`
- `MUST_COMPLETE_BEFORE_DISPATCH`

---

## 7. Capacity Constraints

A tote shall only be released if downstream capacity exists.

Examples:

- CPNA requires manual station availability
- CPF requires automated line availability
- CPC requires route availability

If capacity is not available, the tote shall remain in OSR.

---

## 8. Starvation Avoidance

The scheduler shall prevent indefinite blocking of lower-priority totes.

Rule:

- If a tote exceeds a defined `MaxWaitTime`, it may be released out of priority order
- Provided its route is available

---

## 9. Ordering Guarantees

The scheduler shall **not** assume that:

- 12N messages arrive in execution order
- Release order matches message arrival order

Instead:

```
ReleaseOrder = ServiceCentrePriority
             + ToteTypePriority
             + DependencyState
             + Capacity
             + StarvationRules
```

---

## 10. Notional Tote Behaviour

The Notional Tote shall act as a coordination object only.

It shall:

- Track all associated physical totes
- Track dependency completion
- Enable grouping and reconciliation

It shall **not**:

- Be scheduled directly
- Move through the system

---

## 11. Example

```
Notional Tote: TOTE66

Totes:
  CPNA / 001
  CPF  / 001
  CPC  / 001

Release sequence:
  1. CPNA / 001
  2. CPF  / 001
  3. CPC  / 001 (after CPNA complete or routable)
```

---

## 12. Key Principles

- The scheduler operates on **physical totes**, not logical groupings
- The Notional Tote provides **correlation and dependency context**
- Release order is **dynamic**, not fixed
- Capacity and dependencies take precedence over arrival order

---

## 13. Summary

This model provides a simplified but realistic abstraction of DSP behaviour:

- Logical grouping via Notional Tote
- Execution via 12N-derived physical totes
- Dynamic release controlled by a scheduler
- Inline and offline manual processing paths

The goal is to simulate **flow behaviour**, not replicate proprietary KNAPP algorithms.

This model provides a simplified but realistic abstraction of DSP behaviour:

- Logical grouping via Notional Tote
- Execution via 12N-derived physical totes
- Dynamic release controlled by a scheduler

The goal is to simulate **flow behaviour**, not replicate proprietary KNAPP algorithms.

