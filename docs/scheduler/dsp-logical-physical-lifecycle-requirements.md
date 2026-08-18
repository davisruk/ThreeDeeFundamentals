# DSP Logical Orders And Physical Tote Lifecycle Requirements

Status: agreed requirements baseline. This document consolidates and supersedes the lifecycle interpretation in:

- `docs/dsp-logical-orders-and-physical-tote-lifecycle.md`
- `docs/dsp-logical-orders-and-physical-tote-lifecycle-updated.md`

The source documents may remain temporarily for comparison, but future plans and implementations must use this document.

## 1. Purpose

This document defines the required separation between logical DSP work and the physical totes, packs, and bags that carry that work.

The central rule is:

> `orderId` and `sheetNumber` identify logical work. `transportContainer` identifies a physical tote. They are related by assignments and must never be treated as the same identity.

FULL_PACK and ASSOCIATED are logical order types. They are not persistent physical tote types. Their inbound physical totes carry packs into DSP and terminate before different outbound physical totes carry completed bags away from P2P.

## 2. Scope

The simulator shall model:

- logical order and sheet identity;
- inbound physical tote identity from 12N `transportContainer`;
- logical-to-physical tote assignments and assignment history;
- preparation and fulfilment processing roles;
- pack, bag, and outbound tote containment;
- physical tote termination and substitution at P2P;
- output overflow through additional sheet allocation;
- outbound tote pharmacy purity, service-centre ownership, and capacity;
- enough provenance to explain how source lines became packs, bags, and outbound tote contents.

The simulator does not yet need to model:

- 32R generation;
- detailed empty-tote return conveyors;
- detailed output-tote reservoir mechanics;
- realistic pack placement inside bags;
- detailed Cencora stacking or truck loading;
- permanent database storage of lifecycle history.

## 3. Authoritative Identities

### 3.1 Logical order identity

`orderId` identifies one logical CPAS order. Values may use misleading names such as `TOTE...`; this naming convention does not make them physical tote identifiers.

### 3.2 Logical sheet identity

The effective logical sheet identity is:

```text
OrderSheetKey = orderId + sheetNumber
```

A logical order contains one or more logical sheets. Sheet numbers may arrive in 12N or be allocated by CPAS/simulator output splitting under the same order ID.

### 3.3 Physical tote identity

`transportContainer` identifies one physical tote/load unit. It is the identifier that should be used by physical tote state, movement, station visits, tote load plans, and renderable tote instances.

### 3.4 Order-line identity

`lineReference` is globally distinct in the available 12N contract and identifies an individual logical order line.

For prepared ADAPTED work:

- `referenceOrderId` identifies the future target ASSOCIATED order;
- `lineReference` identifies the exact prepared/dispatch line correlation;
- `referenceSheetNumber` is retained for protocol fidelity but is always `001` in the Boots implementation and is not an identity discriminator.

The meaningful prepared-line key remains:

```text
target order id + line reference
```

### 3.5 Bag identity

`prescriptionId` identifies the patient prescription whose lines should be placed in the same bag where capacity permits.

`patientId` relates prescriptions and bags to a patient. It supports best-effort placement of a patient's bags into the same outbound tote but does not override hard capacity constraints.

If one prescription exceeds bag capacity, bag identity must include a deterministic ordinal:

```text
BagKey = prescriptionId + bagOrdinal
```

## 4. Required 12N Mapping

The 12N mapper shall retain at least:

- `orderId`;
- header `sheetNumber`;
- `transportContainer` when a physical inbound tote exists;
- order type;
- service centre;
- order priority;
- line reference;
- product ID;
- pharmacy ID;
- patient ID;
- prescription ID;
- line type;
- quantity and picked quantity;
- reference order ID and reference sheet number.

The mapper must not:

- copy `orderId` into a physical tote ID field;
- use `sheetNumber` as a barcode;
- infer a physical tote from a logical order name;
- discard patient or prescription identity before bag planning.

## 5. Logical And Physical Cardinality

### 5.1 Inbound assignment

There is no evidence that one inbound physical tote represents more than one current `orderId/sheetNumber`. Model inbound assignment as:

```text
one inbound physical tote -> one current logical order sheet
```

An ADAPTED tote may nevertheless contain lines destined for multiple future ASSOCIATED orders. Those future destinations are line-level references and do not make the current physical tote represent multiple current order sheets.

### 5.2 Active assignment invariant

At any point in time, one logical sheet has at most one active physical tote assignment:

```text
OrderSheetKey -> 0..1 active PhysicalTote
```

A logical sheet may have several assignments sequentially over its lifecycle, for example an inbound pack tote followed by an outbound bag tote.

### 5.3 Outbound aggregation

One outbound physical tote may contain bags from multiple logical orders and sheets:

```text
Order A / Sheet 001 --+
Order B / Sheet 001 --+--> Outbound Tote X
Order C / Sheet 002 --+
```

Every bag in that tote must belong to the same pharmacy and therefore the same service centre.

### 5.4 Concurrent split invariant

One logical sheet must not be represented by two physical totes concurrently. If one order's output must occupy another tote at the same time, allocate another sheet number under the same order ID.

```text
Order A / Sheet 001 -> Outbound Tote X
Order A / Sheet 002 -> Outbound Tote Y
```

The bags and lines placed in Tote Y become owned by generated Sheet 002. Sheet 001 retains only the work allocated to Tote X. Provenance must retain the original source sheet so future reporting can explain the split.

## 6. Physical Tote Assignment History

The simulator shall retain append-only assignment records sufficient to explain lifecycle transitions.

An assignment must identify:

- logical order ID and sheet number;
- physical `transportContainer`;
- assignment stage;
- activation and termination simulation times;
- whether the assignment is active;
- the reason it ended, where applicable.

Required assignment stages are equivalent to:

```text
INBOUND_PACK
PREPARATION
PRE_P2P
OUTBOUND_BAG
OUTBOUND
```

The exact class and enum names are not prescribed.

## 7. Physical Tote Lifecycle

A physical tote shall have lifecycle state equivalent to:

```text
INBOUND_PACK_TOTE
ACTIVE_PRE_P2P
CONSUMED_AT_ADAPTING
CONSUMED_AT_P2P
OUTBOUND_BAG_TOTE
OUTBOUND
```

The simulator may record a terminal tote and remove its renderable. It does not need to animate empty tote return yet.

Terminating an inbound tote must not delete logical order-line execution state.

## 8. Order-Type Physical Lifecycles

### 8.1 ADAPTED

An ADAPTED 12N has an inbound physical tote. It may contain preparation lines for several future ASSOCIATED orders and pharmacies.

```text
Cencora inbound tote
  -> Third Party when required
  -> Adapting STORE
  -> physical tote consumed/removed
```

Adapting stores terminal prepared-line outcomes independently of the source tote. The source tote does not continue to P2P.

### 8.2 FULL_PACK

FULL_PACK is a logical fulfilment order type with an inbound physical pack tote.

```text
inbound physical pack tote
  -> required preparation/source stations
  -> P2P
  -> inbound tote consumed
  -> bags allocated to an independently supplied outbound tote
```

The inbound tote never becomes the outbound tote.

### 8.3 ASSOCIATED

ASSOCIATED is a logical fulfilment order type with an inbound physical tote. It may collect prepared lines and direct Third Party lines before P2P.

```text
inbound physical tote
  -> Third Party when direct work exists
  -> Adapting COLLECT when prepared dependencies exist
  -> P2P
  -> inbound tote consumed
  -> bags allocated to an independently supplied outbound tote
```

### 8.4 EMPTY

EMPTY is logical work without an inbound physical tote in OSR.

```text
logical authorization
  -> dependency readiness
  -> AV02 allocates a physical empty tote
  -> required fulfilment stations
  -> P2P
  -> AV02/input tote consumed
  -> bags allocated to an independently supplied outbound tote
```

EMPTY consumes no OSR physical tote capacity before AV02.

## 9. P2P Physical Boundary

P2P transforms loose packs into completed bags and is a hard physical tote boundary.

```text
Inbound physical tote [packs]
          |
          v
         P2P
          |
          +-- pack processing
          +-- bag planning and assembly
          +-- bag discharge
          |
          v
Outbound physical tote [bags]
```

P2P completion shall:

1. terminate the inbound physical tote assignment;
2. mark the inbound physical tote consumed;
3. preserve pack and logical line provenance;
4. create or identify completed bag records;
5. assign each bag to the current valid outbound tote for its P2P line;
6. create output sheet allocations when concurrent overflow requires them.

The existing tipper, sorter, PDC, PRL, PCR, and bagger state machines may remain separate. This requirement changes identity and handoff contracts, not their internal ownership boundaries without a concrete need.

## 10. Pack And Bag Provenance

A physical pack must be traceable to:

- source logical order and source sheet;
- current/output logical sheet after any split;
- line reference;
- product ID;
- pharmacy ID;
- patient ID;
- prescription ID;
- current physical container, if any;
- bag key after bagging.

A completed bag must identify:

- bag key;
- pharmacy and service centre;
- patient and prescription;
- contained physical pack IDs;
- logical line outcomes, including lines with no physical pack;
- owning logical order sheets;
- current outbound physical tote;
- whether an NS indication is required.

Physical pack plans and logical fulfilment outcomes must remain separate. A missing product must not be represented by a fake physical pack.

## 11. Bag Planning

Lines sharing a `prescriptionId` should share one bag where possible.

Phase 1 shall use a configurable maximum physical pack count per bag. The policy boundary must allow a later dimensional or volume-based implementation without changing logical bag identity.

Rules:

- preserve source line order deterministically;
- never split one physical pack;
- allocate bag ordinals starting from one;
- create another bag when the configured pack count would be exceeded;
- keep all bags for a prescription associated with the same patient;
- permit a prescription to span output totes when tote capacity is reached.

Patient affinity is best effort. A closed outbound tote is never reopened merely because another bag for the same patient arrives later.

## 12. Outbound Tote Allocation

Each P2P instance has its own logical reservoir of empty outbound totes. Empty totes may appear without modelling reservoir conveyor geometry in the first implementation.

Each P2P instance has exactly one open receiving tote at a time.

An outbound tote shall be assigned:

- a physical tote ID;
- its owning P2P line;
- one service centre;
- one pharmacy;
- configurable maximum bag count;
- current bags and remaining capacity;
- lifecycle and closure state.

The first bag assigns an unassigned receiving tote to its service centre and pharmacy. An all-missing prescription may also assign the current tote so it can later visit Exceptions and receive an empty NS bag.

An outbound tote closes when:

- its configured bag capacity is reached;
- the next bag belongs to another pharmacy;
- its P2P line changes service-centre ownership;
- all applicable work is complete and the run/service centre is flushed;
- a hard runtime cutoff requires finalization.

Once closed, a tote is not reopened. A new empty tote is introduced when more bags remain.

## 13. Output Splitting And Generated Sheets

If all output for an order fits in the tote currently assigned to its existing sheet, physical tote substitution may retain that sheet number.

If later bags for the same order require another concurrent outbound tote:

1. allocate the next available sheet number under that order ID;
2. associate the overflow bags and their logical lines with the generated sheet;
3. retain their original source sheet provenance;
4. create an active assignment from the generated sheet to the new outbound tote;
5. ensure no sheet has two active physical assignments.

Sheet allocation must be deterministic and safe when several orders share an outbound tote.

## 14. Exceptions Relationship

Exceptions operates on outbound physical totes after P2P.

The lifecycle foundation must allow:

- unresolved line outcomes to remain attached to the correct bag;
- NS metadata on a nonempty bag without forcing a station visit solely for a Cencora short pick;
- an all-missing prescription to mark/allocate the current pharmacy tote;
- creation of an empty NS bag at Exceptions;
- one outbound tote to contain exception work for several logical orders;
- the tote to continue outbound after Exceptions regardless of resolution outcome.

Detailed Exception behavior remains defined by `docs/machines/exceptions-station-requirements.md` and is implemented only after this lifecycle foundation exists.

## 15. Rendering And Performance

Logical orders, sheets, assignments, packs, bags, and physical totes are domain data. Loading a full day of data must not create renderables for all records.

Renderable rules:

- create a physical inbound tote renderable only when it leaves OSR or is allocated at AV02;
- create pack renderables only while packs are physically active and potentially visible;
- hide contained pack renderables while tote lids are closed;
- retire/hide inbound tote renderables when totes terminate at Adapting or P2P;
- create outbound tote renderables independently from inbound totes;
- create bag renderables only when needed for active physical presentation;
- retain logical contents after their detailed renderables are hidden or retired.

## 16. 32R Deferral

32R generation is deliberately deferred until the simulator can execute a whole production day reliably.

This implementation must retain enough provenance and assignment history for future 32R work, but it must not infer or implement 32R physical identifier fields without the applicable interface specification.

## 17. Required Invariants

1. Logical order identity and physical tote identity are distinct.
2. Logical sheet identity is `orderId + sheetNumber`.
3. `transportContainer` is the physical inbound tote identity.
4. One inbound physical tote represents one current logical sheet.
5. An ADAPTED tote may contain lines for many future ASSOCIATED orders.
6. One logical sheet has at most one active physical tote assignment.
7. One outbound physical tote may carry many logical sheets.
8. Outbound totes are pharmacy-pure and service-centre-pure.
9. P2P consumes the inbound tote and uses a different outbound tote.
10. EMPTY receives its first physical tote at AV02 and consumes no OSR tote capacity beforehand.
11. Prescription ID drives bag grouping; patient ID drives best-effort tote affinity.
12. Output overflow creates another sheet rather than assigning one sheet to two totes concurrently.
13. Logical line state survives physical tote substitution and splitting.
14. Missing lines are logical outcomes, not fake physical packs.
15. Lifecycle history is sufficient to explain source-to-output containment.
16. 32R generation remains out of scope.

## 18. Completion Criteria For The Lifecycle Programme

- 12N mapping retains logical sheet, physical transport container, patient, prescription, service-centre, and priority data.
- No runtime API uses a logical order ID as an implicit physical tote ID.
- Scheduler order state is keyed by logical sheet identity.
- Station visits and tote load plans use explicit physical tote identity.
- ADAPTED, FULL_PACK, ASSOCIATED, and EMPTY physical lifecycles match this document.
- P2P records inbound tote consumption and independent output tote assignment.
- Bags are grouped by prescription with deterministic capacity splitting.
- Outbound totes enforce P2P-line ownership, pharmacy purity, service-centre purity, and configurable bag capacity.
- Output overflow creates deterministic additional sheets with preserved provenance.
- Assignment history and inspection explain the lifecycle without requiring 32R.
