CREATE TABLE shortened_urls(
  id bigint(20) NOT NULL AUTO_INCREMENT,
  long_url varchar(4096) NOT NULL,
  token VARCHAR(8) NOT NULL
);