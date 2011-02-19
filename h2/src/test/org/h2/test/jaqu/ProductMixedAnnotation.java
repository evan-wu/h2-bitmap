/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License, Version
 * 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html). Initial Developer: H2 Group
 */
package org.h2.test.jaqu;

// ## Java 1.5 begin ##
import java.util.Arrays;
import java.util.List;
import org.h2.jaqu.Table.JQColumn;
import org.h2.jaqu.Table.JQIndex;
import org.h2.jaqu.Table.JQTable;

/**
 * A table containing product data.
 */
// ## Java 1.5 begin ##
@JQTable(annotationsOnly = false)
@JQIndex(standard="name,cat")
public class ProductMixedAnnotation {

    @JQColumn(name = "id", primaryKey=true)
    private Integer productId;

    @JQColumn(name = "name")
    private String productName;

    @JQColumn(name = "cat", maxLength = 255)
    String category;

    public Double unitPrice;
    public Integer unitsInStock;
    public String mappedField;

    public ProductMixedAnnotation() {
        // public constructor
    }

    protected ProductMixedAnnotation(int productId, String productName, String category, double unitPrice,
            int unitsInStock, String mappedField) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.unitPrice = unitPrice;
        this.unitsInStock = unitsInStock;
        this.mappedField = mappedField;
    }

    private static ProductMixedAnnotation create(int productId, String productName, String category, double unitPrice,
            int unitsInStock, String mappedField) {
        return new ProductMixedAnnotation(productId, productName, category, unitPrice, unitsInStock, mappedField);
    }

    public static List<ProductMixedAnnotation> getList() {
        String mappedField = "mapped";
        ProductMixedAnnotation[] list = { create(1, "Chai", "Beverages", 18, 39, mappedField),
                create(2, "Chang", "Beverages", 19.0, 17, mappedField),
                create(3, "Aniseed Syrup", "Condiments", 10.0, 13, mappedField),
                create(4, "Chef Anton's Cajun Seasoning", "Condiments", 22.0, 53, mappedField),
                create(5, "Chef Anton's Gumbo Mix", "Condiments", 21.3500, 0, mappedField),
                create(6, "Grandma's Boysenberry Spread", "Condiments", 25.0, 120, mappedField),
                create(7, "Uncle Bob's Organic Dried Pears", "Produce", 30.0, 15, mappedField),
                create(8, "Northwoods Cranberry Sauce", "Condiments", 40.0, 6, mappedField),
                create(9, "Mishi Kobe Niku", "Meat/Poultry", 97.0, 29, mappedField),
                create(10, "Ikura", "Seafood", 31.0, 31, mappedField), };

        return Arrays.asList(list);
    }

    public String toString() {
        return productName + ": " + unitsInStock;    
    }
    
    public int id() {
        return productId;
    }
    
    public String name() {
        return productName;
    }
}
// ## Java 1.5 end ##
