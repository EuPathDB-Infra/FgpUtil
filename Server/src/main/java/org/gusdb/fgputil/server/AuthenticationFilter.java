package org.gusdb.fgputil.server;

import java.io.IOException;
import java.util.Collections;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.glassfish.grizzly.http.server.Request;
import org.gusdb.fgputil.accountdb.AccountManager;
import org.gusdb.fgputil.accountdb.UserProfile;
import org.gusdb.fgputil.web.LoginCookieFactory;
import org.gusdb.fgputil.web.LoginCookieFactory.LoginCookieParts;
import org.gusdb.fgputil.web.RequestData;

@PreMatching
@Priority(200)
public class AuthenticationFilter implements ContainerRequestFilter {

  private static final String AUTH_HEADER_KEY = "Auth_Key";
  private static final String USER_PROFILE_KEY = "User_Profile";

  @Inject
  private Provider<Request> _request;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    UserAwareContext context = (UserAwareContext)RESTServer.getApplicationContext();
    RequestData request = new GrizzlyRequestData(_request.get());
    String authKey = request.getHeader(AUTH_HEADER_KEY);
    LoginCookieParts parsedAuthKey;
    try {
      parsedAuthKey = LoginCookieFactory.parseCookieValue(authKey);
      if (!new LoginCookieFactory(context.getSecretKey()).isValidCookie(parsedAuthKey)) {
        throw new IllegalArgumentException();
      }
    }
    catch (IllegalArgumentException e) {
      requestContext.abortWith(Response
          .status(Status.UNAUTHORIZED)
          .entity("Request must contain header '" + AUTH_HEADER_KEY + "', containing a valid auth key.")
          .build());
      return;
    }
    String userEmail = parsedAuthKey.getUsername();
    AccountManager accountMgr = new AccountManager(context.getAccountDb(), "useraccounts.", Collections.emptyList());
    UserProfile userProfile = accountMgr.getUserProfileByEmail(userEmail);
    if (userProfile == null) {
      requestContext.abortWith(Response
          .status(Status.FORBIDDEN)
          .entity("Cannot find registered user associated with auth key: " + authKey)
          .build());
    }
    request.setAttribute(USER_PROFILE_KEY, userProfile);
  }

  public static UserProfile getUserProfile(Request request) {
    return (UserProfile)request.getAttribute(AuthenticationFilter.USER_PROFILE_KEY);
  }
}
