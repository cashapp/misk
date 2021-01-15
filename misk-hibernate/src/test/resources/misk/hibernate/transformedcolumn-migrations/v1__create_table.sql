CREATE TABLE manytypes (
  id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  int_field INT NOT NULL,
  double_field DOUBLE NOT NULL,
  string_field VARCHAR(250),
  byte_array_field VARBINARY(250)
)