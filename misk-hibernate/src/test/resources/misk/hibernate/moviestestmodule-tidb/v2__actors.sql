CREATE TABLE actors (
    id bigint NOT NULL PRIMARY KEY AUTO_RANDOM,
    created_at timestamp(3) NOT NULL DEFAULT NOW(3),
    updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
    name varchar(255) NOT NULL,
    birth_date date NULL
);