CREATE TABLE companies (
                           id UUID PRIMARY KEY,

                           legal_name VARCHAR(255) NOT NULL,

                           trade_name VARCHAR(255),

                           document VARCHAR(20) NOT NULL UNIQUE,

                           email VARCHAR(255),

                           phone VARCHAR(20),

                           created_at TIMESTAMP NOT NULL,

                           updated_at TIMESTAMP NOT NULL
);