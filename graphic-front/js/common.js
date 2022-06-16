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
     * @param {{ server_name: string; host_ip: string; host_port: number; auth_ip: string; auth_port: number; profile_name: string; }} param0 
     */
    constructor ({ server_name, host_ip, host_port, auth_ip, auth_port, profile_name }) {
        this.server_name = server_name;
        this.host_ip = host_ip;
        this.host_port = host_port;
        this.auth_ip = auth_ip;
        this.auth_port = auth_port;
        this.profile_name = profile_name;
        async function getLen (res, argbyte) {
            const connection = new NSocket();
            connection.connect(host_port, host_ip, async () => {
                connection.write([0x01, argbyte]);
                const len = await connection.read(8, {default:Buffer.alloc(8, 0)});
                res((len[0] << 56) | (len[1] << 48) | (len[2] << 40) | (len[3] << 32) | (len[4] << 24) | (len[5] << 16) | (len[6] << 8) | len[7]);
            });
        }
        this.get_status = () => {
            return new Promise(async (res, _) => {
                const sock = new NSocket();
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
            });
        };
        this.get_max_players = () => {
            return new Promise(async (res, _) => {
                await getLen(res, 0x01);
            });
        }
        this.get_connected_player_count = () => {
            return new Promise(async (res, _) => {
                await getLen(res, 0x02);
            });
        }
    }
    /**
     * gets max and current player counts
     * @param {()=>Promise<number>} get_max
     * @param {()=>Promise<number>} get_current
     * @returns {Promise<[number, number], [number, any]>}
     */
    get_server_player_counts () {
        return new Promise((res, rej) => {
            let max;
            let cur;
            this.get_max_players().then(v => {max = v; if ((cur ?? false) === cur) {res([max, cur]);}}, reason => rej([0, reason]));
            this.get_connected_player_count().then(v => {cur = v; if ((max ?? false) === max) {res([max, cur]);}}, reason => rej([1, reason]));
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