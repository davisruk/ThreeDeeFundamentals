package online.davisfamily.warehouse.sim.dsp.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.ProductCategory;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;

class ProductMasterJsonLoaderTest {
    private final ProductMasterJsonLoader loader = new ProductMasterJsonLoader();

    @Test
    void shouldLoadTopLevelArray() {
        List<ProductMasterRecord> products = loader.loadString("""
                [
                  {"productId":"product-1","category":"AUTOMATED","thirdParty":false},
                  {"productId":"product-2","category":"MANUAL","thirdParty":true}
                ]
                """);

        assertEquals(2, products.size());
        assertEquals(new ProductMasterRecord("product-1", ProductCategory.AUTOMATED, false), products.get(0));
        assertEquals(new ProductMasterRecord("product-2", ProductCategory.MANUAL, true), products.get(1));
    }

    @Test
    void shouldLoadProductsWrapperObject() {
        List<ProductMasterRecord> products = loader.loadString("""
                {
                  "products": [
                    {"productId":"product-1","category":"SORTABLE","thirdParty":false}
                  ]
                }
                """);

        assertEquals(List.of(new ProductMasterRecord("product-1", ProductCategory.SORTABLE, false)), products);
    }

    @Test
    void shouldTrimProductIds() {
        List<ProductMasterRecord> products = loader.loadString("""
                [
                  {"productId":"   product-1   ","category":"AUTOMATED","thirdParty":false}
                ]
                """);

        assertEquals("product-1", products.getFirst().productId());
    }

    @Test
    void shouldTreatCategoryCaseInsensitively() {
        List<ProductMasterRecord> products = loader.loadString("""
                [
                  {"productId":"product-1","category":"sortable","thirdParty":false}
                ]
                """);

        assertEquals(ProductCategory.SORTABLE, products.getFirst().category());
    }

    @Test
    void shouldRejectUnknownCategory() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> loader.loadString("""
                        [
                          {"productId":"product-1","category":"COLD_CHAIN","thirdParty":false}
                        ]
                        """));

        assertTrue(exception.getMessage().contains("Unknown product category"));
    }
}
