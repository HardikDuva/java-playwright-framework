package com.data;

/** Immutable test-data record for a synthetic user. */
public record TestUser(
        String firstName,
        String lastName,
        String email,
        String password,
        String phone,
        String address,
        String city,
        String state,
        String postcode,
        String country) {
}
