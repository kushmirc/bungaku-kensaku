-- Add chapter position tracking fields to chunks table
-- These fields enable percentage-based location references within chapters

ALTER TABLE chunks 
ADD COLUMN IF NOT EXISTS chapter_number INTEGER,
ADD COLUMN IF NOT EXISTS chapter_position INTEGER,
ADD COLUMN IF NOT EXISTS chapter_percentage INTEGER;

-- Add comments to explain the fields
COMMENT ON COLUMN chunks.chapter_number IS 'Chapter number (1-based) within the book';
COMMENT ON COLUMN chunks.chapter_position IS 'Character position of chunk start within the chapter';
COMMENT ON COLUMN chunks.chapter_percentage IS 'Percentage position (0-100) within the chapter';

-- Create index for efficient chapter-based queries
CREATE INDEX IF NOT EXISTS idx_chunks_chapter_info 
ON chunks(book_id, chapter_number, chapter_position);