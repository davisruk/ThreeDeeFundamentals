package online.davisfamily.warehouse.sim.dsp.analysis.report;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayBlockCategory;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayOccupancySample;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspP2pLineMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspServiceCentreMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.UnresolvedProductLine;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.AllocatedOutboundBag;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationIssue;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportArrivalControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportInFlightSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressControllerSnapshot;

/** Writes a full-day report through a safe, deterministic UTF-8 replacement. */
public final class DspFullDayReportJsonWriter {
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public DspFullDayReportJsonWriter() {
        objectMapper = new ObjectMapper();
    }

    /** Serializes the report without touching the filesystem. */
    public byte[] serialize(DspFullDayAnalysisReport report) throws JsonProcessingException {
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }
        return objectMapper.writeValueAsBytes(toJson(report));
    }

    public String serializeToString(DspFullDayAnalysisReport report)
            throws JsonProcessingException {
        return new String(serialize(report), StandardCharsets.UTF_8);
    }

    /** Writes a new report and refuses an existing target. */
    public Path write(DspFullDayAnalysisReport report, Path output) throws IOException {
        return write(report, output, false);
    }

    /** Writes a report, replacing an existing target only when overwrite is true. */
    public Path write(DspFullDayAnalysisReport report, Path output, boolean overwrite)
            throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output must not be null");
        }

        // Complete serialization happens before checking or opening the destination.  A mapping
        // or serialization failure therefore cannot truncate or replace an existing report.
        byte[] bytes = serialize(report);
        Path targetParent = output.toAbsolutePath().getParent();
        if (targetParent == null) {
            throw new IOException("report output has no parent directory: " + output);
        }
        if (!overwrite && Files.exists(output)) {
            throw new FileAlreadyExistsException(output.toString());
        }

        Files.createDirectories(targetParent);
        Path temporary = Files.createTempFile(
                targetParent,
                output.getFileName().toString() + ".",
                ".tmp");
        boolean moved = false;
        try {
            Files.write(
                    temporary,
                    bytes,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            moveIntoPlace(temporary, output, overwrite);
            moved = true;
            return output;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void moveIntoPlace(Path temporary, Path target, boolean overwrite) throws IOException {
        try {
            if (overwrite) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException unsupported) {
            if (overwrite) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temporary, target);
            }
        }
    }

    private ObjectNode toJson(DspFullDayAnalysisReport report) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.set("profile", profileNode(report));
        root.set("termination", terminationNode(report));
        root.set("clock", clockNode(report.metrics().clock()));
        root.set("configuration", objectMapper.valueToTree(report.configuration()));
        root.set("load", loadNode(report.loadReport()));
        root.set("metrics", metricsNode(report.metrics()));

        ArrayNode centres = root.putArray("serviceCentres");
        report.serviceCentres().forEach(value -> centres.add(serviceCentreNode(value)));
        ArrayNode lines = root.putArray("p2pLines");
        report.p2pLines().forEach(value -> lines.add(lineMetricsNode(value)));
        ArrayNode occupancy = root.putArray("occupancySamples");
        report.occupancySamples().forEach(value -> occupancy.add(occupancyNode(value)));
        ArrayNode infeasibility = root.putArray("elasticInfeasibilityHistory");
        report.elasticInfeasibilityHistory().forEach(value -> infeasibility.add(infeasibilityNode(value)));
        addStrings(root, "warnings", report.warnings());
        addStrings(root, "unsupportedWork", report.unsupportedWork());
        addStrings(root, "unfinishedIdentities", report.unfinishedIdentities());
        root.set("current", currentNode(report.runtimeSnapshot()));
        return root;
    }

    private ObjectNode profileNode(DspFullDayAnalysisReport report) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", report.profileId());
        node.put("calibrationStatus", report.calibrationStatus());
        node.put("completionMilestone", report.completionMilestone().name());
        return node;
    }

    private ObjectNode terminationNode(DspFullDayAnalysisReport report) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("state", report.state().name());
        node.put("reason", report.terminationReason().name());
        putDuration(node, "elapsedSimulationTime", report.metrics().clock().elapsedSimulationTime());
        node.put("cutoffActed", report.runtimeSnapshot().cutoff().acted());
        ArrayNode closed = node.putArray("hardCutoffClosedTotes");
        report.runtimeSnapshot().cutoff().hardCutoffClosedTotes()
                .forEach(value -> closed.add(outboundToteNode(value)));
        node.put("diagnostic", report.runtimeSnapshot().cutoff().diagnostic());
        return node;
    }

    private ObjectNode clockNode(online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot clock) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        putDuration(node, "elapsedSimulationTime", clock.elapsedSimulationTime());
        node.put("operatingDate", clock.operatingDate().toString());
        node.put("businessDateTime", clock.businessDateTime().toString());
        node.put("operatingDayTime", clock.operatingDayTime().toString());
        node.put("phase", clock.phase().name());
        node.put("normalEndDateTime", clock.normalEndDateTime().toString());
        node.put("hardCutoffDateTime", clock.hardCutoffDateTime().toString());
        return node;
    }

    private ObjectNode loadNode(DspDatasetLoadReport report) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("ignoredManualMessageCount", report.ignoredManualMessageCount());
        node.put("ignoredManualLineCount", report.ignoredManualLineCount());
        node.put("omittedOrderCount", report.omittedOrderCount());
        ArrayNode unresolved = node.putArray("unresolvedProductLines");
        for (UnresolvedProductLine value : report.unresolvedProductLines()) {
            unresolved.addObject()
                    .put("orderId", value.orderId())
                    .put("lineReference", value.lineReference())
                    .put("productId", value.productId());
        }
        return node;
    }

    private ObjectNode metricsNode(DspFullDayMetricsSnapshot metrics) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("profileId", metrics.profileId());
        node.put("serviceCentreSupplyPolicyId", metrics.serviceCentreSupplyPolicyId());
        node.put("orderEligibilityPolicyId", metrics.orderEligibilityPolicyId());
        node.put("candidateRankingPolicyId", metrics.candidateRankingPolicyId());
        node.put("p2pLineAllocationPolicyId", metrics.p2pLineAllocationPolicyId());
        node.put("outboundAllocationPolicyId", metrics.outboundAllocationPolicyId());
        node.put("calibrationStatus", metrics.calibrationStatus());
        node.put("completionMilestone", metrics.completionMilestone());
        node.put("state", metrics.state().name());
        node.set("clock", clockNode(metrics.clock()));
        node.put("requestedExecutionSpeed", metrics.requestedExecutionSpeed());
        node.put("achievedExecutionSpeed", metrics.achievedExecutionSpeed());
        putDuration(node, "observedSimulationDuration", metrics.observedSimulationDuration());
        putDuration(node, "configuredInboundInterval", metrics.configuredInboundInterval());
        node.put("configuredInboundTotesPerSecond", metrics.configuredInboundTotesPerSecond());
        node.put("admittedInboundToteCount", metrics.admittedInboundToteCount());
        node.put("actualInboundTotesPerSecond", metrics.actualInboundTotesPerSecond());
        node.put("departedInboundToteCount", metrics.departedInboundToteCount());
        node.put("osrNetFlow", metrics.osrNetFlow());
        putDuration(node, "capacityBlockedDuration", metrics.capacityBlockedDuration());
        node.put("closedOutboundToteCount", metrics.closedOutboundToteCount());
        node.put("closedOutboundTotesPerSecond", metrics.closedOutboundTotesPerSecond());
        node.put("allocatedBagCount", metrics.allocatedBagCount());
        node.put("allocatedBagsPerSecond", metrics.allocatedBagsPerSecond());
        node.put("minimumOsrOccupancy", metrics.minimumOsrOccupancy());
        node.put("maximumOsrOccupancy", metrics.maximumOsrOccupancy());
        node.put("meanOsrOccupancy", metrics.meanOsrOccupancy());
        ObjectNode blocks = node.putObject("blockDurations");
        for (DspFullDayBlockCategory category : DspFullDayBlockCategory.values()) {
            putDuration(blocks, category.name(), metrics.blockDurations().get(category));
        }
        node.put("ignoredManualMessageCount", metrics.ignoredManualMessageCount());
        node.put("ignoredManualLineCount", metrics.ignoredManualLineCount());
        node.put("omittedOrderCount", metrics.omittedOrderCount());
        addStrings(node, "unsupportedWork", metrics.unsupportedWork());
        ArrayNode unresolved = node.putArray("unresolvedProductLines");
        for (UnresolvedProductLine value : metrics.unresolvedProductLines()) {
            unresolved.addObject()
                    .put("orderId", value.orderId())
                    .put("lineReference", value.lineReference())
                    .put("productId", value.productId());
        }
        ArrayNode occupancy = node.putArray("occupancySamples");
        metrics.occupancySamples().forEach(value -> occupancy.add(occupancyNode(value)));
        ArrayNode lines = node.putArray("p2pLines");
        metrics.p2pLines().forEach(value -> lines.add(lineMetricsNode(value)));
        ArrayNode infeasibility = node.putArray("elasticInfeasibilityHistory");
        metrics.elasticInfeasibilityHistory()
                .forEach(value -> infeasibility.add(infeasibilityNode(value)));
        return node;
    }

    private ObjectNode serviceCentreNode(DspServiceCentreAnalysisResult result) {
        DspServiceCentreMetricsSnapshot metrics = result.metrics();
        DspServiceCentreCompletionSnapshot completion = result.completion();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("serviceCentreId", result.serviceCentreId());
        node.put("priority", result.priority());
        node.put("supplyState", metrics.supplyState().name());
        optionalDuration(node, "authorizationElapsedTime", metrics.authorizationElapsedTime());
        node.put("upstreamWaitingCount", metrics.upstreamWaitingCount());
        node.put("complete", result.complete());
        node.put("outcome", result.outcome().name());
        optionalDuration(node, "completionElapsedTime", result.completionElapsedTime());
        optionalDateTime(node, "completionDateTime", result.completionDateTime());
        optionalDuration(node, "targetLateness", result.targetLateness());
        optionalDuration(node, "latestAllowedLateness", result.latestAllowedLateness());
        node.set("deadline", deadlineNode(metrics.deadline()));

        ObjectNode remaining = node.putObject("remainingWork");
        remaining.put("unfinishedSheetCount", metrics.unfinishedSheetCount());
        remaining.put("unfinishedToteCount", metrics.unfinishedToteCount());
        remaining.put("unfinishedPackCount", metrics.unfinishedPackCount());
        remaining.put("unfinishedBagCount", metrics.unfinishedBagCount());
        remaining.put("completionRemainingPhysicalToteCount", completion.remainingPhysicalToteCount());
        remaining.put("completionRemainingPhysicalPackCount", completion.remainingPhysicalPackCount());
        remaining.put("completionRemainingPlannedBagCount", completion.remainingPlannedBagCount());

        ObjectNode lines = node.putObject("lineAllocation");
        lines.put("required", metrics.requiredLineCount());
        lines.put("desired", metrics.desiredLineCount());
        lines.put("owned", metrics.ownedLineCount());
        lines.put("unmet", metrics.unmetLineCount());
        lines.put("infeasible", metrics.elasticInfeasible());
        ArrayNode issues = node.putArray("elasticIssues");
        metrics.elasticIssues().forEach(issue -> issues.add(issueNode(issue)));
        ObjectNode blockSummaries = node.putObject("blockSummaries");
        for (DspFullDayBlockCategory category : DspFullDayBlockCategory.values()) {
            DspServiceCentreMetricsSnapshot.BlockSummary summary = metrics.block(category);
            ObjectNode summaryNode = blockSummaries.putObject(category.name());
            summaryNode.put("blockedUnitCount", summary.blockedUnitCount());
            putDuration(summaryNode, "blockedSimulationDuration", summary.blockedSimulationDuration());
            optionalString(summaryNode, "latestReason", summary.latestReason());
        }
        addStrings(node, "unsupportedWork", result.unsupportedWork());
        addStrings(node, "unfinishedIdentities", result.unfinishedIdentities());
        return node;
    }

    private ObjectNode deadlineNode(online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot deadline) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("serviceCentreId", deadline.serviceCentreId());
        node.put("displayName", deadline.displayName());
        node.put("priority", deadline.priority());
        node.put("evaluatedAt", deadline.evaluatedAt().toString());
        node.put("trunkerDepartureDateTime", deadline.trunkerDepartureDateTime().toString());
        node.put("trunkerReadyDeadline", deadline.trunkerReadyDeadline().toString());
        node.put("targetCompletion", deadline.targetCompletion().toString());
        node.put("latestAllowedCompletion", deadline.latestAllowedCompletion().toString());
        putDuration(node, "availableTime", deadline.availableTime());
        node.put("targetPassed", deadline.targetPassed());
        node.put("latestAllowedCompletionPassed", deadline.latestAllowedCompletionPassed());
        return node;
    }

    private ObjectNode lineMetricsNode(DspP2pLineMetricsSnapshot metrics) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("lineId", metrics.lineId().value());
        optionalString(node, "ownerServiceCentreId", metrics.serviceCentreId());
        node.put("feeding", metrics.feeding());
        node.put("draining", metrics.draining());
        node.set("activity", activityNode(metrics.activity()));
        putDuration(node, "observedSimulationDuration", metrics.observedSimulationDuration());
        putDuration(node, "busySimulationDuration", metrics.busySimulationDuration());
        node.put("utilization", metrics.utilization());
        node.put("consumedToteCount", metrics.consumedToteCount());
        node.put("consumedToteRate", metrics.consumedToteRate());
        node.put("allocatedBagCount", metrics.allocatedBagCount());
        node.put("allocatedBagRate", metrics.allocatedBagRate());
        node.put("closedOutboundToteCount", metrics.closedOutboundToteCount());
        node.put("closedOutboundToteRate", metrics.closedOutboundToteRate());
        return node;
    }

    private ObjectNode activityNode(P2pLineActivitySnapshot activity) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        P2pInputActivitySnapshot input = activity.input();
        ObjectNode inputNode = node.putObject("input");
        inputNode.put("stationArrivalCount", input.stationArrivalCount());
        inputNode.put("tipperInputCount", input.tipperInputCount());
        inputNode.put("activeTipperTote", input.activeTipperTote());
        inputNode.put("activeTipperDischargeCount", input.activeTipperDischargeCount());
        P2pPackPathActivitySnapshot path = activity.packPath();
        ObjectNode pathNode = node.putObject("packPath");
        pathNode.put("sorterInputCount", path.sorterInputCount());
        pathNode.put("sorterOutputCount", path.sorterOutputCount());
        pathNode.put("pendingSorterOutfeedCount", path.pendingSorterOutfeedCount());
        pathNode.put("pdcPackCount", path.pdcPackCount());
        pathNode.put("activePdcTransferCount", path.activePdcTransferCount());
        pathNode.put("nonIdlePrlCount", path.nonIdlePrlCount());
        pathNode.put("prlPackCount", path.prlPackCount());
        pathNode.put("activePrlToPcrTransferCount", path.activePrlToPcrTransferCount());
        pathNode.put("pcrPackCount", path.pcrPackCount());
        pathNode.put("pcrTravellingGroupCount", path.pcrTravellingGroupCount());
        pathNode.put("pcrReleasedGroupCount", path.pcrReleasedGroupCount());
        pathNode.put("outstandingExpectedBagGroupCount", path.outstandingExpectedBagGroupCount());
        P2pBaggingActivitySnapshot bagging = activity.bagging();
        ObjectNode baggingNode = node.putObject("bagging");
        baggingNode.put("currentBagGroup", bagging.currentBagGroup());
        baggingNode.put("reservedBagGroup", bagging.reservedBagGroup());
        baggingNode.put("activeBagReservation", bagging.activeBagReservation());
        baggingNode.put("pendingBagDischargeCount", bagging.pendingBagDischargeCount());
        baggingNode.put("activeBagDischarge", bagging.activeBagDischarge());
        baggingNode.put("receiverReservation", bagging.receiverReservation());
        baggingNode.put("receiverReceivingBag", bagging.receiverReceivingBag());
        baggingNode.put("receiverCompletedBagCount", bagging.receiverCompletedBagCount());
        activity.openOutboundTote().ifPresentOrElse(
                tote -> node.set("openOutboundTote", outboundToteNode(tote)),
                () -> node.set("openOutboundTote", NullNode.instance));
        return node;
    }

    private ObjectNode outboundToteNode(OutboundToteSnapshot tote) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("physicalToteId", tote.physicalToteId().value());
        node.put("p2pLineId", tote.p2pLineId().value());
        optionalString(node, "serviceCentreId", tote.serviceCentreId());
        optionalString(node, "pharmacyId", tote.pharmacyId());
        node.put("maximumBagCount", tote.maximumBagCount());
        node.put("bagCount", tote.bagCount());
        node.put("open", tote.open());
        tote.closureReason().ifPresentOrElse(
                reason -> node.put("closureReason", reason.name()),
                () -> node.set("closureReason", NullNode.instance));
        ArrayNode bags = node.putArray("allocatedBags");
        tote.allocatedBags().forEach(bag -> bags.add(allocatedBagNode(bag)));
        return node;
    }

    private ObjectNode allocatedBagNode(AllocatedOutboundBag bag) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("correlationId", bag.bagKey().correlationId());
        node.put("prescriptionId", bag.bagKey().prescriptionId());
        node.put("bagOrdinal", bag.bagKey().bagOrdinal());
        node.put("serviceCentreId", bag.plannedBag().serviceCentreId());
        node.put("pharmacyId", bag.plannedBag().pharmacyId());
        node.put("patientId", bag.plannedBag().patientId());
        node.put("prescriptionId", bag.plannedBag().prescriptionId());
        ArrayNode packs = node.putArray("physicalPackIds");
        bag.plannedBag().physicalPackIds().forEach(packs::add);
        ArrayNode sheets = node.putArray("owningOrderSheets");
        bag.plannedBag().owningOrderSheetKeys().forEach(key -> sheets.add(orderSheetNode(key)));
        node.put("outboundPhysicalToteId", bag.outboundPhysicalToteId().value());
        return node;
    }

    private ObjectNode occupancyNode(DspFullDayOccupancySample sample) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        putDuration(node, "elapsedSimulationTime", sample.elapsedSimulationTime());
        node.put("businessDateTime", sample.businessDateTime().toString());
        node.put("phase", sample.phase().name());
        node.put("osrOccupancy", sample.osrOccupancy());
        node.put("osrCapacity", sample.osrCapacity());
        node.put("osrLowWaterMark", sample.osrLowWaterMark());
        node.put("upstreamWaitingCount", sample.upstreamWaitingCount());
        node.put("admittedInboundToteCount", sample.admittedInboundToteCount());
        node.put("departedInboundToteCount", sample.departedInboundToteCount());
        node.put("closedOutboundToteCount", sample.closedOutboundToteCount());
        node.put("allocatedBagCount", sample.allocatedBagCount());
        node.put("osrNetFlow", sample.osrNetFlow());
        ObjectNode owners = node.putObject("activeLineOwners");
        sample.activeLineOwners().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(value -> value.value())))
                .forEach(entry -> optionalString(owners, entry.getKey().value(), entry.getValue()));
        return node;
    }

    private ObjectNode infeasibilityNode(DspFullDayMetricsSnapshot.ElasticInfeasibilityEvent event) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        putDuration(node, "elapsedSimulationTime", event.elapsedSimulationTime());
        node.put("serviceCentreId", event.serviceCentreId());
        node.put("type", event.type().name());
        node.put("detail", event.detail());
        return node;
    }

    private ObjectNode issueNode(P2pElasticAllocationIssue issue) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("serviceCentreId", issue.serviceCentreId());
        node.put("type", issue.type().name());
        node.put("detail", issue.detail());
        return node;
    }

    private ObjectNode currentNode(DspFullDayAnalysisRuntimeSnapshot runtime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("state", runtime.state().name());
        node.put("closed", runtime.closed());
        node.set("supply", supplyNode(runtime));
        node.set("osr", osrNode(runtime));
        node.set("av02", av02Node(runtime));
        node.set("scheduler", schedulerNode(runtime));
        node.set("operationalRelease", operationalReleaseNode(runtime.operationalRelease()));
        node.set("transport", transportNode(runtime));
        node.set("stationOwnership", stationNode(runtime));
        node.set("elastic", elasticNode(runtime));
        ArrayNode lines = node.putArray("p2pLines");
        runtime.p2pLines().forEach(value -> lines.add(headlessLineNode(value)));
        return node;
    }

    private ObjectNode supplyNode(DspFullDayAnalysisRuntimeSnapshot runtime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        var supply = runtime.supply();
        node.put("policyId", supply.policyId());
        node.put("lowWaterMark", supply.lowWaterMark());
        node.put("capacity", supply.osrCapacity());
        node.put("occupancy", supply.osrOccupancy());
        optionalString(node, "activeInboundServiceCentreId", supply.activeInboundServiceCentreId());
        optionalDuration(node, "nextPhysicalAdmissionElapsedTime", supply.nextPhysicalAdmissionElapsedTime());
        node.put("admittedAfterStartupCount", supply.admittedAfterStartupCount());
        ArrayNode centres = node.putArray("serviceCentres");
        supply.serviceCentres().forEach(value -> centres.add(supplyCentreNode(value)));
        return node;
    }

    private ObjectNode supplyCentreNode(ServiceCentreSupplySnapshot value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("serviceCentreId", value.serviceCentreId());
        node.put("priority", value.priority());
        node.put("authorizationState", value.authorizationState().name());
        optionalDuration(node, "authorizationElapsedTime", value.authorizationElapsedTime());
        node.put("physicalManifestCount", value.physicalManifestCount());
        node.put("preloadedCount", value.preloadedCount());
        node.put("admittedAfterStartupCount", value.admittedAfterStartupCount());
        node.put("upstreamWaitingCount", value.upstreamWaitingCount());
        ArrayNode totes = node.putArray("physicalTotes");
        value.physicalTotes().forEach(tote -> totes.addObject()
                .put("physicalToteId", tote.physicalToteId().value())
                .put("orderId", tote.orderSheetKey().orderId())
                .put("sheetNumber", tote.orderSheetKey().sheetNumber())
                .put("orderType", tote.orderType().name())
                .put("sourceSequenceNumber", tote.sourceSequenceNumber())
                .put("state", tote.state().name()));
        return node;
    }

    private ObjectNode osrNode(DspFullDayAnalysisRuntimeSnapshot runtime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        var osr = runtime.osr();
        node.put("capacity", osr.capacity());
        node.put("occupancy", osr.occupancy());
        node.put("remainingCapacity", osr.remainingCapacity());
        ArrayNode stored = node.putArray("storedTotes");
        osr.storedTotes().forEach(value -> stored.addObject()
                .put("physicalToteId", value.physicalToteId().value())
                .put("serviceCentreId", value.serviceCentreId())
                .put("orderId", value.orderSheetKey().orderId())
                .put("sheetNumber", value.orderSheetKey().sheetNumber()));
        node.put("departedToteCount", osr.departedTotes().size());
        return node;
    }

    private ObjectNode av02Node(DspFullDayAnalysisRuntimeSnapshot runtime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        var av02 = runtime.av02();
        node.put("capacity", av02.capacity());
        node.put("occupancy", av02.occupancy());
        node.put("remainingCapacity", av02.remainingCapacity());
        ArrayNode waiting = node.putArray("waitingTotes");
        av02.waitingTotes().forEach(value -> waiting.add(av02ToteNode(value)));
        node.put("departedToteCount", av02.departedTotes().size());
        return node;
    }

    private ObjectNode av02ToteNode(Av02AllocatedTote value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("physicalToteId", value.physicalToteId().value());
        node.put("serviceCentreId", value.serviceCentreId());
        node.put("pharmacyId", value.pharmacyId());
        node.put("orderId", value.orderSheetKey().orderId());
        node.put("sheetNumber", value.orderSheetKey().sheetNumber());
        node.put("sourceSequenceNumber", value.sourceSequenceNumber());
        node.put("state", value.physicalTote().state().name());
        return node;
    }

    private ObjectNode schedulerNode(DspFullDayAnalysisRuntimeSnapshot runtime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        var scheduler = runtime.scheduler();
        optionalString(node, "activeServiceCentreId", scheduler.activeServiceCentreId());
        node.put("preparedLineCount", scheduler.preparedLineKeys().size());
        ArrayNode orders = node.putArray("orderStates");
        scheduler.orderStates().forEach(value -> orders.add(orderStateNode(value)));
        return node;
    }

    private ObjectNode orderStateNode(DspSchedulerOrderState value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        var order = value.order();
        node.put("orderId", order.orderId());
        node.put("notionalToteId", order.notionalToteId());
        node.put("serviceCentreId", order.serviceCentreId());
        node.put("sheetNumber", order.sheetNumber());
        node.put("orderType", order.orderType().name());
        node.put("orderPriority", order.orderPriority());
        node.put("sequenceNumber", order.sequenceNumber());
        node.put("status", value.status().name());
        return node;
    }

    private ObjectNode operationalReleaseNode(DspOperationalReleaseControllerSnapshot value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("evaluationMode", value.evaluationMode());
        node.put("evaluationInFlight", value.evaluationInFlight());
        value.lastCompletedEvaluationSequence().ifPresent(sequence -> node.put("lastCompletedEvaluationSequence", sequence));
        value.lastCompletedEvaluationSequence().ifPresentOrElse(
                ignored -> { },
                () -> node.set("lastCompletedEvaluationSequence", NullNode.instance));
        value.lastEvaluation().ifPresentOrElse(
                evaluation -> {
                    node.put("blockedCandidateCount", evaluation.blockedCandidates().size());
                    evaluation.releaseDecision().ifPresentOrElse(
                            decision -> node.put("releaseDecision", decision.command().physicalToteId().value()),
                            () -> node.set("releaseDecision", NullNode.instance));
                },
                () -> {
                    node.put("blockedCandidateCount", 0);
                    node.set("releaseDecision", NullNode.instance);
                });
        value.lastCommandApplicationResult().ifPresentOrElse(
                result -> {
                    node.put("commandApplied", result.applied());
                    node.put("commandDeferred", result.deferred());
                    node.put("commandReason", result.reason());
                },
                () -> node.set("commandApplication", NullNode.instance));
        optionalString(node, "lastPhysicalToteId", value.lastPhysicalToteId().map(PhysicalToteId::value));
        return node;
    }

    private ObjectNode transportNode(DspFullDayAnalysisRuntimeSnapshot runtime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        WarehouseTransportIngressControllerSnapshot ingress = runtime.transportIngress();
        node.put("transportCapacity", ingress.transportCapacity());
        node.put("transportOccupancy", ingress.transportOccupancy());
        node.put("inFlightCapacity", ingress.inFlightCapacity());
        node.put("inFlightOccupancy", ingress.inFlightOccupancy());
        node.put("successfulIngressCount", ingress.successfulIngressCount());
        optionalString(node, "blockedPhysicalToteId", ingress.blockedPhysicalToteId().map(PhysicalToteId::value));
        node.put("blockedReason", ingress.blockedReason());
        WarehouseTransportInFlightSnapshot inFlight = runtime.transportInFlight();
        ArrayNode inFlightEntries = node.putArray("inFlight");
        inFlight.entries().forEach(entry -> inFlightEntries.addObject()
                .put("physicalToteId", entry.physicalToteId().value())
                .put("destination", destination(entry.destination()))
                .put("routeSegment", entry.currentRouteSegmentLabel())
                .put("motionState", entry.motionState().name())
                .put("arrivalPending", entry.arrivalPending()));
        OsrOutboundTransportQueueSnapshot outbound = runtime.outboundTransport();
        node.put("outboundQueueId", outbound.queueId());
        node.put("outboundQueueCapacity", outbound.capacity());
        node.put("outboundQueueOccupancy", outbound.occupancy());
        ArrayNode pending = node.putArray("pendingArrivals");
        WarehouseTransportArrivalControllerSnapshot arrivals = runtime.transportArrival();
        arrivals.pendingArrivals().forEach(entry -> pending.addObject()
                .put("physicalToteId", entry.physicalToteId().value())
                .put("destination", destination(entry.destination()))
                .put("terminalSensorId", entry.terminalSensorId()));
        node.put("successfulArrivalCount", arrivals.successfulArrivalCount());
        node.put("arrivalBlockedReason", arrivals.blockedReason());
        node.put("stationArrivalQueueCount", runtime.stationArrivals().stream()
                .mapToInt(StationRoutedToteArrivalQueueSnapshot::occupancy).sum());
        return node;
    }

    private ObjectNode stationNode(DspFullDayAnalysisRuntimeSnapshot runtime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        var station = runtime.stationProcessing();
        node.put("activeClaimCount", station.activeClaims().size());
        node.put("pendingDispositionCount", station.pendingDispositions().size());
        node.put("completedCount", station.completedCount());
        node.put("acknowledgedContinuationCount", station.acknowledgedContinuationCount());
        node.put("acknowledgedConsumeCount", station.acknowledgedConsumeCount());
        ArrayNode claims = node.putArray("activeClaims");
        station.activeClaims().forEach(claim -> claims.addObject()
                .put("physicalToteId", claim.physicalToteId().value())
                .put("destination", destination(claim.destination()))
                .put("claimedAt", claim.claimedAt().toString()));
        ArrayNode dispositions = node.putArray("pendingDispositions");
        station.pendingDispositions().forEach(value -> dispositions.addObject()
                .put("physicalToteId", value.physicalToteId().value())
                .put("destination", destination(value.destination()))
                .put("type", value.type().name())
                .put("claimedAt", value.claimedAt().toString())
                .put("completedAt", value.completedAt().toString()));
        node.put("continuationBlockedReason", runtime.continuation().blockedReason());
        node.put("continuationCount", runtime.continuation().continuedCount());
        node.put("consumedAcknowledgementCount", runtime.continuation().consumedAcknowledgementCount());
        return node;
    }

    private ObjectNode elasticNode(DspFullDayAnalysisRuntimeSnapshot runtime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        var allocation = runtime.elastic().allocation();
        node.put("profileId", allocation.profileId());
        node.put("calibrationStatus", allocation.calibrationStatus().name());
        node.put("evaluatedAt", allocation.evaluatedAt().toString());
        node.put("maximumConcurrentServiceCentres", allocation.maximumConcurrentServiceCentres());
        ArrayNode issues = node.putArray("issues");
        allocation.issues().forEach(issue -> issues.add(issueNode(issue)));
        return node;
    }

    private ObjectNode headlessLineNode(DspHeadlessP2pLineRuntimeSnapshot value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("lineId", value.lineDefinition().lineId().value());
        node.put("destination", destination(value.lineDefinition().destination()));
        node.put("closed", value.closed());
        node.put("processingDrained", value.processingDrained());
        node.put("quiescent", value.quiescent());
        node.put("prlCount", value.prlStatesById().size());
        node.put("completedBagCorrelationCount", value.completedBagCorrelationIds().size());
        node.set("activity", activityNode(value.activity()));
        node.set("outboundAllocation", outboundAllocationNode(value.outboundAllocation()));
        return node;
    }

    private ObjectNode outboundAllocationNode(OutboundAllocationSnapshot value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        ArrayNode open = node.putArray("openTotes");
        value.openTotesByLine().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(line -> line.value())))
                .forEach(entry -> open.add(outboundToteNode(entry.getValue())));
        ArrayNode closed = node.putArray("closedTotes");
        value.closedTotes().forEach(tote -> closed.add(outboundToteNode(tote)));
        node.put("allocatedBagCount", value.allocatedBags().size());
        return node;
    }

    private static String destination(online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination destination) {
        return destination.stationType().name() + ":" + destination.targetId();
    }

    private ObjectNode orderSheetNode(OrderSheetKey key) {
        return JsonNodeFactory.instance.objectNode()
                .put("orderId", key.orderId())
                .put("sheetNumber", key.sheetNumber());
    }

    private void putDuration(ObjectNode node, String fieldName, Duration value) {
        ObjectNode duration = node.putObject(fieldName);
        BigInteger totalNanos = BigInteger.valueOf(value.getSeconds())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(value.getNano()));
        duration.put("nanos", totalNanos);
        duration.put("iso", value.toString());
    }

    private void optionalDuration(ObjectNode node, String fieldName, Optional<Duration> value) {
        value.ifPresentOrElse(
                duration -> putDuration(node, fieldName, duration),
                () -> node.set(fieldName, NullNode.instance));
    }

    private void optionalDateTime(ObjectNode node, String fieldName, Optional<LocalDateTime> value) {
        value.ifPresentOrElse(
                dateTime -> node.put(fieldName, dateTime.toString()),
                () -> node.set(fieldName, NullNode.instance));
    }

    private void optionalString(ObjectNode node, String fieldName, Optional<String> value) {
        value.ifPresentOrElse(
                string -> node.put(fieldName, string),
                () -> node.set(fieldName, NullNode.instance));
    }

    private void addStrings(ObjectNode node, String fieldName, List<String> values) {
        ArrayNode array = node.putArray(fieldName);
        values.forEach(array::add);
    }
}
