package org.gusdb.fgputil.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;

import jakarta.ws.rs.client.AsyncInvoker;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.gusdb.fgputil.IoUtil;
import org.gusdb.fgputil.Tuples;
import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.fgputil.web.HttpMethod;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ClientUtil {
  private static final Logger LOG = Logger.getLogger(ClientUtil.class);

  public static boolean LOG_RESPONSE_HEADERS = false;

  public static <T> T getResponseObject(String url, Class<T> responseObjectClass) throws IOException {
    return new ObjectMapper().readerFor(responseObjectClass).readValue(new URL(url));
  }

  public static <T> T getResponseObject(String url, Class<T> responseObjectClass, Map<String,String> headers) throws Exception {
    return new ObjectMapper().readerFor(responseObjectClass).readValue(
      makeAsyncGetRequest(url, MediaType.APPLICATION_JSON, headers).getInputStream()
    );
  }

  public static ResponseFuture makeAsyncGetRequest(
      String url, String expectedResponseType) {
    return makeAsyncGetRequest(url, expectedResponseType, Collections.emptyMap());
  }

  public static ResponseFuture makeAsyncPostRequest(
      String url, String requestBody, String requestMimeType, String expectedResponseType) {
    return makeAsyncPostRequest(url, requestBody, requestMimeType, expectedResponseType, Collections.emptyMap());
  }

  public static ResponseFuture makeAsyncPostRequest(
      String url, Object postBodyObject, String expectedResponseType) {
    return makeAsyncPostRequest(url, postBodyObject, expectedResponseType, Collections.emptyMap());
  }

  public static ResponseFuture makeAsyncGetRequest(
      String url, String expectedResponseType, Map<String,String> additionalHeaders) {
    LOG.info("Will send following GET request to " + url);
    return makeAsyncRequest(url, expectedResponseType,
      invoker -> invoker.get(), additionalHeaders, Function.identity());
  }

  public static ResponseFuture makeAsyncPostRequest(
      String url, String requestBody, String requestMimeType, String expectedResponseType, Map<String,String> additionalHeaders) {
    LOG.info("Will send following POST request to " + url + "\n" + requestBody);
    return makeAsyncRequest(url, expectedResponseType,
      invoker -> invoker.post(Entity.entity(requestBody,requestMimeType)),
      additionalHeaders, Function.identity());
  }

  public static ResponseFuture makeAsyncPostRequest(
      String url, Object postBodyObject, String expectedResponseType, Map<String,String> additionalHeaders) {
    return makeAsyncPostRequest(url, JsonUtil.serializeObject(postBodyObject),
        MediaType.APPLICATION_JSON, expectedResponseType, additionalHeaders);
  }

  public static ResponseFuture makeAsyncMultiPartPostRequest(
      String url, MultiPart multiPartEntity, String expectedResponseType, Map<String,String> additionalHeaders) {
    LOG.info("Will send a multi-part POST request to " + url);
    multiPartEntity.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
    return makeAsyncRequest(url, expectedResponseType,
      invoker -> invoker.post(Entity.entity(multiPartEntity, multiPartEntity.getMediaType())),
      additionalHeaders, cli -> cli.register(MultiPartFeature.class));
  }

  private static ResponseFuture makeAsyncRequest(String url, String expectedResponseType,
      Function<AsyncInvoker, Future<Response>> responseProducer,
      Map<String,String> additionalHeaders,
      Function<Client,Client> clientModifier) {

    MultivaluedMap<String,Object> headers = new MultivaluedHashMap<>();

    // add accept header using passed response type or ALL if null
    headers.add(HttpHeaders.ACCEPT, expectedResponseType == null
        ? MediaType.WILDCARD : expectedResponseType);

    // add jersey header tracing header if logging flag turned on
    if (LOG_RESPONSE_HEADERS) {
      headers.add("X-Jersey-Tracing-Accept", "any");
    }

    // add any extra headers the caller submitted, overriding if necessary
    for (Entry<String,String> header : additionalHeaders.entrySet()) {
      headers.putSingle(header.getKey(), header.getValue());
    }

    return new ResponseFuture(
      responseProducer.apply(
        clientModifier.apply(makeClient())
          .target(url)
          .request()
          .headers(headers)
          .async()), LOG_RESPONSE_HEADERS);
  }

  public static CloseableResponse makeRequest(String url, HttpMethod method, Optional<JSONObject> body, Map<String,String> headers) {
    Client client = makeClient();
    WebTarget webTarget = client.target(url);
    Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
    switch(method) {
      case POST:
        return new CloseableResponse(invocationBuilder
            .headers(new MultivaluedHashMap<>(headers))
            .post(Entity.entity(body.map(JSONObject::toString)
            .orElseThrow(() -> new RuntimeException("Body is required for POST")), MediaType.APPLICATION_JSON)));
      case GET:
        body.ifPresent(b -> LOG.warn("JSONObject passed to method to generated GET request; it will be ignored."));
        return new CloseableResponse(invocationBuilder
            .headers(new MultivaluedHashMap<>(headers))
            .get());
      default:
        throw new RuntimeException("Only POST and GET methods are currently supported (not " + method + ").");
    }
  }

  public static String readSmallResponseBody(InputStream smallResponseStream) throws IOException {
    try (ByteArrayOutputStream str = new ByteArrayOutputStream()) {
      IoUtil.transferStream(str, smallResponseStream);
      return str.toString();
    }
  }

  public static String readSmallResponseBody(Response smallResponse) throws IOException {
    if (!smallResponse.hasEntity()) return "";
    try (InputStream body = (InputStream)smallResponse.getEntity()) {
      return readSmallResponseBody(body);
    }
  }

  public static Optional<Tuples.TwoTuple<String, String>> getTraceIdHeader() {
    if (ThreadContext.get(TracingConstants.TRACE_CONTEXT_KEY) != null) {
      return Optional.of(
          new Tuples.TwoTuple<>(TracingConstants.TRACE_HEADER, ThreadContext.get(TracingConstants.TRACE_CONTEXT_KEY))
      );
    }
    return Optional.empty();
  }

  private static Client makeClient() {
    return ClientBuilder
        .newClient()
        .register(new TracePropagatingClientInterceptor(ThreadContext.get(TracingConstants.TRACE_CONTEXT_KEY)));
  }
}
