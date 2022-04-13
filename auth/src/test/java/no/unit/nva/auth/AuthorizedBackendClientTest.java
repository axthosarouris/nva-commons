package no.unit.nva.auth;

import static no.unit.nva.auth.AuthorizedBackendClient.CLIENT_ID;
import static no.unit.nva.auth.AuthorizedBackendClient.CLIENT_SECRET;
import static no.unit.nva.auth.AuthorizedBackendClient.COGNITO_URI;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.StringContains.containsString;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import no.unit.nva.stubs.FakeAuthServer;
import no.unit.nva.stubs.WiremockHttpClient;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizedBackendClientTest {

    public static final String EXAMPLE_RESOURCE_PATH = "/example";
    private URI serverUri;
    private HttpClient httpClient;
    private String protectedContent;
    private String expectedAccessToken;

    @BeforeEach
    public void init() {
        FakeAuthServer authServer = new FakeAuthServer();
        expectedAccessToken = randomString();
        protectedContent = authServer.registerBackendClient(CLIENT_ID.get(),
                                                            CLIENT_SECRET.get(),
                                                            expectedAccessToken,
                                                            EXAMPLE_RESOURCE_PATH);
        serverUri = authServer.getServerUri();
        this.httpClient = WiremockHttpClient.create();
    }

    @Test
    void shouldSendRequestsContainingTheBackendAccessTokenWhenUserAccessTokenIsNotSubmitted() throws IOException,
                                                                                                     InterruptedException {
        var client = AuthorizedBackendClient.prepareWithBackendCredentials(serverUri, httpClient);
        var resourceUri = UriWrapper.fromUri(serverUri).addChild(EXAMPLE_RESOURCE_PATH).getUri();
        var request = HttpRequest.newBuilder(resourceUri).GET();
        var response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.body(), containsString(protectedContent));
    }

    @Test
    void shouldSendAsyncRequestsContainingTheAccessTokenWhenUserAccessTokenIsNotSubmitted() {
        var client = AuthorizedBackendClient.prepareWithBackendCredentials(serverUri, httpClient);
        var resourceUri = UriWrapper.fromUri(serverUri).addChild(EXAMPLE_RESOURCE_PATH).getUri();
        var request = HttpRequest.newBuilder(resourceUri).GET();
        var response =
            client.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8)).join();
        assertThat(response.body(), containsString(protectedContent));
    }

    @Test
    void shouldSendRequestsContainingTheUserAccessTokenWhenUserAccessTokenIsSubmitted()
        throws IOException, InterruptedException {
        String bearerToken = "Bearer " + expectedAccessToken;
        var client = AuthorizedBackendClient.prepareWithUserCredentials(httpClient, bearerToken);
        var resourceUri = UriWrapper.fromUri(serverUri).addChild(EXAMPLE_RESOURCE_PATH).getUri();
        var request = HttpRequest.newBuilder(resourceUri).GET();

        var response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.body(), containsString(protectedContent));

        var asyncResponse =
            client.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8)).join();
        assertThat(asyncResponse.body(), containsString(protectedContent));
    }

    @Test
    void shouldNotRequireEnvVariablesForBackendAccessTokenIfUserAccessTokenIsUsed() {
        assertThat(CLIENT_ID, is(instanceOf(Supplier.class)));
        assertThat(CLIENT_SECRET, is(instanceOf(Supplier.class)));
        assertThat(COGNITO_URI, is(instanceOf(Supplier.class)));
    }
}