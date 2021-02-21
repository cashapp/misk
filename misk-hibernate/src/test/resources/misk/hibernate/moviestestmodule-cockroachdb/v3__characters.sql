CREATE TABLE characters (
    id serial PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    name varchar(200) NOT NULL,
    movie_id int NOT NULL REFERENCES movies(id),
    actor_id int NULL REFERENCES actors(id)
);