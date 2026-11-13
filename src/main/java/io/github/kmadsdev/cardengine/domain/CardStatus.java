package io.github.kmadsdev.cardengine.domain;

/** A card is ACTIVE until an upgrade issues a new card; then the old one is REPLACED. */
public enum CardStatus {
    ACTIVE,
    REPLACED
}
