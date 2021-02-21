CREATE TABLE nullable_mismatch_table (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    tbl5_both_notnull INT NOT NULL DEFAULT 0,
    tbl5_hibernate_null INT NOT NULL,
    tbl5_hibernate_null_default INT NOT NULL DEFAULT 0,
    tbl5_both_null INT DEFAULT NULL,
    tbl5_database_null INT DEFAULT NULL
)