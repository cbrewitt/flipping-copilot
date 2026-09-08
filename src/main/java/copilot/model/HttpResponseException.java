package copilot.model;

import lombok.*;

import java.io.*;

@Getter
public class HttpResponseException extends IOException {
    public final int responseCode;
    public final String responseMessage;

    public HttpResponseException(int responseCode, String message) {
        super(message);
        this.responseCode = responseCode; responseMessage = message;
    }

    public HttpResponseException(int responseCode, String message, Throwable cause) {
        super(message, cause);
        this.responseCode = responseCode; responseMessage = message;
    }
}