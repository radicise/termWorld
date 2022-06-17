const { ipcRenderer } = require("electron");
const { NSocket } = require("../socket");
const { writeFileSync, existsSync, mkdirSync } = require("fs");
const join_path = require("path").join;

/**
 * makes a tmp file
 * @param {{defaultData:string,path:string[]}} param0
 */
function mkTmp ({defaultData, path}) {
    defaultData = defaultData ?? "";
    for (let i = 0; i < path.length; i++) {
        const c = path.slice(0, i+1);
        const p = join_path(__dirname, ...c);
        if (existsSync(p)) continue;
        if (p.includes(".")) {
            writeFileSync(p, defaultData, {encoding:"utf-8"});
        } else {
            mkdirSync(p);
        }
    }
}

function log (...data) {
    ipcRenderer.send("console:log", data.map(v => JSON.stringify(v)));
}

/**
 * @param msg error message
 */
function fatal_err (msg) {
    ipcRenderer.send("console:fatal", msg);
}

class ServerDescriptor {
    /**
     * describes server data
     * @param {{ server_name: string; host_ip: string; host_port: number; auth_ip: string; auth_port: number; profile_name: string; icon? : string}} param0 
     */
    constructor ({ server_name, host_ip, host_port, auth_ip, auth_port, profile_name, icon }) {
        /**@type {string|undefined|null} */
        this.icon = icon;
        this.server_name = server_name;
        this.host_ip = host_ip;
        this.host_port = host_port;
        this.auth_ip = auth_ip;
        this.auth_port = auth_port;
        this.profile_name = profile_name;
        // this.get_max_players = () => {
        //     const erE = new Error();
        //     return new Promise(async (res, _) => {
        //         try {
        //         await getLen(res, 0x01);
        //         } catch (e) {e.stack+=erE.stack;throw e;}
        //     });
        // }
        // this.get_connected_player_count = () => {
        //     const erE = new Error();
        //     return new Promise(async (res, _) => {
        //         try {
        //         await getLen(res, 0x02);
        //         } catch (e) {e.stack+=erE.stack;throw e;}
        //     });
        // }
    }
    /**
     * @returns {Promise<number>}
     */
    get_status () {
        const erE = new Error();
        return new Promise(async (res, _) => {
            try {
            const sock = new NSocket();
            sock.on("error", () => {
                res(2);
            });
            sock.setTimeout(5000, () => {
                sock.destroy();
                res(2);return;
            });
            sock.connect(this.host_port, this.host_ip, async () => {
                const r = (await sock.read(1, {default:0x00}))[0];
                if (r === 0x00) {sock.destroy();res(2);return;}
                if (r === 0x01) {sock.end();res(0);return;}
                if (r === 0x02) {sock.end();res(1);return;}
            });
            } catch (e) {e.stack+=erE.stack;throw e;}
        });
    }
    /**
     * gets max and current player counts
     * @returns {Promise<[number, number], [number, any]>}
     */
    get_server_player_counts () {
        return new Promise(async (res, _) => {
            const erE = new Error();
            try {
            const connection = new NSocket();
            connection.on("error", () => {
                res([0, 0]);
            });
            connection.connect(this.host_port, this.host_ip, async () => {
                connection.write([0x64, 0x01]);
                const len = await connection.read(4, {default:Buffer.alloc(4, 0)});
                res([(len[0] << 8) | len[1], (len[2] << 8) | len[3]]);
            });
            } catch (e) {e.stack+=erE.stack;throw e;}
            // let max;
            // let cur;
            // this.get_max_players().then(v => {max = v; if ((cur ?? false) === cur) {res([max, cur]);}}, reason => rej([0, reason]));
            // this.get_connected_player_count().then(v => {cur = v; if ((max ?? false) === max) {res([max, cur]);}}, reason => rej([1, reason]));
        });
    }
    /**
     * exports data to JSON compatable
     * @returns {{ server_name: string; host_ip: string; host_port: number; auth_ip: string; auth_port: number; profile_name: string; }}
     */
    export () {
        return {auth_ip:this.auth_ip,auth_port:this.auth_port,host_ip:this.host_ip,host_port:this.host_port,server_name:this.server_name,profile_name:this.profile_name};
    }
}

exports.ServerDescriptor = ServerDescriptor;
exports.NSocket = NSocket;
exports.log = log;
exports.fatal_err = fatal_err;
exports.send = ipcRenderer.send;
exports.invoke = ipcRenderer.invoke;
exports.mkTmp = mkTmp;