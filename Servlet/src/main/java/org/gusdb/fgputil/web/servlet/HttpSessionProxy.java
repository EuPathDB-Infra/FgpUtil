package org.gusdb.fgputil.web.servlet;

import java.util.Enumeration;

import jakarta.servlet.http.HttpSession;

import org.gusdb.fgputil.collection.ReadOnlyHashMap;
import org.gusdb.fgputil.collection.ReadOnlyMap;
import org.gusdb.fgputil.web.SessionProxy;

public class HttpSessionProxy implements SessionProxy {

  private final HttpSession _session;

  public HttpSessionProxy(HttpSession session) {
    _session = session;
  }

  @Override
  public Object getAttribute(String key) {
    return _session.getAttribute(key);
  }

  @Override
  public ReadOnlyMap<String, Object> getAttributeMap() {
    ReadOnlyHashMap.Builder<String, Object> map = ReadOnlyHashMap.builder();
    Enumeration<String> names = _session.getAttributeNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      map.put(name, _session.getAttribute(name));
    }
    return map.build();
  }

  @Override
  public void setAttribute(String key, Object value) {
    _session.setAttribute(key, value);
  }

  @Override
  public void removeAttribute(String key) {
    _session.removeAttribute(key);
  }

  @Override
  public Object getUnderlyingSession() {
    return _session;
  }

  @Override
  public void invalidate() {
    _session.invalidate();
  }

  @Override
  public String getId() {
    return _session.getId();
  }

}
