const { log, fatal_err, send, load_view } = require("../js/common");

document.getElementById("play-btn").addEventListener("click", () => {
    load_view("server-select");
});

document.getElementById("server-manage-btn").addEventListener("click", () => {
    load_view("manage");
});