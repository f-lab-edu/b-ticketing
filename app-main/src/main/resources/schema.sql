CREATE TABLE IF NOT EXISTS seat (
                                    seat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    seat_row VARCHAR(10) NOT NULL,
    seat_number INT NOT NULL
    );

CREATE TABLE IF NOT EXISTS seat_reservation (
                                                reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                seat_id BIGINT NOT NULL,
                                                schedule_id BIGINT NOT NULL,
                                                status VARCHAR(20) NOT NULL,
    FOREIGN KEY (seat_id) REFERENCES seat(seat_id)
    );
