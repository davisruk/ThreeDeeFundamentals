# Third Party Area Requirements

Status: agreed requirements baseline for `feature/third-party-station-phase-1`.

## 1. Purpose

The Third Party Area supplies products held in manually replenished shelving beside a through-track. A tote stops in the area and an operative picks the required packs from the product's configured bin location.

The simulation must reproduce the significant routing, dependency, tote-content, capacity, and lifecycle behaviour without modelling operatives, supplier replenishment, detailed shelving, or label printing.

The area has two roles:

1. Direct fulfilment: add Third Party packs directly to a `FULL_PACK`, `ASSOCIATED`, or `EMPTY` fulfilment tote.
2. Preparation supply: add Third Party packs to an `ADAPTED` preparation tote before it continues to the Adapting Area.

These roles must remain distinct at line level.

## 2. Authoritative Data Sources

### 2.1 Product master data

Third Party classification is not present in 12N. It comes from the DSP product-master export:

`app/md/product_automation.csv`

The join key is:

```text
12N productId
    = product_automation.csv dispensingProductPackColumbusCode
```

A product is Third Party when `thirdPartyLocation` is nonblank. The domain must retain the location, not only a derived boolean, because it identifies the physical bin and will support inspection and later visualisation.

The current export contains approximately 5,500 products and is small enough to load once into an in-memory repository. A relational database is not required.

Product master loading and 12N loading must be separate concerns. The product export is CSV; 12N messages remain JSON.

Useful product-master fields for the simulation are initially:

- `dispensingProductPackColumbusCode` as product id
- `name` for inspection/debug display
- `thirdPartyLocation`, optional
- optional `length`, `width`, and `height`, converted from millimetres to metres when present

Some active master records contain `0 x 0 x 0` because physical dimensions have not been supplied. These products must remain available for identity and routing lookup, but their dimensions are treated as missing rather than as valid geometry. Physical pack creation must fail or report the missing dimensions only when such a product actually enters a simulated run. Partially missing or otherwise nonpositive dimension triples are invalid master data.

Other CSV fields should be parsed only if needed. Do not place source-specific CSV column names throughout the domain model.

### 2.2 12N order data

12N owns order-specific processing intent:

- `orderLineType = 05` means `FULL_PACK` processing for that line.
- `orderLineType = 02` means `ADAPTED` processing for that line.
- `orderLineType = 01` means `MANUAL`; manual work is excluded from the active simulator scope.

Order-specific factors such as a label being too long for P2P can cause an otherwise automatable product to appear on an ADAPTED line. The simulator must therefore not infer the line's processing route from a fixed product category.

Product master determines where the physical product is sourced. The 12N line type determines how that line is processed for the current order.

## 3. Terminology

Use current simulator order names:

- `FULL_PACK`: fulfilment order primarily supplied by automated warehouse packs.
- `ASSOCIATED`: pharmacy-pure fulfilment/consolidation order that may combine direct, Third Party, and prepared lines.
- `ADAPTED`: mixed preparation order carrying work through Third Party and/or Adapting before later collection.
- `EMPTY`: fulfilment order that obtains a physical empty tote at AV02 and then behaves like an associated fulfilment flow.

Do not use `FULL` as an alias in code or requirements.

## 4. Pharmacy Purity

- `FULL_PACK`, `ASSOCIATED`, and `EMPTY` fulfilment orders are pharmacy-pure.
- `ADAPTED` preparation orders may contain lines for multiple pharmacies.
- Third Party processing must not change these rules.

## 5. Line Correlation

The 12N order-line reference is globally distinct and is the exact correlation between an ADAPTED preparation line and the corresponding line in the target ASSOCIATED order.

For an ADAPTED line:

- `lineReference` identifies the exact target line.
- `referenceOrderId` identifies/groups the destination ASSOCIATED order.
- `referenceSheetNumber` is retained for protocol fidelity but is always `001` and must not be used as a meaningful discriminator.

The meaningful prepared-line identity is:

```text
target order id + line reference
```

The 12N header `sheetNumber` remains the real sheet number for sequencing the message/order. It is independent from the line's `referenceSheetNumber`.

## 6. Manual-Line Exclusion

Manual processing is being removed from the production DSP and is not worth simulating from historical datasets.

The loader must:

- ignore `MANUAL_PREPARATION` messages;
- remove `MANUAL` lines from mixed dispatch messages;
- omit a dispatch order when no simulated lines remain;
- report ignored manual messages, lines, and resulting empty orders;
- derive no manual station or manual-merge requirement.

Sheet sequencing must operate over retained simulated sheets so an omitted manual-only sheet cannot block a later retained sheet indefinitely.

## 7. Third Party Work Selection

Third Party work is selected from order type, line type, picked quantity, and product master data.

| Current flow | Qualifying line | Third Party behaviour |
|---|---|---|
| `ADAPTED` preparation order | Product has a Third Party location and has outstanding quantity | Pick into the preparation tote, then continue to Adapting |
| `FULL_PACK`, `ASSOCIATED`, or `EMPTY` | Line type is `FULL_PACK`, product has a Third Party location, and has outstanding quantity | Pick directly into the fulfilment tote |
| `ASSOCIATED` or `EMPTY` | Line type is `ADAPTED`, even when its product has a Third Party location | Do not repick at Third Party; collect its preparation outcome through Adapting |
| Any order | Line type is `MANUAL` | Ignore under the active simulation policy |

Outstanding quantity is derived from the ordered pack quantity and `numberOfPacksPicked`. Completed Third Party work must be recorded so the same line is not picked twice or cause a revisit.

An ASSOCIATED order may contain both:

- direct Third Party `FULL_PACK` lines; and
- `ADAPTED` lines that depend on preparation/storage.

It therefore may visit both the Third Party Area and the Adapting Area before P2P.

## 8. Route Order

### 8.1 ADAPTED preparation flow

```text
OSR
  -> Third Party Area when qualifying source lines exist
  -> Adapting Area
  -> logical temporary storage
```

The ADAPTED tote is a transport/preparation carrier. Third Party picking alone does not make the target ASSOCIATED line ready. Adapting processing must still produce the terminal preparation outcome.

### 8.2 Fulfilment flow

```text
OSR
  -> Third Party Area when direct Third Party lines exist
  -> Adapting collection when prepared lines exist
  -> P2P
  -> Dispatch
```

The exact track path is selected by route state, but completed Third Party work must not cause a later revisit.

### 8.3 EMPTY flow

AV02 is the physical empty-tote introduction point:

```text
EMPTY logical order
  -> allocate physical tote at AV02
  -> Third Party Area when direct Third Party lines exist
  -> remaining fulfilment route
```

The Third Party Area does not create empty totes.

## 9. Conservative Scheduler Release

The existing conservative dependency policy remains mandatory.

An ASSOCIATED or EMPTY tote with prepared-line dependencies remains in OSR/at its start boundary until every required prepared line has reached a terminal preparation outcome. It must not be released early merely to perform its direct Third Party picks because there is insufficient physical queue space at or downstream of the Third Party Area.

Release also requires Third Party Area admission when the candidate has outstanding Third Party work.

Scheduler release admission and area processing admission remain separate:

- release admission means configured waiting/processing capacity exists for the candidate;
- processing admission means the live area can start or queue that specific visit.

## 10. Area Capacity And State

The real area can hold multiple stopped totes along the shelving track. Phase 1 may model this as one logical area with configurable concurrent capacity rather than individual pick benches.

At minimum, the area must support:

- configurable input waiting capacity;
- configurable maximum concurrent processing visits;
- configurable deterministic processing duration or duration strategy;
- immutable scheduler/debug snapshots;
- deterministic FIFO progression for waiting visits;
- blocking while a tote's Third Party work is outstanding;
- completion records that can be applied once by simulation-thread code.

The area must not own scheduler decisions and the scheduler worker must not mutate the live area.

## 11. Successful Pick Processing

Phase 1 uses successful picks only.

For each qualifying outstanding line, processing must:

1. identify its product through the in-memory product repository;
2. retain the Third Party bin location in visit/completion/debug state;
3. create the required logical pack plan using product-master dimensions;
4. add the pack plan to the current tote's mutable `ToteLoadPlan`;
5. mark the line's Third Party work complete;
6. release the tote when all work for the visit is complete.

For direct fulfilment, the updated load plan must be visible to downstream P2P.

For ADAPTED preparation, the updated source tote continues to Adapting. The later Adapting STORE operation, not Third Party completion, publishes the preparation outcome for the target ASSOCIATED line.

Third Party stock is unlimited. Supplier ordering, replenishment, bin stock quantity, and stock depletion are outside the simulator.

## 12. Missing Product Master Data

Current master data and historical 12N runs may not align because products are introduced and removed regularly.

A missing product-master record must not be silently treated as non-Third-Party. Dataset loading should retain/report the unresolved line so future Exception Area work can route it correctly.

Phase 1 Third Party tests and visual fixtures must use known master-data products. Full historical-volume execution with unresolved products is deferred until Exception Station Phase 1 establishes issue routing.

Missing product master data is a data/configuration issue, distinct from a physical short pick, even though both may eventually route to the Exception Area.

## 13. Reserved Pick Outcomes And Exception Contract

The future processing boundary must allow a line to finish with:

```text
COMPLETE
INCOMPLETE
```

Phase 1 produces `COMPLETE` only. Do not implement random short picks, NS labels, Exception routing, or empty bags in this branch.

Future rules already agreed:

- an ASSOCIATED dependency is resolved when every required prepared line is either COMPLETE or INCOMPLETE;
- only COMPLETE lines add physical packs during collection;
- INCOMPLETE lines retain fulfilment metadata for a future NS bag label;
- a short pick does not automatically fail or stop the tote;
- a `FULL_PACK` tote with at least one fulfilled line bypasses Exceptions and continues to P2P, carrying NS metadata for missing lines;
- an all-incomplete case may visit Exceptions and produce an empty NS bag;
- routine incomplete fulfilment is not a terminal failed order state.

The current `Set<PreparedLineKey>` can represent successful Phase 1 readiness, but Exception Station planning must replace or extend it with per-line terminal outcomes before incomplete lines are simulated.

## 14. Physical Layout And Presentation

The production context is shown in:

- `docs/layout/warehouse_layout.png`
- `docs/layout/warehouse_layout_with_comments.png`

The Third Party Area is shelving beside a through-track. It has no adapting-style pick benches. Totes stop while an operative picks packs from bins and then continue along their route.

Phase 1 presentation should use:

- a simple through-track or small isolated route that preserves this movement;
- one selectable placeholder identifying the area;
- optional simple shelving blocks only if useful for orientation;
- inspection text for state, queue, active visits, line references, product ids, and bin locations.

Do not build detailed shelves, bins, operatives, supplier replenishment, or pack-transfer animation. Those belong to Phase 2.

## 15. Phase 1 Completion Criteria

- Product master CSV and 12N JSON are loaded independently.
- Product lookup uses Columbus product code and retains Third Party bin location and dimensions.
- Manual messages/lines are excluded and reported.
- Prepared-line identity no longer uses `referenceSheetNumber`.
- Direct and preparation Third Party work is selected using the line-level matrix in section 7.
- The area exposes configurable waiting and concurrent processing capacity.
- Successful picks update the tote load plan exactly once.
- ADAPTED totes continue to Adapting after Third Party processing.
- Fulfilment totes continue toward P2P with direct Third Party packs included.
- Conservative dependency release remains in force.
- Missing product and incomplete-pick behaviour remain explicit deferred Exception contracts.
- Phase 1 visuals remain minimal and selectable.
