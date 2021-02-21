CREATE TABLE character_name_lookup (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    name varchar(200) NOT NULL,
    movie_id bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `unq_name_movie_id` (`name`, `movie_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;