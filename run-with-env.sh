#!/bin/bash
# Script to run the application with required environment variables

echo "Starting Sensei Guidance Search..."
echo "Please make sure you have set your Pinecone API key"

# Check if PINECONE_API_KEY is set
if [ -z "$PINECONE_API_KEY" ]; then
    echo "ERROR: PINECONE_API_KEY is not set!"
    echo "Please export it first:"
    echo "  export PINECONE_API_KEY='your-api-key-here'"
    exit 1
fi

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "WARNING: OPENAI_API_KEY is not set!"
    echo "The application may not work properly without it."
fi

# Run the application
./mvnw spring-boot:run