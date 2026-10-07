package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationConfig;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadCostConfig;
import online.davisfamily.warehouse.sim.dsp.supply.FixedIntervalInboundToteArrivalPolicy;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplyConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;

class DspUncalibratedFullDayProfileTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldKeepLegacyBenchConstructionAndValidateSeparateDurationsAndPositions() {
        var legacy = new DspUncalibratedFullDayProfile.AdaptingBenchDefinition(" b1 ", 60d);
        assertEquals(new DspUncalibratedFullDayProfile.AdaptingBenchDefinition("b1", 60d, 60d, 1), legacy);
        assertEquals(60d, legacy.processingDurationSeconds());
        assertEquals("b1", legacy.benchId().value());
        var zero = new DspUncalibratedFullDayProfile.AdaptingBenchDefinition("b1", 0d);
        assertEquals(0d, zero.storeDurationSeconds());
        assertEquals(0d, zero.collectDurationSeconds());
        for (double invalid : new double[] {-1d, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DspUncalibratedFullDayProfile.AdaptingBenchDefinition("b1", invalid));
            assertThrows(IllegalArgumentException.class,
                    () -> new DspUncalibratedFullDayProfile.AdaptingBenchDefinition("b1", invalid, 10d, 3));
            assertThrows(IllegalArgumentException.class,
                    () -> new DspUncalibratedFullDayProfile.AdaptingBenchDefinition("b1", 60d, invalid, 3));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new DspUncalibratedFullDayProfile.AdaptingBenchDefinition(" ", 60d));
        assertThrows(IllegalArgumentException.class,
                () -> new DspUncalibratedFullDayProfile.AdaptingBenchDefinition("b1", 60d, 10d, 0));
    }

    @Test
    void shouldCopyAndNormalizeOverrideIdsOnceAndReuseTheirImmutablePublication() {
        List<String> supplied = new ArrayList<>(List.of(" b1 ", "b2"));
        var overrides = new DspFullDayStationProcessingOverrides(
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalInt.empty(), OptionalInt.empty(), Optional.of(supplied));
        List<String> published = overrides.benchIds().orElseThrow();
        supplied.clear();
        assertEquals(List.of("b1", "b2"), published);
        assertSame(published, overrides.benchIds().orElseThrow());
        assertSame(DspFullDayStationProcessingOverrides.empty(), DspFullDayStationProcessingOverrides.empty());
        assertThrows(UnsupportedOperationException.class, published::clear);
    }

    @Test
    void shouldExposeTheExplicitUncalibratedProfileAndProductionShape() {
        DspUncalibratedFullDayProfile profile = profile();

        assertEquals("DEADLINE_AWARE_ELASTIC_STICKY_LEASES", profile.profileId());
        assertEquals("UNCALIBRATED", profile.timingCalibrationStatus());
        assertEquals("P2P_OUTPUT_CLOSED", profile.completionMilestone());
        assertEquals("PRIORITY_ORDERED_OSR_LOW_WATERMARK", profile.serviceCentreSupplyPolicyId());
        assertEquals("ORDER_WIDE_PREPARATION_READY_OVERLAP", profile.orderEligibilityPolicyId());
        assertEquals("ADAPTED_FIRST_PHARMACY_GROUPED_THEN_SOURCE_SEQUENCE",
                profile.candidateRankingPolicyId());
        assertEquals("PHARMACY_PURE_FIXED_BAG_CAPACITY", profile.outboundAllocationPolicyId());
        assertEquals(OPERATING_DATE, profile.operatingDate());
        assertEquals(Duration.ofMillis(50), profile.fixedStep());
        assertEquals(2_000, profile.maximumStepsPerAdvance());
        assertEquals(Duration.ofSeconds(60), profile.metricSampleInterval());
        assertEquals(5, profile.p2pLineDefinitions().size());
        assertEquals(31, profile.prlCountPerLine());
        assertEquals(Duration.ofHours(1), profile.p2pElasticAllocationConfig().downstreamHandlingDuration());
        assertEquals(1, profile.timetable().find("104").orElseThrow().priority() - 998);
        assertEquals(1, profile.timetable().find("109").orElseThrow().trunkerDepartureTime().dayOffset());
        assertEquals(List.of(new DspUncalibratedFullDayProfile.AdaptingBenchDefinition("adapting-bench-1", 60d)),
                profile.adaptingBenchDefinitions());
        assertEquals(4, profile.queueCapacities().adaptingQueueCapacityPerBench());
        assertEquals(60d, profile.thirdPartyAreaConfig().processingDurationSeconds());
        assertEquals(1, profile.thirdPartyAreaConfig().maxConcurrentVisits());
        assertEquals(16, profile.thirdPartyAreaConfig().waitingCapacity());
    }

    @Test
    void shouldKeepNestedValuesAndListsImmutable() {
        DspUncalibratedFullDayProfile original = profile();
        List<online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition> supplied =
                new ArrayList<>(original.p2pLineDefinitions());
        List<DspUncalibratedFullDayProfile.AdaptingBenchDefinition> suppliedBenches =
                new ArrayList<>(original.adaptingBenchDefinitions());
        DspUncalibratedFullDayProfile copy = new DspUncalibratedFullDayProfile(
                original.operatingDate(),
                original.osrInventoryConfig(),
                original.serviceCentreSupplyConfig(),
                original.inboundToteArrivalPolicy(),
                original.av02AllocationConfig(),
                original.p2pElasticAllocationConfig(),
                original.outboundToteConfig(),
                original.maximumPacksPerBag(),
                original.fixedStep(),
                original.maximumStepsPerAdvance(),
                original.metricSampleInterval(),
                original.routeSpeedUnitsPerSecond(),
                original.queueCapacities(),
                original.thirdPartyAreaConfig(),
                original.adaptingStorageConfig(),
                suppliedBenches,
                original.p2pPlaceholderDurations(),
                supplied,
                original.prlCountPerLine(),
                original.timetable());

        supplied.clear();
        suppliedBenches.clear();
        assertEquals(5, copy.p2pLineDefinitions().size());
        assertEquals(original.adaptingBenchDefinitions(), copy.adaptingBenchDefinitions());
        assertThrows(UnsupportedOperationException.class,
                () -> copy.adaptingBenchDefinitions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> copy.p2pLineDefinitions().clear());
        assertEquals(original, copy);
    }

    @Test
    void shouldRejectEveryStructuralProfileShape() {
        DspUncalibratedFullDayProfile valid = profile();
        assertThrows(IllegalArgumentException.class, () -> new DspUncalibratedFullDayProfile(
                null, valid.osrInventoryConfig(), valid.serviceCentreSupplyConfig(),
                valid.inboundToteArrivalPolicy(), valid.av02AllocationConfig(),
                valid.p2pElasticAllocationConfig(), valid.outboundToteConfig(),
                valid.maximumPacksPerBag(), valid.fixedStep(), valid.maximumStepsPerAdvance(),
                valid.metricSampleInterval(), valid.routeSpeedUnitsPerSecond(), valid.queueCapacities(),
                valid.thirdPartyAreaConfig(), valid.adaptingStorageConfig(), valid.adaptingBenchDefinitions(),
                valid.p2pPlaceholderDurations(), valid.p2pLineDefinitions(), valid.prlCountPerLine(),
                valid.timetable()));
        assertThrows(IllegalArgumentException.class, () -> new DspUncalibratedFullDayProfile(
                valid.operatingDate(), valid.osrInventoryConfig(), valid.serviceCentreSupplyConfig(),
                valid.inboundToteArrivalPolicy(), valid.av02AllocationConfig(),
                wrongLineCountConfig(), valid.outboundToteConfig(), valid.maximumPacksPerBag(),
                valid.fixedStep(), valid.maximumStepsPerAdvance(), valid.metricSampleInterval(),
                valid.routeSpeedUnitsPerSecond(), valid.queueCapacities(), valid.thirdPartyAreaConfig(),
                valid.adaptingStorageConfig(), valid.adaptingBenchDefinitions(), valid.p2pPlaceholderDurations(),
                valid.p2pLineDefinitions(), valid.prlCountPerLine(), valid.timetable()));
        assertThrows(IllegalArgumentException.class, () -> new DspUncalibratedFullDayProfile(
                valid.operatingDate(), valid.osrInventoryConfig(), valid.serviceCentreSupplyConfig(),
                valid.inboundToteArrivalPolicy(), valid.av02AllocationConfig(), valid.p2pElasticAllocationConfig(),
                valid.outboundToteConfig(), 0, valid.fixedStep(), valid.maximumStepsPerAdvance(),
                valid.metricSampleInterval(), valid.routeSpeedUnitsPerSecond(), valid.queueCapacities(),
                valid.thirdPartyAreaConfig(), valid.adaptingStorageConfig(), valid.adaptingBenchDefinitions(),
                valid.p2pPlaceholderDurations(), valid.p2pLineDefinitions(), 30, valid.timetable()));
        assertThrows(IllegalArgumentException.class, () -> new DspUncalibratedFullDayProfile(
                valid.operatingDate(), valid.osrInventoryConfig(), valid.serviceCentreSupplyConfig(),
                valid.inboundToteArrivalPolicy(), valid.av02AllocationConfig(), valid.p2pElasticAllocationConfig(),
                valid.outboundToteConfig(), valid.maximumPacksPerBag(), Duration.ZERO,
                valid.maximumStepsPerAdvance(), valid.metricSampleInterval(), valid.routeSpeedUnitsPerSecond(),
                valid.queueCapacities(), valid.thirdPartyAreaConfig(), valid.adaptingStorageConfig(),
                valid.adaptingBenchDefinitions(), valid.p2pPlaceholderDurations(), valid.p2pLineDefinitions(),
                valid.prlCountPerLine(), valid.timetable()));
        assertThrows(IllegalArgumentException.class, () -> new DspUncalibratedFullDayProfile(
                valid.operatingDate(), valid.osrInventoryConfig(), valid.serviceCentreSupplyConfig(),
                valid.inboundToteArrivalPolicy(), valid.av02AllocationConfig(), valid.p2pElasticAllocationConfig(),
                valid.outboundToteConfig(), valid.maximumPacksPerBag(), valid.fixedStep(), 0,
                valid.metricSampleInterval(), valid.routeSpeedUnitsPerSecond(), valid.queueCapacities(),
                valid.thirdPartyAreaConfig(), valid.adaptingStorageConfig(), valid.adaptingBenchDefinitions(),
                valid.p2pPlaceholderDurations(), valid.p2pLineDefinitions(), valid.prlCountPerLine(), valid.timetable()));
    }

    private static DspUncalibratedFullDayProfile profile() {
        return DspUncalibratedFullDayProfile.productionBaseline(
                OPERATING_DATE, 10, Duration.ofSeconds(3), 2, 4, 3);
    }

    private static P2pElasticAllocationConfig wrongLineCountConfig() {
        return new P2pElasticAllocationConfig(
                4, 2, 1, 1000, 1000, Duration.ofHours(1),
                new P2pWorkloadCostConfig(
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }
}
