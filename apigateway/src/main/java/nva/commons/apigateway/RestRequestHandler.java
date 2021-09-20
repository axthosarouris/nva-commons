package nva.commons.apigateway;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.GatewayResponseSerializingException;
import nva.commons.apigateway.exceptions.UnsupportedAcceptHeaderException;
import nva.commons.core.Environment;
import nva.commons.core.attempt.Failure;
import nva.commons.core.ioutils.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.exceptions.ExceptionUtils.stackTraceInSingleLine;

/**
 * Template class for implementing Lambda function handlers that get activated through a call to ApiGateway. This class
 * is for processing a HTTP query directly without the usage of a Jersey-server or a SpringBoot template.
 *
 * @param <I> Class of the object in the body field of the ApiGateway message.
 * @param <O> Class of the response object.
 * @see <a href="https://github.com/awslabs/aws-serverless-java-container">aws-serverless-container</a> for
 *     alternative solutions.
 */
public abstract class RestRequestHandler<I, O> implements RequestStreamHandler {

    public static final String REQUEST_ID = "RequestId:";
    private static final Logger logger = LoggerFactory.getLogger(RestRequestHandler.class);
    public static final String SPACE = " ";
    public static final String EMPTY_STRING = "";
    public static final String COMMA = ",";
    protected final Environment environment;
    private final transient Class<I> iclass;
    private final transient ApiMessageParser<I> inputParser = new ApiMessageParser<>();

    protected transient OutputStream outputStream;
    protected transient String allowedOrigin;

    private static final List<MediaType> DEFAULT_SUPPORTED_MEDIA_TYPES = List.of(MediaType.JSON_UTF_8);

    /**
     * Checking headers on request and acting upon known headers.
     * @param requestInfo the request
     * @throws UnsupportedAcceptHeaderException if no provided Accept header media types are supported in this handler.
     */
    protected void checkHeaders(RequestInfo requestInfo) throws UnsupportedAcceptHeaderException {
        if (requestInfo.getHeaders().keySet().contains(HttpHeaders.ACCEPT)) {
            List<MediaType> acceptMediaTypes = parseAcceptHeader(requestInfo.getHeader(HttpHeaders.ACCEPT));
            suppliedAcceptsHeadersAreSupported(acceptMediaTypes);
        }
    }

    private List<MediaType> parseAcceptHeader(String header) {
        return Arrays.stream(header.replace(SPACE, EMPTY_STRING).split(COMMA))
                .map(string -> MediaType.parse(string))
                .collect(Collectors.toList());
    }

    private void suppliedAcceptsHeadersAreSupported(List<MediaType> acceptMediaTypes)
            throws UnsupportedAcceptHeaderException {
        acceptMediaTypes.stream()
                .filter(acceptMediaType -> inSupportedMediaTypeRange(acceptMediaType))
                .findAny()
                .orElseThrow(() -> new UnsupportedAcceptHeaderException(acceptMediaTypes, listSupportedMediaTypes()));
    }

    private boolean inSupportedMediaTypeRange(MediaType mediaType) {
        MediaType mediaTypeRange = mediaType.withoutParameters();
        return listSupportedMediaTypes().stream()
                .anyMatch(supportedMediaType -> supportedMediaType.is(mediaTypeRange));
    }

    /**
     * Override this method to change the supported media types for the handler.
     * @return a list of supported media types
     */
    protected List<MediaType> listSupportedMediaTypes() {
        return DEFAULT_SUPPORTED_MEDIA_TYPES;
    }

    protected MediaType getDefaultResponseHeader() {
        return listSupportedMediaTypes().get(0);
    }

    /**
     * The input class should be set explicitly by the inherting class.
     *
     * @param iclass      The class object of the input class.
     * @param environment the Environment from where the handler will read ENV variables.
     */
    public RestRequestHandler(Class<I> iclass, Environment environment) {
        this.iclass = iclass;
        this.environment = environment;
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        logger.info(REQUEST_ID + context.getAwsRequestId());
        I inputObject = null;
        try {

            init(output, context);
            String inputString = IoUtils.streamToString(input);
            inputObject = attempt(() -> parseInput(inputString))
                .orElseThrow(this::parsingExceptionToBadRequestException);

            O response;
            response = processInput(inputObject, inputString, context);

            writeOutput(inputObject, response);
        } catch (ApiGatewayException e) {
            handleExpectedException(context, inputObject, e);
        } catch (Exception e) {
            handleUnexpectedException(context, inputObject, e);
        }
    }

    protected ApiGatewayException parsingExceptionToBadRequestException(Failure<I> fail) {
        return new BadRequestException(fail.getException().getMessage(), fail.getException());
    }

    protected void handleUnexpectedException(Context context, I inputObject, Exception e) throws IOException {
        logger.error(e.getMessage());
        logger.error(stackTraceInSingleLine(e));
        writeUnexpectedFailure(inputObject, e, context.getAwsRequestId());
    }

    protected void handleExpectedException(Context context, I inputObject, ApiGatewayException e) throws IOException {
        logger.warn(e.getMessage());
        logger.warn(stackTraceInSingleLine(e));
        writeExpectedFailure(inputObject, e, context.getAwsRequestId());
    }

    protected void init(OutputStream outputStream, Context context) {
        this.outputStream = outputStream;
    }

    /**
     * Implements the main logic of the handler. Any exception thrown by this method will be handled by {@link
     * RestRequestHandler#handleExpectedException} method.
     *
     * @param input       The input object to the method. Usually a deserialized json.
     * @param requestInfo Request headers and path.
     * @param context     the ApiGateway context.
     * @return the Response body that is going to be serialized in json
     * @throws ApiGatewayException all exceptions are caught by writeFailure and mapped to error codes through the
     *                             method {@link RestRequestHandler#getFailureStatusCode}
     */
    protected abstract O processInput(I input, RequestInfo requestInfo, Context context) throws ApiGatewayException;

    /**
     * Maps an object I to an object O.
     *
     * @param input                 the input object of class I
     * @param apiGatewayInputString The message of apiGateway, for extracting the headers and in case we need other
     *                              fields during the processing
     * @param context               the Context
     * @return an output object of class O
     * @throws ApiGatewayException when some predictable error happens.
     */
    protected O processInput(I input, String apiGatewayInputString, Context context) throws ApiGatewayException {
        RequestInfo requestInfo = inputParser.getRequestInfo(apiGatewayInputString);
        checkHeaders(requestInfo);
        return processInput(input, requestInfo, context);
    }

    /**
     * Define the response statusCode in case of failure.
     *
     * @param input The request input.
     * @param error The exception that caused the failure.
     * @return the failure status code.
     */
    protected int getFailureStatusCode(I input, ApiGatewayException error) {
        return error.getStatusCode();
    }

    /**
     * Method for parsing the input object from the ApiGateway message.
     *
     * @param inputString the ApiGateway message.
     * @return an object of class I.
     * @throws IOException when parsing fails.
     */
    protected I parseInput(String inputString) throws IOException {
        return inputParser.getBodyElementFromJson(inputString, getIClass());
    }

    /**
     * Define the success status code.
     *
     * @param input  The request input.
     * @param output The response output
     * @return the success status code.
     */
    protected abstract Integer getSuccessStatusCode(I input, O output);

    protected abstract void writeOutput(I input, O output) throws IOException, GatewayResponseSerializingException;

    protected abstract void writeExpectedFailure(I input, ApiGatewayException exception, String requestId)
        throws IOException;

    protected abstract void writeUnexpectedFailure(I input, Exception exception, String requestId) throws IOException;

    private Class<I> getIClass() {
        return iclass;
    }
}

