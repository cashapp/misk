CREATE TABLE movies (
    id serial PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    name varchar(191) NOT NULL UNIQUE,
    release_date date NULL
);