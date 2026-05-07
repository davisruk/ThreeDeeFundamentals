package online.davisfamily.warehouse.testing.scheduler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

public class ScheduledTipperToteReleaseCatalog {
    private final List<ScheduledTipperToteRelease> releases;
    private final Map<String, ScheduledTipperToteRelease> releasesByOrderId;
    private final Map<String, ToteLoadPlan> toteLoadPlansByToteId;

    public ScheduledTipperToteReleaseCatalog(List<ScheduledTipperToteRelease> releases) {
        if (releases == null) {
            throw new IllegalArgumentException("releases must not be null");
        }

        this.releases = List.copyOf(releases);
        this.releasesByOrderId = new LinkedHashMap<>();
        this.toteLoadPlansByToteId = new LinkedHashMap<>();
        for (ScheduledTipperToteRelease release : this.releases) {
            if (release == null) {
                throw new IllegalArgumentException("releases must not contain null entries");
            }
            ScheduledTipperToteRelease existingOrderRelease = releasesByOrderId.putIfAbsent(release.orderId(), release);
            if (existingOrderRelease != null) {
                throw new IllegalArgumentException("Duplicate scheduled tipper release orderId: " + release.orderId());
            }

            String toteId = release.toteLoadPlan().getToteId();
            ToteLoadPlan existingToteLoadPlan = toteLoadPlansByToteId.putIfAbsent(toteId, release.toteLoadPlan());
            if (existingToteLoadPlan != null) {
                throw new IllegalArgumentException("Duplicate scheduled tipper release toteId: " + toteId);
            }
        }
    }

    public Optional<ScheduledTipperToteRelease> findByOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        return Optional.ofNullable(releasesByOrderId.get(orderId));
    }

    public List<ToteLoadPlan> toteLoadPlans() {
        return releases.stream()
                .map(ScheduledTipperToteRelease::toteLoadPlan)
                .toList();
    }

    public ToteLoadPlanProvider toteLoadPlanProvider() {
        return toteId -> {
            if (toteId == null || toteId.isBlank()) {
                throw new IllegalArgumentException("toteId must not be blank");
            }
            return toteLoadPlansByToteId.get(toteId);
        };
    }
}
