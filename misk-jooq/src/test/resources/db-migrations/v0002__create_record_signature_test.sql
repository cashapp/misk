CREATE TABLE record_signature_test(
    id BIGINT NOT NULL AUTO_INCREMENT primary key,
    name VARCHAR(191) NOT NULL,
    binary_data BLOB,
    created_by VARCHAR(191) NOT NULL,
    updated_by VARCHAR(191),
    created_at TIMESTAMP(3) NULL DEFAULT NULL,
    updated_at TIMESTAMP(0) NULL DEFAULT NULL,
    record_signature BLOB
)