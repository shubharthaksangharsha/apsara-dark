# Deployment Guide — Apsara Dark Backend on Oracle Cloud

## Your Setup

- **Server IP**: `80.225.224.160`
- **DNS**: `apsara-dark-backend.devshubh.me` → `80.225.224.160` (A Record)
- **Backend path on server**: `/home/ubuntu/apsara-dark-backend/backend`
- **Backend port**: `3012`

---

## Prerequisites

- **Oracle Cloud** VM (or any Ubuntu/Debian VPS) with a public IP
- **Node.js ≥ 20** installed
- **Caddy v2** installed
- DNS A record: `apsara-dark-backend.devshubh.me` → `80.225.224.160`
- A valid **Gemini API Key**

---

## 1. Set up the server (if not done)

```bash
# SSH into your Oracle Cloud VM
ssh ubuntu@80.225.224.160

# Install Node.js 20+ (if not already)
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs

# Install Caddy
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install caddy

# Install PM2 globally
sudo npm install -g pm2
```

## 2. Configure .env

```bash
cd /home/ubuntu/apsara-dark-backend/backend
nano .env
```

Make sure `.env` contains:
```
GEMINI_API_KEY=your_actual_api_key_here
PORT=3012
HOST=0.0.0.0
```

## 3. Configure Caddy

**Yes, you can directly append/merge the Caddyfile content into your existing Caddy config.**

If you already have other sites in `/etc/caddy/Caddyfile`, just **append** the apsara-dark-backend block at the end:

```bash
sudo nano /etc/caddy/Caddyfile
```

Add this block (don't remove your existing blocks):

```caddyfile
apsara-dark-backend.devshubh.me {
    reverse_proxy localhost:3012
    
    header {
        X-Content-Type-Options nosniff
        X-Frame-Options DENY
        Referrer-Policy strict-origin-when-cross-origin
    }

    log {
        output file /var/log/caddy/apsara-dark-backend.log
        format json
    }
}
```

Then:
```bash
# Validate the Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile

# Reload Caddy (zero-downtime, no restart needed)
sudo systemctl reload caddy

# Or if reload doesn't work:
sudo systemctl restart caddy
```

> **Important**: Caddy auto-provisions TLS via Let's Encrypt. Just make sure ports 80 and 443 are open.

## 4. Option A: PM2 (Recommended — easiest)

```bash
# Install PM2 globally (if not already)
sudo npm install -g pm2

# Start the backend with PM2
cd /home/ubuntu/apsara-dark-backend/backend
pm2 start src/server.js --name apsara-dark-backend

# Save the PM2 process list so it auto-starts on reboot
pm2 save

# Set PM2 to start on boot
pm2 startup
# ↑ This prints a command — copy and run it (starts with sudo env ...)

# Useful PM2 commands:
pm2 status                      # See all processes
pm2 logs apsara-dark-backend    # View live logs
pm2 restart apsara-dark-backend # Restart
pm2 stop apsara-dark-backend    # Stop
pm2 delete apsara-dark-backend  # Remove from PM2
pm2 monit                       # Real-time monitoring dashboard
```

## 4. Option B: systemd service (alternative)

Create `/etc/systemd/system/apsara-dark-backend.service`:

```ini
[Unit]
Description=Apsara Dark - Gemini Live Backend
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/apsara-dark-backend/backend
ExecStart=/usr/bin/node src/server.js
Restart=on-failure
RestartSec=5
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
```

Then:
```bash
sudo systemctl daemon-reload
sudo systemctl start apsara-dark-backend
sudo systemctl enable apsara-dark-backend
sudo systemctl status apsara-dark-backend
```

> Use PM2 OR systemd — not both. PM2 is easier to manage.

## 5. Oracle Cloud Firewall

Make sure ports **80** and **443** are open in:

### A. Oracle Cloud Security List
VCN → Subnet → Security Lists → Ingress Rules:
- Port 80 (TCP) from 0.0.0.0/0
- Port 443 (TCP) from 0.0.0.0/0

### B. iptables on the VM

```bash
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 6. Verify

```bash
# Health check
curl https://apsara-dark-backend.devshubh.me/health

# Config endpoint
curl https://apsara-dark-backend.devshubh.me/config

# WebSocket test
npx wscat -c wss://apsara-dark-backend.devshubh.me/live
```

---

## Architecture

```
Android App (OkHttp WebSocket)
        │
        │  wss://apsara-dark-backend.devshubh.me/live
        ▼
   ┌─────────┐
   │  Caddy   │  (TLS termination, reverse proxy)
   │  :443    │
   └────┬─────┘
        │  ws://localhost:3012/live
        ▼
   ┌──────────────────────────────────────┐
   │  Node.js Backend (PM2 managed)       │
   │  /home/ubuntu/apsara-dark-backend/   │
   │  :3012                               │
   └────┬─────────────────────────────────┘
        │  Gemini Live API (WebSocket)
        ▼
   ┌──────────────┐
   │  Google       │
   │  Gemini API   │
   └──────────────┘
```

## Quick Reference — PM2 Commands

| Command | What it does |
|---------|-------------|
| `pm2 start src/server.js --name apsara-dark-backend` | Start |
| `pm2 restart apsara-dark-backend` | Restart |
| `pm2 stop apsara-dark-backend` | Stop |
| `pm2 logs apsara-dark-backend` | Live logs |
| `pm2 logs apsara-dark-backend --lines 50` | Last 50 log lines |
| `pm2 monit` | Real-time dashboard |
| `pm2 status` | Process list |
| `pm2 save` | Save process list for auto-start |
| `pm2 startup` | Configure boot auto-start |

## Updating

```bash
cd /home/ubuntu/apsara-dark-backend/backend
git pull  # or scp new files
npm install --production
pm2 restart apsara-dark-backend
```
