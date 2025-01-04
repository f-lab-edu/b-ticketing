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

CREATE TABLE IF NOT EXISTS payment (
                         payment_id INT AUTO_INCREMENT PRIMARY KEY,
                         reservation_id INT NOT NULL,
                         payment_status VARCHAR(255) NOT NULL,
                         amount DOUBLE NOT NULL,
                         payment_date DATETIME NOT NULL
);
