package org.gusdb.fgputil.web;

import jakarta.ws.rs.core.NewCookie;

public class CookieBuilder {

  private String _name;
  private String _value = "";
  private int _maxAge = -1;
  private String _path = "/";

  public CookieBuilder() {}

  public CookieBuilder(String name, String value) {
    _name = name;
    _value = value;
  }

  public String getName() {
    return _name;
  }

  public void setName(String name) {
    _name = name;
  }

  public String getValue() {
    return _value;
  }

  public void setValue(String value) {
    _value = value;
  }

  public int getMaxAge() {
    return _maxAge;
  }

  public void setMaxAge(int maxAge) {
    _maxAge = maxAge;
  }

  public String getPath() {
    return _path;
  }

  public void setPath(String path) {
    _path = path;
  }

  public NewCookie toJaxRsCookie() {
    return new NewCookie.Builder(getName())
        .value(getValue())
        .path(getPath())
        .version(NewCookie.DEFAULT_VERSION)
        .maxAge(getMaxAge())
        .secure(false)
        .build();
  }

}
