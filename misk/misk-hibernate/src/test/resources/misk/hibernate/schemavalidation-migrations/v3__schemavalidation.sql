CREATE TABLE database_only_table (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    tbl3_string_column VARCHAR(255) NOT NULL,
    tbl3_int_column INT NOT NULL DEFAULT 0
)