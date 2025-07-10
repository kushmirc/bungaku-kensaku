#!/bin/bash

# Load environment variables
source /opt/bungaku-kensaku/.env

# Bungaku Kensaku Deployment Script
# Usage: ./deploy.sh

APP_DIR="/opt/bungaku-kensaku"
LOG_DIR="$APP_DIR/logs"
JAR_NAME="bungaku-kensaku.jar"
APP_PORT=8080
PYTHON_PORT=8000

echo "=== Starting deployment at $(date) ==="

# Check if running from correct directory
if [ ! -d ".git" ]; then
    echo "Error: Not in a git repository. Please run from the project root."
    exit 1
fi

# Update code from repository
echo "Updating code from repository..."
git pull origin main
if [ $? -ne 0 ]; then
    echo "Git pull failed!"
    exit 1
fi

# Build the application
echo "Building application..."
./mvnw clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

# Stop existing services
echo "Stopping existing services..."
if [ -f "$APP_DIR/app.pid" ]; then
    PID=$(cat $APP_DIR/app.pid)
    if ps -p $PID > /dev/null; then
        kill $PID
        echo "Waiting for Java application to stop..."
        sleep 5
    fi
    rm -f $APP_DIR/app.pid
fi

if [ -f "$APP_DIR/python-ai.pid" ]; then
    PID=$(cat $APP_DIR/python-ai.pid)
    if ps -p $PID > /dev/null; then
        kill $PID
        echo "Waiting for Python service to stop..."
        sleep 3
    fi
    rm -f $APP_DIR/python-ai.pid
fi

# Copy the jar
echo "Copying JAR file..."
cp target/*.jar $APP_DIR/$JAR_NAME

# Set up Python virtual environment if it doesn't exist
echo "Setting up Python virtual environment..."
cd $APP_DIR/python-ai-service
if [ ! -d "venv" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
else
    echo "Virtual environment exists, updating dependencies..."
    source venv/bin/activate
    pip install -r requirements.txt --upgrade
fi

# Start the Java application
echo "Starting Java application..."
cd $APP_DIR
nohup java -Xmx300m -jar $APP_DIR/$JAR_NAME \
    --server.port=$APP_PORT \
    --spring.profiles.active=production \
    > $LOG_DIR/app.log 2>&1 &

# Save Java app PID
echo $! > $APP_DIR/app.pid

# Start Python AI service
echo "Starting Python AI service..."
cd $APP_DIR/python-ai-service
source venv/bin/activate
nohup python main.py > $LOG_DIR/python-ai.log 2>&1 &
echo $! > $APP_DIR/python-ai.pid

# Wait a moment for services to start
sleep 3

# Check if services are running
echo "=== Service Status ==="
if [ -f "$APP_DIR/app.pid" ] && ps -p $(cat $APP_DIR/app.pid) > /dev/null 2>&1; then
    echo "✓ Java app: Running (PID: $(cat $APP_DIR/app.pid))"
else
    echo "✗ Java app: Failed to start"
fi

if [ -f "$APP_DIR/python-ai.pid" ] && ps -p $(cat $APP_DIR/python-ai.pid) > /dev/null 2>&1; then
    echo "✓ Python AI: Running (PID: $(cat $APP_DIR/python-ai.pid))"
else
    echo "✗ Python AI: Failed to start"
fi

echo "=== Deployment completed at $(date) ==="
echo "Logs: $LOG_DIR/app.log and $LOG_DIR/python-ai.log"
echo ""
echo "To check logs in real-time:"
echo "  tail -f $LOG_DIR/app.log"
echo "  tail -f $LOG_DIR/python-ai.log"
echo ""
echo "To check service status:"
echo "  ./scripts/manage.sh status"