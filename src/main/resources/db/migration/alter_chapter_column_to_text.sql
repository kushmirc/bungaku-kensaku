-- Migration to change chapter column from VARCHAR(255) to TEXT
-- This allows for longer chapter titles that exceed 255 characters

ALTER TABLE chunks ALTER COLUMN chapter TYPE TEXT;