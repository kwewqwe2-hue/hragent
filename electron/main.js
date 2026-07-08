const { app, BrowserWindow, dialog } = require("electron");
const path = require("path");
const { spawn } = require("child_process");
const http = require("http");

const PORT = 8765;
const APP_URL = `http://127.0.0.1:${PORT}`;

let mainWindow = null;
let backend = null;

function appRoot() {
  return app.isPackaged ? path.join(process.resourcesPath, "app") : path.resolve(__dirname, "..");
}

function pythonPath() {
  const root = appRoot();
  const local = path.join(root, "runtime", "python", "python.exe");
  return local;
}

function backendPath() {
  return path.join(appRoot(), "app.py");
}

function healthCheck(timeoutMs = 800) {
  return new Promise((resolve) => {
    const req = http.get(`${APP_URL}/api/health`, (res) => {
      res.resume();
      resolve(res.statusCode === 200);
    });
    req.on("error", () => resolve(false));
    req.setTimeout(timeoutMs, () => {
      req.destroy();
      resolve(false);
    });
  });
}

async function waitForBackend() {
  for (let i = 0; i < 40; i += 1) {
    if (await healthCheck()) return true;
    await new Promise((r) => setTimeout(r, 300));
  }
  return false;
}

async function startBackend() {
  if (await healthCheck()) return;

  const py = pythonPath();
  const script = backendPath();
  const cwd = appRoot();

  backend = spawn(py, [script], {
    cwd,
    windowsHide: true,
    stdio: "ignore"
  });

  backend.on("exit", () => {
    backend = null;
  });

  const ok = await waitForBackend();
  if (!ok) {
    dialog.showErrorBox(
      "HR智能体后端启动失败",
      `无法启动本地后端。\nPython: ${py}\nApp: ${script}\n请确认端口 ${PORT} 未被占用。`
    );
  }
}

function stopBackend() {
  if (backend && !backend.killed) {
    try {
      backend.kill();
    } catch (_) {
      // ignore
    }
  }
  backend = null;
}

async function createWindow() {
  await startBackend();

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 1080,
    minHeight: 680,
    title: "HR智能体",
    backgroundColor: "#f5f7fb",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  mainWindow.removeMenu();
  await mainWindow.loadURL(APP_URL);

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

app.whenReady().then(createWindow);

app.on("window-all-closed", () => {
  stopBackend();
  app.quit();
});

app.on("before-quit", () => {
  stopBackend();
});
