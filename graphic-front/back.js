const { app, BrowserWindow, ipcMain, dialog } = require("electron");

function createWindow () {
    const win = new BrowserWindow({
        webPreferences : {
            contextIsolation : false,
            nodeIntegration : true,
        }
    });
    win.loadFile("index.html");
}

app.whenReady().then(() => {
    createWindow();

    ipcMain.on("console:log", (_, data) => console.log(...data.map(v => JSON.parse(v))));
    ipcMain.on("console:fatal", (_, msg) => {dialog.showErrorBox("an error occured", msg);app.quit();});
    ipcMain.handle("request:args", () => process.argv);
});

app.on("window-all-closed", () => {
    app.quit();
});