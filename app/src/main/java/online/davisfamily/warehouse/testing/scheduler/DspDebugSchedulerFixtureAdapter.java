package online.davisfamily.warehouse.testing.scheduler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.testing.TipperDemoFixtures;

public class DspDebugSchedulerFixtureAdapter {
    public DspSchedulerRuntimeState createRuntimeState(
            List<TipperDemoFixtures.DemoTipperFeed> feeds,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            String serviceCentreId) {
        if (feeds == null || stationAdmissions == null || serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("Fixture adapter inputs must not be null or blank");
        }
        String trimmedServiceCentreId = serviceCentreId.trim();

        List<DspSchedulerOrderState> orderStates = IntStream.range(0, feeds.size())
                .mapToObj(index -> toOrderState(feeds.get(index), index, trimmedServiceCentreId))
                .toList();

        return new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                orderStates,
                stationAdmissions,
                java.util.Set.of(),
                Optional.empty()));
    }

    public ScheduledTipperToteReleaseCatalog createReleaseCatalog(List<TipperDemoFixtures.DemoTipperFeed> feeds) {
        if (feeds == null) {
            throw new IllegalArgumentException("feeds must not be null");
        }

        return new ScheduledTipperToteReleaseCatalog(feeds.stream()
                .map(feed -> new ScheduledTipperToteRelease(
                        feed.toteLoadPlan().getToteId(),
                        feed.toteLoadPlan(),
                        feed::totePayload))
                .toList());
    }

    private DspSchedulerOrderState toOrderState(
            TipperDemoFixtures.DemoTipperFeed feed,
            int sequenceNumber,
            String serviceCentreId) {
        if (feed == null) {
            throw new IllegalArgumentException("feed must not be null");
        }

        String toteId = feed.toteLoadPlan().getToteId();
        NotionalToteOrder order = new NotionalToteOrder(
                toteId,
                "notional-" + toteId,
                serviceCentreId,
                1,
                OrderType.ASSOCIATED,
                feed.toteLoadPlan().getPackPlans().stream()
                        .map(packPlan -> new DspOrderItem(
                                packPlan.packId(),
                                packPlan.packId(),
                                1,
                                serviceCentreId,
                                DspOrderLineType.FULL_PACK,
                                toteId,
                                1,
                                0))
                        .toList(),
                sequenceNumber);

        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(false, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
    }
}
