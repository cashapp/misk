CREATE TABLE customers (
  id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  group_id BIGINT,
  aliases INT NOT NULL,
  address VARBINARY(250),
  image VARBINARY(250),
  ssn_column VARBINARY(250),
  INDEX ssn_idx(ssn_column)
  #, FOREIGN KEY (group_id) REFERENCES customer_group(id)
);

