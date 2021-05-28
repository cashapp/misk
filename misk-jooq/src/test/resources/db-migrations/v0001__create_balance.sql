CREATE TABLE balance(
    id BIGINT NOT NULL AUTO_INCREMENT primary key,
    entity VARCHAR(191) NOT NULL,
    jurisdiction VARCHAR(3) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    version INT NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE KEY idx_float_entity(entity, jurisdiction)
)