CREATE TABLE movies(
  id bigint(20) NOT NULL PRIMARY KEY AUTO_INCREMENT,
  name varchar(191) NOT NULL,
  created_at timestamp NOT NULL,
  release_date date NULL,
  UNIQUE KEY `unq_name` (`name`)
);