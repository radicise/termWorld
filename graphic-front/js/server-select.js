const { send, invoke, load_view, ServerDescriptor, mkTmp, NSocket } = require("../js/common");
const { readFileSync, writeFileSync } = require("fs");
// import { read } from "../block-read";
// const { NSocket } = require("../defs");
// const Socket = require("net").Socket;
const join_path = require("path").join;

mkTmp({defaultData:"{\n    \"servers\": []\n}",path:["..", "data", "servers.json"]});

/**@type {HTMLDivElement} */
const server_list = document.getElementById("server-cont");

/**@type {ServerDescriptor[]} */
let servers;

const server_data_path = join_path(__dirname, "..", "data", "servers.json");

function save_servers () {
    writeFileSync(server_data_path, JSON.stringify({servers:servers.map(v => v.export())}));
}

/**
 * @returns {ServerDescriptor[]}
 */
function fetchServers () {
    /**@type {{host_ip:string,auth_ip:string,host_port:number,auth_port:number,server_name:string,profile_name:string}[]} */
    const datal = JSON.parse(readFileSync(server_data_path, {encoding:"utf-8"})).servers;
    return datal.map(data => new ServerDescriptor(data));
}

function updateServerCounts () {
    let i = 0;
    const x = ["active", "full", "offline"];
    for (const server of servers) {
        server.get_server_player_counts().then(v => {
            server_list.children[i].querySelector("span.server-info").querySelector("span.server-players").textContent = `${v[1]} / ${v[0]}`;
        });
        server.get_status().then(v => {
            const status = server_list.children[i].querySelector("span.server-info").querySelector("span.server-status");
            status.textContent = x[v];
            status.className = `server-status status-${x[v]}`;
        });
        i ++;
    }
}

async function reloadServers () {
    server_list.replaceChildren();
    servers = fetchServers();
    const x = ["active", "full", "offline"];
    // servers = await invoke("request:servers");
    for (const server of servers) {
        const cd = document.createElement("div");
        const img = document.createElement("img");
        const cs = document.createElement("span");
        img.className = "server-icon";
        img.src = server.icon ?? "../assets/default-icon.svg";
        cs.className = "server-info";
        const name = document.createElement("span");
        name.className = "server-name";
        const players = document.createElement("span");
        players.className = "server-players";
        const status = document.createElement("span");
        name.textContent = server.server_name;
        server.get_server_player_counts().then(v => players.textContent = `${v[1]} / ${v[0]}`);
        server.get_status().then(v => {
            const sico = document.createElement("img");
            sico.src = `../assets/status-icos/${x[v]}.svg`;
            status.appendChild(sico);
            status.append(x[v]);
            // status.textContent = x[v];
            status.className = `server-status status-${x[v]}`;
        });
        cs.replaceChildren(name, players, status);
        cd.replaceChildren(img, cs);
        server_list.appendChild(cd);
    }
}

reloadServers();


document.getElementById("back-btn").addEventListener("click", () => {
    load_view("title");
});

document.getElementById("add-server-btn").addEventListener("click", () => {
    //
});