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
        DspFullDayAnalysisReport report = snapshot.report();
        List<String> lines = new ArrayList<>();
        lines.add("Run: state=" + report.state()
                + " termination=" + report.terminationReason()
                + " profile=" + report.profileId()
                + " calibration=" + report.calibrationStatus()
                + " milestone=" + report.completionMilestone());
        lines.add("Clock: business=" + report.metrics().clock().businessDateTime()
                + " elapsed=" + report.metrics().clock().elapsedSimulationTime()
                + " phase=" + report.metrics().clock().phase());
        lines.add("Speed: requested=" + decimal(report.metrics().requestedExecutionSpeed())
                + " achieved=" + decimal(report.metrics().achievedExecutionSpeed()));
        var currentSample = report.occupancySamples().isEmpty()
                ? null
                : report.occupancySamples().getLast();
        int osrOccupancy = currentSample == null
                ? report.runtimeSnapshot().osr().occupancy()
                : currentSample.osrOccupancy();
        int osrCapacity = currentSample == null
                ? report.runtimeSnapshot().osr().capacity()
                : currentSample.osrCapacity();
        int osrLowWater = currentSample == null
                ? report.runtimeSnapshot().supply().lowWaterMark()
                : currentSample.osrLowWaterMark();
        lines.add("OSR: occupancy=" + osrOccupancy
                + "/" + osrCapacity
                + " lowWater=" + osrLowWater
                + " netFlow=" + report.metrics().osrNetFlow()
                + " inboundRate=" + decimal(report.metrics().actualInboundTotesPerSecond())
                + " outboundToteRate=" + decimal(report.metrics().closedOutboundTotesPerSecond())
                + " bagRate=" + decimal(report.metrics().allocatedBagsPerSecond()));

        for (DspServiceCentreAnalysisResult result : report.serviceCentres()) {
            lines.add(serviceCentreLine(result));
        }
        for (DspP2pLineMetricsSnapshot line : report.p2pLines()) {
            lines.add(lineLine(line));
        }

        var runtime = report.runtimeSnapshot();
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

        lines.add("Load: manualMessages=" + report.loadReport().ignoredManualMessageCount()
                + " manualLines=" + report.loadReport().ignoredManualLineCount()
                + " omittedOrders=" + report.loadReport().omittedOrderCount()
                + " unresolvedProducts=" + report.loadReport().unresolvedProductLines().size());
        lines.add("Unsupported: " + joinOrNone(report.unsupportedWork()));
        lines.add("Unfinished: " + joinOrNone(report.unfinishedIdentities()));
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
