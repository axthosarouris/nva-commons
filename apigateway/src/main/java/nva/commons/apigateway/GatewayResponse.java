package nva.commons.apigateway;

import static nva.commons.apigateway.RestConfig.defaultRestObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import nva.commons.apigateway.exceptions.GatewayResponseSerializingException;
import nva.commons.core.JacocoGenerated;

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
     * @param objectMapper desired object mapper
     * @throws GatewayResponseSerializingException when serializing fails
     */
    public GatewayResponse(T body, Map<String, String> headers, int statusCode, ObjectMapper objectMapper)
        throws GatewayResponseSerializingException {
        try {
            this.statusCode = statusCode;
            this.body = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
            this.headers = Collections.unmodifiableMap(Map.copyOf(headers));
        } catch (JsonProcessingException e) {
            throw new GatewayResponseSerializingException(e);
        }
    }

    /**
     * Create GatewayResponse object from an output stream. Used when we call the method {@code handleRequest()} of a
     * Handler directly and we want to read the output.
     *
     * @param outputStream the outputStream updated by the lambda handler
     * @return the GatewayResponse containing the output of the handler
     * @throws JsonProcessingException when the OutputStream cannot be parsed to a JSON object.
     */
    public static <T> GatewayResponse<T> fromOutputStream(ByteArrayOutputStream outputStream, Class<T> className)
        throws JsonProcessingException {
        String json = outputStream.toString(StandardCharsets.UTF_8);
        return fromString(json,className);
    }

    /**
     * Create GatewayResponse object from a String. Used when we call the method {@code handleRequest()} of a Handler
     * directly and we want to read the output. Usually the String is the output of an OutputStream.
     *
     * @param responseString a String containing the serialized GatwayResponse
     * @return the GatewayResponse containing the output of the handler
     * @throws JsonProcessingException when the OutputStream cannot be parsed to a JSON object.
     */
    public static <T> GatewayResponse<T> fromString(String responseString, Class<T> className)
        throws JsonProcessingException {

        GatewayResponse<T> gatewayResponse;
        if (className.getTypeName().equals(String.class.getTypeName())) {
            gatewayResponse = constructGatewayResponse(responseString);
        } else {
            TypeReference<GatewayResponse<T>> typeref = new TypeReference<>() {
            };
            gatewayResponse = defaultRestObjectMapper.readValue(responseString, typeref);
        }
        return gatewayResponse;
    }

    private static <T> GatewayResponse<T> constructGatewayResponse(String responseString) throws JsonProcessingException {
        GatewayResponse<T> gatewayResponse;
        JsonNode jsonNode = defaultRestObjectMapper.readTree(responseString);

        String body = jsonNode.get("body").asText();
        TypeReference<Map<String,String>> typeref = new TypeReference<>() {
        };
        Map<String, String> headers = defaultRestObjectMapper.convertValue(jsonNode.get("headers"), typeref);
        int statusCode = jsonNode.get("statusCode").asInt();

        gatewayResponse = attempt(() -> new GatewayResponse(body, headers, statusCode, defaultRestObjectMapper)).orElseThrow();
        return gatewayResponse;
    }

    public String getBody() {
        return body;
    }

    /**
     * Parses the JSON body to an object.
     *
     * @param clazz the class of the body object
     * @return the body object.
     * @throws JsonProcessingException when JSON processing fails
     */
    public T getBodyObject(Class<T> clazz) throws JsonProcessingException {
        return defaultRestObjectMapper.readValue(body, clazz);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(body, headers, statusCode);
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GatewayResponse<?> that = (GatewayResponse<?>) o;
        return statusCode == that.statusCode
               && Objects.equals(body, that.body)
               && Objects.equals(headers, that.headers);
    }
}
