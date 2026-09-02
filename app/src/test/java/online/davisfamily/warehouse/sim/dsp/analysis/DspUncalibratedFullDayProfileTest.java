package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
    void shouldExposeTheExplicitUncalibratedProfileAndProductionShape() {
        DspUncalibratedFullDayProfile profile = profile();

        assertEquals("DEADLINE_AWARE_ELASTIC_STICKY_LEASES", profile.profileId());
        assertEquals("UNCALIBRATED", profile.timingCalibrationStatus());
        assertEquals("P2P_OUTPUT_CLOSED", profile.completionMilestone());
        assertEquals("PRIORITY_ORDERED_OSR_LOW_WATERMARK", profile.serviceCentreSupplyPolicyId());
        assertEquals("DEPENDENCY_READY_OVERLAP", profile.orderEligibilityPolicyId());
        assertEquals("PHARMACY_GROUPED_THEN_SOURCE_SEQUENCE", profile.candidateRankingPolicyId());
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
    }

    @Test
    void shouldKeepNestedValuesAndListsImmutable() {
        DspUncalibratedFullDayProfile original = profile();
        List<online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition> supplied =
                new ArrayList<>(original.p2pLineDefinitions());
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
                original.adaptingBenchDefinitions(),
                original.p2pPlaceholderDurations(),
                supplied,
                original.prlCountPerLine(),
                original.timetable());

        supplied.clear();
        assertEquals(5, copy.p2pLineDefinitions().size());
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
