package org.gusdb.fgputil.client;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.functional.Either;

public class ResponseFuture {

  private static final Logger LOG = LogManager.getLogger(ResponseFuture.class);

  private final Future<Response> _response;
  private final boolean _logResponseHeadersOnReceipt;

  public static void waitForAll(Collection<ResponseFuture> futures) throws InterruptedException {
    boolean allDone = false;
    while (!allDone) {
      allDone = true;
      for (ResponseFuture response : futures) {
        if (!response.isDone()) {
          allDone = false;
          break;
        }
      }
      Thread.sleep(10); // let other threads run
    }
  }

  public ResponseFuture(Future<Response> response, boolean logResponseHeadersOnReceipt) {
    _response = response;
    _logResponseHeadersOnReceipt = logResponseHeadersOnReceipt;
  }

  public InputStream getInputStream() throws Exception {
    Either<InputStream, RequestFailure> either = getEither();
    return either.leftOrElseThrow(
      () -> new RuntimeException(either.getRight().toString()));
  }

  @SuppressWarnings("resource")
  public Either<InputStream, RequestFailure> getEither() throws Exception {
    try {
      // wait for response if necessary, and retrieve
      Response response = _response.get();

      // log headers if requested
      if (_logResponseHeadersOnReceipt) {
       logHeaders(response.getHeaders());
      }

      // if successful, return entity stream if present, else empty body
      if (response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
        if (response.hasEntity()) {
          return Either.left(new ResponseEntityInputStream(response));
        }
        else {
          // close response; it is considered "processed"
          response.close();
          return Either.left(new ByteArrayInputStream(new byte[0]));
        }
      }

      // throw exception containing error response info returned by the server
      return Either.right(new RequestFailure(response));
    }
    catch (InterruptedException e) {
      throw new RuntimeException("Thread waiting for HTTP response was interrupted", e);
    }
  }

  public boolean isDone() {
    return _response.isDone();
  }

  public void cancel() {
    _response.cancel(true);
  }

  private static void logHeaders(MultivaluedMap<String, Object> headers) {
    List<String> headerNames = new ArrayList<>(headers.keySet());
    Collections.sort(headerNames);
    for (String header : headerNames) {
      LOG.info("Header " + header + ": " + FormatUtil.join(headers.get(header).toArray(), ","));
    }
  }
}

