CREATE TABLE text_tokens (
    id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
    text varchar(255) NOT NULL,
    token varchar(32) NOT NULL COLLATE utf8_bin,
    optional_token varchar(32) NULL DEFAULT NULL COLLATE utf8_bin,
    UNIQUE KEY `unq_token` (`token`)
);