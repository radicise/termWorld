const { log, fatal_err, send } = require("../js/common");

document.getElementById("play-btn").addEventListener("click", () => {
    send("load:view", "server-select");
});