package com.dudububu.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "store_accounts")
class StoreAccount {
    @Id
    @GeneratedValue
    private UUID id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 80)
    private String displayName;
    @Column(nullable = false, length = 128)
    private String passwordHash;
    @Column(length = 200_000)
    private byte[] picture;

    protected StoreAccount() {}

    StoreAccount(String email, String displayName, String passwordHash) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    UUID getId() { return id; }
    String getEmail() { return email; }
    String getDisplayName() { return displayName; }
    String getPasswordHash() { return passwordHash; }
    byte[] getPicture() { return picture; }
    void setPicture(byte[] picture) { this.picture = picture; }
}
