CREATE TABLE text_hashes (
    id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
    text varchar(255) NOT NULL,
    hash varbinary(32) NOT NULL,
    nullable_hash varbinary(32),
    UNIQUE KEY `unq_hash` (`hash`)
);