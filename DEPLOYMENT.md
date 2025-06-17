# Sensei Search AWS EC2 Deployment Guide

This guide covers deploying the Sensei Guidance Search application to AWS EC2.

## Prerequisites

- AWS EC2 instance (t2.micro or larger) running Ubuntu 22.04
- Domain name purchased through AWS Route 53 (e.g., sensei-search.com)
- OpenAI API key
- Pinecone API key

## Quick Deployment

### 1. Initial EC2 Setup

SSH into your EC2 instance and run the setup script:

```bash
# Download and run the setup script
wget https://raw.githubusercontent.com/your-username/sensei-guidance-search/main/scripts/setup-ec2.sh
chmod +x setup-ec2.sh
./setup-ec2.sh
```

### 2. Configure API Keys

Edit the environment file with your actual API keys:

```bash
nano /opt/sensei-search/.env
```

Replace the placeholder values:
```bash
export OPENAI_API_KEY="sk-your-actual-openai-key"
export PINECONE_API_KEY="your-actual-pinecone-key"
```

### 3. Update Database Password

Edit the production configuration:

```bash
nano /opt/sensei-search/src/main/resources/application-production.properties
```

Update the database password line:
```properties
spring.datasource.password=sensei2024
```

### 4. Deploy Application

```bash
cd /opt/sensei-search
./scripts/deploy.sh
```

### 5. Configure DNS

In AWS Route 53, create an A record for your domain pointing to your EC2 instance's public IP.

## Service Management

Use the management script to control services:

```bash
# Check status
./scripts/manage.sh status

# Start services
./scripts/manage.sh start

# Stop services
./scripts/manage.sh stop

# Restart services
./scripts/manage.sh restart

# View logs
./scripts/manage.sh logs
./scripts/manage.sh python-logs
```

## Architecture

- **Java Application**: Runs on port 8080
- **Python AI Service**: Runs on port 8000
- **Nginx**: Reverse proxy routing traffic based on domain
- **PostgreSQL**: Local database on EC2
- **Static Files**: Served locally from EC2

## File Locations

- **Application**: `/opt/sensei-search/`
- **Logs**: `/opt/sensei-search/logs/`
- **Configuration**: `/opt/sensei-search/src/main/resources/application-production.properties`
- **Environment**: `/opt/sensei-search/.env`
- **Nginx Config**: `/etc/nginx/sites-available/sensei-search`

## Monitoring

### Check Application Status
```bash
./scripts/manage.sh status
```

### View Real-time Logs
```bash
# Java application logs
tail -f /opt/sensei-search/logs/app.log

# Python service logs
tail -f /opt/sensei-search/logs/python-ai.log
```

### Check System Resources
```bash
# Memory usage
free -h

# Disk usage
df -h

# Process status
ps aux | grep java
ps aux | grep python
```

## Troubleshooting

### Services Won't Start

1. Check logs for errors:
   ```bash
   ./scripts/manage.sh logs
   ```

2. Verify database connection:
   ```bash
   sudo -u postgres psql -d sensei_search -c "SELECT 1;"
   ```

3. Check if ports are available:
   ```bash
   netstat -tlnp | grep -E ':8080|:8000'
   ```

### Database Issues

1. Reset database:
   ```bash
   sudo -u postgres psql -c "DROP DATABASE sensei_search;"
   sudo -u postgres psql -c "CREATE DATABASE sensei_search;"
   sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE sensei_search TO sensei_user;"
   ```

2. Check database connection:
   ```bash
   sudo -u postgres psql -d sensei_search -U sensei_user
   ```

### Python Environment Issues

1. Recreate virtual environment:
   ```bash
   cd /opt/sensei-search/python-ai-service
   rm -rf venv
   python3 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
   ```

### Memory Issues

If the t2.micro instance runs out of memory:

1. Add swap space:
   ```bash
   sudo fallocate -l 1G /swapfile
   sudo chmod 600 /swapfile
   sudo mkswap /swapfile
   sudo swapon /swapfile
   ```

2. Reduce Java heap size in `deploy.sh`:
   ```bash
   java -Xmx200m -jar ...
   ```

## Security Considerations

- Environment file permissions are set to 600 (owner read/write only)
- Database password should be changed from default
- Consider setting up SSL certificates with Let's Encrypt
- Nginx configuration includes security headers

## Updating the Application

To deploy updates:

```bash
cd /opt/sensei-search
./scripts/deploy.sh
```

This will:
1. Pull latest code from Git
2. Build the application
3. Stop running services
4. Deploy new version
5. Start services

## Domain Setup

1. In AWS Route 53, create a hosted zone for your domain
2. Create an A record pointing to your EC2 instance's public IP
3. Update your domain registrar's name servers to use Route 53's name servers

## Optional: SSL Certificate

To set up HTTPS with Let's Encrypt:

```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx

# Get certificate
sudo certbot --nginx -d sensei-search.com -d www.sensei-search.com

# Test auto-renewal
sudo certbot renew --dry-run
```

## Cost Optimization

- Use t2.micro for free tier eligibility
- Monitor memory usage and add swap if needed
- Consider using Amazon Linux 2 instead of Ubuntu for lower resource usage
- Keep local PostgreSQL and static files to avoid RDS/S3 costs