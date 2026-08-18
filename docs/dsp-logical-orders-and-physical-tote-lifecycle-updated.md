# DSP Logical Orders, Sheets and Physical Tote Lifecycle

## 1. Purpose

This document describes the distinction between **logical order
identity** and **physical tote identity** within the DSP, particularly
for a `FULL_PACK` order passing through P2P and bagging.

The central principle is:

> **Order ID and Sheet Number identify logical work within CPAS;
> Transport Container identifies the physical tote currently carrying
> that work.**

The physical tote entering P2P with packs is not assumed to be the
physical tote leaving bagging with patient bags. TDP-182 explicitly
indicates that the physical tote changes after the bagging machine even
for a one-tote-in/one-tote-out flow.

## 2. Key Identifiers

### 2.1 Order ID

`orderId` identifies the logical CPAS order independently of the
physical tote currently carrying its contents.

### 2.2 Sheet Number

`sheetNumber` identifies a logical subdivision of the order. The
effective logical identity is:

``` text
Order ID + Sheet Number
```

### 2.3 Transport Container

`transportContainer` identifies the **physical tote/load unit**,
normally by its barcode or physical tote identifier.

For example:

``` text
orderId            = NT1001
sheetNumber        = 001
transportContainer = TOTE-A123
```

means that logical order/sheet `NT1001/001` is currently associated with
physical tote `TOTE-A123`.

## 3. Logical and Physical Identity

The simulator shall distinguish:

``` text
LogicalOrderSheet
    orderId
    sheetNumber
    orderType
    orderLines
    destination
    routeRequirements
```

from:

``` text
PhysicalTote
    transportContainer
    contents
    currentLocation
    state
```

The logical order/sheet and physical tote may be associated, but they
are not the same entity.

## 4. FULL_PACK Inbound Lifecycle

A FULL_PACK 12N might contain:

``` text
Order ID            NT1001
Sheet Number        001
Order Type          FULL_PACK
Transport Container TOTE-A123
```

At this stage:

``` text
Logical order/sheet
      NT1001/001
          |
          | currently carried by
          v
Physical tote TOTE-A123
          |
          v
      loose packs
```

## 5. P2P Transformation

P2P transforms the physical form of the order contents:

``` text
Physical input tote
    loose packs
        |
        v
       P2P
        |
        +-- product processing
        +-- product labelling
        +-- bag assembly
        +-- bag labelling
        |
        v
Physical output tote
    completed bags
```

The simulator should model P2P/bagging as a **physical tote boundary**,
rather than merely changing the contents of the original tote.

## 6. Physical Tote Substitution

Before P2P:

``` text
NT1001 / 001
     |
     v
TOTE-A123
     |
     v
loose packs
```

After P2P and bagging, the logical identity can remain `NT1001/001`,
while the physical tote changes:

``` text
NT1001 / 001
     |
     v
TOTE-B987
     |
     v
patient bags
```

This is **physical tote substitution**. The logical Order ID/Sheet
Number remains continuous while its physical carrier changes.

## 7. Sheet Number Is Not Physical Tote Identity

`Order ID + Sheet Number` identifies logical work, while
`Transport Container` identifies the current physical load unit.

The relationship is:

``` text
Logical Order/Sheet
       |
       | currently assigned to
       v
Physical Transport Container
```

That assignment can change sequentially during processing.

## 8. Physical Tote Substitution, Splitting and Overflow

Two different mechanisms must be distinguished: **physical tote
substitution** and **physical tote splitting**.

### 8.1 Physical tote substitution

A logical Order ID/Sheet Number may be associated with different
physical totes at different points in its lifecycle.

``` text
Before P2P:

NT1001 / 001 -> TOTE-A123 [packs]

After P2P:

NT1001 / 001 -> TOTE-B987 [bags]
```

There is still only one physical tote representing `NT1001/001` at that
point in time, so the Sheet Number remains `001`.

### 8.2 Physical tote splitting

Where contents must be distributed across multiple physical totes
concurrently, the additional physical tote does **not** share the same
Order ID/Sheet Number.

Instead, another Sheet Number is allocated under the same Order ID.

``` text
Before bagging:

NT1001 / 001 -> TOTE-A123 [packs]

After bagging with output overflow:

NT1001 / 001 -> TOTE-B987 [bags]
NT1001 / 002 -> TOTE-C654 [bags]
```

The relationship becomes:

``` text
                   NT1001
                      |
              +-------+-------+
              |               |
             001             002
              |               |
              v               v
         TOTE-B987       TOTE-C654
          [bags]           [bags]
```

TDP-182 describes Sheet Numbers as the mechanism that allows a single
CPAS Order ID to be spread across multiple physical totes. It also
describes splitting both before DSP processing and after the bagging
machine.

### 8.3 Cardinality rule

At any point in time:

``` text
Order ID + Sheet Number
          |
          | 0..1 active physical assignment
          v
Transport Container
```

A logical sheet may have **multiple physical tote assignments over
time**, but it shall not be concurrently represented by multiple
physical totes.

This is valid because the assignments are sequential:

``` text
NT1001 / 001
      |
      +--> TOTE-A123 [inbound packs]
      |
     P2P
      |
      +--> TOTE-B987 [outbound bags]
```

This is not the intended model:

``` text
NT1001 / 001
      |
      +--> TOTE-B987
      |
      +--> TOTE-C654
```

If both output totes are required concurrently:

``` text
NT1001 / 001 -> TOTE-B987
NT1001 / 002 -> TOTE-C654
```

### 8.4 Simulator invariant

> **A single Order ID/Sheet Number shall not be concurrently assigned to
> multiple physical totes. If logical work is split across multiple
> physical totes, each physical tote shall be represented by a distinct
> Sheet Number under the same Order ID. Physical tote substitution may
> retain the same Sheet Number because the assignments occur
> sequentially.**

## 9. P2P as a Lifecycle Boundary

Before P2P the physical tote principally transports packs. After P2P the
output physical tote transports completed patient bags.

``` text
Cencora / downstairs
        |
        v
Inbound physical tote
     [packs]
        |
        v
       DSP
        |
        v
       P2P
        |
        | physical tote boundary
        v
Outbound physical tote
      [bags]
        |
        v
Strapping / outbound
        |
        v
Cencora / delivery flow
```

## 10. Relationship to Preparation and Fulfilment

The distinction between **preparation** and **fulfilment** remains
useful, but it describes processing role rather than persistent physical
tote identity.

There are at least two independent dimensions:

``` text
Processing role:
    Preparation
    Fulfilment

Physical transport phase:
    Pack transport
    Bag transport
```

A FULL_PACK order is a fulfilment flow but still crosses from pack
transport to bag transport at P2P.

## 11. Suggested Simulator Domain Model

A useful conceptual separation is:

``` text
LogicalOrder
    orderId
    orderType

LogicalOrderSheet
    orderId
    sheetNumber
    orderLines
    routeRequirements

PhysicalTote
    transportContainer
    contents
    currentLocation
    lifecyclePhase

PhysicalToteAssignment
    orderId
    sheetNumber
    transportContainer
    assignmentStage
    active
```

The cardinality should be understood as:

``` text
LogicalOrder
     |
     | 1..n
     v
LogicalOrderSheet
     |
     | zero or one ACTIVE assignment
     v
PhysicalTote
```

A `LogicalOrderSheet` may have multiple `PhysicalToteAssignment` records
over its lifecycle so that tote substitution can be represented
historically. Only one such assignment may be active at a time.

For example:

``` text
NT1001 / 001
    |
    +-- TOTE-A123  INBOUND_PACK   [historical]
    |
    +-- TOTE-B987  OUTBOUND_BAG   [active]
```

If another physical tote is required **at the same time**, a new Sheet
Number is required:

``` text
NT1001
    |
    +-- 001 -> TOTE-B987
    |
    +-- 002 -> TOTE-C654
```

## 12. Suggested Physical Tote Lifecycle

A physical tote may have lifecycle states equivalent to:

``` text
INBOUND_PACK_TOTE
PRE_P2P
CONSUMED_AT_P2P
OUTBOUND_BAG_TOTE
OUTBOUND
```

For example:

``` text
TOTE-A123
    INBOUND_PACK_TOTE
          |
          v
       PRE_P2P
          |
          v
   CONSUMED_AT_P2P
```

while a different tote follows:

``` text
TOTE-B987
    OUTBOUND_BAG_TOTE
          |
          v
       OUTBOUND
```

`TOTE-A123` does not become `TOTE-B987`; they are different physical
entities associated with the same logical work at different stages.

## 13. Physical Contents

Before P2P:

``` text
PhysicalTote TOTE-A123
    contents:
        Pack A
        Pack B
        Pack C
```

After P2P:

``` text
PhysicalTote TOTE-B987
    contents:
        Bag 1
            Pack A
            Pack B

        Bag 2
            Pack C
```

Order lines remain associated with the logical order while their
physical containment changes.

## 14. Relationship to 32R

The 32R represents the outcome of CPAS processing.

The simulator should preserve sufficient state across physical tote
substitution and splitting to construct the appropriate
completion/status information for the original logical order and its
lines.

``` text
12N
 |
 +-- Order ID
 +-- Sheet Number
 +-- inbound Transport Container
 |
 v
CPAS / simulator execution
 |
 +-- pack processing
 +-- physical tote substitution
 +-- optional output splitting
 +-- bag creation
 +-- output tote allocation
 +-- order-line status
 |
 v
32R
```

The precise physical identifier fields emitted in 32R should follow the
32R interface specification rather than being inferred from this
lifecycle model.

## 15. Simulator Requirements

The simulator shall enforce the following:

1.  `orderId` represents logical order identity.
2.  `sheetNumber` represents a logical subdivision of an Order ID and
    distinguishes physical tote allocations where an order is split
    across multiple physical totes.
3.  `transportContainer` represents a physical tote/load-unit
    identifier.
4.  Logical order/sheet identity is independent of physical tote
    identity.
5.  A 12N may associate a logical Order ID/Sheet Number with an inbound
    physical tote.
6.  At any point in time, a single Order ID/Sheet Number shall have at
    most one active physical tote assignment.
7.  A logical sheet may be associated with different physical totes
    sequentially over its lifecycle.
8.  The physical tote carrying packs into P2P shall not be assumed to
    carry bags out of P2P.
9.  P2P/bagging shall be modelled as a physical tote lifecycle boundary.
10. Where P2P replaces an inbound physical tote with a single outbound
    physical tote, the same Order ID/Sheet Number may be retained.
11. Physical tote substitution shall not be modelled as two simultaneous
    physical totes for the same Order ID/Sheet Number.
12. Where output splitting requires multiple physical totes
    concurrently, each physical tote shall be allocated a distinct Sheet
    Number under the same Order ID.
13. Logical order-line state shall survive physical tote substitution
    and splitting.
14. Physical containment shall change as products move from loose packs
    into bags.
15. Sheet information shall not be treated as a physical tote barcode.
16. Physical tote identifiers shall not be used as permanent logical
    order identity.
17. The simulator shall retain assignment history sufficient to explain
    how inbound physical pack totes resulted in outbound physical bag
    totes.
18. 32R generation shall use logical/order-line execution state and the
    physical identifiers defined by the applicable interface
    specification.

## 16. Example End-to-End FULL_PACK Flow

### 16.1 12N received

``` text
Order ID:             NT1001
Sheet Number:         001
Order Type:           FULL_PACK
Transport Container: TOTE-A123
```

### 16.2 Initial state

``` text
LogicalOrderSheet
    NT1001 / 001
         |
         v
PhysicalTote
    TOTE-A123
         |
         +-- Pack A
         +-- Pack B
         +-- Pack C
```

### 16.3 P2P processing

``` text
TOTE-A123
   |
   +-- Pack A -- label --+
   +-- Pack B -- label --+--> Bag 1
   +-- Pack C -- label ------> Bag 2
   |
   v
input tote processing complete
```

### 16.4 Output tote substitution

``` text
LogicalOrderSheet
    NT1001 / 001
         |
         v
PhysicalTote
    TOTE-B987
         |
         +-- Bag 1
         |    +-- Pack A
         |    +-- Pack B
         |
         +-- Bag 2
              +-- Pack C
```

### 16.5 Outbound

``` text
TOTE-B987
    |
    v
Strapping / completion
    |
    v
Outbound from DSP
    |
    v
Cencora / delivery process
```

The logical identity `NT1001/001` remains continuous even though
physical tote `TOTE-A123` has been replaced by `TOTE-B987`.

### 16.6 Output overflow variant

If the bagging output cannot fit into one physical tote, the additional
tote receives another Sheet Number under the same Order ID:

``` text
LogicalOrder
    NT1001
      |
      +-- Sheet 001 -> TOTE-B987
      |                  +-- Bag 1
      |                  +-- Bag 2
      |
      +-- Sheet 002 -> TOTE-C654
                         +-- Bag 3
                         +-- Bag 4
```

This is **splitting**, rather than substitution. `NT1001/001` is not
concurrently assigned to both output totes.

## 17. Source-Derived and Modelled Interpretation

The documentation supports the key behaviours that CPAS distinguishes
logical order/sheet information from physical transport-container
identification, that physical tote splitting can occur, and that
splitting can occur after the bagging machine. TDP-182 also explicitly
states that the physical tote changes after bagging even where there is
one tote entering and one tote leaving.

The following are simulator/domain-model recommendations derived from
those behaviours rather than prescribed CPAS implementation structures:

-   separate `LogicalOrderSheet` and `PhysicalTote` entities;
-   explicit temporal physical-tote assignment/history;
-   at most one active physical tote assignment for a given Order
    ID/Sheet Number;
-   allocation of another Sheet Number when an Order ID is concurrently
    split across multiple physical totes;
-   explicit pack-transport and bag-transport lifecycle phases;
-   treating P2P as a physical tote lifecycle boundary.

The model therefore distinguishes **substitution** from **splitting**:

-   **substitution** changes the physical `transportContainer` while
    retaining the logical Order ID/Sheet Number;
-   **splitting** creates additional Sheet Numbers under the same Order
    ID so that each concurrent physical tote has its own logical sheet
    identity.

These modelling choices may be refined while preserving the fundamental
separation between logical order identity and physical
transport-container identity.
