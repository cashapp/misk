CREATE TABLE avengers_movies (
    id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
    name varchar(255) NOT NULL,
    hero LONGBLOB NOT NULL,
    anotherhero LONGBLOB DEFAULT NULL
)