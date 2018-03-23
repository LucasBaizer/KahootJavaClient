package io.github.lucasbaizer.kjc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class KahootWebSocket extends WebSocketListener {
	private KahootClient client;

	private OkHttpClient webSocketClient;
	private Request request;
	private JSONObject system;
	private int nonce = 0;
	private final int waitTime = 500;

	public KahootWebSocket(KahootClient client) throws IOException {
		this.client = client;
		webSocketClient = new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();
		request = new Request.Builder().url("wss://kahoot.it/cometd/" + client.getLobby() + "/"
				+ deriveToken(client.getSessionToken(), client.getChallengeResult())).build();
		system = new JSONObject(new String(Files.readAllBytes(Paths.get("system.json")), StandardCharsets.UTF_8));

		webSocketClient.newWebSocket(request, this);
	}

	@Override
	public void onOpen(WebSocket socket, Response response) {
		debug("Web socket opened!");

		socket.send(wrap("initialHandshake"));
	}

	@Override
	public void onMessage(WebSocket socket, String text) {
		debug("Received web socket message: " + text);

		JSONObject obj = unwrap(text);
		if (obj.has("successful")) {
			if (!obj.getBoolean("successful")) {
				debug("A call was not successful, aborting.");
				abort();
			}
		}
		int id = obj.has("id") ? Integer.parseInt(obj.getString("id")) : -1;
		if (id == 1) { // initial packet just sent -- this is the response
			client.setClientID(obj.getString("clientId"));
			debug("Client ID: " + client.getClientID());

			Thread sendThread = new Thread(() -> {
				subscribe(socket, "player");
				subscribe(socket, "controller");
				subscribe(socket, "status");

				wait(waitTime);

				socket.send(wrap("connect"));

				wait(waitTime);

				unsubscribe(socket, "player");
				unsubscribe(socket, "controller");
				unsubscribe(socket, "status");

				wait(waitTime);

				subscribe(socket, "player"); // yes, this happens twice. no, I don't know why.
				subscribe(socket, "controller");
				subscribe(socket, "status");

				wait(waitTime);

				socket.send(wrap("connect"));

				wait(waitTime);

				socket.send(wrap("login", "data.gameid", client.getLobby(), "data.name", client.getUsername())); // the fun part: get in!

				wait(waitTime);

				socket.send(wrap("connect", "ack", 2));
			});
			sendThread.start();
		}
		if (id == 13 && obj.getString("channel").equals("/service/controller")) { // finished login
			Thread refresh = new Thread(() -> {
				try {
					int sent = 2;
					while (!Thread.currentThread().isInterrupted()) {
						Thread.sleep(30 * 1000);
						socket.send(wrap("connect", "ack", sent++));
						debug("Refreshed.");
					}
				} catch (InterruptedException e) {
					debug("Refresh thread interrupted!");
					abort();
					return;
				}
			});
			refresh.setDaemon(true); // if everything else is done, stop this
			refresh.start();
		}
	}

	@Override
	public void onFailure(WebSocket socket, Throwable e, Response response) {
		debug("Web socket error: ");
		e.printStackTrace(System.out);
		try {
			debug(response.body().string());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private void abort() {
		webSocketClient.dispatcher().executorService().shutdown();
	}

	private void wait(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			debug("Error while waiting between messages: ");
			e.printStackTrace(System.out);
			abort();
		}
	}

	private void subscribe(WebSocket socket, String event) {
		socket.send(wrap("subscribe", "subscription", "/service/" + event));
	}

	private void unsubscribe(WebSocket socket, String event) {
		socket.send(wrap("unsubscribe", "subscription", "/service/" + event));
	}

	private JSONObject unwrap(String text) {
		return new JSONObject(text.substring(1, text.length() - 1));
	}

	private String wrap(String objectName, Object... values) {
		JSONObject object = new JSONObject(system.getJSONObject(objectName).toString());
		for (int i = 0; i < values.length; i += 2) {
			String key = (String) values[i];
			Object val = values[i + 1];

			JSONObject parent = object;
			String last = key;
			String[] spl = key.split(Pattern.quote("."));
			for (String s : spl) {
				Object json = parent.get(s);
				if (json instanceof JSONObject) {
					parent = (JSONObject) json;
				}
				last = s;
			}

			parent.put(last, val);
		}

		object.put("id", Integer.toString(++nonce));
		object.put("ext", new JSONObject().put("timesync",
				new JSONObject().put("tc", System.currentTimeMillis()).put("l", 0).put("o", 0)));
		if (object.has("ack")) {
			object.getJSONObject("ext").put("ack", object.get("ack"));
			object.remove("ack");
		}

		if (client.getClientID() != null) {
			object.put("clientId", client.getClientID());
		}

		String str = "[" + object.toString() + "]";
		debug("Web socket request string: " + str);
		return str;
	}

	/**
	 * "Derives" a token from the session token and the challenge result. This
	 * is Kahoot's algorithm for (presumably) making it harder to create fake
	 * clients. It is used for finding the CometD endpoint to connect to.
	 **/
	private String deriveToken(String token, String challenge) throws UnsupportedEncodingException {
		String decoded = new String(Base64.getDecoder().decode(token), "UTF-8");

		String derived = "";
		for (int i = 0; i < decoded.length(); i++) {
			int decodedChar = decoded.charAt(i);
			int challengeChar = challenge.charAt(i % challenge.length());
			int xor = decodedChar ^ challengeChar;

			derived += (char) xor;
		}

		debug("Derived token: " + derived);

		return derived;
	}

	private void debug(String s) {
		if (KahootClient.DEBUG) {
			System.out.println(s);
		}
	}
}
