package com.order.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents lifecycle states for an Order.
 *
 * Improvements / best-practices applied:
 * - Added JavaDoc for clarity.
 * - Added JSON (de)serialization hooks for stable REST/JSON behavior.
 * - Added helper methods to check final states and valid transitions.
 */
public enum OrderStatus {
    CREATED,
    PROCESSING,
    CANCELLED,
    SUCCESS,
    FAILURE;

    /**
     * Returns true when the status is a terminal state (no further processing expected).
     */
    public boolean isFinal() {
        return this == SUCCESS || this == FAILURE || this == CANCELLED;
    }

    /**
     * Basic transition rules for the order lifecycle. Keep this small and explicit.
     * Adjust this logic if your domain requires different transitions.
     */
    public boolean canTransitionTo(OrderStatus next) {
        if (next == null) return false;
        switch (this) {
            case CREATED:
                return next == PROCESSING || next == CANCELLED;
            case PROCESSING:
                return next == SUCCESS || next == FAILURE || next == CANCELLED;
            default:
                return false;
        }
    }

    /**
     * Ensure Jackson serializes enums as their name().
     */
    @JsonValue
    public String toValue() {
        return name();
    }

    /**
     * Robust deserialization from string (case-insensitive) for incoming JSON.
     */
    @JsonCreator
    public static OrderStatus fromValue(String value) {
        if (value == null) return null;
        try {
            return OrderStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown OrderStatus: " + value, ex);
        }
    }

}
