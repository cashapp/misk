CREATE TABLE missing_columns_table (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    tbl4_string_column VARCHAR(255) NOT NULL,
    tbl4_int_column INT NOT NULL DEFAULT 0,
    tbl4_string_column_database VARCHAR(255) NOT NULL,
    tbl4_int_column_database INT NOT NULL DEFAULT 0
)