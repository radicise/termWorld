const { send, invoke, ServerDescriptor } = require("../js/common");
const { readFileSync, writeFileSync } = require("fs");
const { read } = require("../block-read");
const Socket = require("net").Socket;
const join_path = require("path").join;

/**@type {HTMLDivElement} */
const server_list = document.getElementById("server-cont");

/**@type {ServerDescriptor[]} */
let servers;

const server_data_path = join_path(__dirname, "..", "data", "servers.json");

/**
 * @returns {ServerDescriptor[]}
 */
function fetchServers () {
    /**@type {{host_ip:String,auth_ip:String,host_port:Number,auth_port:Number,server_name:String,profile_name:String}[]} */
    const datal = JSON.parse(readFileSync(server_data_path)).servers;
    return datal.map(data => {
        data.get_max_players = () => {
            return new Promise(async (res, rej) => {
                const sock = new Socket();
                const timeoutID = setTimeout(() => {
                    rej("connection timeout");
                }, 15000);
                sock.on("error", (err) => {rej(`an error ocurred ${err.toString()}`)})
                sock.connect(data.host_port, data.host_ip, () => {
                    clearTimeout(timeoutID);
                    //
                });
            });
        };
    });
}

/**
 * gets max and current player counts
 * @param {()=>Promise<Number>} get_max
 * @param {()=>Promise<Number>} get_current
 * @returns {Promise<[Number, Number], [Number, any]>}
 */
function get_server_player_counts (get_max, get_current) {
    return new Promise((res, rej) => {
        let max;
        let cur;
        get_max().then(v => {max = v; if (cur ?? false === cur) {res([max, cur]);}}, reason => rej([0, reason]));
        get_current().then(v => {cur = v; if (max ?? false === max) {res([max, cur]);}}, reason => rej([1, reason]));
    });
}

async function reloadServers () {
    server_list.replaceChildren();
    servers = fetchServers();
    // servers = await invoke("request:servers");
    for (const server of servers) {
        const cd = document.createElement("div");
        const img = document.createElement("img");
        const cs = document.createElement("span");
        img.className = "server-icon";
        cs.className = "server-info";
        const name = document.createElement("span");
        name.className = "server-name";
        const players = document.createElement("span");
        players.className = "server-players";
        name.textContent = server.server_name;
        get_server_player_counts().then(v => players.textContent = v);
    }
}

reloadServers();


document.getElementById("back-btn").addEventListener("click", () => {
    send("load:view", "title");
});