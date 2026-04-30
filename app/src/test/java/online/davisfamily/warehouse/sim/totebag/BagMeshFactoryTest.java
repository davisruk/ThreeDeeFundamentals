package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.warehouse.sim.totebag.bag.BagMeshFactory;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;

class BagMeshFactoryTest {
    @Test
    void shouldCreateTaperedBagMeshWithinBagSpecBounds() {
        BagSpec spec = new BagSpec(0.34f, 0.28f, 0.22f);

        Mesh mesh = BagMeshFactory.createBagMesh(spec);

        assertEquals("bag", mesh.name);
        assertEquals(24, mesh.v4Vertices.length);
        assertTrue(mesh.triangles.length > 20);
        assertTrue(mesh.maxX - mesh.minX < spec.width());
        assertEquals(0f, mesh.minY, 0.0001f);
        assertTrue(mesh.maxY > spec.height());
        assertTrue(mesh.maxZ - mesh.minZ < spec.depth());
        assertTrue((mesh.maxY - mesh.minY) > (mesh.maxX - mesh.minX) * 2f);
    }

    @Test
    void shouldRejectNullSpec() {
        assertThrows(IllegalArgumentException.class, () -> BagMeshFactory.createBagMesh(null));
    }
}
