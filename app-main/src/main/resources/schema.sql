CREATE TABLE seats (
                       seat_id INT AUTO_INCREMENT PRIMARY KEY,
                       schedule_id INT NOT NULL,
                       section_id INT NOT NULL,
                       status VARCHAR(255) NOT NULL
);
