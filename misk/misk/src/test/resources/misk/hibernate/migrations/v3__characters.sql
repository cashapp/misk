CREATE TABLE characters(
  id bigint(20) NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  movie_id bigint(20) NOT NULL,
  actor_id bigint(20) NOT NULL
);