package com.monssef.orderservice.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * The accounts of auth-service, read only: carts and orders point to users.id,
 * while the JWT only gives the email. Only the two columns needed here are mapped.
 */
@Entity
@Table(name = "users")
@Immutable // never written by this service: auth-service owns the accounts
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;
}
