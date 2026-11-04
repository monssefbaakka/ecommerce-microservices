package com.monssef.orderservice.cart;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One cart per user. The email of the JWT, not a foreign key: the accounts live in auth-service
    @Column(nullable = false, unique = true, length = 150)
    private String userEmail;

    // The cart owns its items: saving the cart saves them, removing one from the list deletes it
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CartItem> items = new ArrayList<>();

    public Cart(String userEmail) {
        this.userEmail = userEmail;
    }

    public Optional<CartItem> findItem(Long productId) {
        return items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();
    }

    public CartItem addItem(Long productId, int quantity) {
        CartItem item = new CartItem(this, productId, quantity);
        items.add(item);
        return item;
    }

    public List<Long> getProductIds() {
        return items.stream().map(CartItem::getProductId).toList();
    }
}
