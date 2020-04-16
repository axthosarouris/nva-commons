package nva.commons.handlers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import nva.commons.exceptions.GatewayResponseSerializingException;
import nva.commons.utils.JsonUtils;

public class GatewayResponse<T> implements Serializable {

    private final String body;
    private final Map<String, String> headers;
    private final int statusCode;

    /**
     * Constructor for JSON deserializing.
     *
     * @param body       the body as JSON string
     * @param headers    the headers map.
     * @param statusCode the status code.
     */
    @JsonCreator
    public GatewayResponse(
        @JsonProperty("body") final String body,
        @JsonProperty("headers") final Map<String, String> headers,
        @JsonProperty("statusCode") final int statusCode) {
        this.body = body;
        this.headers = headers;
        this.statusCode = statusCode;
    }

    /**
     * Constructor for GatewayResponse.
     *
     * @param body       body of response
     * @param headers    http headers for response
     * @param statusCode status code for response
     * @throws GatewayResponseSerializingException when serializing fails
     */
    public GatewayResponse(T body, Map<String, String> headers, int statusCode)
        throws GatewayResponseSerializingException {
        try {
            this.statusCode = statusCode;
            this.body = JsonUtils.jsonParser.writeValueAsString(body);
            this.headers = Collections.unmodifiableMap(Map.copyOf(headers));
        } catch (JsonProcessingException e) {
            throw new GatewayResponseSerializingException(e);
        }
    }

    public String getBody() {
        return body;
    }

    public T getBodyObject(Class<T> clazz) throws JsonProcessingException {
        return JsonUtils.jsonParser.readValue(body, clazz);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GatewayResponse<?> that = (GatewayResponse<?>) o;
        return statusCode == that.statusCode &&
            Objects.equals(body, that.body) &&
            Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(body, headers, statusCode);
    }
}

