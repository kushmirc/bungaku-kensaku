#!/bin/bash

# Start the FastAPI service
echo "Starting Sensei AI Service on port 8001..."
uvicorn main:app --host 0.0.0.0 --port 8001 --reload