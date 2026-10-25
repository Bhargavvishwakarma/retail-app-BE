CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(255),
    email VARCHAR(255),
    address VARCHAR(255),
    created_at TIMESTAMP
);

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(255) UNIQUE,
    description VARCHAR(255),
    size VARCHAR(255),
    color VARCHAR(255),
    category_id UUID REFERENCES categories(id),
    price NUMERIC(38, 2) NOT NULL,
    cost NUMERIC(38, 2),
    stock_quantity INTEGER,
    active BOOLEAN,
    created_at TIMESTAMP
);

CREATE TABLE sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_number VARCHAR(255) UNIQUE,
    customer_id UUID REFERENCES customers(id),
    sale_date TIMESTAMP,
    payment_method VARCHAR(255),
    total_amount NUMERIC(38, 2)
);

CREATE TABLE sale_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID REFERENCES sales(id),
    product_id UUID REFERENCES products(id),
    quantity INTEGER,
    unit_price NUMERIC(38, 2),
    subtotal NUMERIC(38, 2)
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
