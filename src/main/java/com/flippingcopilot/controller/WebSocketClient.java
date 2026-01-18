package com.flippingcopilot.controller;

import okhttp3.*;
import okio.ByteString;

public class WebSocketClient {
    private OkHttpClient client;
    private WebSocket webSocket;

    public void connect(String url) {
        client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("WebSocket connected");
                // Send a message
                webSocket.send("Hello, Server!");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                System.out.println("Received text: " + text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                System.out.println("Received bytes: " + bytes.hex());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
                System.out.println("Closing: " + code + " / " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                System.err.println("Error: " + t.getMessage());
            }
        };

        webSocket = client.newWebSocket(request, listener);
    }

    public void sendMessage(String message) {
        if (webSocket != null) {
            webSocket.send(message);
        }
    }

    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Client closing");
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }

    public static void main(String[] args) {
        WebSocketClient client = new WebSocketClient();
        client.connect("ws://echo.websocket.org/");

        // Keep the connection alive for a bit
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client.close();
    }
}