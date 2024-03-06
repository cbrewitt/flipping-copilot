package com.flippingcopilot.controller;

import com.flippingcopilot.model.AccountStatus;
import com.flippingcopilot.model.HttpResponseException;
import com.flippingcopilot.model.LoginResponse;
import com.flippingcopilot.model.Suggestion;
import com.google.gson.*;
import lombok.Getter;
import okhttp3.*;
import java.io.IOException;
import java.util.function.Consumer;

public class ApiRequestHandler {

    private static final String serverUrl = "https://api.flippingcopilot.com/";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();
    private String jwtToken = null;
    @Getter
    private LoginResponse loginResponse;

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
        JsonObject status = accountStatus.toJson();
        JsonObject suggestionJson = postJson(status, "/suggestion");
        return Suggestion.fromJson(suggestionJson);
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

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HttpResponseException(response.code(), response.message());
            }
            return gson.fromJson(response.body().string(), JsonObject.class);
        } catch (HttpResponseException e) {
            throw e;
        } catch (IOException e) {
            throw new HttpResponseException(-1, e.getMessage());
        }
    }
}
