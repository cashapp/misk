CREATE TABLE character_name_lookup(
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  movie_id bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unq_name_movie_id` (`name`, `movie_id`)
);
