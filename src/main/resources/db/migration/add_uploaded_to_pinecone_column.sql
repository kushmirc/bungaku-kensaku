-- Add uploaded_to_pinecone column to chunks table
ALTER TABLE chunks 
ADD COLUMN uploaded_to_pinecone BOOLEAN NOT NULL DEFAULT false;

-- Create index for efficient queries
CREATE INDEX idx_chunks_uploaded_to_pinecone ON chunks(uploaded_to_pinecone);