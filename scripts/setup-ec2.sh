#!/bin/bash

# EC2 Initial Setup Script for Bungaku Kensaku
# Run this script on your EC2 instance to set up the environment

echo "=== Bungaku Kensaku EC2 Setup Script ==="
echo "This script will set up the EC2 environment for Bungaku Kensaku"
echo ""

# Check if running as root
if [ "$EUID" -eq 0 ]; then
    echo "Please run this script as a regular user with sudo privileges, not as root"
    exit 1
fi

# Update system
echo "Updating system packages..."
sudo apt update

# Install required packages
echo "Installing required packages..."
sudo apt install -y openjdk-17-jdk maven git postgresql postgresql-contrib nginx python3 python3-venv python3-pip

# Create application directory
echo "Creating application directory..."
sudo mkdir -p /opt/bungaku-kensaku
sudo chown $USER:$USER /opt/bungaku-kensaku

# Create log directory
mkdir -p /opt/bungaku-kensaku/logs

# Set up PostgreSQL
echo "Setting up PostgreSQL..."
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Create database and user
echo "Creating database and user..."
sudo -u postgres psql -c "CREATE DATABASE bungaku_kensaku;"
sudo -u postgres psql -c "CREATE USER bungaku_user WITH PASSWORD 'bungaku2024';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE bungaku_kensaku TO bungaku_user;"

# Configure Nginx
echo "Setting up Nginx configuration..."
sudo tee /etc/nginx/sites-available/bungaku-kensaku > /dev/null << 'EOF'
server {
    listen 80;
    server_name bungaku-kensaku.com www.bungaku-kensaku.com;

    # Main application
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Python AI service
    location /api/ai/ {
        proxy_pass http://localhost:8000/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

# Enable the site
sudo ln -sf /etc/nginx/sites-available/bungaku-kensaku /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx

# Create environment file template
echo "Creating environment file template..."
cat > /opt/bungaku-kensaku/.env << 'EOF'
# Environment Variables for Bungaku Kensaku
# IMPORTANT: Replace these with your actual API keys

export OPENAI_API_KEY="your-openai-api-key-here"
export PINECONE_API_KEY="your-pinecone-api-key-here"
export PINECONE_ENVIRONMENT="us-east-1"
export PINECONE_INDEX_NAME="bungaku-kensaku"
EOF

# Set secure permissions on environment file
chmod 600 /opt/bungaku-kensaku/.env

# Clone the repository
echo "Cloning repository..."
cd /opt/bungaku-kensaku
git clone https://github.com/your-username/bungaku-kensaku.git .

# Make scripts executable
chmod +x scripts/*.sh

echo ""
echo "=== Setup Complete! ==="
echo ""
echo "Next steps:"
echo "1. Edit /opt/bungaku-kensaku/.env with your actual API keys"
echo "2. Update the database password in src/main/resources/application-production.properties"
echo "3. Configure your domain DNS to point to this EC2 instance"
echo "4. Run: ./scripts/deploy.sh to start the application"
echo ""
echo "Service management:"
echo "  ./scripts/manage.sh status   - Check service status"
echo "  ./scripts/manage.sh start    - Start services"
echo "  ./scripts/manage.sh stop     - Stop services"
echo "  ./scripts/manage.sh logs     - View logs"
echo ""
echo "Important files:"
echo "  /opt/bungaku-kensaku/.env                                    - API keys"
echo "  /opt/bungaku-kensaku/src/main/resources/application-production.properties - App config"
echo "  /etc/nginx/sites-available/bungaku-kensaku                   - Nginx config"
echo "  /opt/bungaku-kensaku/logs/                                    - Application logs"