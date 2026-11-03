package com.monssef.orderservice.order;

import com.monssef.orderservice.product.ReservedItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Id of the product in product-service, without foreign key (another database): kept as is
    // when the product is deleted later, the line still has its copied name and price
    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Copies taken at purchase time: the order never changes when the product does
    @Column(nullable = false, length = 150)
    private String productName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Min(1)
    @Column(nullable = false)
    private int quantity;

    public OrderItem(Order order, ReservedItem reserved) {
        this.order = order;
        this.productId = reserved.productId();
        this.productName = reserved.name();
        this.unitPrice = reserved.price();
        this.quantity = reserved.quantity();
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
