CREATE TABLE demo_payment_profiles (
    buyer_id uuid PRIMARY KEY REFERENCES members(id),
    customer_id varchar(255) NOT NULL UNIQUE,
    payment_method_id varchar(255) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);
