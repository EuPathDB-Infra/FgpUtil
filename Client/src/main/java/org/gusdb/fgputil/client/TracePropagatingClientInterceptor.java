package org.gusdb.fgputil.client;

import org.apache.log4j.Logger;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.io.IOException;

/**
 * Jersey client request interceptor used to propagate the trace ID from the thread context as a header in the
 * HTTP request.
 */
public class TracePropagatingClientInterceptor implements ClientRequestFilter {

  private String traceId;

  public TracePropagatingClientInterceptor(String traceId) {
    this.traceId = traceId;
  }

  @Override
  public void filter(ClientRequestContext clientRequestContext) throws IOException {
    if (traceId != null) {
      clientRequestContext.getHeaders().add(TracingConstants.TRACE_HEADER, traceId);
    }
  }
}
