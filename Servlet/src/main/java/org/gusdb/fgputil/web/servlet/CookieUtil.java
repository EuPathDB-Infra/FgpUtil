package org.gusdb.fgputil.web.servlet;

import jakarta.servlet.http.Cookie;

import org.gusdb.fgputil.web.CookieBuilder;

public class CookieUtil {

  public static Cookie toHttpCookie(CookieBuilder cb) {
    Cookie cookie = new Cookie(cb.getName(), cb.getValue());
    cookie.setMaxAge(cb.getMaxAge());
    cookie.setPath(cb.getPath());
    return cookie;
  }
}
