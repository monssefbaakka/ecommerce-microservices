package com.monssef.productservice.product;

import com.monssef.productservice.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    // BigDecimal for money, never double (no rounding errors)
    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // Units left. Orders still decrease it in the monolith (same table, row lock while ordering).
    // The default fills existing rows when Hibernate adds the column
    @Min(0)
    @ColumnDefault("0")
    @Column(nullable = false)
    private int stock;

    // Many products -> one category. The client sends its id ("categoryId" in ProductRequest)
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // Filled by Hibernate at insertion time
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
