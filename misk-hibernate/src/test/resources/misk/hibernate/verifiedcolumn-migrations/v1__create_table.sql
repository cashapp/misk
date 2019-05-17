CREATE TABLE auth_tokens(
  id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  client_id VARCHAR(64) NOT NULL,
  token VARCHAR(64),
  token_salt VARBINARY(36),
  token_hmac VARBINARY(256),
  CONSTRAINT token_salt_unique UNIQUE (token_salt)
)