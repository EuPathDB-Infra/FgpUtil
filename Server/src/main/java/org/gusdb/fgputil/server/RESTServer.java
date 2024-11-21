package org.gusdb.fgputil.server;

import static org.gusdb.fgputil.FormatUtil.NL;
import static org.gusdb.fgputil.IoUtil.closeQuietly;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;

import jakarta.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.IoUtil;
import org.gusdb.fgputil.Tuples.ThreeTuple;
import org.gusdb.fgputil.web.ApplicationContext;
import org.gusdb.fgputil.Wrapper;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * General use Jersey-based REST server that provides hooks for startup and
 * creation/closing of application-scoped resources, and graceful shutdown.
 * 
 * @author rdoherty
 */
public abstract class RESTServer {

  protected abstract ResourceConfig getResourceConfig();
  protected abstract ApplicationContext createApplicationContext(URI serviceUri, JSONObject config);

  private static ApplicationContext _applicationContext;

  private final String _baseUri;
  private final int _port;
  private final JSONObject _config;

  public static ApplicationContext getApplicationContext() {
    return _applicationContext;
  }

  /**
   * Creates a REST server superclass with the passed args.  Args should be:
   * @param commandLineArgs
   */
  public RESTServer(String[] commandLineArgs) {

    // initialize logging
    LoggingInitializer.initialize();

    // parse command line args into desired port and server config
    ThreeTuple<String, Integer, JSONObject> parsedArgs = parseConfig(commandLineArgs);
    _baseUri = parsedArgs.getFirst();
    _port = parsedArgs.getSecond();
    _config = parsedArgs.getThird();
  }

  /**
   * @return true by default; subclasses may override if they do not need a config file
   */
  protected boolean requiresConfigFile() {
    return true;
  }

  public void start() {

    HttpServer server = null;
    ApplicationContext applicationContext = null;

    try {

      // create base URI for server
      URI serviceUri = UriBuilder
          .fromUri(_baseUri)
          .port(_port)
          .build();

      // create runtime environment (application-scoped data)
      applicationContext = createApplicationContext(serviceUri, _config);
      synchronized(RESTServer.class) {
        if (_applicationContext != null) {
          throw new RuntimeException("Only one RESTServer can be used per application.");
        }
        _applicationContext = applicationContext;
      }

      // build Grizzly server
      server = GrizzlyHttpServerFactory.createHttpServer(serviceUri, getResourceConfig());

      // add hook to shut down resources if JVM is asked to shut down
      Wrapper<HttpServer> serverWrapper = new Wrapper<>(server);
      Wrapper<ApplicationContext> contextWrapper = new Wrapper<>(applicationContext);
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        serverWrapper.get().shutdownNow();
        closeQuietly(contextWrapper.get());
      }));

      // start the server
      server.start();

      System.out.println("Server started on port " + serviceUri.getPort() + ", serving from " + serviceUri + ". Stop the application using CTRL+C");

      Thread.currentThread().join();
    }
    catch (Exception e) {
      System.out.println("An exception occurred and the server " +
          "must shut down." + NL + FormatUtil.getStackTrace(e));
      System.exit(3);
    }
    finally {
      if (server != null) server.shutdown();
      if (applicationContext != null) closeQuietly(applicationContext);
    }
  }

  protected ThreeTuple<String, Integer, JSONObject> parseConfig(String[] args) {
    int maxArgs = requiresConfigFile() ? 3 : 2;
    if (args.length < 2 || args.length > maxArgs || !FormatUtil.isInteger(args[1])) {
      String configFileOpt = requiresConfigFile() ? " [<config-file>]" : "";
      System.err.println("USAGE: fgpJava " + getClass().getName() + " <baseUri> <port>" + configFileOpt);
      System.err.println("\n     Note: baseUri argument should be a fully qualified URL to the service (e.g. http...) OR the service path beginning with a '/' (e.g. /my-service)\n");
      System.exit(1);
    }
    String baseUri = args[0];
    if (baseUri.startsWith("/")) {
      baseUri = "http://localhost" + baseUri;
    }
    int port = Integer.parseInt(args[1]);
    JSONObject config = new JSONObject(args.length < 3 ? "{}" : readConfigFile(args[2]));
    return new ThreeTuple<>(baseUri, port, config);
  }

  private String readConfigFile(String fileName) {
    try (FileReader in = new FileReader(fileName)) {
      return IoUtil.readAllChars(in);
    }
    catch (IOException | JSONException e) {
      System.err.println("Error: Unable to read JSON config file " + fileName + ":" + NL + e.getMessage());
      System.exit(2);
      return null;
    }
  }
}
