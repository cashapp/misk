CREATE TABLE jerry_garcia_songs (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(64) NOT NULL,
    length INT NOT NULL,
    album VARBINARY(250),
    reviewer VARBINARY(250)
)