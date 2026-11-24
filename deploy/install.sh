#!/usr/bin/env bash
# Runs on the VM (Ubuntu 24.04), started by deploy.sh. Safe to run again: it keeps data and passwords.
set -euo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [[ $EUID -ne 0 ]]; then
  echo "Run this with sudo." >&2
  exit 1
fi

SECRETS=/etc/lifeline/lifeline.env
DNS=/etc/lifeline/duckdns.curl
DOMAIN_FILE=/etc/lifeline/domain

random_text() { # length
  head -c 4096 /dev/urandom | LC_ALL=C tr -dc 'ABCDEFGHJKMNPQRSTUVWXYZ23456789' | cut -c "1-$1"
}

retry() { # Ubuntu may still be installing its own updates on a new VM.
  local attempt
  for attempt in {1..20}; do
    "$@" && return 0
    echo "  Trying again in 15 seconds ($attempt of 20)..."
    sleep 15
  done
  return 1
}

apt_install() {
  DEBIAN_FRONTEND=noninteractive retry \
    apt-get -o DPkg::Lock::Timeout=300 -o Dpkg::Options::=--force-confold install -y -q "$@"
}

# --- Web address -------------------------------------------------------------
command -v curl > /dev/null || { retry apt-get update -q; apt_install curl; }
install -d -m 700 /etc/lifeline
if [[ ! -s $DOMAIN_FILE ]]; then
  echo
  echo "Enter the details from https://www.duckdns.org"
  while true; do
    read -rp "  Name (the part before .duckdns.org): " name
    read -rsp "  Token (hidden while you paste it): " token
    echo
    name=${name,,}
    name=${name//[[:space:]]/}
    name=${name%.duckdns.org}
    token=${token//[[:space:]]/}
    if [[ ! $name =~ ^[a-z0-9-]+$ || ! $token =~ ^[0-9a-fA-F-]{36}$ ]]; then
      echo "  That name or token does not look right. Try again."
      continue
    fi
    # Kept in a root-only file so the token never shows up in the process list.
    (umask 077; printf 'url = "https://www.duckdns.org/update?domains=%s&token=%s&ip="\n' "$name" "$token" > "$DNS")
    if [[ $(curl -sS --max-time 30 --config "$DNS") == OK ]]; then
      echo "$name.duckdns.org" > "$DOMAIN_FILE"
      break
    fi
    echo "  DuckDNS did not accept that name and token. Try again."
  done
fi
domain=$(<"$DOMAIN_FILE")

# --- Memory: the VM has 1 GB, so add 1 GB of swap ------------------------------
if [[ ! -f /swapfile ]]; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile > /dev/null
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# --- Java and Caddy ------------------------------------------------------------
echo "Installing Java and Caddy (a few minutes the first time)..."
retry apt-get update -q
apt_install openjdk-21-jre-headless curl gnupg debian-keyring debian-archive-keyring apt-transport-https
if [[ ! -s /etc/apt/sources.list.d/caddy-stable.list ]]; then
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key |
    gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt -o /etc/apt/sources.list.d/caddy-stable.list
  chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
  retry apt-get update -q
fi
apt_install caddy

# --- Passwords: created once, readable by root only -----------------------------
if [[ ! -s $SECRETS ]]; then
  (umask 077; printf 'LIFELINE_DASHBOARD_PASSWORD=%s\nLIFELINE_JOIN_CODE=%s\n' \
    "$(random_text 16)" "$(random_text 8)" > "$SECRETS")
fi

# --- The app -------------------------------------------------------------------
id lifeline > /dev/null 2>&1 || useradd --system --home-dir /var/lib/lifeline --shell /usr/sbin/nologin lifeline
install -d /opt/lifeline
install -m 644 "$here/lifeline.jar" /opt/lifeline/lifeline.jar.new
mv /opt/lifeline/lifeline.jar.new /opt/lifeline/lifeline.jar

cat > /etc/systemd/system/lifeline.service <<'UNIT'
[Unit]
Description=LifeLine battery monitor
After=network.target

[Service]
User=lifeline
StateDirectory=lifeline
WorkingDirectory=/var/lib/lifeline
EnvironmentFile=/etc/lifeline/lifeline.env
ExecStart=/usr/bin/java -Xmx256m -XX:+UseSerialGC -jar /opt/lifeline/lifeline.jar --spring.profiles.active=cloud
SuccessExitStatus=143
Restart=always
RestartSec=5
NoNewPrivileges=yes
ProtectSystem=strict
ProtectHome=yes
PrivateTmp=yes

[Install]
WantedBy=multi-user.target
UNIT

# The VM's address can change if it is stopped and started, so keep DuckDNS up to date.
cat > /etc/systemd/system/lifeline-dns.service <<'UNIT'
[Unit]
Description=Point the DuckDNS name at this VM
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/bin/curl -sS --max-time 30 --config /etc/lifeline/duckdns.curl
UNIT

cat > /etc/systemd/system/lifeline-dns.timer <<'UNIT'
[Unit]
Description=Keep the DuckDNS name up to date

[Timer]
OnBootSec=15s
OnUnitActiveSec=5min

[Install]
WantedBy=timers.target
UNIT

cat > /etc/caddy/Caddyfile <<CADDY
$domain {
	encode zstd gzip
	reverse_proxy 127.0.0.1:8080
}
CADDY

systemctl daemon-reload
systemctl enable -q lifeline caddy
systemctl enable -q --now lifeline-dns.timer
systemctl start lifeline-dns.service
systemctl restart lifeline caddy

# --- Check that it works -------------------------------------------------------
wait_for() { # url
  local attempt
  for attempt in {1..60}; do
    curl -fsS --max-time 5 -o /dev/null "$1" 2> /dev/null && return 0
    sleep 5
  done
  return 1
}

echo "Starting the app..."
if ! wait_for http://127.0.0.1:8080/api/auth/me; then
  echo "The app did not start. Its last messages:" >&2
  journalctl -u lifeline -n 40 --no-pager >&2
  exit 1
fi
echo "Getting the HTTPS certificate..."
if ! wait_for "https://$domain/api/auth/me"; then
  echo "The app runs, but https://$domain is not reachable yet. Caddy's last messages:" >&2
  journalctl -u caddy -n 20 --no-pager >&2
  echo "Certificates can take a while. Wait 10 minutes and open the address. If it still fails, run this again." >&2
  exit 1
fi

# shellcheck source=/dev/null
source "$SECRETS"
cat <<DONE

  Deployed.

  Dashboard     https://$domain/dashboard/
  Sign-in       admin / $LIFELINE_DASHBOARD_PASSWORD

  Device link   https://$domain/device/#join=$LIFELINE_JOIN_CODE
                Open it on each device to add that device.

  Save these. To see the password and join code again: sudo cat $SECRETS

DONE
