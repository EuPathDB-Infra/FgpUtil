package org.gusdb.fgputil.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jakarta.ws.rs.core.Response;

/**
 * InputStream implementation that wraps a JAX-RS response entity and the
 * Response source of the stream so both can be closed by the handling code
 * once the stream has been read/processed.  All InputStream methods except
 * close() are delegated to the underlying stream.
 *
 * @author rdoherty
 */
public class ResponseEntityInputStream extends InputStream {

  private final Response _response;
  private final InputStream _underlyingInputStream;

  public ResponseEntityInputStream(Response response) {
    _response = response;
    if (!response.hasEntity()) {
      throw new RuntimeException("Cannot instantiate a " +
          getClass().getSimpleName() + " if no response body is present.");
    }
    _underlyingInputStream = (InputStream)response.getEntity();
  }

  /**
   * Closes the Response object that begat the underlying InputStream being read
   * (which also closes the entity input stream if not already closed).
   */
  @Override
  public void close() throws IOException {
    _response.close(); // will also close underlying stream
  }

  /****************************************************************************************
   ***  Wrapper methods below this point are simple delegates to the underlying stream  ***
   ****************************************************************************************/

  @Override
  public int hashCode() {
    return _underlyingInputStream.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return _underlyingInputStream.equals(obj);
  }

  @Override
  public int read() throws IOException {
    return _underlyingInputStream.read();
  }

  @Override
  public int read(byte[] b) throws IOException {
    return _underlyingInputStream.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return _underlyingInputStream.read(b, off, len);
  }

  @Override
  public String toString() {
    return _underlyingInputStream.toString();
  }

  @Override
  public byte[] readAllBytes() throws IOException {
    return _underlyingInputStream.readAllBytes();
  }

  @Override
  public byte[] readNBytes(int len) throws IOException {
    return _underlyingInputStream.readNBytes(len);
  }

  @Override
  public int readNBytes(byte[] b, int off, int len) throws IOException {
    return _underlyingInputStream.readNBytes(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException {
    return _underlyingInputStream.skip(n);
  }

  @Override
  public int available() throws IOException {
    return _underlyingInputStream.available();
  }

  @Override
  public synchronized void mark(int readlimit) {
    _underlyingInputStream.mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException {
    _underlyingInputStream.reset();
  }

  @Override
  public boolean markSupported() {
    return _underlyingInputStream.markSupported();
  }

  @Override
  public long transferTo(OutputStream out) throws IOException {
    return _underlyingInputStream.transferTo(out);
  }

}
