package com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("customer_accounts")
public class CustomerAccountAggregate implements Persistable<String> {

    @Id
    @Column("customer_id")
    private final String customerId;

    @Column("login_id")
    private final String loginId;

    @Column("password_hash")
    private final String passwordHash;

    @Column("display_name")
    private final String displayName;

    @Column("default_locale")
    private final String defaultLocale;

    @Column("scopes")
    private final String scopes;

    public CustomerAccountAggregate(
            String customerId,
            String loginId,
            String passwordHash,
            String displayName,
            String defaultLocale,
            String scopes) {
        this.customerId = customerId;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.defaultLocale = defaultLocale;
        this.scopes = scopes;
    }

    @Override
    public String getId() {
        return customerId;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    public String customerId() {
        return customerId;
    }

    public String loginId() {
        return loginId;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultLocale() {
        return defaultLocale;
    }

    public String scopes() {
        return scopes;
    }
}