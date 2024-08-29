package com.flippingcopilot.controller;

import com.flippingcopilot.model.*;
import com.google.gson.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import java.io.IOException;
import java.util.function.Consumer;

@Slf4j
public class ApiRequestHandler {

    private static final String serverUrl = System.getenv("FLIPPING_COPILOT_HOST") != null ? System.getenv("FLIPPING_COPILOT_HOST")  : "https://api.flippingcopilot.com/";
    private final OkHttpClient client;
    private final Gson gson;
    private String jwtToken = null;
    @Getter
    private LoginResponse loginResponse;

    public ApiRequestHandler(Gson gson, OkHttpClient client) {
        this.gson = gson;
        this.client = client;
    }

    public void onLogout() {
        jwtToken = null;
        loginResponse = null;
    }

    public void setLoginResponse(LoginResponse loginResponse) {
        this.loginResponse = loginResponse;
        this.jwtToken = loginResponse.jwt;
    }

    public void authenticate(String username, String password, Consumer<LoginResponse> callback) {
        Request request = new Request.Builder()
                .url(serverUrl + "/login")
                .addHeader("Authorization", Credentials.basic(username, password))
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), ""))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.accept(null);
            }
            @Override
            public void onResponse(Call call, Response response) {
                // todo: this response can be a non 200 in which case we deserialize a null jwt
                handleLoginResponse(response, callback);
            }
        });
    }

    private void handleLoginResponse(Response response, Consumer<LoginResponse> callback) {
        try {
            JsonObject responseJson = gson.fromJson(response.body().string(), JsonObject.class);
            loginResponse = gson.fromJson(responseJson, LoginResponse.class);
            if (response.isSuccessful()) {
                jwtToken = responseJson.get("jwt").getAsString();
            }
            callback.accept(loginResponse);
        } catch (IOException e) {
            callback.accept(null);
        }
        catch (JsonParseException e) {
            loginResponse = new LoginResponse(true, response.message(), null, -1);
            callback.accept(loginResponse);
        }
    }

    public Suggestion getSuggestion(AccountStatus accountStatus) throws IOException {
        JsonObject status = accountStatus.toJson(gson);
        JsonObject suggestionJson = postJson(status, "/suggestion");
        return Suggestion.fromJson(suggestionJson, gson);
    }

    public ItemPrice getItemPrice(int itemId, String displayName) {
        JsonObject respObj = null;
        try {
            JsonObject body = new JsonObject();
            body.add("item_id", new JsonPrimitive(itemId));
            body.add("display_name", new JsonPrimitive(displayName));
            respObj = postJson(body, "/prices");
            return ItemPrice.fromJson(respObj, gson);
        } catch (HttpResponseException e) {
            log.error("error fetching copilot price for item " + itemId, e);
            return new ItemPrice(0, 0, "Unable to fetch price copilot price (possible server update)");
        } catch (JsonSyntaxException e) {
            log.error("unable to parse prices response object: " + gson.toJson(respObj), e);
            return new ItemPrice(0, 0, "Unable to fetch price copilot price (possible server update)");
        }
    }

    private JsonObject postJson(JsonObject json, String route) throws HttpResponseException {
        if (jwtToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        RequestBody body = RequestBody.create(MediaType.get("application/json; charset=utf-8"), json.toString());
        Request request = new Request.Builder()
                .url(serverUrl + route)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body)
                .build();
        String responseBody = null;
        try (Response response = client.newCall(request).execute()) {
            responseBody = response.body().string();
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            if (!response.isSuccessful()) {
                throw new HttpResponseException(response.code(), responseJson.get("message").getAsString());
            }
            return responseJson;
        } catch (HttpResponseException e) {
            throw e;
        } catch (JsonParseException e) {
            log.error("Error occurred while parsing JSON response: " + e.getMessage());
            log.error("Response body: " + responseBody);
            throw new HttpResponseException(-1, "Unknown server error (possible system update)");
        } catch (IOException e) {
            log.error("Error occurred while posting json: ", e);
            throw new HttpResponseException(-1, "Unknown server error (possible system update)");
        }
    }

}
