# DSP Scheduler JSON Loading Plan

Status: drafted. Implement on `feature/dsp-scheduler-json-loading`.

## Summary

Detailed implementation plan for `feature/dsp-scheduler-json-loading`.

This branch adds JSON loading for product master data and 12N order messages, then converts that data into the existing DSP scheduler domain objects. It must remain a data-ingestion branch: no renderable creation, no scheduler threading, no new machine behavior, and no visual scene wiring unless explicitly requested later.

The branch should use the existing sample files under `docs/message-examples/` as schema references:

- `12N_Adapted_Order_Tote_Type.txt`
- `12N_Associated_Order_Tote_Type.txt`
- `12N_Full_Pack_Order_Tote_Type.txt`
- `12N_Manual_Order_Tote_Type.txt`

The user may later provide full product master JSON and many 12N files. This branch should therefore keep APIs file/path based and deterministic, but tests should use small in-test JSON fixtures rather than relying on large external files.

## Key Decisions

- Product classification still comes from product master data, not 12N line type.
- 12N JSON loading should preserve line-level `pharmacyId`, `referenceOrderId`, `referenceSheetNumber`, `orderLineType`, and picked-pack counts.
- `ADAPTED` and manual 12N files are preparation inputs. They should be convertible into `PreparedLineKey`s for target dispatch readiness.
- `ASSOCIATED`, `EMPTY`, and `FULL_PACK` are dispatch scheduler candidates and must pass existing pharmacy-purity validation.
- Manual examples are pharmacy-pure, but manual work is still preparation input rather than a scheduler dispatch `OrderType`.
- Do not introduce a database in this branch. Use in-memory loaded collections first; database choice depends on measured query shape later.
- Do not create active totes, packs, bags, or renderables from loaded JSON.

Branch strategy:

```powershell
git switch master
git pull
git switch -c feature/dsp-scheduler-json-loading
```

## Step 1: Add JSON Dependency And Loader Package

Allowed files:

- Update `gradle/libs.versions.toml`
- Update `app/build.gradle`
- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/`

Implementation:

- Add Jackson databind to the Gradle version catalog:
  - version key: `jackson-databind = "2.17.2"`
  - library key: `jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson-databind" }`
- Add `implementation libs.jackson.databind` to `app/build.gradle`.
- Create package `online.davisfamily.warehouse.sim.dsp.io`.
- Add `JsonLoaderSupport.java` with:
  - a private/static configured `ObjectMapper`
  - `public static <T> T read(Path path, Class<T> type)`
  - `public static <T> T readString(String json, Class<T> type)`
- Methods should reject null inputs and wrap checked IO errors in `IllegalArgumentException` with the path/type in the message.

Rules:

- Do not use ad hoc string parsing.
- Do not add JSON loading to scheduler classes directly.
- Keep the support class package-private if no other package needs it.

Test:

- Create `JsonLoaderSupportTest`.
- Cover valid JSON string mapping into a small private test record.
- Cover invalid JSON failure.
- Cover missing file failure with a useful exception.

Expected output:

- A small, tested JSON loading utility exists for later DSP loaders.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.JsonLoaderSupportTest
```

## Step 2: Load Product Master JSON

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/`

Create:

- `ProductMasterJsonRecord.java`
  - fields matching expected product master JSON:
    - `String productId`
    - `String category`
    - `boolean thirdParty`
- `ProductMasterJsonLoader.java`
  - `public List<ProductMasterRecord> load(Path path)`
  - `public List<ProductMasterRecord> loadString(String json)`
  - accepts either:
    - a top-level JSON array of product records, or
    - a top-level object with a `products` array

Mapping:

- Trim `productId`.
- Map category strings to existing `ProductCategory`.
- Accept category text case-insensitively.
- Map `thirdParty` directly.
- Return `List<ProductMasterRecord>`.
- Let `InMemoryProductMasterRepository` continue to own duplicate-product rejection.

Test:

- Create `ProductMasterJsonLoaderTest`.
- Cover top-level array input.
- Cover `{ "products": [...] }` input.
- Cover trimming product ids.
- Cover category case-insensitivity.
- Cover rejection of an unknown category.

Expected output:

- Product master JSON can be converted to existing `ProductMasterRecord`s and then used by `InMemoryProductMasterRepository`.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.ProductMasterJsonLoaderTest --tests online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriverTest
```

## Step 3: Model Raw 12N JSON Message DTOs

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/`

Create DTO records/classes that match the sample message shape:

- `TwelveNMessageJson.java`
  - `TwelveNHeaderJson header`
  - `TwelveNFieldJson toteIdentifier`
  - `TwelveNFieldJson transportContainer`
  - `TwelveNFieldJson orderPriority`
  - `TwelveNFieldJson departureTime`
  - `TwelveNFieldJson serviceCentre`
  - `TwelveNOrderDetailJson orderDetail`
- `TwelveNHeaderJson.java`
  - `String orderId`
  - `String sheetNumber`
- `TwelveNFieldJson.java`
  - `String payload`
- `TwelveNOrderDetailJson.java`
  - `int numberOfOrderLines`
  - `List<TwelveNOrderLineJson> orderLines`
- `TwelveNOrderLineJson.java`
  - `String orderLineNumber`
  - `String orderLineType`
  - `String pharmacyId`
  - `String patientId`
  - `String prescriptionId`
  - `String productId`
  - `String numberOfPacks`
  - `String numberOfPills`
  - `String referenceOrderId`
  - `String referenceSheetNumber`
  - `String numberOfPacksPicked`

Implementation:

- Keep these as raw JSON DTOs only.
- Do not put scheduler/domain validation in DTO constructors beyond null-safe JSON mapping needs.
- Keep field names aligned with the sample JSON.

Test:

- Create `TwelveNMessageJsonTest`.
- Parse a minimal full-pack-like JSON string.
- Assert nested fields are read correctly.
- Assert `orderLines` count is read correctly.

Expected output:

- The sample 12N JSON shape can be parsed into raw DTOs.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageJsonTest
```

## Step 4: Convert 12N Messages To Scheduler Domain Orders

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/`

Create:

- `TwelveNMessageKind.java`
  - enum values:
    - `MANUAL_PREPARATION`
    - `ADAPTED_PREPARATION`
    - `EMPTY_DISPATCH`
    - `ASSOCIATED_DISPATCH`
    - `FULL_PACK_DISPATCH`
- `TwelveNMessageKindMapper.java`
  - maps tote identifier payload:
    - `"01"` -> `MANUAL_PREPARATION`
    - `"02"` -> `ADAPTED_PREPARATION`
    - `"03"` -> `EMPTY_DISPATCH`
    - `"04"` -> `ASSOCIATED_DISPATCH`
    - `"05"` -> `FULL_PACK_DISPATCH`
  - rejects unknown or blank codes
- `TwelveNOrderMapper.java`
  - `public NotionalToteOrder toDispatchOrder(TwelveNMessageJson message, long sequenceNumber)`
  - accepts only `EMPTY_DISPATCH`, `ASSOCIATED_DISPATCH`, and `FULL_PACK_DISPATCH`
  - rejects `MANUAL_PREPARATION` and `ADAPTED_PREPARATION`
- `TwelveNPreparedLineMapper.java`
  - `public List<DspOrderItem> toPreparedLines(TwelveNMessageJson message)`
  - accepts only `MANUAL_PREPARATION` and `ADAPTED_PREPARATION`
  - rejects dispatch message kinds

Dispatch order mapping:

- `orderId` from `header.orderId`
- `notionalToteId` from `header.orderId` for now
- `serviceCentreId` from `serviceCentre.payload`
- `sheetNumber` from `header.sheetNumber`
- `orderType` from `toteIdentifier.payload`
  - `"03"` -> `OrderType.EMPTY`
  - `"04"` -> `OrderType.ASSOCIATED`
  - `"05"` -> `OrderType.FULL_PACK`
- each order line maps to `DspOrderItem`:
  - `itemId` from `orderLineNumber`
  - `productId` from `productId`
  - `quantity` from `numberOfPacks`
  - `pharmacyId` from `pharmacyId`
  - `lineType` from `orderLineType`
  - `referenceOrderId` from `referenceOrderId`
  - `referenceSheetNumber` from `referenceSheetNumber`
  - `numberOfPacksPicked` from `numberOfPacksPicked`
- Trim fixed-width string fields before constructing domain records.
- Parse numeric fixed-width strings as integers.
- Validate `orderDetail.numberOfOrderLines == orderDetail.orderLines.size()`.

Prepared line mapping:

- Convert each manual/adapted order line into a `DspOrderItem` using the same line mapping rules.
- The source message itself is not converted to `NotionalToteOrder`.
- Do not add `OrderType.MANUAL`.
- The prepared line's `referenceOrderId`, `referenceSheetNumber`, `orderLineNumber`, and `orderLineType` are the important fields for `PreparedLineKey.forPreparedLine(...)`.

Tests:

- Create `TwelveNOrderMapperTest`.
- Cover full-pack conversion.
- Cover associated conversion with mixed line types.
- Cover empty conversion using a minimal in-test `"03"` fixture, since no real empty sample exists yet.
- Cover adapted preparation line conversion where lines reference different target orders/pharmacies.
- Cover manual preparation line conversion.
- Cover line-count mismatch rejection.
- Cover unknown tote type rejection.
- Cover unknown order-line type rejection.

Expected output:

- 12N dispatch message JSON can be converted into existing `NotionalToteOrder` / `DspOrderItem` objects.
- 12N preparation message JSON can be converted into prepared `DspOrderItem`s without changing the scheduler domain model.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapperTest --tests online.davisfamily.warehouse.sim.dsp.model.*
```

## Step 5: Build Loaded Scheduler Input

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/`

Create:

- `LoadedDspData.java`
  - `List<ProductMasterRecord> products`
  - `List<NotionalToteOrder> dispatchOrders`
  - `List<DspOrderItem> preparedLines`
  - `Set<PreparedLineKey> preparedLineKeys`
- `DspJsonDatasetLoader.java`
  - constructor accepts `ProductMasterJsonLoader`, `TwelveNMessageKindMapper`, `TwelveNOrderMapper`, `TwelveNPreparedLineMapper`, and `DspOrderValidator`
  - `public LoadedDspData load(Path productMasterPath, List<Path> twelveNPaths)`
  - `public LoadedDspData load(List<ProductMasterRecord> products, List<TwelveNMessageJson> messages)`

Classification:

- `ASSOCIATED`, `EMPTY`, and `FULL_PACK` go into `dispatchOrders` and must be validated with `DspOrderValidator.validateForScheduler(...)`.
- `ADAPTED_PREPARATION` and `MANUAL_PREPARATION` go into `preparedLines`.
- Manual 12N examples must not become dispatch scheduler candidates.
- `preparedLineKeys` are built from `preparedLines` using `PreparedLineKey.forPreparedLine(...)`.
- Preserve input order as dispatch `sequenceNumber`; preparation messages do not consume dispatch sequence numbers.

Tests:

- Create `DspJsonDatasetLoaderTest`.
- Cover products plus one full-pack message.
- Cover adapted message producing prepared line keys and no dispatch order.
- Cover associated message requiring pharmacy purity validation.
- Cover mixed-pharmacy associated rejection.
- Cover stable sequence numbers from input order.

Expected output:

- A loaded JSON dataset can provide product master records, dispatch orders, prepared lines, and prepared-line readiness keys for scheduler runtime construction.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.DspJsonDatasetLoaderTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.routing.*
```

## Step 6: Create Scheduler Runtime Factory From Loaded Data

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/`

Create:

- `LoadedDspSchedulerRuntimeFactory.java`
  - constructor accepts `DspRouteDeriver`
  - method:
    - `public DspSchedulerRuntimeState createRuntimeState(LoadedDspData data, Map<StationType, StationAdmissionSnapshot> stationAdmissions, Optional<String> activeServiceCentreId)`

Implementation:

- Build `DspSchedulerOrderState` only from `LoadedDspData.dispatchOrders()`.
- Derive each order's `RouteRequirements` with `DspRouteDeriver`.
- Initial status is `DspOrderStatus.WAITING`.
- Include `LoadedDspData.preparedLineKeys()` in the initial `WarehouseSchedulerSnapshot`.
- Copy station admissions and active service centre into the snapshot.
- Do not create release catalogs, tote payloads, or renderables here.

Tests:

- Create `LoadedDspSchedulerRuntimeFactoryTest`.
- Cover runtime state creation from loaded dispatch orders.
- Cover route derivation uses product master categories.
- Cover prepared line keys are present in the snapshot.
- Cover missing product master data still fails through `DspRouteDeriver`.

Expected output:

- Loaded JSON data can be turned into a scheduler runtime snapshot without touching visual/debug integration.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.LoadedDspSchedulerRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.*
```

## Step 7: Branch Closure

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.*
```

Then ask the user to run their trusted broader suite if desired.

Completion criteria:

- Product master JSON loads into existing product master records.
- 12N JSON loads into existing DSP order/domain objects.
- Prepared-line readiness keys are derived from preparation messages.
- Scheduler runtime state can be built from loaded data.
- No renderables are created from loaded data.
- No scheduler behavior, machine behavior, visual scene behavior, or threading behavior changes are introduced.

## Deferred Work

- Loading thousands of files from a directory with progress reporting.
- Database-backed product/order storage.
- Mapping loaded dispatch orders to real tote payloads for visual release.
- Production-scale performance measurements.
- Scheduler thread integration.
- Manual exception / command-panel workflows.
