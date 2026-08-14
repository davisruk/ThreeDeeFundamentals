package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class ProductMasterCsvLoaderTest {
    private final ProductMasterCsvLoader loader = new ProductMasterCsvLoader();

    @Test
    void shouldLoadRealFieldsAndConvertMillimetresToMetres() {
        List<ProductMasterRecord> products = loader.loadString("""
                dispensingProductPackColumbusCode,pipCode,name,thirdPartyLocation,length,width,height,status
                002253,2833184,"Yasmin tablets, Bayer [63]",Y74,112,44,25,1
                """);

        ProductMasterRecord product = products.getFirst();
        assertEquals("002253", product.productId());
        assertEquals("Yasmin tablets, Bayer [63]", product.displayName());
        assertEquals("Y74", product.thirdPartyLocation().orElseThrow());
        assertEquals(new PackDimensions(0.112f, 0.044f, 0.025f), product.dimensions().orElseThrow());
        assertTrue(product.thirdParty());
    }

    @Test
    void shouldTrimValuesAndTreatBlankLocationAsNotThirdParty() {
        ProductMasterRecord product = loader.loadString("""
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                  9114  ,  Product name  ,   ,200,100,80
                """).getFirst();

        assertEquals("9114", product.productId());
        assertEquals("Product name", product.displayName());
        assertTrue(product.thirdPartyLocation().isEmpty());
        assertFalse(product.thirdParty());
    }

    @Test
    void shouldRetainProductWithMissingDimensionTriple() {
        ProductMasterRecord product = loader.loadString("""
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                50341,Dapagliflozin tablets,,0,0,0
                """).getFirst();

        assertEquals("50341", product.productId());
        assertTrue(product.dimensions().isEmpty());
    }

    @Test
    void shouldRejectDuplicateIdsAndInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> loader.loadString("""
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                9114,First,,100,50,20
                9114,Second,,100,50,20
                """));
        assertThrows(IllegalArgumentException.class, () -> loader.loadString("""
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                9114,Product,,100,0,20
                """));
        assertThrows(IllegalArgumentException.class, () -> loader.loadString("""
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                9114,Product,,wide,50,20
                """));
        assertThrows(IllegalArgumentException.class, () -> loader.loadString("""
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                9114,Product,,-1,-1,-1
                """));
    }

    @Test
    void shouldRejectMissingInputAndBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(null));
        assertThrows(IllegalArgumentException.class, () -> loader.loadString(null));
        assertThrows(IllegalArgumentException.class, () -> loader.loadString("""
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                ,Product,,100,50,20
                """));
        assertThrows(IllegalArgumentException.class, () -> loader.loadString("""
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                9114,,,100,50,20
                """));
    }

    @Test
    void shouldLoadSuppliedProductMaster() {
        Path productMasterPath = Files.exists(Path.of("app", "md", "product_automation.csv"))
                ? Path.of("app", "md", "product_automation.csv")
                : Path.of("md", "product_automation.csv");

        List<ProductMasterRecord> products = loader.load(productMasterPath);

        assertEquals(5_498, products.size());
        assertEquals(78, products.stream().filter(ProductMasterRecord::thirdParty).count());
        assertEquals(8, products.stream().filter(product -> product.dimensions().isEmpty()).count());
    }
}
