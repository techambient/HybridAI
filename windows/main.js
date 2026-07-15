const { app, BrowserWindow, dialog, shell } = require("electron");
const path = require("path");

let isQuitting = false;

async function showGitHubDialog(win) {
  const result = await dialog.showMessageBox(win, {
    type: "info",
    title: "Ambient HybridAI",
    message: "⭐ Enjoying HybridAI?",
    detail: "Remember to star us on GitHub to support the project!",
    buttons: ["⭐ Star on GitHub", "Later"],
    defaultId: 0,
    cancelId: 1
  });

  if (result.response === 0) {
    shell.openExternal("https://github.com/techambient/HybridAI");
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    autoHideMenuBar: true,
    icon: path.join(__dirname, "icon.ico")
  });

  win.loadFile("index.html");

  // Show on app startup
  showGitHubDialog(win);

  // Show again when the user closes the app
  win.on("close", async (event) => {
    if (isQuitting) return;

    event.preventDefault();

    await showGitHubDialog(win);

    isQuitting = true;
    win.close();
  });
}

app.whenReady().then(createWindow);

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});