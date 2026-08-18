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

`sheetNumber` identifies a logical subdivision of the order.

The effective logical identity is:

``` text
Order ID + Sheet Number
```

For example, `NT1001 / 001` identifies a logical order/sheet.

### 2.3 Transport Container

`transportContainer` identifies the **physical tote/load unit**,
normally by its barcode or physical tote identifier.

For example:

``` text
orderId           = NT1001
sheetNumber       = 001
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

`TOTE-A123` is the physical tote arriving from the Cencora/downstairs
warehouse. The logical identity is `NT1001/001`.

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

Therefore:

``` text
                  LOGICAL IDENTITY
                    NT1001 / 001
                         |
            +------------+------------+
            |                         |
       before P2P                  after P2P
            |                         |
            v                         v
       TOTE-A123                  TOTE-B987
       loose packs                patient bags
            |                         ^
            +---------- P2P ----------+
```

The logical work survives replacement of the physical transport
container.

## 7. Sheet Number Is Not Physical Tote Identity

It is tempting to model:

``` text
Order ID + Sheet Number = Physical Tote
```

because an order/sheet is often associated with one physical tote at a
particular point.

That association is not permanent.

Instead:

``` text
Order ID + Sheet Number
```

identifies logical work, while:

``` text
Transport Container
```

identifies the current physical load unit.

The relationship is better represented as:

``` text
Logical Order/Sheet
       |
       | assigned to
       v
Physical Transport Container
```

and that assignment can change during processing.

## 8. Sheet Splitting and Overflow

TDP-182 allows physical tote splitting, including splitting after the
bagging machine.

An order may therefore evolve from:

``` text
NT1001 / 001
      |
      v
TOTE-A123
```

to multiple outbound allocations:

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

The simulator must not assume either one Order ID equals one physical
tote or that a physical tote remains constant throughout an order's
lifetime.

## 9. P2P as a Lifecycle Boundary

For simulation purposes, P2P/bagging should be treated as a meaningful
physical lifecycle boundary.

Before it, the tote principally transports packs. After it, the output
tote transports completed patient bags.

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

This is more accurate than treating a FULL_PACK tote as one physical
object whose contents simply change from packs to bags.

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

The simulator should therefore avoid making the term "fulfilment tote"
synonymous with one persistent physical tote object.

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
```

The exact implementation is not prescribed.

The important requirement is that a logical order/sheet must be able to
become associated with a different physical tote without losing its
logical identity or order-line state.

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

This lets the simulator answer independently:

-   Which logical order does a pack belong to?
-   Which physical tote currently contains it?
-   Has it been bagged?
-   Which bag contains it?
-   Which outbound physical tote contains that bag?

## 14. Relationship to 32R

The 32R represents the outcome of CPAS processing.

The simulator should preserve sufficient state across physical tote
substitution to construct the appropriate completion/status information
for the original logical order and its lines.

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
2.  `sheetNumber` represents the logical sheet/subdivision within an
    order.
3.  `transportContainer` represents a physical tote/load-unit
    identifier.
4.  Logical order/sheet identity is independent of physical tote
    identity.
5.  A 12N may associate a logical order/sheet with an inbound physical
    tote.
6.  The physical tote carrying packs into P2P shall not be assumed to
    carry bags out of P2P.
7.  P2P/bagging shall be modelled as a physical tote lifecycle boundary.
8.  The inbound physical tote can terminate its association with the
    logical order at P2P.
9.  A new physical output tote can become associated with the same
    logical order/sheet.
10. Logical order-line state shall survive physical tote substitution.
11. Physical containment shall change as products move from loose packs
    into bags.
12. The simulator shall support more than one physical output tote where
    output capacity causes splitting.
13. Sheet information shall not be treated as a physical tote barcode.
14. Physical tote identifiers shall not be used as permanent logical
    order identity.
15. The simulator shall retain enough logical and physical history to
    explain how an inbound pack tote resulted in one or more outbound
    bag totes.
16. 32R generation shall use logical/order-line execution state and the
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

### 16.4 Output tote allocation

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
-   explicit physical-tote assignment/history;
-   explicit pack-transport and bag-transport lifecycle phases;
-   treating P2P as a physical tote lifecycle boundary.

These modelling choices may be refined while preserving the fundamental
separation between logical order identity and physical
transport-container identity.
