CREATE TABLE BAD_identifier_table (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    tbl6CamelCase INT DEFAULT NULL,
    tbl6_UPPER_UNDERSCORE INT DEFAULT NULL,
    tbl6_MixEd_UNDERScore INT DEFAULT NULL,
    `tbl6-lower-hyphen` INT DEFAULT NULL,
    tbl6NotReallyUnique INT DEFAULT NULL,
    tbl6_not_really_unique INT DEFAULT NULL,
    tbl6_hibernate_camelcase INT DEFAULT NULL,
    tbl6DatabaseCamelcase INT DEFAULT NULL
)