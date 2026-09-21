package online.davisfamily.warehouse.sim.dsp.analysis.input;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.io.DspRetainedInputLine;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

/** Pure full-day correlation validation and executable-input projection. */
public final class DspFullDayInputPreflight {
    @FunctionalInterface
    interface GroupValidationHook {
        void validate(PreparedLineKey key);
    }

    private final GroupValidationHook groupValidationHook;

    public DspFullDayInputPreflight() {
        this(key -> {
        });
    }

    DspFullDayInputPreflight(GroupValidationHook groupValidationHook) {
        if (groupValidationHook == null) {
            throw new IllegalArgumentException("groupValidationHook must not be null");
        }
        this.groupValidationHook = groupValidationHook;
    }

    public DspFullDayInputProjection project(
            LoadedDspData assembledData,
            DspInputRejectionCatalog loadRejections) {
        if (assembledData == null) {
            throw new IllegalArgumentException("assembledData must not be null");
        }
        if (loadRejections == null) {
            throw new IllegalArgumentException("loadRejections must not be null");
        }

        List<NotionalToteOrder> reportableOrders = List.copyOf(assembledData.orders());
        Map<String, ProductMasterRecord> productsById = indexProducts(assembledData.products());
        Map<OrderSheetKey, NotionalToteOrder> ordersBySheet = indexOrders(reportableOrders);
        Map<OrderSheetKey, Map<String, DspOrderItem>> orderLinesBySheet = indexOrderLines(
                reportableOrders);
        Map<LineIdentity, DspRetainedInputLine> retainedLinesByIdentity = indexRetainedLines(
                assembledData.retainedInputLines());
        validateRetainedLineCoverage(reportableOrders, orderLinesBySheet, retainedLinesByIdentity);
        Map<OrderSheetKey, List<InboundToteManifest>> manifestsBySheet = indexManifests(
                assembledData.inboundToteManifests());

        Map<PreparedLineKey, List<Participant>> sourceParticipantsByKey = new LinkedHashMap<>();
        Map<PreparedLineKey, List<Participant>> fulfilmentParticipantsByKey = new LinkedHashMap<>();
        Map<LineIdentity, Participant> participantsByIdentity = new LinkedHashMap<>();
        for (DspRetainedInputLine retainedLine : assembledData.retainedInputLines()) {
            NotionalToteOrder order = ordersBySheet.get(retainedLine.sourceOrderSheetKey());
            if (order == null) {
                throw new IllegalArgumentException(
                        "Retained line has no matching order sheet: "
                                + retainedLine.sourceOrderSheetKey());
            }
            if (retainedLine.sourceOrderType() != order.orderType()) {
                throw new IllegalArgumentException(
                        "Retained line order type does not match order sheet: "
                                + retainedLine.sourceOrderSheetKey());
            }

            if (order.orderType() == OrderType.ADAPTED) {
                PreparedLineKey key = PreparedLineKey.forPreparedLine(retainedLine.orderItem());
                Participant participant = new Participant(retainedLine, order, false, key);
                sourceParticipantsByKey.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(participant);
                participantsByIdentity.put(lineIdentity(retainedLine), participant);
            } else {
                PreparedLineKey key = PreparedLineKey.forDispatchLine(
                        order,
                        retainedLine.orderItem());
                Participant participant = new Participant(retainedLine, order, true, key);
                participantsByIdentity.put(lineIdentity(retainedLine), participant);
                if (retainedLine.orderItem().lineType() == DspOrderLineType.ADAPTED) {
                    fulfilmentParticipantsByKey.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add(participant);
                }
            }
        }
        Map<PreparedLineKey, DependencyGroup> dependencyGroups = new LinkedHashMap<>();
        for (DspRetainedInputLine retainedLine : assembledData.retainedInputLines()) {
            NotionalToteOrder order = ordersBySheet.get(retainedLine.sourceOrderSheetKey());
            Participant participant = participantsByIdentity.get(lineIdentity(retainedLine));
            if (order.orderType() == OrderType.ADAPTED) {
                dependencyGroups.computeIfAbsent(participant.key(), DependencyGroup::new)
                        .addSource(participant);
                continue;
            }

            if (fulfilmentParticipantsByKey.containsKey(participant.key())
                    || sourceParticipantsByKey.containsKey(participant.key())) {
                dependencyGroups.computeIfAbsent(participant.key(), DependencyGroup::new)
                        .addFulfilment(participant);
            }
        }

        Map<LineIdentity, DspRejectedLine> rejectedLinesByIdentity = new LinkedHashMap<>();
        for (DependencyGroup dependencyGroup : dependencyGroups.values()) {
            classifyGroup(dependencyGroup, rejectedLinesByIdentity);
        }

        List<DspRejectedLine> rejectedLines = new ArrayList<>();
        for (DspRetainedInputLine retainedLine : assembledData.retainedInputLines()) {
            DspRejectedLine rejectedLine = rejectedLinesByIdentity.get(lineIdentity(retainedLine));
            if (rejectedLine != null) {
                rejectedLines.add(rejectedLine);
            }
        }

        Set<LineIdentity> unresolvedPairLines = unresolvedPairLines(
                dependencyGroups,
                productsById);
        Set<LineIdentity> rejectedLineIdentities = new LinkedHashSet<>(
                rejectedLinesByIdentity.keySet());
        Map<OrderSheetKey, List<DspOrderItem>> executableItemsBySheet = new LinkedHashMap<>();
        Set<LineIdentity> executableLineIdentities = new LinkedHashSet<>();
        List<NotionalToteOrder> executableOrders = new ArrayList<>();
        for (NotionalToteOrder order : reportableOrders) {
            List<DspOrderItem> executableItems = new ArrayList<>();
            for (DspOrderItem item : order.items()) {
                LineIdentity identity = new LineIdentity(order.orderSheetKey(), item.lineReference());
                if (rejectedLineIdentities.contains(identity)
                        || unresolvedPairLines.contains(identity)
                        || !productsById.containsKey(item.productId())) {
                    continue;
                }
                executableItems.add(item);
                executableLineIdentities.add(identity);
            }
            if (!executableItems.isEmpty()) {
                List<DspOrderItem> immutableItems = List.copyOf(executableItems);
                NotionalToteOrder executableOrder = immutableItems.size() == order.items().size()
                        ? order
                        : withItems(order, immutableItems);
                executableOrders.add(executableOrder);
                executableItemsBySheet.put(order.orderSheetKey(), immutableItems);
            }
        }

        List<InboundToteManifest> executableManifests = new ArrayList<>();
        for (InboundToteManifest manifest : assembledData.inboundToteManifests()) {
            if (!manifestsBySheet.containsKey(manifest.orderSheetKey())) {
                throw new IllegalArgumentException(
                        "Inbound manifest index lost source order sheet: "
                                + manifest.orderSheetKey());
            }
            List<DspOrderItem> executableOrderItems = executableItemsBySheet.get(
                    manifest.orderSheetKey());
            if (executableOrderItems == null) {
                continue;
            }
            Set<String> executableLineReferences = new LinkedHashSet<>();
            for (DspOrderItem item : executableOrderItems) {
                executableLineReferences.add(item.lineReference());
            }
            List<DspOrderItem> retainedManifestItems = manifest.items().stream()
                    .filter(item -> executableLineReferences.contains(item.lineReference()))
                    .toList();
            if (!retainedManifestItems.isEmpty()) {
                executableManifests.add(retainedManifestItems.size() == manifest.items().size()
                        ? manifest
                        : manifest.withItems(retainedManifestItems));
            }
        }

        List<DspOrderItem> executablePreparedLines = new ArrayList<>();
        Set<PreparedLineKey> executablePreparedLineKeys = new LinkedHashSet<>();
        for (NotionalToteOrder order : executableOrders) {
            if (order.orderType() != OrderType.ADAPTED) {
                continue;
            }
            for (DspOrderItem line : order.items()) {
                executablePreparedLines.add(line);
                executablePreparedLineKeys.add(PreparedLineKey.forPreparedLine(line));
            }
        }
        Set<PreparedLineKey> executableStartupReadyKeys = new LinkedHashSet<>();
        for (PreparedLineKey startupReadyKey : assembledData.startupReadyPreparedLineKeys()) {
            if (executablePreparedLineKeys.contains(startupReadyKey)) {
                executableStartupReadyKeys.add(startupReadyKey);
            }
        }

        List<DspRetainedInputLine> executableRetainedLines = assembledData.retainedInputLines()
                .stream()
                .filter(retainedLine -> executableLineIdentities.contains(lineIdentity(retainedLine)))
                .toList();
        LoadedDspData executableData = new LoadedDspData(
                assembledData.products(),
                executableOrders,
                executablePreparedLines,
                executablePreparedLineKeys,
                executableStartupReadyKeys,
                executableManifests,
                assembledData.report(),
                executableRetainedLines);
        DspInputRejectionCatalog rejectionCatalog = new DspInputRejectionCatalog(
                rejectedLines,
                loadRejections.rejectedMessages());
        return new DspFullDayInputProjection(executableData, reportableOrders, rejectionCatalog);
    }

    private void classifyGroup(
            DependencyGroup dependencyGroup,
            Map<LineIdentity, DspRejectedLine> rejectedLinesByIdentity) {
        Map<LineIdentity, DspRejectedLine> groupRejections = new LinkedHashMap<>();
        try {
            groupValidationHook.validate(dependencyGroup.key());
            List<LineRejection> decisions = namedDecisions(dependencyGroup);
            for (LineRejection decision : decisions) {
                groupRejections.put(
                        decision.participant().identity(),
                        rejectedLine(
                                decision.participant(),
                                dependencyGroup,
                                decision.reason(),
                                decision.diagnostic(),
                                Optional.empty(),
                                List.of()));
            }
        } catch (RuntimeException exception) {
            groupRejections.clear();
            String detail = exception.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = exception.toString();
            }
            String diagnostic = "REQUIRES INVESTIGATION: " + detail.trim();
            List<String> stackTraceLines = stackTraceLines(exception);
            for (Participant participant : dependencyGroup.allParticipants()) {
                groupRejections.put(
                        participant.identity(),
                        rejectedLine(
                                participant,
                                dependencyGroup,
                                DspInputRejectionReason.UNCLASSIFIED_CORRELATION_ANOMALY,
                                diagnostic,
                                Optional.of(exception.getClass().getName()),
                                stackTraceLines));
            }
        }
        for (Map.Entry<LineIdentity, DspRejectedLine> entry : groupRejections.entrySet()) {
            if (rejectedLinesByIdentity.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException(
                        "A retained input line was classified more than once: " + entry.getKey());
            }
        }
    }

    private static List<LineRejection> namedDecisions(DependencyGroup dependencyGroup) {
        int sourceCount = dependencyGroup.sources().size();
        int fulfilmentCount = dependencyGroup.fulfilments().size();
        String cardinality = "ADAPTED dependency key " + dependencyGroup.key()
                + " has " + sourceCount + " source participant(s) and "
                + fulfilmentCount + " fulfilment participant(s)";

        if (sourceCount > 1) {
            return decisionsFor(
                    dependencyGroup.allParticipants(),
                    DspInputRejectionReason.DUPLICATE_ADAPTED_SOURCE,
                    cardinality);
        }
        if (fulfilmentCount > 1) {
            return decisionsFor(
                    dependencyGroup.allParticipants(),
                    DspInputRejectionReason.DUPLICATE_ADAPTED_FULFILMENT,
                    cardinality);
        }
        if (sourceCount == 0) {
            return decisionsFor(
                    dependencyGroup.fulfilments(),
                    DspInputRejectionReason.MISSING_ADAPTED_SOURCE,
                    "No ADAPTED source participant exists for " + dependencyGroup.key());
        }
        if (fulfilmentCount == 0) {
            return decisionsFor(
                    dependencyGroup.sources(),
                    DspInputRejectionReason.MISSING_ADAPTED_FULFILMENT,
                    "No ADAPTED fulfilment alias exists for " + dependencyGroup.key());
        }

        Participant source = dependencyGroup.sources().getFirst();
        Participant fulfilment = dependencyGroup.fulfilments().getFirst();
        List<String> mismatches = mismatches(source, fulfilment);
        if (mismatches.isEmpty()) {
            return List.of();
        }
        return decisionsFor(
                List.of(source, fulfilment),
                DspInputRejectionReason.ADAPTED_SOURCE_FULFILMENT_MISMATCH,
                "ADAPTED source/fulfilment mismatch for " + dependencyGroup.key()
                        + ": " + String.join(", ", mismatches));
    }

    private static List<LineRejection> decisionsFor(
            List<Participant> participants,
            DspInputRejectionReason reason,
            String diagnostic) {
        List<LineRejection> decisions = new ArrayList<>();
        for (Participant participant : participants) {
            decisions.add(new LineRejection(participant, reason, diagnostic));
        }
        return decisions;
    }

    private static List<String> mismatches(Participant source, Participant fulfilment) {
        DspOrderItem sourceLine = source.line();
        DspOrderItem fulfilmentLine = fulfilment.line();
        List<String> mismatches = new ArrayList<>();
        if (!sourceLine.productId().equals(fulfilmentLine.productId())) {
            mismatches.add("productId");
        }
        if (!sourceLine.pharmacyId().equals(fulfilmentLine.pharmacyId())) {
            mismatches.add("pharmacyId");
        }
        if (!sourceLine.patientId().equals(fulfilmentLine.patientId())) {
            mismatches.add("patientId");
        }
        if (!sourceLine.prescriptionId().equals(fulfilmentLine.prescriptionId())) {
            mismatches.add("prescriptionId");
        }
        if (sourceLine.lineType() != fulfilmentLine.lineType()) {
            mismatches.add("lineType");
        }
        if (!source.order().serviceCentreId().equals(fulfilment.order().serviceCentreId())) {
            mismatches.add("serviceCentreId");
        }
        return mismatches;
    }

    private static DspRejectedLine rejectedLine(
            Participant participant,
            DependencyGroup dependencyGroup,
            DspInputRejectionReason reason,
            String diagnostic,
            Optional<String> exceptionClassName,
            List<String> stackTraceLines) {
        return new DspRejectedLine(
                participant.line(),
                participant.order().orderSheetKey(),
                participant.order().orderType(),
                targetOrderSheetKey(participant, dependencyGroup),
                participant.retainedLine().sourceMessageEncounterIndex(),
                participant.retainedLine().sourceLineIndex(),
                Optional.of(participant.key()),
                reason,
                diagnostic,
                exceptionClassName,
                stackTraceLines);
    }

    private static Optional<OrderSheetKey> targetOrderSheetKey(
            Participant participant,
            DependencyGroup dependencyGroup) {
        if (participant.fulfilment()) {
            return Optional.of(participant.order().orderSheetKey());
        }
        if (dependencyGroup.fulfilments().size() == 1) {
            return Optional.of(dependencyGroup.fulfilments().getFirst().order().orderSheetKey());
        }
        return Optional.empty();
    }

    private static Set<LineIdentity> unresolvedPairLines(
            Map<PreparedLineKey, DependencyGroup> dependencyGroups,
            Map<String, ProductMasterRecord> productsById) {
        Set<LineIdentity> unresolvedPairLines = new LinkedHashSet<>();
        for (DependencyGroup dependencyGroup : dependencyGroups.values()) {
            if (dependencyGroup.sources().size() != 1
                    || dependencyGroup.fulfilments().size() != 1) {
                continue;
            }
            Participant source = dependencyGroup.sources().getFirst();
            Participant fulfilment = dependencyGroup.fulfilments().getFirst();
            if (!productsById.containsKey(source.line().productId())
                    || !productsById.containsKey(fulfilment.line().productId())) {
                unresolvedPairLines.add(source.identity());
                unresolvedPairLines.add(fulfilment.identity());
            }
        }
        return unresolvedPairLines;
    }

    private static Map<String, ProductMasterRecord> indexProducts(
            List<ProductMasterRecord> products) {
        Map<String, ProductMasterRecord> productsById = new LinkedHashMap<>();
        for (ProductMasterRecord product : products) {
            if (product == null) {
                throw new IllegalArgumentException("products must not contain null");
            }
            if (productsById.putIfAbsent(product.productId(), product) != null) {
                throw new IllegalArgumentException("Duplicate productId: " + product.productId());
            }
        }
        return productsById;
    }

    private static Map<OrderSheetKey, NotionalToteOrder> indexOrders(
            List<NotionalToteOrder> orders) {
        Map<OrderSheetKey, NotionalToteOrder> ordersBySheet = new LinkedHashMap<>();
        for (NotionalToteOrder order : orders) {
            if (order == null) {
                throw new IllegalArgumentException("orders must not contain null");
            }
            if (ordersBySheet.putIfAbsent(order.orderSheetKey(), order) != null) {
                throw new IllegalArgumentException("Duplicate order sheet: " + order.orderSheetKey());
            }
        }
        return ordersBySheet;
    }

    private static Map<OrderSheetKey, Map<String, DspOrderItem>> indexOrderLines(
            List<NotionalToteOrder> orders) {
        Map<OrderSheetKey, Map<String, DspOrderItem>> orderLinesBySheet = new LinkedHashMap<>();
        for (NotionalToteOrder order : orders) {
            Map<String, DspOrderItem> lines = new LinkedHashMap<>();
            for (DspOrderItem line : order.items()) {
                if (line.lineType() == DspOrderLineType.MANUAL) {
                    throw new IllegalArgumentException(
                            "MANUAL line is outside reportable input: " + order.orderSheetKey());
                }
                if (lines.putIfAbsent(line.lineReference(), line) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate line reference " + line.lineReference()
                                    + " for order sheet " + order.orderSheetKey());
                }
            }
            orderLinesBySheet.put(order.orderSheetKey(), lines);
        }
        return orderLinesBySheet;
    }

    private static Map<LineIdentity, DspRetainedInputLine> indexRetainedLines(
            List<DspRetainedInputLine> retainedLines) {
        Map<LineIdentity, DspRetainedInputLine> retainedLinesByIdentity = new LinkedHashMap<>();
        for (DspRetainedInputLine retainedLine : retainedLines) {
            if (retainedLine.orderItem().lineType() == DspOrderLineType.MANUAL) {
                throw new IllegalArgumentException(
                        "MANUAL line is outside retained input metadata: "
                                + retainedLine.sourceOrderSheetKey());
            }
            LineIdentity identity = lineIdentity(retainedLine);
            if (retainedLinesByIdentity.putIfAbsent(identity, retainedLine) != null) {
                throw new IllegalArgumentException(
                        "Duplicate retained line identity: " + identity);
            }
        }
        return retainedLinesByIdentity;
    }

    private static void validateRetainedLineCoverage(
            List<NotionalToteOrder> orders,
            Map<OrderSheetKey, Map<String, DspOrderItem>> orderLinesBySheet,
            Map<LineIdentity, DspRetainedInputLine> retainedLinesByIdentity) {
        for (NotionalToteOrder order : orders) {
            Map<String, DspOrderItem> orderLines = orderLinesBySheet.get(order.orderSheetKey());
            for (DspOrderItem line : order.items()) {
                LineIdentity identity = new LineIdentity(order.orderSheetKey(), line.lineReference());
                DspRetainedInputLine retainedLine = retainedLinesByIdentity.get(identity);
                if (retainedLine == null || !retainedLine.orderItem().equals(line)) {
                    throw new IllegalArgumentException(
                            "Missing or mismatched retained metadata for " + identity);
                }
                if (retainedLine.sourceOrderType() != order.orderType()
                        || !retainedLine.sourceOrderSheetKey().equals(order.orderSheetKey())
                        || orderLines.get(line.lineReference()) == null) {
                    throw new IllegalArgumentException(
                            "Retained metadata source identity does not match " + identity);
                }
            }
        }
        for (DspRetainedInputLine retainedLine : retainedLinesByIdentity.values()) {
            Map<String, DspOrderItem> orderLines = orderLinesBySheet.get(
                    retainedLine.sourceOrderSheetKey());
            if (orderLines == null
                    || !retainedLine.orderItem().equals(
                            orderLines.get(retainedLine.orderItem().lineReference()))) {
                throw new IllegalArgumentException(
                        "Retained metadata has no matching reportable line: "
                                + lineIdentity(retainedLine));
            }
        }
    }

    private static Map<OrderSheetKey, List<InboundToteManifest>> indexManifests(
            List<InboundToteManifest> manifests) {
        Map<OrderSheetKey, List<InboundToteManifest>> manifestsBySheet = new LinkedHashMap<>();
        for (InboundToteManifest manifest : manifests) {
            if (manifest == null) {
                throw new IllegalArgumentException("inboundToteManifests must not contain null");
            }
            manifestsBySheet.computeIfAbsent(manifest.orderSheetKey(), ignored -> new ArrayList<>())
                    .add(manifest);
        }
        manifestsBySheet.replaceAll((orderSheetKey, values) -> List.copyOf(values));
        return manifestsBySheet;
    }

    private static NotionalToteOrder withItems(
            NotionalToteOrder order,
            List<DspOrderItem> items) {
        return new NotionalToteOrder(
                order.orderId(),
                order.notionalToteId(),
                order.serviceCentreId(),
                order.sheetNumber(),
                order.orderType(),
                items,
                order.orderPriority(),
                order.sequenceNumber());
    }

    private static LineIdentity lineIdentity(DspRetainedInputLine retainedLine) {
        return new LineIdentity(
                retainedLine.sourceOrderSheetKey(),
                retainedLine.orderItem().lineReference());
    }

    private static List<String> stackTraceLines(RuntimeException exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString().lines().toList();
    }

    private record LineIdentity(OrderSheetKey orderSheetKey, String lineReference) {
    }

    private record Participant(
            DspRetainedInputLine retainedLine,
            NotionalToteOrder order,
            boolean fulfilment,
            PreparedLineKey key) {

        private LineIdentity identity() {
            return new LineIdentity(order.orderSheetKey(), line().lineReference());
        }

        private DspOrderItem line() {
            return retainedLine.orderItem();
        }
    }

    private record LineRejection(
            Participant participant,
            DspInputRejectionReason reason,
            String diagnostic) {
    }

    private static final class DependencyGroup {
        private final PreparedLineKey key;
        private final List<Participant> sources = new ArrayList<>();
        private final List<Participant> fulfilments = new ArrayList<>();

        private DependencyGroup(PreparedLineKey key) {
            this.key = key;
        }

        private PreparedLineKey key() {
            return key;
        }

        private List<Participant> sources() {
            return sources;
        }

        private List<Participant> fulfilments() {
            return fulfilments;
        }

        private void addSource(Participant participant) {
            sources.add(participant);
        }

        private void addFulfilment(Participant participant) {
            fulfilments.add(participant);
        }

        private List<Participant> allParticipants() {
            List<Participant> participants = new ArrayList<>(sources.size() + fulfilments.size());
            participants.addAll(sources);
            participants.addAll(fulfilments);
            return participants;
        }
    }
}
