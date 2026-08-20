package online.davisfamily.warehouse.sim.dsp.osr.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class ReleasePhysicalToteFromOsrCommandTest {

    @Test
    void shouldRetainDistinctPhysicalAndLogicalCommandIdentity() {
        PhysicalToteId physicalToteId = new PhysicalToteId("tote-1");
        OrderSheetKey orderSheetKey = new OrderSheetKey("order-1", 2);

        ReleasePhysicalToteFromOsrCommand command =
                new ReleasePhysicalToteFromOsrCommand(
                        physicalToteId,
                        orderSheetKey,
                        " 104 ",
                        " p2p-1 ");

        assertSame(physicalToteId, command.physicalToteId());
        assertSame(orderSheetKey, command.orderSheetKey());
        assertEquals("104", command.serviceCentreId());
        assertEquals("p2p-1", command.releaseTargetId());
    }

    @Test
    void shouldRejectInvalidPhysicalReleaseCommandFields() {
        PhysicalToteId physicalToteId = new PhysicalToteId("tote-1");
        OrderSheetKey orderSheetKey = new OrderSheetKey("order-1", 1);

        assertThrows(IllegalArgumentException.class, () ->
                new ReleasePhysicalToteFromOsrCommand(
                        null, orderSheetKey, "104", "p2p-1"));
        assertThrows(IllegalArgumentException.class, () ->
                new ReleasePhysicalToteFromOsrCommand(
                        physicalToteId, null, "104", "p2p-1"));
        assertThrows(IllegalArgumentException.class, () ->
                new ReleasePhysicalToteFromOsrCommand(
                        physicalToteId, orderSheetKey, " ", "p2p-1"));
        assertThrows(IllegalArgumentException.class, () ->
                new ReleasePhysicalToteFromOsrCommand(
                        physicalToteId, orderSheetKey, "104", null));
    }

    @Test
    void shouldRetainManifestAndSimulationTimeInReleaseRequest() {
        InboundToteManifest manifest = manifest();
        Duration releaseTime = Duration.ofSeconds(17);

        OsrProcessingReleaseRequest request =
                new OsrProcessingReleaseRequest(manifest, releaseTime);

        assertSame(manifest, request.manifest());
        assertSame(releaseTime, request.releaseTime());
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseRequest(null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseRequest(manifest, null));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseRequest(manifest, Duration.ofNanos(-1)));
    }

    private static InboundToteManifest manifest() {
        return new InboundToteManifest(
                new PhysicalToteId("tote-1"),
                new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem("line-1", "product-1", 1)),
                0);
    }
}
