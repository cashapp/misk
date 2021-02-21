CREATE TABLE unquoted_database_table (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    tbl2_string_nullable VARCHAR(255) DEFAULT NULL,
    tbl2_int_column_nullable INT,
    tbl2_bin_column_nullable VARBINARY(255),
    tbl2_some_string VARCHAR(255) NOT NULL,
    tbl2_int_column INT NOT NULL DEFAULT 0,
    tbl2_bin_column VARBINARY(255) NOT NULL
)