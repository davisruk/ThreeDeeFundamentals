package online.davisfamily.warehouse.sim.dsp.analysis.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionCatalog;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionReason;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayBlockCategory;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayOccupancySample;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspP2pLineMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspServiceCentreMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentrePolicySnapshot;

/** Deterministic compact formatter for routine full-day progress milestones. */
public final class DspFullDayProgressFormatter {

    public List<String> describe(DspFullDayProgressSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        var runtime = snapshot.runtime();
        var metrics = runtime.metrics();
        List<String> lines = new ArrayList<>();
        lines.add("Run: state=" + runtime.state()
                + " profile=" + snapshot.profileId()
                + " calibration=" + snapshot.calibrationStatus()
                + " milestone=" + snapshot.completionMilestone()
                + " completedWithInputExclusions="
                + snapshot.completedWithInputExclusions()
                + " completedWithNsCandidates=" + snapshot.completedWithNsCandidates());
        lines.add("Clock: business=" + metrics.clock().businessDateTime()
                + " elapsed=" + metrics.clock().elapsedSimulationTime()
                + " phase=" + metrics.clock().phase());
        lines.add("Speed: requested=" + decimal(metrics.requestedExecutionSpeed())
                + " achieved=" + decimal(metrics.achievedExecutionSpeed()));

        DspFullDayOccupancySample sample = metrics.occupancySamples().isEmpty()
                ? null
                : metrics.occupancySamples().get(metrics.occupancySamples().size() - 1);
        int osrOccupancy = sample == null ? runtime.osr().occupancy() : sample.osrOccupancy();
        int osrCapacity = sample == null ? runtime.osr().capacity() : sample.osrCapacity();
        int osrLowWater = sample == null
                ? runtime.supply().lowWaterMark()
                : sample.osrLowWaterMark();
        lines.add("OSR: occupancy=" + osrOccupancy
                + "/" + osrCapacity
                + " lowWater=" + osrLowWater
                + " netFlow=" + metrics.osrNetFlow()
                + " inboundRate=" + decimal(metrics.actualInboundTotesPerSecond())
                + " outboundToteRate=" + decimal(metrics.closedOutboundTotesPerSecond())
                + " bagRate=" + decimal(metrics.allocatedBagsPerSecond()));
        runtime.elastic().allocation().wholeServiceCentrePolicy()
                .ifPresent(policy -> lines.add(wholeServiceCentreLine(policy)));

        List<DspServiceCentreMetricsSnapshot> serviceCentres = metrics.serviceCentres().stream()
                .sorted(Comparator.comparingInt(DspServiceCentreMetricsSnapshot::priority)
                        .reversed()
                        .thenComparing(DspServiceCentreMetricsSnapshot::serviceCentreId))
                .toList();
        Map<String, DspServiceCentreCompletionSnapshot> completionsById = new LinkedHashMap<>();
        runtime.completions().forEach(value ->
                completionsById.put(value.serviceCentreId(), value));
        serviceCentres.stream().map(value -> serviceCentreLine(
                value,
                completionsById.get(value.serviceCentreId()))).forEach(lines::add);
        metrics.p2pLines().stream().map(this::lineLine).forEach(lines::add);

        var release = runtime.operationalRelease();
        String releaseDecision = release.lastEvaluation()
                .map(value -> value.releaseDecision().isPresent() ? "present" : "none")
                .orElse("none");
        String releaseBlocks = release.lastEvaluation()
                .map(value -> Integer.toString(value.blockedCandidates().size()))
                .orElse("0");
        String command = release.lastCommandApplicationResult()
                .map(this::command)
                .orElse("none");
        lines.add("Release: mode=" + release.evaluationMode()
                + " inFlight=" + release.evaluationInFlight()
                + " decision=" + releaseDecision
                + " blockedCandidates=" + releaseBlocks
                + " command=" + command);
        lines.add("Transport: queue=" + runtime.transportIngress().transportOccupancy()
                + "/" + runtime.transportIngress().transportCapacity()
                + " inFlight=" + runtime.transportIngress().inFlightOccupancy()
                + "/" + runtime.transportIngress().inFlightCapacity()
                + " outboundQueue=" + runtime.outboundTransport().occupancy()
                + " stationArrival=" + runtime.stationArrivals().stream()
                        .mapToInt(value -> value.entries().size()).sum()
                + " blocked=" + valueOrNone(runtime.transportIngress().blockedReason()));
        lines.add("Station: claims=" + runtime.stationProcessing().activeClaims().size()
                + " pending=" + runtime.stationProcessing().pendingDispositions().size()
                + " completed=" + runtime.stationProcessing().completedCount()
                + " continued=" + runtime.continuation().continuedCount()
                + " consumed=" + runtime.continuation().consumedAcknowledgementCount()
                + " blocked=" + valueOrNone(runtime.continuation().blockedReason()));

        lines.add("Load: manualMessages=" + snapshot.loadReport().ignoredManualMessageCount()
                + " manualLines=" + snapshot.loadReport().ignoredManualLineCount()
                + " omittedOrders=" + snapshot.loadReport().omittedOrderCount()
                + " unresolvedProducts=" + snapshot.loadReport().unresolvedProductLines().size()
                + " rejectedLines=" + snapshot.rejectionCatalog().rejectedLineCount()
                + " rejectedMessages=" + snapshot.rejectionCatalog().rejectedMessageCount()
                + " countsByReason=" + rejectionReasonSummary(snapshot.rejectionCatalog())
                + " reusedInboundToteIds="
                + snapshot.loadReport().inboundToteIdSubstitutions().size());
        lines.add("NsCandidatesPendingByServiceCentre: "
                + snapshot.nsCandidateInputLineCountByServiceCentreId()
                + " (input-line occurrences; NS labels/Exceptions completion have not happened)");
        lines.add("Unsupported: count=" + metrics.unsupportedWork().size());

        int remainingSheets = serviceCentres.stream()
                .mapToInt(DspServiceCentreMetricsSnapshot::unfinishedSheetCount).sum();
        int remainingTotes = serviceCentres.stream()
                .mapToInt(DspServiceCentreMetricsSnapshot::unfinishedToteCount).sum();
        int remainingPacks = serviceCentres.stream()
                .mapToInt(DspServiceCentreMetricsSnapshot::unfinishedPackCount).sum();
        int remainingBags = serviceCentres.stream()
                .mapToInt(DspServiceCentreMetricsSnapshot::unfinishedBagCount).sum();
        lines.add("Remaining: sheets=" + remainingSheets
                + " totes=" + remainingTotes
                + " packs=" + remainingPacks
                + " bags=" + remainingBags);
        return List.copyOf(lines);
    }

    private String serviceCentreLine(
            DspServiceCentreMetricsSnapshot metrics,
            DspServiceCentreCompletionSnapshot completionSnapshot) {
        if (completionSnapshot == null) {
            throw new IllegalArgumentException(
                    "missing completion snapshot for " + metrics.serviceCentreId());
        }
        String blocks = java.util.Arrays.stream(DspFullDayBlockCategory.values())
                .map(category -> block(category, metrics.block(category)))
                .collect(Collectors.joining(";"));
        String completion = metrics.completionDateTime().map(Object::toString).orElse("none");
        return "ServiceCentre[" + metrics.serviceCentreId() + "]: priority="
                + metrics.priority()
                + " supply=" + metrics.supplyState()
                + " remaining=sheets:" + metrics.unfinishedSheetCount()
                + ",totes:" + metrics.unfinishedToteCount()
                + ",packs:" + metrics.unfinishedPackCount()
                + ",bags:" + metrics.unfinishedBagCount()
                + " lines=required:" + metrics.requiredLineCount()
                + ",desired:" + metrics.desiredLineCount()
                + ",owned:" + metrics.ownedLineCount()
                + ",unmet:" + metrics.unmetLineCount()
                + " completion=" + completion
                + " outcome=" + metrics.completionOutcome()
                + " p2pOutputClosure=" + completionSnapshot.p2pOutputClosureState()
                + " exceptions=missingPacks:" + completionSnapshot.missingPackCount()
                + ",pdcCollectedPacks:" + completionSnapshot.pdcCollectedPackCount()
                + ",affectedAllocatedBags:" + completionSnapshot.affectedAllocatedBagCount()
                + ",markedOutboundTotes:" + completionSnapshot.markedOutboundToteCount()
                + ",pendingEmptyBags:" + completionSnapshot.pendingEmptyBagCount()
                + ",nsCandidateInputLines:" + completionSnapshot.nsCandidateInputLineCount()
                + " blocks=" + blocks;
    }

    static String wholeServiceCentreLine(WholeServiceCentrePolicySnapshot policy) {
        var releases = policy.releases();
        var current = releases.releaseServiceCentreId();
        return "WholeServiceCentre: releaseCentre=" + current.orElse("none")
                + " eligibleCentre=" + policy.eligibleServiceCentreId().orElse("none")
                + " unreleasedOsr=" + current.map(releases.unreleasedOsrToteCounts()::get).orElse(0)
                + " unreleasedEmpty=" + current.map(releases.unreleasedEmptySheetCounts()::get).orElse(0)
                + " availableUnleasedLines=" + policy.availableUnleasedLineIds().stream()
                        .map(line -> line.value()).toList()
                + " deadlinesAndWorkloadCosts=diagnosticOnly";
    }

    private String lineLine(DspP2pLineMetricsSnapshot line) {
        P2pLineActivitySnapshot activity = line.activity();
        int queue = activity.input().stationArrivalCount()
                + activity.input().tipperInputCount()
                + activity.packPath().sorterInputCount()
                + activity.packPath().sorterOutputCount()
                + activity.bagging().pendingBagDischargeCount();
        return "P2P[" + line.lineId().value() + "]: owner="
                + line.serviceCentreId().orElse("none")
                + " feeding=" + line.feeding()
                + " draining=" + line.draining()
                + " queue=" + queue
                + " activity=input=" + activity.input().tipperInputCount()
                + ",packPath=" + activity.packPath().pcrPackCount()
                + ",bagging=" + activity.bagging().receiverCompletedBagCount()
                + ",quiescent=" + activity.quiescent()
                + " utilization=" + decimal(line.utilization())
                + " bags=" + line.allocatedBagCount() + "@" + decimal(line.allocatedBagRate())
                + " outboundTotes=" + line.closedOutboundToteCount()
                + "@" + decimal(line.closedOutboundToteRate());
    }

    private String block(
            DspFullDayBlockCategory category,
            DspServiceCentreMetricsSnapshot.BlockSummary summary) {
        return category + "[count=" + summary.blockedUnitCount()
                + ",reason=" + summary.latestReason().orElse("none") + "]";
    }

    private String command(SchedulerCommandApplicationResult result) {
        if (result.applied()) {
            return "applied";
        }
        return (result.deferred() ? "deferred:" : "rejected:") + result.reason();
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String rejectionReasonSummary(DspInputRejectionCatalog catalog) {
        String summary = java.util.Arrays.stream(DspInputRejectionReason.values())
                .map(reason -> reason + "=" + catalog.count(reason))
                .filter(value -> !value.endsWith("=0"))
                .collect(Collectors.joining(","));
        return summary.isEmpty() ? "none" : summary;
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
