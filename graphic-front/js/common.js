const { ipcRenderer } = require("electron");

function log (...data) {
    ipcRenderer.send("console:log", data.map(v => JSON.stringify(v)));
}

/**
 * @param {String} msg
 */
function fatal_err (msg) {
    ipcRenderer.send("console:fatal", msg);
}

/**
 * @typedef ServerDescriptor
 * @type {{
 * server_name : String,
 * host_ip : String,
 * host_port : Number,
 * auth_ip : String,
 * auth_port : Number,
 * profile_name : String,
 * max_players : () => Promise<Number>,
 * get_connected_player_count : () => Promise<Number>,
 * }}
 */

exports.log = log;
exports.fatal_err = fatal_err;
exports.send = ipcRenderer.send;
exports.invoke = ipcRenderer.invoke;
exports.ServerDescriptor = this.ServerDescriptor;