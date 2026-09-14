import defu$1, { defu } from "defu";
import { createFetch } from "ofetch";
import { createStorage } from "unstorage";
import { format } from "date-fns";
import * as mima from "mima-kit";
import { createContext } from "unctx";
//#region src/constants.ts
/**
* 森空岛签名时间向前偏移，规避服务端时间校验过严导致的误判。
*/
const SERVER_TIMESTAMP_OFFSET = 2 * 1e3;
const SKLAND_APP_CODE = "4ca99fa6b56cc2ba";
/**
* 森空岛访问 token
* @description 没下面的重要，会过期，类似 `accessToken`
*/
const STORAGE_OAUTH_TOKEN_KEY = "skland:token";
/**
* 森空岛应用授权凭证
* @description 用于获取权限的主要凭证，类似 `refreshToken`
*/
const STORAGE_CREDENTIAL_KEY = "skland:cred";
/**
* 森空岛用户 ID
*/
const STORAGE_USER_ID_KEY = "skland:userId";
/**
* 设备 ID
*/
const STORAGE_DID_KEY = "skland:did";
//#endregion
//#region src/utils/assert.ts
function assert(condition, errorOrMessage) {
	if (!condition) throw errorOrMessage instanceof Error ? errorOrMessage : new Error(errorOrMessage);
}
//#endregion
//#region src/utils/crypto.ts
const PEM_PUBLIC_KEY_HEADER_RE = /-----BEGIN PUBLIC KEY-----/;
const PEM_PUBLIC_KEY_FOOTER_RE = /-----END PUBLIC KEY-----/;
const WHITESPACE_RE = /\s/g;
const BASE64URL_DASH_RE = /-/g;
const BASE64URL_UNDERSCORE_RE = /_/g;
function md5(string) {
	return mima.md5(mima.UTF8(String(string))).to(mima.HEX);
}
function hmacSha256(key, data) {
	return mima.hmac(mima.sha256)(mima.UTF8(String(key)), mima.UTF8(data)).to(mima.HEX);
}
/**
* AES CBC 加密
*/
async function encryptAES(message, key) {
	const iv = new TextEncoder().encode("0102030405060708");
	const data = new TextEncoder().encode(message);
	const cryptoKey = await crypto.subtle.importKey("raw", new TextEncoder().encode(key), { name: "AES-CBC" }, false, ["encrypt"]);
	const encrypted = await crypto.subtle.encrypt({
		name: "AES-CBC",
		iv
	}, cryptoKey, data);
	return mima.HEX(new Uint8Array(encrypted));
}
function padData(data) {
	const blockSize = 8;
	const padLength = blockSize - data.length % blockSize;
	return data + "\0".repeat(padLength);
}
/**
* DES ECB 加密
*/
async function encryptDES(message, key) {
	const inputStr = padData(String(message));
	const TripleDES = mima.t_des(64);
	return mima.ecb(TripleDES, mima.NO_PAD)(mima.UTF8(key)).encrypt(mima.UTF8(inputStr)).to(mima.B64);
}
async function encryptObjectByDESRules(object, rules) {
	const result = {};
	for (const i in object) if (i in rules) {
		const rule = rules[i];
		if (rule.is_encrypt === 1) result[rule.obfuscated_name] = await encryptDES(object[i], rule.key);
		else result[rule.obfuscated_name] = object[i];
	} else result[i] = object[i];
	return result;
}
/**
* 从PEM格式的公钥中提取RSA参数的n和e (BigInt形式)
*/
async function extractJWKFromPEM(publicKeyPEM) {
	const pemContents = publicKeyPEM.replace(PEM_PUBLIC_KEY_HEADER_RE, "").replace(PEM_PUBLIC_KEY_FOOTER_RE, "").replace(WHITESPACE_RE, "");
	const binaryDer = atob(pemContents);
	const derBuffer = new Uint8Array(binaryDer.length);
	for (let i = 0; i < binaryDer.length; i++) derBuffer[i] = binaryDer.charCodeAt(i);
	const cryptoKey = await crypto.subtle.importKey("spki", derBuffer, {
		name: "RSA-OAEP",
		hash: "SHA-256"
	}, true, ["encrypt"]);
	const jwk = await crypto.subtle.exportKey("jwk", cryptoKey);
	return {
		n: base64URLToBigInt(jwk.n),
		e: base64URLToBigInt(jwk.e)
	};
}
function base64URLToBigInt(base64url) {
	const base64 = base64url.replace(BASE64URL_DASH_RE, "+").replace(BASE64URL_UNDERSCORE_RE, "/").padEnd(Math.ceil(base64url.length / 4) * 4, "=");
	const binaryStr = atob(base64);
	let result = 0n;
	for (let i = 0; i < binaryStr.length; i++) result = result << 8n | BigInt(binaryStr.charCodeAt(i));
	return result;
}
async function encryptRSA(message, publicKey) {
	const { n, e } = await extractJWKFromPEM(publicKey);
	const key = mima.rsa({
		n,
		e
	});
	return mima.pkcs1_es_1_5(key).encrypt(mima.UTF8(message)).to(mima.B64);
}
//#endregion
//#region src/utils/env.ts
/**
* 数美科技配置
* `window._smConf`
*/
const SKLAND_SM_CONFIG = {
	organization: "UWXspnCCJN4sfYlNfqps",
	appId: "default",
	publicKey: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCmxMNr7n8ZeT0tE1R9j/mPixoinPkeM+k4VGIn/s0k7N5rJAfnZ0eMER+QhwFvshzo0LNmeUkpR8uIlU/GEVr8mN28sKmwd2gpygqj0ePnBmOW4v0ZVwbSYK+izkhVFk2V/doLoMbWy6b+UnA8mkjvg0iYWRByfRsK2gdl7llqCwIDAQAB",
	protocol: "https",
	apiHost: "fp-it.portal101.cn",
	apiPath: "/deviceprofile/v4"
};
const DES_RULE = {
	appId: {
		cipher: "DES",
		is_encrypt: 1,
		key: "uy7mzc4h",
		obfuscated_name: "xx"
	},
	box: {
		is_encrypt: 0,
		obfuscated_name: "jf"
	},
	canvas: {
		cipher: "DES",
		is_encrypt: 1,
		key: "snrn887t",
		obfuscated_name: "yk"
	},
	clientSize: {
		cipher: "DES",
		is_encrypt: 1,
		key: "cpmjjgsu",
		obfuscated_name: "zx"
	},
	organization: {
		cipher: "DES",
		is_encrypt: 1,
		key: "78moqjfc",
		obfuscated_name: "dp"
	},
	os: {
		cipher: "DES",
		is_encrypt: 1,
		key: "je6vk6t4",
		obfuscated_name: "pj"
	},
	platform: {
		cipher: "DES",
		is_encrypt: 1,
		key: "pakxhcd2",
		obfuscated_name: "gm"
	},
	plugins: {
		cipher: "DES",
		is_encrypt: 1,
		key: "v51m3pzl",
		obfuscated_name: "kq"
	},
	pmf: {
		cipher: "DES",
		is_encrypt: 1,
		key: "2mdeslu3",
		obfuscated_name: "vw"
	},
	protocol: {
		is_encrypt: 0,
		obfuscated_name: "protocol"
	},
	referer: {
		cipher: "DES",
		is_encrypt: 1,
		key: "y7bmrjlc",
		obfuscated_name: "ab"
	},
	res: {
		cipher: "DES",
		is_encrypt: 1,
		key: "whxqm2a7",
		obfuscated_name: "hf"
	},
	rtype: {
		cipher: "DES",
		is_encrypt: 1,
		key: "x8o2h2bl",
		obfuscated_name: "lo"
	},
	sdkver: {
		cipher: "DES",
		is_encrypt: 1,
		key: "9q3dcxp2",
		obfuscated_name: "sc"
	},
	status: {
		cipher: "DES",
		is_encrypt: 1,
		key: "2jbrxxw4",
		obfuscated_name: "an"
	},
	subVersion: {
		cipher: "DES",
		is_encrypt: 1,
		key: "eo3i2puh",
		obfuscated_name: "ns"
	},
	svm: {
		cipher: "DES",
		is_encrypt: 1,
		key: "fzj3kaeh",
		obfuscated_name: "qr"
	},
	time: {
		cipher: "DES",
		is_encrypt: 1,
		key: "q2t3odsk",
		obfuscated_name: "nb"
	},
	timezone: {
		cipher: "DES",
		is_encrypt: 1,
		key: "1uv05lj5",
		obfuscated_name: "as"
	},
	tn: {
		cipher: "DES",
		is_encrypt: 1,
		key: "x9nzj1bp",
		obfuscated_name: "py"
	},
	trees: {
		cipher: "DES",
		is_encrypt: 1,
		key: "acfs0xo4",
		obfuscated_name: "pi"
	},
	ua: {
		cipher: "DES",
		is_encrypt: 1,
		key: "k92crp1t",
		obfuscated_name: "bj"
	},
	url: {
		cipher: "DES",
		is_encrypt: 1,
		key: "y95hjkoo",
		obfuscated_name: "cf"
	},
	version: {
		is_encrypt: 0,
		obfuscated_name: "version"
	},
	vpw: {
		cipher: "DES",
		is_encrypt: 1,
		key: "r9924ab5",
		obfuscated_name: "ca"
	}
};
const BROWSER_ENV = {
	plugins: "MicrosoftEdgePDFPluginPortableDocumentFormatinternal-pdf-viewer1,MicrosoftEdgePDFViewermhjfbmdgcfjbbpaeojofohoefgiehjai1",
	ua: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0",
	canvas: "259ffe69",
	timezone: -480,
	platform: "Win32",
	url: "https://www.skland.com/",
	referer: "",
	res: "1920_1080_24_1.25",
	clientSize: "0_0_1080_1920_1920_1080_1920_1080",
	status: "0011"
};
const JSON_COLON_RE = /":"/g;
const JSON_COMMA_RE = /","/g;
const stringify = (obj) => JSON.stringify(obj).replace(JSON_COLON_RE, "\": \"").replace(JSON_COMMA_RE, "\", \"");
async function gzipObject(o) {
	const encoded = new TextEncoder().encode(stringify(o));
	const compressed = await new Response(new Blob([encoded]).stream().pipeThrough(new CompressionStream("gzip"))).arrayBuffer();
	const compressedArray = new Uint8Array(compressed);
	compressedArray[9] = 19;
	return btoa(String.fromCharCode(...compressedArray));
}
async function getSmId() {
	const v = `${format(/* @__PURE__ */ new Date(), "yyyyMMddHHmmss") + md5(crypto.randomUUID())}00`;
	return `${v + md5(`smsk_web_${v}`).substring(0, 14)}0`;
}
function getTn(o) {
	const sortedKeys = Object.keys(o).sort();
	const resultList = [];
	for (const key of sortedKeys) {
		let v = o[key];
		if (typeof v === "number") v = String(v * 1e4);
		else if (typeof v === "object" && v !== null) v = getTn(v);
		resultList.push(v);
	}
	return resultList.join("");
}
const SM_CONFIG = SKLAND_SM_CONFIG;
const devices_info_url = `${SKLAND_SM_CONFIG.protocol}://${SKLAND_SM_CONFIG.apiHost}${SKLAND_SM_CONFIG.apiPath}`;
async function getDid(storage) {
	if (await storage.hasItem("skland:did")) {
		const did = await storage.getItem(STORAGE_DID_KEY);
		if (did) return did;
	}
	const uid = crypto.randomUUID();
	const priId = md5(uid).substring(0, 16);
	const ep = await encryptRSA(uid, SM_CONFIG.publicKey);
	const desTarget = {
		...BROWSER_ENV,
		vpw: crypto.randomUUID(),
		svm: Date.now(),
		trees: crypto.randomUUID(),
		pmf: Date.now(),
		protocol: 102,
		organization: SM_CONFIG.organization,
		appId: SM_CONFIG.appId,
		os: "web",
		version: "3.0.0",
		sdkver: "3.0.0",
		box: "",
		rtype: "all",
		smid: await getSmId(),
		subVersion: "1.0.0",
		time: 0
	};
	desTarget.tn = md5(getTn(desTarget));
	const body = {
		appId: "default",
		compress: 2,
		data: await encryptAES(await gzipObject(await encryptObjectByDESRules(desTarget, DES_RULE)), priId),
		encode: 5,
		ep,
		organization: SM_CONFIG.organization,
		os: "web"
	};
	const resp = await (await fetch(devices_info_url, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(body)
	})).json();
	if (resp.code !== 1100) throw new Error("did计算失败，请联系作者");
	const did = `B${resp.detail.deviceId}`;
	await storage.setItem(STORAGE_DID_KEY, did);
	return did;
}
//#endregion
//#region src/utils/signature.ts
function parseURL(ctx) {
	const url = typeof ctx.request === "string" ? ctx.request : ctx.request.url;
	if (URL.canParse(url)) return new URL(url);
	return new URL(url, ctx.options.baseURL);
}
async function signRequest(ctx, storage) {
	const token = await storage.get(STORAGE_OAUTH_TOKEN_KEY);
	const cred = await storage.get(STORAGE_CREDENTIAL_KEY);
	assert(cred, "【skland-kit】森空岛 cred 未获取");
	assert(token, "【skland-kit】森空岛 token 未设置");
	const parsedURL = parseURL(ctx);
	const headers = new Headers(ctx.options.headers);
	if (!headers.has("user-agent")) headers.set("user-agent", "Mozilla/5.0 (Linux; Android 12; SM-A5560 Build/V417IR; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/101.0.4951.61 Safari/537.36; SKLand/1.52.1");
	if (!headers.has("accept-encoding")) headers.set("accept-encoding", "gzip");
	if (!headers.has("connection")) headers.set("connection", "close");
	if (!headers.has("x-requested-with")) headers.set("x-requested-with", "com.hypergryph.skland");
	const query = new URLSearchParams(ctx.options.query ?? {}).toString();
	const timestamp = (Date.now() - SERVER_TIMESTAMP_OFFSET).toString().slice(0, -3);
	const signatureHeaders = {
		platform: "3",
		timestamp,
		dId: await getDid(storage),
		vName: "1.0.0"
	};
	const signature = md5(hmacSha256(token, `${parsedURL.pathname}${query}${ctx.options.body ? JSON.stringify(ctx.options.body) : ""}${timestamp}${JSON.stringify(signatureHeaders)}`));
	Object.entries(signatureHeaders).forEach(([key, value]) => {
		headers.set(key, value);
	});
	headers.set("sign", signature);
	headers.set("cred", cred);
	ctx.options.headers = headers;
}
//#endregion
//#region src/client/ctx.ts
const clientCtx = createContext();
const useClientContext = clientCtx.use;
//#endregion
//#region src/client/collections/game.ts
function buildGameCollection() {
	const { $fetch, storage } = useClientContext();
	async function fetchGame(url, options, errorMessage) {
		const res = await $fetch(url, {
			...options,
			onRequest: (ctx) => signRequest(ctx, storage),
			onResponseError(ctx) {
				throw new Error(`【skland-kit】${errorMessage}`, { cause: ctx.response._data });
			}
		});
		if (res.code !== 0) throw new Error(`【skland-kit】${errorMessage}`, { cause: res });
		return res;
	}
	async function getAttendanceStatus(opt) {
		if ("roleId" in opt && "serverId" in opt) return (await fetchGame("/api/v1/game/endfield/attendance", { headers: {
			"content-type": "application/json",
			"sk-game-role": `${opt.gameId}_${opt.roleId}_${opt.serverId}`
		} }, "获取签到状态错误")).data;
		else return (await fetchGame("/api/v1/game/attendance", { query: opt }, "获取签到状态错误")).data;
	}
	async function attendance(opt) {
		if ("roleId" in opt && "serverId" in opt) return (await fetchGame("/api/v1/game/endfield/attendance", {
			method: "POST",
			headers: {
				"content-type": "application/json",
				"sk-game-role": `${opt.gameId}_${opt.roleId}_${opt.serverId}`,
				"referer": "https://game.skland.com/",
				"origin": "https://game.skland.com/"
			}
		}, "获取签到信息错误")).data;
		else return (await fetchGame("/api/v1/game/attendance", {
			method: "POST",
			body: opt,
			headers: { "content-type": "application/json" }
		}, "执行签到错误")).data;
	}
	return {
		getAttendanceStatus,
		attendance
	};
}
//#endregion
//#region src/client/collections/hypergrayph.ts
function isSuccessResponse(res) {
	if (typeof res.data === "undefined" || typeof res.status === "undefined" || typeof res.type === "undefined") return false;
	if (res.msg !== "OK" || res.status !== 0) return false;
	return true;
}
function parseOAuthToken(input) {
	const token = input.trim();
	try {
		const parsed = JSON.parse(token);
		if (typeof parsed?.data?.content === "string") return parsed.data.content;
	} catch {}
	return token;
}
function buildHypergryphCollection() {
	const { $fetch, storage } = useClientContext();
	const $fetchHypergryph = $fetch.create({ baseURL: "https://as.hypergryph.com" });
	async function fetchHypergryph(url, options, errorMessage) {
		const headers = new Headers(options.headers);
		headers.set("user-agent", "Mozilla/5.0 (Linux; Android 12; SM-A5560 Build/V417IR; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/101.0.4951.61 Safari/537.36; SKLand/1.52.1");
		headers.set("dId", await getDid(storage));
		headers.set("x-requested-with", "com.hypergryph.skland");
		const res = await $fetchHypergryph(url, {
			...options,
			headers,
			onResponseError(ctx) {
				throw new Error(`【skland-kit】${errorMessage}`, { cause: ctx.response._data });
			}
		});
		if (!isSuccessResponse(res)) throw new Error(`【skland-kit】${errorMessage}`, { cause: res });
		return res;
	}
	return {
		async sendPhoneCode(phone) {
			await fetchHypergryph("/general/v1/send_phone_code", {
				method: "POST",
				body: {
					phone,
					type: 2
				}
			}, "发送手机验证码错误");
		},
		async generateScanLoginUrl() {
			return (await fetchHypergryph("/general/v1/gen_scan/login", {
				method: "POST",
				body: { appCode: SKLAND_APP_CODE }
			}, "生成扫码登录 URL 错误")).data;
		},
		async getScanStatus(scanId) {
			return (await fetchHypergryph("/general/v1/scan_status", { query: { scanId } }, "获取扫码登录状态错误")).data;
		},
		async getOAuthTokenByPhonePassword(data) {
			return (await fetchHypergryph("/user/auth/v1/token_by_phone_password", {
				method: "POST",
				body: data
			}, "通过手机号和密码获取鹰角 OAuth token 错误")).data.token;
		},
		async getOAuthTokenByPhoneCode(data) {
			return (await fetchHypergryph("/user/auth/v2/token_by_phone_code", {
				method: "POST",
				body: data
			}, "通过手机号和验证码获取鹰角 OAuth token 错误")).data.token;
		},
		async getOAuthTokenByScanCode(scanCode) {
			return (await fetchHypergryph("/user/auth/v1/token_by_scan_code", {
				method: "POST",
				body: { scanCode }
			}, "通过扫码获取鹰角 OAuth token 错误")).data.token;
		},
		async grantAuthorizeCode(token, options) {
			const { appCode, type } = defu$1(options, {
				appCode: SKLAND_APP_CODE,
				type: 0
			});
			return (await fetchHypergryph("/user/oauth2/v2/grant", {
				method: "POST",
				body: {
					appCode,
					token: parseOAuthToken(token),
					type
				}
			}, "通过 OAuth 登录凭证验证鹰角网络通行证错误")).data;
		}
	};
}
//#endregion
//#region src/client/collections/player.ts
function buildPlayerCollection() {
	const { $fetch, storage } = useClientContext();
	async function fetchPlayer(url, options, errorMessage) {
		const res = await $fetch(url, {
			...options,
			onRequest: (ctx) => signRequest(ctx, storage),
			onResponseError(ctx) {
				throw new Error(`【skland-kit】${errorMessage}`, { cause: ctx.response._data });
			}
		});
		if (res.code !== 0) throw new Error(`【skland-kit】${errorMessage}`, { cause: res });
		return res;
	}
	return {
		async getEndfieldDetails({ ...query }) {
			return (await fetchPlayer("/api/v1/game/endfield/card/detail", {
				query,
				headers: { "sk-game-role": `3_${query.roleId}_${query.serverId}` }
			}, "获取终末地玩家详情错误")).data;
		},
		async getBinding() {
			return (await fetchPlayer("/api/v1/game/player/binding", {}, "获取游戏绑定信息错误")).data;
		},
		async getInfo(query) {
			return (await fetchPlayer("/api/v1/game/player/info", { query }, "获取玩家信息错误")).data;
		}
	};
}
//#endregion
//#region src/client/collections/index.ts
function buildCollections() {
	const context = useClientContext();
	return clientCtx.call(context, () => {
		return {
			hypergryph: buildHypergryphCollection(),
			score: {},
			player: buildPlayerCollection(),
			game: buildGameCollection()
		};
	});
}
//#endregion
//#region src/client/core.ts
function createClient(config = {}) {
	const { baseURL, timeout, driver } = defu(config, {
		baseURL: "https://zonai.skland.com",
		timeout: 30 * 1e3
	});
	const storage = createStorage(driver ? { driver } : void 0);
	const $fetch = createFetch({ defaults: {
		baseURL,
		timeout
	} });
	async function signIn(authorizeCode) {
		const data = await $fetch("/web/v1/user/auth/generate_cred_by_code", {
			method: "POST",
			headers: {
				"content-type": "application/json",
				"user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
				"referer": "https://www.skland.com/",
				"origin": "https://www.skland.com",
				"dId": await getDid(storage),
				"platform": "3",
				"timestamp": `${Math.floor(Date.now() / 1e3)}`,
				"vName": "1.0.0"
			},
			body: {
				code: authorizeCode,
				kind: 1
			}
		}).then((res) => res.data);
		await storage.setItems([
			{
				key: STORAGE_OAUTH_TOKEN_KEY,
				value: data.token
			},
			{
				key: STORAGE_CREDENTIAL_KEY,
				value: data.cred
			},
			{
				key: STORAGE_USER_ID_KEY,
				value: data.userId
			}
		]);
		return data;
	}
	async function refresh() {
		assert(await storage.getItem(STORAGE_CREDENTIAL_KEY), "【skland-kit】cred 未获取");
		const data = await $fetch(`/web/v1/auth/refresh`, {
			headers: {
				"content-type": "application/json",
				"user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
				"referer": "https://www.skland.com/",
				"origin": "https://www.skland.com"
			},
			onRequest: (ctx) => signRequest(ctx, storage)
		});
		await storage.setItems([{
			key: STORAGE_OAUTH_TOKEN_KEY,
			value: data.data.token
		}]);
		return data.data;
	}
	const collections = clientCtx.call({
		storage,
		$fetch
	}, buildCollections);
	return Object.freeze({
		$fetch,
		storage,
		signIn,
		refresh,
		collections
	});
}
//#endregion
export { SERVER_TIMESTAMP_OFFSET, SKLAND_APP_CODE, STORAGE_CREDENTIAL_KEY, STORAGE_DID_KEY, STORAGE_OAUTH_TOKEN_KEY, STORAGE_USER_ID_KEY, createClient };
