# Mucker Zero-Install Dev Container

Experience Mucker and build the Android demo app entirely inside a container without installing Android Studio, Java, Gradle, or Android SDK on your local machine.

---

## ⚡ 1-Click Launch Options

### Option 1: GitHub Codespaces
1. Open the repository on GitHub.
2. Click **Code** → **Codespaces** → **Create codespace on main**.
3. GitHub Codespaces boots the pre-configured Ubuntu 24.04 environment with OpenJDK 17, Android SDK 34, and Node.js 22 LTS.
4. The post-create hook automatically compiles `:app:assembleDebug`.

### Option 2: VS Code Remote - Containers
1. Ensure [Docker Desktop](https://www.docker.com/products/docker-desktop/) and the [Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) extension are installed.
2. Open this project in VS Code.
3. When prompted, click **Reopen in Container** (or press `F1` → `Dev Containers: Reopen in Container`).

---

## 🛠️ What's Pre-configured Inside the Container

* **Java & Build Tools**: OpenJDK 17 (`java -version`), Gradle Wrapper (`./gradlew`)
* **Android SDK**: Pre-installed at `/opt/android-sdk` with `platform-tools`, `platforms;android-34`, `build-tools;34.0.0`
* **Node.js**: v22 LTS pre-installed with npm
* **Ports Auto-Forwarded**:
  * `8080`: Mucker Embedded Web Dashboard & CDP WebSocket Server
  * `4000`: Jekyll Documentation Server
  * `5173`: Vite Dashboard Dev Server

---

## 🚀 Commands Inside Container

```bash
# Build the Android Demo APK
./gradlew :app:assembleDebug

# Run unit and integration tests
./gradlew test
npm test --workspace=cli

# Run Chaos Engineering Scripts
npm run chaos:playwright
npm run chaos:puppeteer
```
