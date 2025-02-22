package nva.commons.apigateway;

import static java.util.Objects.isNull;
import static java.util.function.Predicate.not;
import static nva.commons.apigateway.RequestInfoConstants.AUTHORIZATION_FAILURE_WARNING;
import static nva.commons.apigateway.RequestInfoConstants.BACKEND_SCOPE_AS_DEFINED_IN_IDENTITY_SERVICE;
import static nva.commons.apigateway.RequestInfoConstants.CLIENT_ID;
import static nva.commons.apigateway.RequestInfoConstants.DEFAULT_COGNITO_URI;
import static nva.commons.apigateway.RequestInfoConstants.DOMAIN_NAME_FIELD;
import static nva.commons.apigateway.RequestInfoConstants.EXTERNAL_USER_POOL_URI;
import static nva.commons.apigateway.RequestInfoConstants.FEIDE_ID;
import static nva.commons.apigateway.RequestInfoConstants.HEADERS_FIELD;
import static nva.commons.apigateway.RequestInfoConstants.ISS;
import static nva.commons.apigateway.RequestInfoConstants.METHOD_ARN_FIELD;
import static nva.commons.apigateway.RequestInfoConstants.MISSING_FROM_HEADERS;
import static nva.commons.apigateway.RequestInfoConstants.MISSING_FROM_PATH_PARAMETERS;
import static nva.commons.apigateway.RequestInfoConstants.MISSING_FROM_QUERY_PARAMETERS;
import static nva.commons.apigateway.RequestInfoConstants.MISSING_FROM_REQUEST_CONTEXT;
import static nva.commons.apigateway.RequestInfoConstants.PATH_FIELD;
import static nva.commons.apigateway.RequestInfoConstants.PATH_PARAMETERS_FIELD;
import static nva.commons.apigateway.RequestInfoConstants.PERSON_CRISTIN_ID;
import static nva.commons.apigateway.RequestInfoConstants.PERSON_GROUPS;
import static nva.commons.apigateway.RequestInfoConstants.PERSON_NIN;
import static nva.commons.apigateway.RequestInfoConstants.QUERY_STRING_PARAMETERS_FIELD;
import static nva.commons.apigateway.RequestInfoConstants.REQUEST_CONTEXT_FIELD;
import static nva.commons.apigateway.RequestInfoConstants.SCOPES_CLAIM;
import static nva.commons.apigateway.RequestInfoConstants.TOP_LEVEL_ORG_CRISTIN_ID;
import static nva.commons.apigateway.RequestInfoConstants.USER_NAME;
import static nva.commons.apigateway.RestConfig.defaultRestObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.net.HttpHeaders;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.unit.nva.auth.CognitoUserInfo;
import no.unit.nva.auth.FetchUserInfo;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.SingletonCollector;
import nva.commons.core.attempt.Failure;
import nva.commons.core.exceptions.ExceptionUtils;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("PMD.GodClass")
public class RequestInfo {

    public static final String ERROR_FETCHING_COGNITO_INFO = "Could not fetch user information from Cognito:{}";
    private static final HttpClient DEFAULT_HTTP_CLIENT = HttpClient.newBuilder().build();
    private static final Logger logger = LoggerFactory.getLogger(RequestInfo.class);
    private final HttpClient httpClient;
    private final Supplier<URI> cognitoUri;
    private final Supplier<URI> e2eTestsUserInfoUri;
    @JsonProperty(HEADERS_FIELD)
    private Map<String, String> headers;
    @JsonProperty(PATH_FIELD)
    private String path;
    @JsonProperty(PATH_PARAMETERS_FIELD)
    private Map<String, String> pathParameters;
    @JsonProperty(QUERY_STRING_PARAMETERS_FIELD)
    private Map<String, String> queryParameters;
    @JsonProperty(REQUEST_CONTEXT_FIELD)
    private JsonNode requestContext;
    @JsonProperty(METHOD_ARN_FIELD)
    private String methodArn;
    @JsonAnySetter
    private Map<String, Object> otherProperties;

    public RequestInfo(HttpClient httpClient, Supplier<URI> cognitoUri, Supplier<URI> e2eTestsUserInfoUri) {
        this.httpClient = httpClient;
        this.cognitoUri = cognitoUri;
        this.e2eTestsUserInfoUri = e2eTestsUserInfoUri;
    }

    public RequestInfo() {
        this.headers = new HashMap<>();
        this.pathParameters = new HashMap<>();
        this.queryParameters = new HashMap<>();
        this.otherProperties = new LinkedHashMap<>(); // ordinary HashMap and ConcurrentHashMap fail.
        this.requestContext = defaultRestObjectMapper.createObjectNode();
        this.httpClient = DEFAULT_HTTP_CLIENT;
        this.cognitoUri = DEFAULT_COGNITO_URI;
        this.e2eTestsUserInfoUri = RequestInfoConstants.E2E_TESTING_USER_INFO_ENDPOINT;
    }

    public static RequestInfo fromRequest(InputStream requestStream) {
        return attempt(() -> JsonUtils.dtoObjectMapper.readValue(requestStream, RequestInfo.class)).orElseThrow();
    }

    @JsonIgnore
    public String getHeader(String header) {
        return Optional.ofNullable(getHeaders().get(header))
                   .orElseThrow(() -> new IllegalArgumentException(MISSING_FROM_HEADERS + header));
    }

    @JsonIgnore
    public String getAuthHeader() {
        return getHeader(HttpHeaders.AUTHORIZATION);
    }

    @JsonIgnore
    public String getQueryParameter(String parameter) throws BadRequestException {
        return getQueryParameterOpt(parameter)
                   .orElseThrow(() -> new BadRequestException(MISSING_FROM_QUERY_PARAMETERS + parameter));
    }

    @JsonIgnore
    public Optional<String> getQueryParameterOpt(String parameter) {
        return Optional.ofNullable(getQueryParameters()).map(params -> params.get(parameter));
    }

    @JsonIgnore
    public String getPathParameter(String parameter) {
        return Optional.ofNullable(getPathParameters().get(parameter))
                   .orElseThrow(() -> new IllegalArgumentException(MISSING_FROM_PATH_PARAMETERS + parameter));
    }

    @JsonIgnore
    public String getRequestContextParameter(JsonPointer jsonPointer) {
        return getRequestContextParameterOpt(jsonPointer).orElseThrow(
            () -> new IllegalArgumentException(MISSING_FROM_REQUEST_CONTEXT + jsonPointer.toString()));
    }

    /**
     * Get request context parameter. The root node is the {@link RequestInfoConstants#REQUEST_CONTEXT_FIELD} node of
     * the {@link RequestInfo} class.
     * <p>Example: {@code JsonPointer.compile("/authorizer/claims/custom:currentCustomer");  }
     * </p>
     *
     * @param jsonPointer A {@link JsonPointer}
     * @return a present {@link Optional} if there is a non empty value for the parameter, an empty {@link Optional}
     *     otherwise.
     */
    @JsonIgnore
    public Optional<String> getRequestContextParameterOpt(JsonPointer jsonPointer) {
        return Optional.ofNullable(getRequestContext())
                   .map(context -> context.at(jsonPointer))
                   .filter(not(JsonNode::isMissingNode))
                   .filter(not(JsonNode::isNull))
                   .map(JsonNode::asText);
    }

    @JacocoGenerated
    public String getMethodArn() {
        return methodArn;
    }

    @JacocoGenerated
    public void setMethodArn(String methodArn) {
        this.methodArn = methodArn;
    }

    @JacocoGenerated
    @JsonAnyGetter
    public Map<String, Object> getOtherProperties() {
        return otherProperties;
    }

    @JacocoGenerated
    public void setOtherProperties(Map<String, Object> otherProperties) {
        this.otherProperties = otherProperties;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = nonNullMap(headers);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    public void setPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = nonNullMap(pathParameters);
    }

    public Map<String, String> getQueryParameters() {
        return queryParameters;
    }

    public void setQueryParameters(Map<String, String> queryParameters) {
        this.queryParameters = nonNullMap(queryParameters);
    }

    @JacocoGenerated
    public JsonNode getRequestContext() {
        return requestContext;
    }

    /**
     * Sets the request context.
     *
     * @param requestContext the request context.
     */
    @JacocoGenerated
    public void setRequestContext(JsonNode requestContext) {
        if (isNull(requestContext)) {
            this.requestContext = defaultRestObjectMapper.createObjectNode();
        } else {
            this.requestContext = requestContext;
        }
    }

    @JsonIgnore
    public URI getRequestUri() {
        return new UriWrapper(HTTPS, getDomainName()).addChild(getPath())
                   .addQueryParameters(getQueryParameters())
                   .getUri();
    }

    @JsonIgnore
    public String getDomainName() {
        return attempt(() -> this.getRequestContext().get(DOMAIN_NAME_FIELD).asText()).orElseThrow();
    }

    public boolean userIsApplicationAdmin() {
        return userIsAuthorized(AccessRight.ADMINISTRATE_APPLICATION.toString());
    }

    public boolean userIsAuthorized(String accessRight) {
        return checkAuthorizationOnline(accessRight) || checkAuthorizationOffline(accessRight);
    }

    @JacocoGenerated
    @JsonIgnore
    @Deprecated(forRemoval = true)
    public URI getCustomerId() throws UnauthorizedException {
        return getCurrentCustomer();
    }

    @Deprecated(since = "1.25.5")
    @JacocoGenerated
    @JsonIgnore
    public String getNvaUsername() throws UnauthorizedException {
        return getUserName();
    }

    @JsonIgnore
    public String getUserName() throws UnauthorizedException {
        return extractUserNameOffline().or(this::fetchUserNameFromCognito).orElseThrow(UnauthorizedException::new);
    }

    @JsonIgnore
    public Optional<String> getFeideId() {
        return extractFeideIdOffline().or(this::fetchFeideIdFromCognito);
    }

    @JsonIgnore
    public Optional<URI> getTopLevelOrgCristinId() {
        return extractTopLevelOrgIdOffline().or(this::fetchTopLevelOrgCristinIdFromCognito);
    }

    @JsonIgnore
    public Optional<String> getClientId() {
        return getRequestContextParameterOpt(CLIENT_ID);
    }

    @JsonIgnore
    public URI getCurrentCustomer() throws UnauthorizedException {
        return fetchCustomerIdFromCognito().or(this::fetchCustomerIdOffline).orElseThrow(UnauthorizedException::new);
    }

    @JsonIgnore
    public URI getPersonCristinId() throws UnauthorizedException {
        return extractPersonCristinIdOffline().or(this::fetchPersonCristinIdFromCognito)
                   .orElseThrow(UnauthorizedException::new);
    }

    @JsonIgnore
    public String getPersonNin() {
        return extractPersonNinOffline().or(this::fetchPersonNinFromCognito).orElseThrow(IllegalStateException::new);
    }

    public boolean clientIsInternalBackend() {
        return getRequestContextParameterOpt(SCOPES_CLAIM).map(
            value -> value.contains(BACKEND_SCOPE_AS_DEFINED_IN_IDENTITY_SERVICE)).orElse(false);
    }

    public boolean clientIsThirdParty() {
        return getRequestContextParameterOpt(ISS).map(
            value -> value.equals(EXTERNAL_USER_POOL_URI.get())
        ).orElse(false);
    }

    private Optional<String> fetchFeideIdFromCognito() {
        return fetchUserInfoFromCognito().map(CognitoUserInfo::getFeideId);
    }

    private Optional<String> extractFeideIdOffline() {
        return getRequestContextParameterOpt(FEIDE_ID);
    }

    private Optional<URI> fetchCustomerIdOffline() {
        return getRequestContextParameterOpt(PERSON_GROUPS).stream()
                   .flatMap(AccessRightEntry::fromCsv)
                   .filter(AccessRightEntry::describesCustomerUponLogin)
                   .map(AccessRightEntry::getCustomerId)
                   .collect(SingletonCollector.tryCollect())
                   .toOptional();
    }

    private Optional<URI> extractTopLevelOrgIdOffline() {
        return getRequestContextParameterOpt(TOP_LEVEL_ORG_CRISTIN_ID).map(URI::create);
    }

    private Optional<URI> fetchTopLevelOrgCristinIdFromCognito() {
        return fetchUserInfoFromCognito().map(CognitoUserInfo::getTopOrgCristinId);
    }

    private void logOnlineFetchResult(Failure<CognitoUserInfo> fail) {
        logger.warn(ERROR_FETCHING_COGNITO_INFO, ExceptionUtils.stackTraceInSingleLine(fail.getException()));
    }

    private Optional<String> extractUserNameOffline() {
        return getRequestContextParameterOpt(USER_NAME);
    }

    private Optional<String> fetchUserNameFromCognito() {
        return fetchUserInfoFromCognito().map(CognitoUserInfo::getUserName);
    }

    private Optional<URI> extractPersonCristinIdOffline() {
        return getRequestContextParameterOpt(PERSON_CRISTIN_ID).map(URI::create);
    }

    private Optional<URI> fetchPersonCristinIdFromCognito() {
        return fetchUserInfoFromCognito().map(CognitoUserInfo::getPersonCristinId);
    }

    private Optional<String> extractPersonNinOffline() {
        return getRequestContextParameterOpt(PERSON_NIN);
    }

    private Optional<String> fetchPersonNinFromCognito() {
        return fetchUserInfoFromCognito().map(CognitoUserInfo::getPersonNin);
    }

    private boolean checkAuthorizationOffline(String accessRight) {
        return attempt(this::getCurrentCustomer)
                   .map(currentCustomer -> new AccessRightEntry(accessRight, currentCustomer))
                   .map(requiredAccessRight -> fetchAvailableAccessRights().anyMatch(requiredAccessRight::equals))
                   .orElse(fail -> handleAuthorizationFailure());
    }

    private boolean handleAuthorizationFailure() {
        logger.warn(AUTHORIZATION_FAILURE_WARNING);
        return false;
    }

    private Stream<AccessRightEntry> fetchAvailableAccessRights() {
        return getRequestContextParameterOpt(PERSON_GROUPS).stream().flatMap(AccessRightEntry::fromCsv);
    }

    private Boolean checkAuthorizationOnline(String accessRight) {
        var accessRightAtCustomer = fetchCustomerIdFromCognito().map(
            customer -> new AccessRightEntry(accessRight, customer));

        var availableRights = fetchAvailableRights();
        return accessRightAtCustomer.map(availableRights::contains).orElse(false);
    }

    private List<AccessRightEntry> fetchAvailableRights() {
        return fetchUserInfoFromCognito().map(CognitoUserInfo::getAccessRights)
                   .map(AccessRightEntry::fromCsv)
                   .map(stream -> stream.collect(Collectors.toList()))
                   .orElseGet(Collections::emptyList);
    }

    private Optional<CognitoUserInfo> fetchUserInfoFromCognito() {
        return attempt(() -> fetchUserInfo(cognitoUri)).or(() -> fetchUserInfo(e2eTestsUserInfoUri))
                   .toOptional(this::logOnlineFetchResult);
    }

    private CognitoUserInfo fetchUserInfo(Supplier<URI> cognitoUri) {
        var userInfo = new FetchUserInfo(httpClient, cognitoUri, extractAuthorizationHeader());
        return userInfo.fetch();
    }

    private String extractAuthorizationHeader() {
        return this.getHeader(HttpHeaders.AUTHORIZATION);
    }

    private Optional<URI> fetchCustomerIdFromCognito() {
        return fetchUserInfoFromCognito().map(CognitoUserInfo::getCurrentCustomer);
    }

    private <K, V> Map<K, V> nonNullMap(Map<K, V> map) {
        if (isNull(map)) {
            return new HashMap<>();
        }
        return map;
    }
}

