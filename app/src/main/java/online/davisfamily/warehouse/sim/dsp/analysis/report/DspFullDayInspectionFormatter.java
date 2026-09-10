package online.davisfamily.warehouse.sim.dsp.analysis.report;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayBlockCategory;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspP2pLineMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspServiceCentreMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

/** Pure compact inspection formatter for report values. */
public final class DspFullDayInspectionFormatter {

    public List<String> describe(DspFullDayInspectionSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        var runtime = snapshot.runtime();
        var metrics = runtime.metrics();
        String termination = snapshot.finalReport()
                .map(value -> value.terminationReason().name())
                .orElse(runtime.state().name());
        List<String> lines = new ArrayList<>();
        lines.add("Run: state=" + runtime.state()
                + " termination=" + termination
                + " profile=" + snapshot.profileId()
                + " calibration=" + snapshot.calibrationStatus()
                + " milestone=" + snapshot.completionMilestone());
        lines.add("Clock: business=" + metrics.clock().businessDateTime()
                + " elapsed=" + metrics.clock().elapsedSimulationTime()
                + " phase=" + metrics.clock().phase());
        lines.add("Speed: requested=" + decimal(metrics.requestedExecutionSpeed())
                + " achieved=" + decimal(metrics.achievedExecutionSpeed()));
        var currentSample = snapshot.runtime().metrics().occupancySamples().isEmpty()
                ? null
                : snapshot.runtime().metrics().occupancySamples().getLast();
        int osrOccupancy = currentSample == null
                ? runtime.osr().occupancy()
                : currentSample.osrOccupancy();
        int osrCapacity = currentSample == null
                ? runtime.osr().capacity()
                : currentSample.osrCapacity();
        int osrLowWater = currentSample == null
                ? runtime.supply().lowWaterMark()
                : currentSample.osrLowWaterMark();
        lines.add("OSR: occupancy=" + osrOccupancy
                + "/" + osrCapacity
                + " lowWater=" + osrLowWater
                + " netFlow=" + metrics.osrNetFlow()
                + " inboundRate=" + decimal(metrics.actualInboundTotesPerSecond())
                + " outboundToteRate=" + decimal(metrics.closedOutboundTotesPerSecond())
                + " bagRate=" + decimal(metrics.allocatedBagsPerSecond()));

        for (DspServiceCentreAnalysisResult result : snapshot.serviceCentres()) {
            lines.add(serviceCentreLine(result));
        }
        List<DspP2pLineMetricsSnapshot> p2pLines = snapshot.finalReport()
                .map(DspFullDayAnalysisReport::p2pLines)
                .orElse(metrics.p2pLines());
        for (DspP2pLineMetricsSnapshot line : p2pLines) {
            lines.add(lineLine(line));
        }

        var release = runtime.operationalRelease();
        String releaseDecision = release.lastEvaluation()
                .flatMap(value -> value.releaseDecision())
                .map(value -> value.command().physicalToteId().value())
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
                + " reusedInboundToteIds="
                + snapshot.loadReport().inboundToteIdSubstitutions().size());
        lines.add("Unsupported: " + joinOrNone(snapshot.unsupportedWork()));
        lines.add("Unfinished: " + joinOrNone(snapshot.unfinishedIdentities()));
        return List.copyOf(lines);
    }

    public List<String> describe(DspFullDayAnalysisReport report) {
        return describe(new DspFullDayInspectionSnapshot(report));
    }

    public List<String> format(DspFullDayInspectionSnapshot snapshot) {
        return describe(snapshot);
    }

    public String formatText(DspFullDayInspectionSnapshot snapshot) {
        return String.join(System.lineSeparator(), describe(snapshot));
    }

    private String serviceCentreLine(DspServiceCentreAnalysisResult result) {
        DspServiceCentreMetricsSnapshot metrics = result.metrics();
        String blocks = java.util.Arrays.stream(DspFullDayBlockCategory.values())
                .map(category -> block(category, metrics.block(category)))
                .collect(Collectors.joining(";"));
        String targetLateness = durationOrNone(result.targetLateness());
        String latestLateness = durationOrNone(result.latestAllowedLateness());
        return "ServiceCentre[" + result.serviceCentreId() + "]: supply="
                + metrics.supplyState()
                + " deadlineTarget=" + metrics.deadline().targetCompletion()
                + " deadlineLatest=" + metrics.deadline().latestAllowedCompletion()
                + " remaining=sheets:" + metrics.unfinishedSheetCount()
                + ",totes:" + metrics.unfinishedToteCount()
                + ",packs:" + metrics.unfinishedPackCount()
                + ",bags:" + metrics.unfinishedBagCount()
                + " lines=required:" + metrics.requiredLineCount()
                + ",desired:" + metrics.desiredLineCount()
                + ",owned:" + metrics.ownedLineCount()
                + ",unmet:" + metrics.unmetLineCount()
                + " blocks=" + blocks
                + " completion=" + result.completionDateTime().map(Object::toString).orElse("none")
                + " outcome=" + result.outcome()
                + " lateness=target:" + targetLateness + ",latest:" + latestLateness
                + " unfinishedIdentities=" + joinOrNone(result.unfinishedIdentities());
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
                + " activity=" + activityState(activity)
                + " utilization=" + decimal(line.utilization())
                + " bags=" + line.allocatedBagCount() + "@" + decimal(line.allocatedBagRate())
                + " outboundTotes=" + line.closedOutboundToteCount()
                + "@" + decimal(line.closedOutboundToteRate());
    }

    private String activityState(P2pLineActivitySnapshot activity) {
        return "input=" + activity.input().tipperInputCount()
                + ",packPath=" + activity.packPath().pcrPackCount()
                + ",bagging=" + activity.bagging().receiverCompletedBagCount()
                + ",quiescent=" + activity.quiescent();
    }

    private String block(
            DspFullDayBlockCategory category,
            DspServiceCentreMetricsSnapshot.BlockSummary summary) {
        return category + "[count=" + summary.blockedUnitCount()
                + ",duration=" + summary.blockedSimulationDuration()
                + ",reason=" + summary.latestReason().orElse("none") + "]";
    }

    private String command(SchedulerCommandApplicationResult result) {
        if (result.applied()) {
            return "applied";
        }
        return (result.deferred() ? "deferred:" : "rejected:") + result.reason();
    }

    private String durationOrNone(Optional<Duration> value) {
        return value.map(Duration::toString).orElse("none");
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String joinOrNone(List<String> values) {
        return values.isEmpty() ? "none" : String.join(" | ", values);
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
