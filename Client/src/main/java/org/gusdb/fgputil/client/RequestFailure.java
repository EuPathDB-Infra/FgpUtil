package org.gusdb.fgputil.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import jakarta.ws.rs.core.Response;

import org.gusdb.fgputil.IoUtil;
import org.gusdb.fgputil.Tuples.TwoTuple;

public class RequestFailure extends TwoTuple<Response.StatusType, String> {

  /**
   * Caches the response code and body for future use and closes the response
   *
   * @param failureResponse
   */
  public RequestFailure(Response failureResponse) {
    super(failureResponse.getStatusInfo(), readResponseEntity(failureResponse));
  }

  private static String readResponseEntity(Response response) {
    try {
      if (response.hasEntity()) {
        try (Reader in = new InputStreamReader((InputStream)response.getEntity())) {
          return IoUtil.readAllChars(in);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      else {
        return "";
      }
    }
    finally {
      response.close();
    }
  }

  public Response.StatusType getStatusType() {
    return getFirst();
  }

  public String getResponseBody() {
    return getSecond();
  }

  @Override
  public String toString() {
    return getStatusType().getStatusCode() + " " + getStatusType().getReasonPhrase() + ": " + getResponseBody();
  }
}
