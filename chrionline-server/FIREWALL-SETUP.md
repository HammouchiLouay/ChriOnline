# Windows firewall: let other PCs reach the ChriOnline **socket server**

Remote clients do **not** connect to MySQL on your PC. They open a **TCP** connection to the **Java socket server** (default port **6000**). MySQL stays on `127.0.0.1` on the machine that runs `ServerMain`.

LAN **discovery** uses **UDP multicast** on port **47474** (group `239.255.42.73`) so clients can find your IPv4 without typing it. Some networks block multicast; in that case set `server.host` in `chrionline-client.properties` or use the last successful connection stored in the app.

## Client: first-run firewall prompt (Windows)

The JavaFX client performs a **short multicast listen** once per Windows user profile **before** the UI starts (`LanDiscoveryClient.firewallWarmupForWindows`). That network use is what typically makes Windows show *“Windows Defender Firewall has blocked some features of this app”* for **Java** (`java.exe` / `javaw.exe`), not for Logitech or other apps. Choose **Private networks** (recommended) and **Allow access** so discovery on UDP **47474** can work.

There is **no supported API** to open that exact system dialog from Java; the OS decides whether to prompt. If you missed the prompt or chose **Cancel**, add rules manually (below) or run again with `-Dchrionline.firewall.warmup.reset=true` to retry the warmup once.

| JVM option | Effect |
|------------|--------|
| `-Dchrionline.firewall.warmup.skip=true` | Do not run the multicast warmup |
| `-Dchrionline.firewall.warmup.always=true` | Run warmup on every launch (testing) |
| `-Dchrionline.firewall.warmup.reset=true` | Clear the “warmup done” preference so the next start runs it again |

## 1. Allow inbound TCP on the server port (required)

1. Open **Windows Security** → **Firewall & network protection** → **Advanced settings**.
2. **Inbound Rules** → **New Rule…**
3. **Port** → **TCP** → specific local ports: **6000** (or the port you pass to `ServerMain`).
4. **Allow the connection** → check **Domain**, **Private**, and **Public** as appropriate (for a home LAN, **Private** is often enough).
5. Name it e.g. `ChriOnline TCP 6000`.

Repeat for **UDP 47474** inbound if discovery does not work and you suspect the firewall (optional; many setups allow local multicast without a rule).

## 2. Scope rules (recommended)

- Prefer restricting **Remote IP** to your LAN subnet (e.g. `192.168.1.0/24`) instead of “Any” if you expose **Public** profiles.

## 3. Router / NAT (Internet, not same Wi‑Fi)

- Forward **TCP 6000** from your router to the PC running `ServerMain`.
- Discovery over multicast **does not** work over the Internet. Set `server.host` in `chrionline-client.properties` to your **public hostname** (e.g. Dynamic DNS) or IP, or pass `-Dchrionline.server.host=...`.
- **Do not** expose MySQL (3306) to the Internet unless you know the risks.

## 4. Taskbar icon still shows the Java cup

When you run `java` or `javafx:run`, Windows may show the **JVM** icon in the taskbar. `Stage.getIcons()` still helps the window; for a branded taskbar icon, build a native installer with **`jpackage`** and pass **`--icon path/to.ico`**.
