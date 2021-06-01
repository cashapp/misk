CREATE TABLE movie(
    id BIGINT NOT NULL AUTO_INCREMENT primary key,
    name VARCHAR(191) NOT NULL,
    genre VARCHAR(191) NOT NULL,
    version INT NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
)