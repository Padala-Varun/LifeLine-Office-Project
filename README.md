# LifeLine battery monitor

Open the device page on a phone or laptop and it reports that device's charge.
When the charge drops below 25% and the device is not charging, the dashboard shows an alert straight away.

| Part | Folder | Built with |
|---|---|---|
| Device page | `frontend/projects/device` | Angular 22 |
| Dashboard | `frontend/projects/dashboard` | Angular 22 |
| Server | `backend` | Spring Boot 4.2, Java 21 |
| Database | `backend/data` (created on first run) | H2, embedded |

## Start

Double-click `start.cmd`, or run it in a terminal. It builds everything and prints the addresses and the dashboard password.
Use `start.cmd -SkipBuild` to start again without rebuilding.

You need Node 24 or newer, and JDK 21 or newer: either unpacked in `.tools/jdk`, or with `JAVA_HOME` set.

## Connect a phone

1. Put the phone on the same Wi-Fi as this computer.
2. If Windows asks whether Java may use the network, allow it for the type your Wi-Fi is set to (Private or Public).
3. Open the `https://…:8443/device/` address printed at start.
4. The browser warns about the certificate because it is self-made. Choose Advanced, then Proceed.
5. Name the device and tap "Share charge with dashboard".

A real domain with a real certificate removes the warning. Plain `http://` addresses other than localhost cannot work, because browsers hide the battery from unsecured pages.

## What it can and cannot read

| Works | Does not work |
|---|---|
| Chrome, Edge, Opera and Samsung Internet on Android | iPhone and iPad, in any browser |
| Chrome, Edge and Opera on laptops | Safari, Firefox |

- Readings stop when the page is closed or the screen locks. The dashboard then shows the device as offline with its last known charge.
- A desktop PC without a battery usually reports 100% and charging.
- Clearing browser data makes a device register again as a new one.

## Alert rules

Set in `backend/src/main/resources/application.properties`.

- Below 25% and not charging: one alert.
- The alert clears when the device starts charging or goes above 30%.
- No reading for 150 seconds: shown as offline.

## Passwords

The dashboard username and password are set in `backend/src/main/resources/application.properties`. Change them there and rebuild.
`start.ps1` creates the certificate password once in `backend/local/secrets.json`, which git ignores.
On your own network, anyone who can open the device page can add a device. Set `lifeline.join-code` in the same file to require a code.

## Deploy to the internet (Google Cloud free tier)

Both websites and the server run on one free `e2-micro` VM, with HTTPS from Caddy and a free DuckDNS name.

1. On this computer, build the bundle: `powershell -ExecutionPolicy Bypass -File deploy\package.ps1`
2. Sign in at https://www.duckdns.org and add a name. Keep the page open, because you need the name and the token.
3. Open https://shell.cloud.google.com in a project with billing turned on. Use the three-dot menu, then Upload, to upload `deploy\lifeline-deploy.tar.gz`.
4. Run `rm -rf lifeline && tar xzf lifeline-deploy.tar.gz && bash lifeline/deploy.sh`

It prints the dashboard address, a new password and the device link. The public server does not use the password above.
To update the app later, repeat steps 1, 3 and 4. Data, passwords and the address are kept.

- Stay inside the free tier: one `e2-micro`, a standard disk of at most 30 GB, and 1 GB of outgoing traffic a month. Set a budget alert.
- Change the password or join code on the VM: `sudo nano /etc/lifeline/lifeline.env`, then `sudo systemctl restart lifeline`.
- Read the server log on the VM: `sudo journalctl -u lifeline -n 100`
- Ten wrong passwords or join codes from one address mean a 15 minute wait.
- The database is one file on the VM, in `/var/lib/lifeline/data`. It is not backed up.

## Development

```
cd backend   && mvnw.cmd spring-boot:run     # http://localhost:8080
cd frontend  && npm run start:device         # http://localhost:4300/device/
cd frontend  && npm run start:dashboard      # http://localhost:4200/dashboard/
```

Tests: `mvnw.cmd test` in `backend`, `npm test` in `frontend`. `mvnw.cmd` needs `JAVA_HOME`.

## Adding PostgreSQL later

1. In `backend/pom.xml`, replace `com.h2database:h2` with `org.postgresql:postgresql`. Keep H2 with `<scope>test</scope>` for the tests.
2. In `application.properties`, set `spring.datasource.url=jdbc:postgresql://localhost:5432/lifeline` and its username and password.

Hibernate creates the tables. The code does not change. Existing H2 data is not copied over.
