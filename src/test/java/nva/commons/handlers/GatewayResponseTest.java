package nva.commons.handlers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.HashMap;
import java.util.Map;
import nva.commons.RequestBody;
import nva.commons.exceptions.GatewayResponseSerializingException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class GatewayResponseTest {

    public static final String SOME_VALUE = "Some value";
    public static final String SOME_OTHER_VALUE = "Some other value";
    public static final String SOME_KEY = "key1";
    public static final String SOME_OTHER_KEY = "key2";

    @Test
    @DisplayName("hashCode is the same for equivalent GatewayResponses")
    public void hashCodeIsTheSameForEquivalentGatewayResponses() throws GatewayResponseSerializingException {
        GatewayResponse<RequestBody> leftResponse = sampleGatewayResponse();
        GatewayResponse<RequestBody> rightResponse = sampleGatewayResponse();

        assertThat(leftResponse.hashCode(), is(equalTo(rightResponse.hashCode())));
    }

    @Test
    @DisplayName("equals returns true for equivalent Gateway responses")
    public void equalsReturnsTrueForEquivalentGatewayResponses() throws GatewayResponseSerializingException {
        GatewayResponse<RequestBody> leftResponse = sampleGatewayResponse();
        GatewayResponse<RequestBody> rightResponse = sampleGatewayResponse();

        assertThat(leftResponse.hashCode(), is(equalTo(rightResponse.hashCode())));
    }

    private GatewayResponse<RequestBody> sampleGatewayResponse()
        throws GatewayResponseSerializingException {
        return new GatewayResponse<>(sampleRequestBody(), sampleHeaders(), HttpStatus.SC_OK);
    }

    private Map<String, String> sampleHeaders() {
        Map<String, String> leftHeaders = new HashMap<>();
        leftHeaders.put(SOME_KEY, SOME_VALUE);
        leftHeaders.put(SOME_OTHER_KEY, SOME_OTHER_VALUE);
        return leftHeaders;
    }

    private RequestBody sampleRequestBody() {
        RequestBody leftBody = new RequestBody();
        leftBody.setField1(SOME_VALUE);
        leftBody.setField1(SOME_OTHER_VALUE);
        return leftBody;
    }
}