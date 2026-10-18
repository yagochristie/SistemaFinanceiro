const { app, BrowserWindow, dialog, Menu } = require('electron');
const { spawn } = require('child_process');
const fs = require('fs');
const http = require('http');
const net = require('net');
const path = require('path');

let backendProcess = null;
let backendStartError = null;
let mainWindow = null;
let backendUrl = null;

function backendHealthUrl() {
  return `${backendUrl}/actuator/health`;
}

function appFilePath(...parts) {
  return app.isPackaged
    ? path.join(app.getAppPath(), ...parts)
    : path.join(__dirname, '..', ...parts);
}

function backendJarPath() {
  const packagedJar = path.join(process.resourcesPath, 'backend', 'wheel.jar');
  if (fs.existsSync(packagedJar)) return packagedJar;

  const devJar = appFilePath('wheel', 'target', 'wheel-0.0.1-SNAPSHOT.jar');
  if (fs.existsSync(devJar)) return devJar;

  return null;
}

function javaCommand() {
  const javaHome = process.env.JAVA_HOME;
  if (javaHome) {
    const javaFromHome = path.join(javaHome, 'bin', 'java.exe');
    if (fs.existsSync(javaFromHome)) return javaFromHome;
  }

  const javaDirectory = path.join(process.env.ProgramFiles || 'C:\\Program Files', 'Java');
  try {
    const installations = fs.readdirSync(javaDirectory, { withFileTypes: true })
      .filter((entry) => entry.isDirectory() && /^(jdk|jre)-/i.test(entry.name))
      .map((entry) => ({
        name: entry.name,
        executable: path.join(javaDirectory, entry.name, 'bin', 'java.exe'),
      }))
      .filter((installation) => fs.existsSync(installation.executable))
      .sort((left, right) => right.name.localeCompare(left.name, undefined, { numeric: true }))
      .map((installation) => installation.executable);

    if (installations.length) return installations[0];
  } catch {
    // Java pode estar disponivel apenas pelo PATH.
  }

  return 'java';
}

function legacyPackagedWorkDir() {
  return path.resolve(path.dirname(process.execPath), '..', '..', 'wheel');
}

function originalProjectWorkDir() {
  return path.join(app.getPath('documents'), 'SoftDarf', 'wheel');
}

function databaseFile(workDir) {
  return path.join(workDir, 'data', 'wheel.mv.db');
}

function backendWorkDirPath() {
  if (!app.isPackaged) return appFilePath('wheel');

  const legacyWorkDir = legacyPackagedWorkDir();
  if (fs.existsSync(databaseFile(legacyWorkDir))) {
    return legacyWorkDir;
  }

  const originalProjectDir = originalProjectWorkDir();
  if (fs.existsSync(databaseFile(originalProjectDir))) {
    return originalProjectDir;
  }

  return app.getPath('userData');
}

function escolherPortaBackend() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.unref();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = address && address.port;
      server.close(() => resolve(port));
    });
  });
}

function pingBackend(timeoutMs = 1500) {
  return new Promise((resolve) => {
    if (!backendUrl) {
      resolve(false);
      return;
    }
    const req = http.get(backendHealthUrl(), (res) => {
      let body = '';
      res.on('error', () => resolve(false));
      res.on('data', (chunk) => { body += chunk; });
      res.on('end', () => {
        try { resolve(res.statusCode === 200 && JSON.parse(body).status === 'UP'); }
        catch { resolve(false); }
      });
    });

    req.on('error', () => resolve(false));
    req.setTimeout(timeoutMs, () => {
      req.destroy();
      resolve(false);
    });
  });
}

async function waitForBackend(timeoutMs = 30000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt <= timeoutMs) {
    if (backendStartError) throw backendStartError;
    if (await pingBackend()) return;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }

  throw new Error('O backend demorou para iniciar.');
}

function startBackend(port) {
  const jar = backendJarPath();
  if (!jar) {
    throw new Error('Nao encontrei o arquivo wheel-0.0.1-SNAPSHOT.jar.');
  }

  const backendWorkDir = backendWorkDirPath();

  fs.mkdirSync(path.join(backendWorkDir, 'data'), { recursive: true });

  const datasourceUrl = 'jdbc:h2:file:' + path.join(backendWorkDir, 'data', 'wheel').replace(/\\/g, '/') + ';AUTO_SERVER=TRUE';

  backendUrl = `http://127.0.0.1:${port}`;
  backendStartError = null;
  backendProcess = spawn(javaCommand(), ['-jar', jar, `--server.port=${port}`, `--spring.datasource.url=${datasourceUrl}`], {
    cwd: backendWorkDir,
    windowsHide: true,
    stdio: 'ignore'
  });

  backendProcess.on('error', (error) => {
    backendStartError = error;
    backendProcess = null;
  });

  backendProcess.on('exit', () => {
    if (!backendStartError) {
      backendStartError = new Error('O backend encerrou antes de ficar pronto.');
    }
    backendProcess = null;
  });
}

function createWindow() {
  Menu.setApplicationMenu(null);

  const win = new BrowserWindow({
    width: 1160,
    height: 820,
    minWidth: 980,
    minHeight: 680,
    backgroundColor: '#0b0c10',
    title: 'SoftDarf',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  win.maximize();
  mainWindow = win;
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (event) => event.preventDefault());
  win.on('closed', () => {
    if (mainWindow === win) mainWindow = null;
  });

  win.loadFile(appFilePath('teste.html'), {
    query: { apiBase: backendUrl || 'http://127.0.0.1:8080' }
  }).catch((error) => {
    dialog.showErrorBox('Erro ao carregar a tela', error.message);
  });
}

const gotSingleInstanceLock = app.requestSingleInstanceLock();

if (!gotSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  });

  app.whenReady().then(async () => {
    try {
      const port = await escolherPortaBackend();
      startBackend(port);
      await waitForBackend();
      createWindow();
    } catch (error) {
      const detalhe = error && error.code === 'ENOENT'
        ? 'Java nao encontrado no PATH do Windows.'
        : error.message;
      dialog.showErrorBox(
        'Nao foi possivel abrir o SoftDarf',
        `${detalhe}\n\nVerifique se o Java esta instalado no computador.`
      );
      app.quit();
    }
  });
}

app.on('window-all-closed', () => {
  if (backendProcess) backendProcess.kill();
  app.quit();
});

app.on('before-quit', () => {
  if (backendProcess) backendProcess.kill();
});
