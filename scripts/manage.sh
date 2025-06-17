#!/bin/bash

# Sensei Search Service Management Script
# Usage: ./manage.sh {start|stop|restart|status|logs|python-logs}

APP_DIR="/opt/sensei-search"
LOG_DIR="$APP_DIR/logs"

case "$1" in
    start)
        echo "Starting Sensei Search services..."
        cd $APP_DIR
        ./scripts/deploy.sh
        ;;
    stop)
        echo "Stopping Sensei Search services..."
        if [ -f "$APP_DIR/app.pid" ]; then
            PID=$(cat $APP_DIR/app.pid)
            if ps -p $PID > /dev/null 2>&1; then
                kill $PID
                echo "Java application stopped (PID: $PID)"
            fi
            rm -f $APP_DIR/app.pid
        else
            echo "Java application not running (no PID file)"
        fi
        
        if [ -f "$APP_DIR/python-ai.pid" ]; then
            PID=$(cat $APP_DIR/python-ai.pid)
            if ps -p $PID > /dev/null 2>&1; then
                kill $PID
                echo "Python AI service stopped (PID: $PID)"
            fi
            rm -f $APP_DIR/python-ai.pid
        else
            echo "Python AI service not running (no PID file)"
        fi
        echo "All services stopped"
        ;;
    restart)
        echo "Restarting Sensei Search services..."
        $0 stop
        sleep 2
        $0 start
        ;;
    status)
        echo "=== Sensei Search Service Status ==="
        
        if [ -f "$APP_DIR/app.pid" ] && ps -p $(cat $APP_DIR/app.pid) > /dev/null 2>&1; then
            echo "✓ Java app: Running (PID: $(cat $APP_DIR/app.pid))"
            echo "  Port: 8080"
            echo "  URL: http://localhost:8080"
        else
            echo "✗ Java app: Stopped"
        fi
        
        if [ -f "$APP_DIR/python-ai.pid" ] && ps -p $(cat $APP_DIR/python-ai.pid) > /dev/null 2>&1; then
            echo "✓ Python AI: Running (PID: $(cat $APP_DIR/python-ai.pid))"
            echo "  Port: 8000"
            echo "  URL: http://localhost:8000"
        else
            echo "✗ Python AI: Stopped"
        fi
        
        echo ""
        echo "=== System Resources ==="
        echo "Memory usage:"
        free -h 2>/dev/null || echo "  (free command not available)"
        
        echo ""
        echo "=== Network Ports ==="
        echo "Services listening on target ports:"
        netstat -tlnp 2>/dev/null | grep -E ':8080|:8000' || ss -tlnp | grep -E ':8080|:8000' || echo "  (port check not available)"
        ;;
    logs)
        echo "=== Java Application Logs (Press Ctrl+C to exit) ==="
        tail -f $LOG_DIR/app.log
        ;;
    python-logs)
        echo "=== Python AI Service Logs (Press Ctrl+C to exit) ==="
        tail -f $LOG_DIR/python-ai.log
        ;;
    *)
        echo "Sensei Search Service Manager"
        echo ""
        echo "Usage: $0 {start|stop|restart|status|logs|python-logs}"
        echo ""
        echo "Commands:"
        echo "  start        - Deploy and start all services"
        echo "  stop         - Stop all services"
        echo "  restart      - Stop and start all services"
        echo "  status       - Show service status and system info"
        echo "  logs         - Follow Java application logs"
        echo "  python-logs  - Follow Python AI service logs"
        echo ""
        exit 1
        ;;
esac