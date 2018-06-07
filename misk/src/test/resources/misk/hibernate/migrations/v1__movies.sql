CREATE TABLE movies(
  id bigint(20) NOT NULL PRIMARY KEY AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  created_at timestamp NOT NULL,
  release_date date NULL
);