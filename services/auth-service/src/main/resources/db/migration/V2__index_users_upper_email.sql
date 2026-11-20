-- Registration checks that the email is free whatever its case (existsByEmailIgnoreCase):
-- Spring Data turns it into WHERE upper(email) = upper(?), which cannot use the unique
-- index on email. This one it can
CREATE INDEX idx_users_upper_email ON users (upper(email));
