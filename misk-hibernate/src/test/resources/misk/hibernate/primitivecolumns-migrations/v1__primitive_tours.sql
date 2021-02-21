CREATE TABLE primitive_tours (
    id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
    i1 tinyint(1) NOT NULL,
    i8 tinyint NOT NULL,
    i16 smallint NOT NULL,
    i32 mediumint NOT NULL,
    i64 bigint NOT NULL,
    c16 char(1) NOT NULL,
    f32 float NOT NULL,
    f64 double NOT NULL
);