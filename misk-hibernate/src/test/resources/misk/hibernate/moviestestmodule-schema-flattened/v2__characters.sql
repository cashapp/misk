CREATE TABLE characters(
  id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  created_at timestamp(3) NOT NULL DEFAULT NOW(3),
  updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
  name varchar(255) NOT NULL,
  movie_id bigint NOT NULL,
  actor_id bigint NULL
);
