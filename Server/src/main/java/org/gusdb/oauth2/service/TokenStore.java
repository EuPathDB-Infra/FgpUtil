package org.gusdb.oauth2.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages state on the OAuth server.  This includes OAuth server sessions,
 * their owners, and associated authentication codes and tokens
 * 
 * @author ryan
 */
public class TokenStore {

  private static final Logger LOG = LoggerFactory.getLogger(TokenStore.class);

  /*
   * HttpSession contains the username (string used in username field of
   * submitted form) of a user if the user has successfully authenticated
   * in the given session.  Any forms sent out before then are either
   * anonymous (accessed directly by client), or identified by an ID as
   * part of an OAuth authentication flow.  The ID is keyed to a set of
   * parameters sent as part of an authentication request and is used to
   * redirect the user to the proper location (with an auth code) once
   * he has successfully authenticated.  Form IDs are retained as part of
   * the session until the form is submitted, or until the session expires
   * or the user logs out.
   */

  public static class AuthCodeData {

    public final String authCode;
    public final String clientId;
    public final String username;
    public final Date creationDate;

    public AuthCodeData(String authCode, String clientId, String username) {
      this.authCode = authCode;
      this.clientId = clientId;
      this.username = username;
      this.creationDate = new Date();
    }

    @Override
    public String toString() {
      return "{ authCode: " + authCode + ", clientId: " + clientId + ", username: " + username + " }";
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof AuthCodeData &&
          authCode.equals(((AuthCodeData)other).authCode));
    }
  }

  public static class AccessTokenData {

    public final String tokenValue;
    public final AuthCodeData authCodeData;
    public final Date creationDate;

    public AccessTokenData(String tokenValue, AuthCodeData authCodeData) {
      this.tokenValue = tokenValue;
      this.authCodeData = authCodeData;
      this.creationDate = new Date();
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof AccessTokenData &&
          tokenValue.equals(((AccessTokenData)other).tokenValue));
    }
  }

  // maps to provide data lookup from code or token value
  private static final Map<String, AuthCodeData> AUTH_CODE_MAP = new HashMap<>();
  private static final Map<String, AccessTokenData> ACCESS_TOKEN_MAP = new HashMap<>();
  private static final Map<String, List<AuthCodeData>> USER_AUTH_CODE_MAP = new HashMap<>();
  private static final Map<String, List<AccessTokenData>> USER_ACCESS_TOKEN_MAP = new HashMap<>();

  public static synchronized void addAuthCode(AuthCodeData authCodeData) {
    AUTH_CODE_MAP.put(authCodeData.authCode, authCodeData);
    List<AuthCodeData> list = USER_AUTH_CODE_MAP.get(authCodeData.username);
    if (list == null) {
      list = new ArrayList<>();
      USER_AUTH_CODE_MAP.put(authCodeData.username, list);
    }
    list.add(authCodeData);
  }

  public static synchronized void addAccessToken(String accessToken, String authCode) {
    AuthCodeData authCodeData = AUTH_CODE_MAP.get(authCode);
    AccessTokenData accessTokenData = new AccessTokenData(accessToken, authCodeData);
    ACCESS_TOKEN_MAP.put(accessTokenData.tokenValue, accessTokenData);
    List<AccessTokenData> list = USER_ACCESS_TOKEN_MAP.get(accessTokenData.authCodeData.username);
    if (list == null) {
      list = new ArrayList<>();
      USER_ACCESS_TOKEN_MAP.put(accessTokenData.authCodeData.username, list);
    }
    list.add(accessTokenData);
  }

  public static synchronized boolean isValidAuthCode(String authCode, String clientId) {
    return AUTH_CODE_MAP.containsKey(authCode) && AUTH_CODE_MAP.get(authCode).clientId.equals(clientId);
  }

  public static String getUserForToken(String accessToken) {
    AccessTokenData data = ACCESS_TOKEN_MAP.get(accessToken);
    if (data != null) {
      return data.authCodeData.username;
    }
    return null;
  }

  public static synchronized void clearObjectsForUser(String username) {
    List<AuthCodeData> codeList = USER_AUTH_CODE_MAP.remove(username);
    if (codeList != null)
      for (AuthCodeData data : codeList)
        AUTH_CODE_MAP.remove(data.authCode);
    List<AccessTokenData> tokenList = USER_ACCESS_TOKEN_MAP.remove(username);
    if (tokenList != null)
      for (AccessTokenData data : tokenList)
        ACCESS_TOKEN_MAP.remove(data.tokenValue);
  }

  public static synchronized void removeExpiredTokens(int expirationSeconds) {
    long currentDateMillis = new Date().getTime();
    List<String> expiredTokens = new ArrayList<>();
    for (Entry<String, AuthCodeData> entry : AUTH_CODE_MAP.entrySet()) {
      if (isExpired(entry.getValue().creationDate, currentDateMillis, expirationSeconds)) {
        expiredTokens.add(entry.getKey());
      }
    }
    LOG.debug("Expiring the following auth codes: " + Arrays.toString(expiredTokens.toArray()));
    for (String authCode : expiredTokens) {
      AuthCodeData removedCode = AUTH_CODE_MAP.remove(authCode);
      USER_AUTH_CODE_MAP.get(removedCode.username).remove(removedCode);
    }
    expiredTokens.clear();
    for (Entry<String, AccessTokenData> entry : ACCESS_TOKEN_MAP.entrySet()) {
      if (isExpired(entry.getValue().creationDate, currentDateMillis, expirationSeconds)) {
        expiredTokens.add(entry.getKey());
      }
    }
    LOG.debug("Expiring the following access tokens: " + Arrays.toString(expiredTokens.toArray()));
    for (String accessToken : expiredTokens) {
      AccessTokenData removedToken = ACCESS_TOKEN_MAP.remove(accessToken);
      USER_AUTH_CODE_MAP.get(removedToken.authCodeData.username).remove(removedToken);
    }
  }

  private static boolean isExpired(Date creationDate, long currentDateMillis, int expirationSeconds) {
    long ageMillis = currentDateMillis - creationDate.getTime();
    return (ageMillis > (1000 * expirationSeconds));
  }
}