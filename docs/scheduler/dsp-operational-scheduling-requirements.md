# DSP Operational Scheduling Requirements

Status: agreed operational baseline plus configurable first experimental profile.

This document defines service-centre supply, OSR replenishment, order eligibility, P2P line isolation, trunker targets, simulation time, and scheduler policy boundaries.

Read it with:

1. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
2. `docs/scheduler/dsp_osr_scheduler_requirements.md`
3. `docs/machines/phase-1-stations-roadmap.md`

Where this document conflicts with the older consolidated scheduler requirements, this document controls operational scheduling behavior. In particular, it supersedes global ADAPTED-first processing, hardcoded ASSOCIATED/EMPTY-before-FULL_PACK priority, round-robin service-centre selection, and the use of a notional tote ID as physical identity.

## 1. Purpose

The simulator must reproduce the known DSP operating model before it is used to compare alternative release strategies.

The operational objective is not merely to complete all work as quickly as possible. Each service centre has a trunker departure, and its outbound totes must leave DSP early enough to travel downstairs, be stacked, and be loaded.

The scheduler framework must therefore support:

- a faithful operational-baseline profile;
- configurable policy components;
- alternative scheduling profiles over the same immutable warehouse facts;
- comparable deadline, throughput, occupancy, and utilization metrics;
- hard physical invariants that no profile may bypass.

## 2. Operational Context

### 2.1 Service-centre distribution

Each pharmacy is allocated to one service centre. Outbound totes return downstairs to Cencora/AHDL, are grouped for the appropriate service centre, and are routed from that service centre to the pharmacy.

DSP work is supplied and controlled in service-centre groups because distribution is geographically organized.

### 2.2 P2P instances

The production layout has five P2P/tote-to-bag instances. Each has its own tipper, sorter path, approximately 31 PRLs, bagging machinery, and output-tote supply.

The instance count and capacities must remain configurable. The agreed production baseline is five.

### 2.3 Operative release

A DSP operative requests the next service centre through a screen connected to the Cencora/AHDL systems. This is not an OSR control-screen release, although it affects the physical supply into the DSP OSR.

The simulator may automate this operative action through a policy. It does not need a detailed operator UI in the first implementation.

## 3. Authoritative 12N Scheduling Fields

### 3.1 Service centre

12N `serviceCentre` identifies the service-centre group and downstream routing destination for the order.

The loader shall retain it on every logical order sheet.

### 3.2 Order priority

12N `orderPriority` is used operationally as the service-centre release sequence, despite its name suggesting individual-order priority.

Rules:

- every retained order for one service centre is expected to carry the same priority;
- service centres are requested in descending priority order;
- `999` is higher priority than `998`;
- priority does not rank individual totes within one service centre;
- inconsistent priorities for one service centre must be reported;
- ties between service centres must be resolved deterministically and reported for inspection.

### 3.3 12N departure time

12N `departureTime` records when the tote was released by Cencora. It is not the service-centre trunker time and shall not drive DSP scheduling.

It may be retained as source metadata but must not be confused with a DSP completion target.

### 3.4 Trunker departure time

Trunker departure is separate configuration data. Use an explicit name such as `trunkerDepartureTime` and an explicit day offset.

## 4. Two Release Boundaries

The simulator shall distinguish two operations currently described by the word release.

### 4.1 Service-centre supply authorization

The operative/policy requests a service centre from Cencora/AHDL. This authorizes its physical inbound totes to feed the DSP OSR.

Authorization does not guarantee immediate OSR entry. OSR capacity and upstream tote order still apply.

### 4.2 OSR processing release

An individual physical tote leaves the OSR and enters its DSP route. This is the release decision made by the warehouse scheduler against dependencies, route requirements, station admission, and selected P2P target.

These boundaries must have distinct state and commands so inspection can explain whether work is:

- still held upstream;
- authorized but waiting for OSR capacity;
- stored in OSR;
- blocked inside OSR by dependencies/capacity;
- released from OSR into DSP.

## 5. OSR Capacity And State

### 5.1 Physical capacity

The DSP OSR has capacity for 1,200 physical totes.

Capacity shall be configurable, with `1200` as the production baseline.

OSR occupancy counts physical inbound totes. It does not count:

- logical EMPTY orders before AV02;
- packs as separate capacity units;
- outbound totes after P2P;
- source data records that have not entered the OSR.

Occupancy decreases when a physical tote actually leaves the OSR, not when it is merely selected by a scheduler worker.

### 5.2 Logical supply state

Logical order-sheet supply state shall distinguish semantics equivalent to:

```text
HELD_UPSTREAM
AUTHORIZED_FOR_OSR
STORED_IN_OSR
RELEASED_FROM_OSR
IN_PROCESS
COMPLETED
```

Physical tote lifecycle remains separately defined by the lifecycle requirements.

### 5.3 Initial preload

At the start of the DSP working day, all retained ADAPTED, FULL_PACK, and ASSOCIATED physical totes for these service centres are already in the OSR:

- Letchworth (`104`, priority `999`);
- Swansea (`108`, priority `998`).

They are sent by Cencora the night before.

Initialization must validate that the configured preload does not exceed OSR capacity. It must fail or report clearly rather than silently dropping or overfilling totes.

EMPTY orders for these service centres are logically authorized but consume no OSR capacity before AV02.

## 6. Service-Centre Replenishment

### 6.1 Low-water-mark trigger

The OSR acts as the DSP reservoir and should remain well supplied. When occupancy falls to or below a configured low-water mark, the operative/policy requests the next service centre by descending order priority.

The source does not quantify the threshold. Therefore:

- the low-water mark is required configuration;
- it must be visible in inspection and metrics;
- changing it must not require scheduler code changes;
- the simulator should support experiments with different thresholds.

### 6.2 Upstream tote order

When Cencora releases a service centre, it sends:

1. all ADAPTED physical totes first;
2. then FULL_PACK and ASSOCIATED physical totes.

This is upstream supply order, not a global DSP preparation phase and not a permanent processing priority once totes are in OSR.

If the OSR fills during ADAPTED supply, remaining ADAPTED and later fulfilment totes stay upstream until capacity becomes available. FULL_PACK/ASSOCIATED supply must not overtake remaining ADAPTED supply for that service centre.

EMPTY work joins the fulfilment portion logically and waits for AV02 rather than entering the physical OSR.

### 6.3 Multiple supplied service centres

The OSR may contain physical totes from more than one authorized service centre. Requesting the next service centre does not require the previous service centre to be physically complete or all of its work to have left DSP.

### 6.4 Rate-limited inbound supply

Authorizing a service centre makes its upstream physical totes eligible for supply. It must not insert all of those totes into the OSR immediately.

The known inbound observations are:

- peak supply is approximately one physical tote every 3 seconds, or 1,200 totes per hour;
- a representative busy hour is approximately 400 totes per hour, or one tote every 9 seconds on average.

Inbound supply rate must be configurable behind a replaceable policy. The initial implementation may use a deterministic fixed interval so tests and repeated profile runs remain reproducible. A later policy may model variable or stochastic arrivals without changing service-centre authorization or OSR admission rules.

The supply stream must:

- preserve the service-centre and ADAPTED-first ordering rules in Sections 6.2 and 6.3;
- stop when the OSR reaches capacity and resume when capacity becomes available;
- distinguish totes authorized upstream, totes waiting to enter, totes admitted to OSR, and totes blocked by OSR capacity;
- exclude the configured start-of-day preload, which is already physically present in OSR at simulation start.

Operationally, inbound and outbound tote rates should remain broadly balanced so the OSR acts as a reservoir rather than continually filling or draining. This is an observed flow objective, not a strict one-for-one identity invariant: inbound tote consumption, EMPTY tote allocation, bagging, and outbound tote capacity can produce different physical tote counts. The simulator must measure both rates and the resulting OSR occupancy trend rather than force them to be equal.

## 7. DSP Order Eligibility

### 7.1 No global preparation barrier

There is no global all-service-centres ADAPTED preparation run.

ADAPTED and FULL_PACK work may process concurrently for authorized service centres.

### 7.2 ADAPTED

An ADAPTED tote stored in OSR is eligible when its required station admissions are available. It may visit Third Party and then Adapting STORE.

Its completion publishes terminal prepared-line outcomes for future ASSOCIATED/EMPTY work.

### 7.3 FULL_PACK

FULL_PACK may be released while ADAPTED work is being processed. It does not wait for unrelated preparation outcomes.

### 7.4 ASSOCIATED and EMPTY

ASSOCIATED and EMPTY become eligible when every one of their own required ADAPTED dependencies has reached a terminal outcome.

Terminal outcomes include successful and incomplete preparation. Only successful outcomes add physical packs; incomplete outcomes retain fulfilment/NS metadata.

Dependency readiness is per target order line. Completion of unrelated ADAPTED orders is not required.

EMPTY additionally requires AV02 physical tote admission and consumes no OSR physical capacity before allocation.

### 7.5 Defensive validation

The simulator must ensure that all source ADAPTED work needed by retained fulfilment orders exists or has a terminal unresolved outcome. The first profile does not model operationally late preparation arriving after the relevant dispatch work has passed through DSP.

## 8. Candidate Ranking

There is no documented rule that ASSOCIATED/EMPTY must be released ahead of FULL_PACK. Remove that assumption from future scheduling profiles.

The first implementation shall rank eligible work using pharmacy grouping and deterministic source order:

1. remain within an authorized service centre;
2. prefer the pharmacy already active on the selected P2P line where possible;
3. otherwise choose the next pharmacy group deterministically;
4. preserve stable logical source/sequence ordering within the pharmacy;
5. apply dependency and station-admission checks before release.

Service-centre `orderPriority` must not be reused as individual candidate priority within the group.

## 9. P2P Service-Centre Isolation

### 9.1 Sticky line ownership

Each P2P instance shall have an optional service-centre assignment/lease.

A line assigned to one service centre must reject candidates from every other service centre until it is fully quiescent and its output tote has been closed.

### 9.2 Quiescent line definition

A P2P line is quiescent only when it has:

- no queued or active input totes;
- no packs active in its tipper, sorter, PDC, PRL, or PCR path;
- no active or reserved bagging work;
- no bag being discharged;
- no outstanding expected bag groups;
- no open output tote belonging to its previous service-centre assignment.

A temporary idle machine state is not sufficient.

### 9.3 Automatic allocation

The simulator automatically selects P2P targets. A released order must remain pinned to its selected P2P instance so later admission evaluation cannot move it onto a line owned by another service centre.

Assignments are sticky and active work is never pre-empted. Reassignment occurs only after quiescence.

## 10. Outbound Tote Safety

Each P2P instance has exactly one open receiving outbound tote at a time.

That tote is owned by:

```text
P2P instance + serviceCentreId + pharmacyId
```

Hard rules:

- no outbound tote mixes pharmacies;
- no outbound tote mixes service centres;
- a service-centre line reassignment closes the previous output tote first;
- tote bag capacity is configurable;
- patient affinity is best effort only;
- a closed tote is never reopened;
- partially filled totes are flushed when their applicable work completes or a cutoff requires closure.

Detailed tote substitution, prescription bag grouping, and output splitting are defined by the lifecycle requirements.

## 11. Service-Centre Timetable

The production baseline is:

| Name | ID | Priority | Trunker departure |
|---|---:|---:|---:|
| Letchworth | 104 | 999 | 17:00 |
| Swansea | 108 | 998 | 17:00 |
| Exeter | 116 | 997 | 17:00 |
| Newcastle | 110 | 996 | 21:00 |
| Chessington | 101 | 995 | 19:00 |
| Croydon | 102 | 994 | 19:00 |
| Hinckley | 105 | 993 | 20:00 |
| Leeds | 106 | 992 | 21:00 |
| Coatbridge | 121 | 991 | 23:00 |
| Preston | 109 | 990 | 05:00, day +1 |

The release priority deliberately does not sort strictly by trunker time. This makes priority and deadline independent scheduling inputs.

The timetable belongs in configuration and must be available to inspection and scheduling profiles.

## 12. Operating Day And Deadlines

### 12.1 Operating window

The normal DSP working day is:

```text
normal start: 06:00
normal end:   22:00
```

`22:00` is an aspirational normal completion target. DSP may continue after 22:00 only while operators remain present.

Cencora shuts down its systems at midnight. `00:00` on day +1 is the hard processing cutoff.

### 12.2 Downstream handling

Trunker times are not DSP completion times. Outbound totes must travel downstairs, be stacked manually, and be loaded.

Use a configurable downstream handling duration with a production baseline of at least one hour.

```text
trunkerReadyDeadline = trunkerDepartureTime - downstreamHandlingDuration

targetCompletion = min(trunkerReadyDeadline, normalOperatingEnd)

latestAllowedCompletion = min(trunkerReadyDeadline, hardProcessingCutoff)
```

### 12.3 Baseline completion targets

With a one-hour downstream duration:

| Service centre | Trunker | Normal target | Latest completion |
|---|---:|---:|---:|
| Letchworth | 17:00 | 16:00 | 16:00 |
| Swansea | 17:00 | 16:00 | 16:00 |
| Exeter | 17:00 | 16:00 | 16:00 |
| Chessington | 19:00 | 18:00 | 18:00 |
| Croydon | 19:00 | 18:00 | 18:00 |
| Hinckley | 20:00 | 19:00 | 19:00 |
| Newcastle | 21:00 | 20:00 | 20:00 |
| Leeds | 21:00 | 20:00 | 20:00 |
| Coatbridge | 23:00 | 22:00 | 22:00 |
| Preston | 05:00, day +1 | 22:00 | 00:00, day +1 |

### 12.4 Completion outcome

Service-centre completion outcome shall distinguish semantics equivalent to:

```text
ON_TARGET
OVERTIME_BUT_DISPATCHABLE
MISSED_TRUNKER
UNFINISHED_AT_HARD_CUTOFF
```

Missing an early trunker does not require immediate simulation termination. Work may continue so lateness can be measured. At the hard cutoff, remaining work is terminally unfinished for that run.

### 12.5 Service-centre completion

A service centre is complete only when:

- every retained logical line has a terminal fulfilment outcome;
- every required bag exists or has a terminal exception outcome;
- all outbound totes for the service centre are closed;
- required Exception processing is complete;
- the totes are released from DSP toward Cencora.

Until Exceptions is implemented, the temporary measurable milestone may be output-tote closure at P2P. The contract must later extend without changing identity.

## 13. Scheduling Policy Framework

### 13.1 Hard invariants

No scheduling profile may bypass:

- logical/physical identity rules;
- prepared-line dependency readiness;
- OSR physical capacity;
- station admission/capacity;
- P2P service-centre isolation;
- outbound pharmacy and service-centre purity;
- sticky active P2P assignments;
- physical tote lifecycle validity;
- simulation-thread ownership of live mutations.

### 13.2 Policy decisions

Profiles may vary:

- OSR low-water mark;
- service-centre authorization strategy;
- overlap between authorized service centres;
- candidate ranking;
- P2P line allocation;
- workload estimation;
- deadline urgency and safety factors;
- output flush timing before soft/hard cutoffs.

### 13.3 Composition

Use composable policy boundaries equivalent to:

```text
service-centre supply policy
order eligibility policy
candidate ranking policy
P2P line allocation policy
outbound tote allocation policy
```

Avoid one scheduler class containing unrelated flags for every profile.

The scheduler remains a pure evaluator over immutable snapshots. It returns decisions/commands; simulation-thread code applies live mutations.

## 14. First Implementation Profile

The first profile shall combine:

```text
serviceCentreSupply:
    PRIORITY_ORDERED_OSR_LOW_WATERMARK

orderEligibility:
    DEPENDENCY_READY_OVERLAP

candidateRanking:
    PHARMACY_GROUPED_THEN_SOURCE_SEQUENCE

p2pLineAllocation:
    DEADLINE_AWARE_ELASTIC_STICKY_LEASES

outboundAllocation:
    PHARMACY_PURE_FIXED_BAG_CAPACITY
```

This profile reproduces confirmed upstream behavior while using an explicit, configurable automatic P2P allocation policy where the exact operational line-allocation rule is not yet confirmed.

It must identify itself in snapshots, inspection, and run reports so it is not mistaken for a fully confirmed production algorithm.

## 15. Workload And Elastic P2P Allocation

### 15.1 Work estimator

Use a replaceable estimator. The initial normalized estimate may be:

```text
remaining work =
    remaining inbound tote count * tote handling cost
  + remaining physical pack count * pack processing cost
  + predicted bag count * bagging cost
```

Weights are configuration and may initially derive from machine durations. The estimate need not predict exact wall-clock time; it must be deterministic and comparable.

Include all nonterminal fulfilment work for an authorized service centre, including temporarily dependency-blocked ASSOCIATED/EMPTY work. Excluding known blocked work could relinquish all of a service centre's lines too early.

### 15.2 Deadline-aware demand

For each authorized service centre:

```text
available time = latest allowed completion - current simulation time

required lines = ceil(remaining estimated single-line work / available time)
```

Apply configurable safety/parallel-efficiency factors and clamp to the configured P2P instance count.

### 15.3 Elastic allocation

- The oldest authorized service centre receives lines up to its required count first.
- A later authorized service centre may use surplus quiescent lines.
- Keep at least one line available/assigned for an earlier nonterminal service centre.
- Limit concurrently active service centres through configuration, initially two.
- Never pre-empt active line work.
- If urgency increases, request additional lines and reclaim them only after current work drains.
- Record infeasibility when total required demand exceeds available line capacity.

### 15.4 Timing fidelity and calibration

Detailed production timings are not yet available for every route and operation. Known gaps include conveyor travel speeds, station service times, label printing, and other tote, pack, and bag handling durations.

The initial operational scheduler must prioritize correct 12N interpretation, order dependencies, physical identities, service-centre authorization, OSR capacity, rate-limited inbound supply, and P2P isolation. It may use explicit configurable placeholder durations and normalized work units, but must not present resulting completion times as calibrated production predictions.

Timing and workload estimation must remain behind replaceable policy boundaries. The known inbound supply rates in Section 6.4 are modeled now because they directly affect OSR replenishment. Remaining route and operation timings may be calibrated later without changing scheduler eligibility, admission, or command contracts.

Tests must use deterministic simulation time and controlled policy inputs. They must not depend on wall-clock sleeps or narrow real-time timing windows.

## 16. Alternative Profiles

The framework shall allow later profiles such as:

- strict service-centre drain before the next centre uses P2P;
- fixed quiescent-line threshold overlap;
- strict preparation-first analysis;
- earliest-deadline or least-slack service-centre authorization;
- alternative OSR low-water marks;
- alternative candidate ranking and pharmacy batching.

These are analytical alternatives. They must use the same hard invariants, machine capacities, input dataset, random seed, and metrics as the operational profile.

## 17. Simulation Clock And Acceleration

### 17.1 Business clock

The simulation clock starts at the configured operating date at `06:00`. It must represent day offsets explicitly for events after midnight.

All machine and station durations remain expressed in simulation seconds.

### 17.2 Configurable time scale

Support a configurable target relationship between real and simulation time:

```text
1x:    1 real second      = 1 simulation second
60x:   1 real second      = 1 simulation minute
1000x: 1 real millisecond = 1 simulation second
```

Do not achieve acceleration by passing one very large frame delta through machines and sensors. That can skip sensor windows and state transitions.

### 17.3 Fixed simulation steps

Acceleration shall use a real-time accumulator and repeated bounded simulation steps:

```text
real frame delta
  -> apply target time scale
  -> accumulate simulation duration
  -> execute repeated fixed simulation steps
  -> render the latest completed state
```

The fixed step, requested multiplier, maximum work budget, and render decimation shall be configurable.

### 17.4 Execution modes

Support semantics equivalent to:

- `REALTIME`: interactive visual verification;
- `ACCELERATED_VISUAL`: fixed simulation steps with decimated rendering;
- `HEADLESS_ANALYSIS`: no rendering, advance as quickly as available CPU permits.

A requested multiplier is a target, not a guarantee. Report actual achieved simulation speed.

The clock boundary should support later simulation/render-thread separation but must not require that work in the first clock implementation.

## 18. Metrics And Inspection

Every profile run shall expose:

- active profile and policy IDs;
- simulation date/time and requested/actual speed;
- OSR capacity, occupancy, low-water mark, and occupancy history;
- service-centre supply state, authorization time, and upstream waiting count;
- configured, requested, and actual inbound tote arrival rates;
- outbound physical tote completion rate and OSR net-flow trend;
- active service centres and priorities;
- P2P line ownership, workload, queue state, and quiescence;
- blocked candidates and reasons;
- preparation dependency counts and outcomes;
- outbound tote service centre, pharmacy, bag count, and closure reason;
- service-centre completion time and target;
- on-target/overtime/missed/cutoff result;
- unfinished sheets and totes at deadlines;
- P2P utilization and throughput;
- time blocked by dependencies, station capacity, OSR state, and P2P assignment;
- timing calibration status so provisional results cannot be mistaken for production predictions.

Algorithm comparisons shall prioritize:

1. number of missed trunkers;
2. number of unfinished service centres at hard cutoff;
3. maximum and total lateness;
4. number of late totes;
5. normal-hours overtime;
6. overall completion time and resource utilization.

## 19. Configuration Baseline

The scheduling/runtime configuration must include at least:

```text
osrCapacity = 1200
osrLowWaterMark = configurable
initialPreloadedServiceCentres = [104, 108]
peakInboundTotesPerHour = 1200
representativeBusyInboundTotesPerHour = 400
inboundToteSupplyRate = configurable
p2pInstanceCount = 5
maximumConcurrentServiceCentres = 2
minimumReservedLinesForEarlierCentre = 1
outboundToteBagCapacity = configurable
maximumPacksPerBag = configurable
normalOperatingStart = 06:00
normalOperatingEnd = 22:00
hardProcessingCutoff = day +1 00:00
downstreamHandlingDuration = 1 hour
simulationTimeScale = configurable
fixedSimulationStepSeconds = configurable
timingCalibrationStatus = UNCALIBRATED
```

Service-centre names, IDs, priorities, trunker times, and day offsets belong in structured configuration rather than scheduler source code.

## 20. Deliberate Deferrals

- exact production OSR low-water mark;
- detailed operative release UI;
- exact production P2P line-allocation algorithm;
- calibrated route travel speeds;
- calibrated station and label-printing service times;
- stochastic inbound arrival distributions beyond the initial deterministic rate policy;
- detailed Cencora tote conveyor, stacking, and truck loading simulation;
- realistic empty-tote reservoir geometry;
- dimensional bag packing;
- 32R generation;
- event-driven full-day fast-forward optimization;
- render-thread separation;
- alternative profile implementation beyond the first selected profile.

These deferrals must remain configurable or behind stable policy boundaries where applicable.

## 21. Completion Criteria For The Operational Scheduling Programme

- Service-centre identity and order priority are retained and validated from 12N.
- 12N departure time is not used as a trunker deadline.
- The initial OSR preload contains Letchworth and Swansea physical totes within configured capacity.
- Subsequent service-centre supply follows descending priority and ADAPTED-first upstream order.
- The OSR low-water mark authorizes replenishment without overfilling the OSR.
- Authorized service-centre totes enter through a configurable rate-limited supply stream rather than appearing in OSR as a batch.
- The 1,200-tote/hour peak and 400-tote/hour representative busy rates are available as baseline configuration.
- ADAPTED and FULL_PACK can process concurrently.
- ASSOCIATED/EMPTY eligibility is based only on their own terminal dependencies.
- EMPTY is authorized with fulfilment work and obtains its physical tote at AV02.
- Candidate ranking no longer hardcodes ASSOCIATED/EMPTY before FULL_PACK.
- Five configurable P2P instances enforce sticky service-centre isolation.
- Output totes enforce service-centre and pharmacy purity.
- Timetable, normal target, hard cutoff, and downstream handling produce explicit completion outcomes.
- The first profile is identified as a configurable policy composition.
- Alternative profiles can reuse the same snapshots, invariants, commands, and metrics.
- Fixed-step accelerated and headless execution preserve deterministic machine/sensor behavior.
- Uncalibrated route and station timings are explicit, replaceable, and never presented as production-accurate predictions.
