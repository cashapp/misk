CREATE TABLE text_hashes(
  id bigint(20) NOT NULL PRIMARY KEY AUTO_INCREMENT,
  text varchar(255) NOT NULL,
  hash varbinary(32) NOT NULL,
  UNIQUE KEY `unq_hash` (`hash`)
);