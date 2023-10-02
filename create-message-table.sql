CREATE TABLE messages (
    id SERIAL PRIMARY KEY,
    method VARCHAR(10) NOT NULL,
    uri VARCHAR(255) NOT NULL,
    body text,
    recieved_timestamp TIMESTAMP NOT NULL DEFAULT NOW()
)