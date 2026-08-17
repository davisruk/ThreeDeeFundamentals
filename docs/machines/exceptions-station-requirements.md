# Exceptions Station Requirements

## 1. Purpose

This document defines the behavioural requirements for the **Exceptions
Station** within the DSP simulator.

The objective is to reproduce the operationally significant exception
flow without modelling detailed operator actions or every possible CPAS
fault condition.

The fundamental principle is:

> **Exceptions must not stop the DSP flow.**

An exception may remain unresolved, but the affected order, bag and tote
shall be appropriately marked and the tote shall continue downstream
after exception processing. A tote shall never be routed backwards to an
earlier DSP station as part of exception resolution.

## 2. Scope

The simulator shall model:

-   detection or assignment of exceptions against order lines;
-   propagation of exception state to the associated order and tote;
-   diversion of affected totes to the Exceptions Station after P2P
    where required;
-   configurable exception processing time;
-   resolved and unresolved exception outcomes;
-   bag-level `NS` (Not Supplied) indication for unresolved supply;
-   special treatment of Cencora/AHDL short picks;
-   creation of an empty `NS` bag where no products are available for a
    bag;
-   release of the tote back to normal outbound flow after exception
    processing.

The simulator does **not** need to model detailed operator actions,
supervisor interactions, every real CPAS exception code, detailed
diagnosis/repair procedures, or return of a tote to an earlier DSP
station.

## 3. Core Concepts

### 3.1 Product/order-line level

An exception originates at the **order-line/product level**. At its
simplest, the system considers that a product expected for a bag is
missing or otherwise requires exception handling.

Reasons may include a DSP short pick, damaged product,
unreadable/missing barcode, automation failure, or another simulated
operational failure. The simulator may use a simplified configurable
reason set.

### 3.2 Propagated exception state

Where an order line enters exception, the simulator shall also make the
exception visible at the associated **order** and **tote**.

The line remains the authoritative unit describing the actual problem.

### 3.3 Exception does not imply process failure

An exception does not mean that the order or tote cannot complete DSP
processing. After the Exceptions Station has processed the tote, it
shall continue even where one or more exception lines remain unresolved.

## 4. High-Level Lifecycle

``` text
Normal DSP processing
        |
        | exception identified
        v
Order line marked IN_EXCEPTION
        |
        +--> order marked as containing exception
        +--> tote marked as containing exception
        |
        v
Normal processing continues
        |
        v
P2P completes
        |
        v
Tote diverted to Exceptions Station
        |
        v
Exception processing delay
        |
        +---- resolved line(s)
        |
        +---- unresolved line(s) --> NS bag indication
        |
        v
Exception processing complete
        |
        v
Tote strapped / completed
        |
        v
Normal outbound flow to Cencora
        |
        v
Delivery process to target pharmacy
```

The tote shall not be returned to P2P, Adapting, Third Party, Manual or
any other earlier station following exception processing.

## 5. Exception Generation

### 5.1 General simulation mechanism

The simulator shall support a configurable exception rate. The simulator
may randomly select eligible orders/order lines according to that rate.

### 5.2 Exception reasons

A selected exception may optionally be assigned a reason from a
configurable set, for example:

``` yaml
exceptionReasons:
  - DSP_SHORT_PICK
  - DAMAGED_PRODUCT
  - BARCODE_FAILURE
  - AUTOMATION_FAILURE
  - OTHER
```

Reason assignment exists primarily to make simulation output realistic
and useful for analysis.

### 5.3 Cencora/AHDL short picks

A Cencora/AHDL short pick represents a product that was not supplied to
the DSP and therefore cannot be recovered by an exception operator.

A Cencora short pick shall always be considered **unresolvable within
the DSP**.

Its presence shall **not automatically cause the tote to visit the
Exceptions Station**. The bag-level rules in Section 7 determine whether
a visit is required.

## 6. Diversion to the Exceptions Station

### 6.1 Normal exception diversion

Where a tote contains one or more exception lines requiring workstation
handling, it shall continue through its normal DSP route, complete P2P
processing, and then be diverted to the Exceptions Station.

The simulator shall not hold the tote upstream waiting for the exception
to be resolved.

### 6.2 Information available at the station

The station shall be able to identify the tote, affected orders, bags
and order lines, exception reason where modelled, whether each exception
is potentially resolvable, and whether an `NS` outcome is already known.

### 6.3 No backwards routing

Once a tote reaches Exceptions, resolution shall occur there. The
simulator shall **never** resolve an exception by routing the tote back
to P2P, Adapting/Sortable, Third Party, Manual or another earlier
station.

## 7. Not-Supplied (NS) Handling

### 7.1 Purpose

An unresolved missing product must be communicated to the target
pharmacy. The physical indication is an `NS` marking/label associated
with the affected bag.

### 7.2 Cencora short pick where other bag contents exist

Where one or more products for a bag were short picked by Cencora/AHDL
**and at least one other product for that bag is available**, the tote
shall **not visit Exceptions solely because of the Cencora short pick**.

``` text
Cencora short pick
        |
        v
Other products available for bag?
        |
       YES
        |
        v
Normal bag created at P2P
        |
        v
Bag label indicates NS
        |
        v
No Exceptions Station visit required
        |
        v
Normal outbound flow
```

### 7.3 Bag with no supplied products

Where an order/bag has outstanding products but **no products are
available to create the bag**, the tote shall visit the Exceptions
Station.

This includes an order consisting entirely of Cencora short-picked
products or other failures resulting in no product being available for
the bag.

At Exceptions:

1.  an empty bag shall be created;
2.  the appropriate `NS` label/indication shall be applied;
3.  the empty marked bag shall be placed into the tote;
4.  relevant lines shall remain recorded as unresolved/not supplied;
5.  the tote shall continue downstream.

### 7.4 DSP exception remaining unresolved

Where a potentially resolvable DSP exception remains unresolved, the
affected line shall be recorded as unresolved/not supplied, the affected
bag shall receive the appropriate `NS` indication, the bag shall be
returned to the tote, and the tote shall continue.

## 8. Exception Resolution

### 8.1 Processing delay

Operator intervention shall be represented as a configurable processing
delay. A fixed, ranged or distribution-based duration may be used.

### 8.2 Resolution probability

Potentially resolvable exception lines shall support a configurable
resolution probability.

### 8.3 Resolved outcome

For a resolved exception:

-   the line shall be marked resolved;
-   the required product shall be considered present/corrected as
    appropriate for the simulation;
-   the bag shall be complete unless another unresolved line exists;
-   no `NS` indication is required solely for the resolved line.

### 8.4 Unresolved outcome

For an unresolved exception:

-   the line shall remain recorded as unresolved/not supplied;
-   the associated bag shall carry an `NS` indication;
-   processing shall nevertheless complete;
-   the tote shall be released to outbound flow.

### 8.5 Permanently unresolvable exceptions

At minimum, `CENCORA_SHORT_PICK` shall have a resolution probability of
zero because the missing stock was never supplied to DSP.

## 9. Bag-Level Behaviour

Exception handling shall recognise that multiple order lines may belong
to the same bag.

A bag may therefore contain no exception lines, one or more resolved
exceptions, a mixture of resolved and unresolved lines, one or more
Cencora short picks, or no physical products at all.

A bag shall be marked `NS` if **at least one required line remains
unresolved/not supplied** when its final DSP state is determined.

If `NS` is required and no supplied products exist for the bag, an empty
physical bag shall be created at Exceptions and included in the tote.

## 10. Tote-Level Behaviour

A tote may contain multiple bags and multiple orders. The simulator
shall distinguish line-level, order-level and tote-level exception
state.

The tote-level exception flag is primarily a routing/operational
indicator and does not mean every bag has an exception.

A tote shall require an Exceptions Station visit if at least one
contained bag requires physical exception-station intervention.

A Cencora short pick associated with a bag that otherwise contains
supplied products shall not, by itself, require such a visit.

All exception work for a tote should be handled during the same
Exceptions Station visit.

## 11. Scheduler and Dependency Behaviour

### 11.1 Exceptions are non-blocking upstream

An exception shall not create a dependency requiring another preparation
flow to complete before the affected tote can progress through P2P.

### 11.2 Station capacity

The Exceptions Station shall have configurable capacity, including
maximum concurrent totes, a waiting queue, and configurable processing
duration.

A tote waiting for station capacity may physically queue, but this is a
**station-capacity constraint**, not a product dependency.

### 11.3 No deadlock dependency

The scheduler shall not wait for an exception line to become resolved
before releasing related work elsewhere in DSP.

Exception processing must not introduce circular dependencies with P2P,
Adapting, Manual, Third Party or other fulfilment totes.

### 11.4 Completion guarantee

Every tote admitted to the Exceptions Station shall eventually leave
after configured processing completes, regardless of whether all
exception lines were resolved.

## 12. Suggested State Model

The exact implementation is not prescribed, but semantics equivalent to
the following should be supported:

``` text
NORMAL
   |
   | exception detected
   v
IN_EXCEPTION
   |
   +--------------------+
   |                    |
   v                    v
RESOLVED          UNRESOLVED_NS
```

For Cencora short picks:

``` text
CENCORA_SHORT_PICK
        |
        v
UNRESOLVED_NS
```

without an attempted resolution.

A separate tote routing flag may be useful so that a tote can contain
known `NS` lines without necessarily requiring an Exceptions Station
visit.

## 13. Example Scenarios

### 13.1 DSP short pick successfully resolved

``` text
Product missing during DSP processing
        |
        v
Line/order/tote marked exception
        |
        v
P2P completes
        |
        v
Exceptions Station
        |
        v
Product issue resolved
        |
        v
Line marked RESOLVED
        |
        v
Tote continues outbound
```

### 13.2 DSP short pick not resolved

``` text
DSP short pick
        |
        v
Exceptions Station
        |
        v
Resolution attempt fails
        |
        v
Line marked UNRESOLVED / NS
        |
        v
NS indication applied to bag
        |
        v
Bag returned to tote
        |
        v
Tote continues outbound
```

### 13.3 Cencora short pick with other supplied products

``` text
Bag:
  Product A - supplied
  Product B - Cencora short pick
  Product C - supplied
        |
        v
Products A + C processed normally
        |
        v
Bag created at P2P with NS indication
        |
        v
NO Exceptions Station visit
        |
        v
Tote continues outbound
```

### 13.4 Cencora short pick with no supplied products

``` text
Bag:
  Product A - Cencora short pick
  Product B - Cencora short pick
        |
        v
No products available
        |
        v
Tote routed to Exceptions
        |
        v
Empty bag created + NS indication
        |
        v
Empty bag placed in tote
        |
        v
Tote continues outbound
```

### 13.5 Multiple bags, only one affected

``` text
Tote
 |
 +-- Bag A -> complete
 +-- Bag B -> DSP exception
 +-- Bag C -> complete
        |
        v
P2P completes
        |
        v
Tote -> Exceptions
        |
        v
Only Bag B requires exception work
        |
        v
Tote continues outbound
```

## 14. Suggested Simulation Configuration

``` yaml
exceptions:
  enabled: true

  generation:
    orderExceptionRate: 0.02
    reasons:
      - DSP_SHORT_PICK
      - DAMAGED_PRODUCT
      - BARCODE_FAILURE
      - AUTOMATION_FAILURE
      - OTHER

  station:
    capacity: 2
    processingTimeSeconds: 120
    resolutionRate: 0.90

  permanentlyUnresolvable:
    - CENCORA_SHORT_PICK
```

These names and structures are illustrative rather than prescribed.

## 15. Core Simulator Rules

1.  Exceptions originate against individual order lines/products.
2.  Exception state shall be visible at line, order and tote level.
3.  A tote containing an operational exception shall continue through
    normal processing until its post-P2P diversion.
4.  Exception resolution shall occur at the Exceptions Station.
5.  A tote shall never be routed backwards through DSP to resolve an
    exception.
6.  Exception processing shall be represented primarily as a
    configurable delay and outcome.
7.  Potentially resolvable exceptions shall support resolved and
    unresolved outcomes.
8.  Cencora/AHDL short picks shall always be unresolvable within DSP.
9.  A Cencora short pick shall not cause an Exceptions Station visit
    where the affected bag contains other supplied products.
10. Such a partially supplied bag shall carry an `NS` indication and
    continue normally.
11. Where no supplied products exist for an affected bag, the tote shall
    visit Exceptions and an empty `NS` bag shall be created.
12. Any unresolved line remaining after exception processing shall
    result in an `NS` indication for its bag.
13. An unresolved exception shall not prevent the tote from completing
    DSP processing.
14. Multiple exceptions within a tote should be handled in a single
    Exceptions Station visit.
15. Exceptions shall not create scheduler dependencies on earlier DSP
    stations.
16. Exceptions Station capacity may delay a tote but shall not create an
    indefinite hold.
17. Every tote completing exception processing shall return to normal
    outbound flow.

## 16. Source-Derived and Simulator-Specific Behaviour

TDP-182 supports the general physical Error Control behaviour, including
diversion of problematic products/totes to Error Control for correction
and examples such as missing barcodes, damaged products and unsuitable
Third Party products.

The detailed simulator behaviour in this document also incorporates the
operational clarifications established for this simulator, particularly:

-   the tote continues through normal processing despite exception
    state;
-   exception resolution is always performed at the Exceptions Station;
-   totes never return to an earlier station;
-   unresolved lines are communicated using `NS`;
-   Cencora/AHDL short picks are not resolvable within DSP;
-   a Cencora short pick does not require an Exceptions visit where
    other products exist for the bag;
-   an empty `NS` bag is created at Exceptions where the affected bag
    has no supplied products;
-   exceptions do not create blocking dependencies for related DSP work.

The following are simulator implementation choices rather than claims
about CPAS internals:

-   percentage-based random exception generation;
-   random assignment of simplified exception reasons;
-   percentage-based resolution outcomes;
-   configurable station processing duration;
-   the exact internal state enumeration;
-   the exact station queue implementation.

These choices may evolve without changing the required operational
behaviour described above.
