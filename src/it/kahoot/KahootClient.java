package it.kahoot;

import java.io.IOException;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.json.JSONObject;

import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class KahootClient {
	public static final boolean DEBUG = true;

	private int lobby;
	private String clientID;
	private String sessionToken;
	private String challengeResult;
	private String username;

	public KahootClient(int lobby, String username) throws IOException, KahootException {
		this.lobby = lobby;
		this.username = username;

		OkHttpClient client = new OkHttpClient();

		Response result = debug(
				client.newCall(newGet("/reserve/session/" + lobby + "?" + Long.toString(System.currentTimeMillis())))
						.execute());
		if (result.code() == 404) {
			throw new KahootException("Lobby does not exist: " + lobby);
		}

		String sessionToken = result.header("x-kahoot-session-token");
		debug("Session token: " + sessionToken);

		String body = result.body().string();
		debug("JSON response: " + body);
		JSONObject obj = new JSONObject(body);
		if (obj.getBoolean("twoFactorAuth")) {
			debug("2FA is enabled on this lobby.");

			//TODO: support 2FA
		}
		if (obj.getBoolean("namerator")) {
			debug("Namerator is enabled on this lobby (whatever that means).");
		}

		String challenge = obj.getString("challenge");
		challenge = challenge.replace("this.angular.", "angular_"); // we don't have Angular, so we replace the Angular-depend calls with a pure JS ones
		challenge = challenge.replace("_.replace", "lodash_Replace"); // we don't have Lodash, so we replace the Lodash-depend call with our replacement

		ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine("-strict", "--no-java",
				"--no-syntax-extensions");
		engine.put("this", null); // we don't "use" this, but give it a value to make not undefined

		String challengeResult;
		try {
			engine.eval("function lodash_Replace(str, regex, func) { return str.replace(regex, func); }");
			engine.eval("function angular_isDate(value) { return toString.call(value) === '[object Date]'; }");
			engine.eval("function angular_isArray(value) { return toString.call(value) === '[object Array]'; }");
			engine.eval("function angular_isObject(value) { return toString.call(value) === '[object Object]'; }");
			engine.eval("function angular_isString(value) { typeof value === 'string'; }");

			debug("Executing challenge: " + challenge);
			challengeResult = engine.eval(challenge).toString();
		} catch (ScriptException e) {
			throw new KahootException("Failed to do the lobby challenge", e);
		}

		debug("Challenge result: " + challengeResult);
		debug("Initializing web socket...");

		this.sessionToken = sessionToken;
		this.challengeResult = challengeResult;

		new KahootWebSocket(this);
	}

	private void debug(String s) {
		if (DEBUG) {
			System.out.println(s);
		}
	}

	private Response debug(Response result) {
		if (DEBUG) {
			System.out.println("Response headers:\n" + result.headers().toString());
		}
		return result;
	}

	private Request newGet(String endpoint) throws IOException {
		return newConnection(endpoint, "GET", null);
	}

	private Request newConnection(String endpoint, String method, String body) throws IOException {
		debug("Creating request to " + endpoint + "...");
		Request req = new Request.Builder().url("https://kahoot.it" + endpoint)
				.addHeader("Accept", "application/json, text/plain, */*").addHeader("Accept-Language", "en-US,en;q=0.9")
				.addHeader("User-Agent",
						"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.186 Safari/537.36")
				.addHeader("Host", "kahoot.it").addHeader("Referer", "https://kahoot.it/")
				.addHeader("Cookie", "kahuna_dev_id=29lr08kkzxqfjjy65gypz00v")
				.method(method, body == null ? null : RequestBody.create(MediaType.parse("application/json"), body))
				.build();
		debug("Request headers:\n" + req.headers().toString());
		return req;
	}

	public int getLobby() {
		return lobby;
	}

	public void setLobby(int lobby) {
		this.lobby = lobby;
	}

	public String getClientID() {
		return clientID;
	}

	public void setClientID(String clientID) {
		this.clientID = clientID;
	}

	public String getSessionToken() {
		return sessionToken;
	}

	public void setSessionToken(String sessionToken) {
		this.sessionToken = sessionToken;
	}

	public String getChallengeResult() {
		return challengeResult;
	}

	public void setChallengeResult(String challengeResult) {
		this.challengeResult = challengeResult;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}
}
