const { app, BrowserWindow, ipcMain, dialog } = require("electron");
// const { readFileSync, writeFileSync } = require("fs");
const join_path = require("path").join;

function createWindow () {
    const win = new BrowserWindow({
        webPreferences : {
            contextIsolation : false,
            nodeIntegration : true,
        }
    });
    win.loadFile(join_path(__dirname, "html", "title.html"));
    win.addListener("close", () => {
        delete seamless_argv[win.webContents.id];
    });
}

let seamless_argv = {};

// function fetchServers () {
//     //
// }

app.whenReady().then(() => {
    createWindow();

    ipcMain.on("console:log", (_, data) => console.log(...data.map(v => JSON.parse(v))));
    ipcMain.on("console:fatal", (_, msg) => {dialog.showErrorBox("an error occured", msg);app.quit();});
    ipcMain.on("load:view", (f, ...args) => {BrowserWindow.fromId(f.sender.id).loadFile(join_path(__dirname, "html", `${args[0]}.html`));if(args.length>1){seamless_argv[f.sender.id]=args.slice(1);}});
    ipcMain.handle("request:args", (f) => {if(f.sender.id in seamless_argv){return seamless_argv[f.sender.id];};return process.argv;});
    // ipcMain.handle("request:servers", fetchServers);
});

app.on("window-all-closed", () => {
    app.quit();
});