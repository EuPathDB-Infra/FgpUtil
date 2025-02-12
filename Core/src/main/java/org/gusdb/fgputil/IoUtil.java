package org.gusdb.fgputil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.log4j.Logger;

public class IoUtil {

  private static final Logger LOG = Logger.getLogger(IoUtil.class);

  public static final int DEFAULT_ERROR_EXIT_CODE = 2;

  /**
   * Converts binary data into an input stream.  This can be used if the result
   * type is a stream, and the content to be returned already exists in memory
   * as a string.  This is simply a wrapper around the ByteArrayInputStream
   * constructor.
   *
   * @param data data to be converted
   * @return stream representing the data
   */
  public static InputStream getStreamFromBytes(byte[] data) {
    return new ByteArrayInputStream(data);
  }

  /**
   * Converts a string into an open input stream.  This can be used if the
   * result type is a stream, and the content to be returned already exists in
   * memory as a string.
   *
   * @param str string to be converted
   * @return input stream representing the string
   */
  public static InputStream getStreamFromString(String str) {
    return getStreamFromBytes(str.getBytes(Charset.defaultCharset()));
  }

  /**
   * Recursively removes the passed directory, except for any paths provided as exclusions
   *
   * @param directory directory to remove
   * @throws IOException if unable to delete entire directory tree (deletion will stop after first error)
   */
  public static void deleteDirectoryTree(Path directory, Path... exclusions) throws IOException {
    Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {

      private boolean exclude(Path file) throws IOException {
        for (Path path : exclusions) {
          if (Files.isSameFile(path, file))
            return true;
        }
        return false;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!exclude(file)) {
          Files.delete(file);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
        if (e == null) {
          if (!exclude(dir)) {
            Files.delete(dir);
          }
          return FileVisitResult.CONTINUE;
        }
        else {
          // directory iteration failed
          throw e;
        }
      }
    });
  }

  /**
   * Checks if the passed directory exists and is readable and throws an exception supplied by
   * the passed supplier if not
   *
   * @param directoryName directory to check
   * @return File object for found, readable directory
   * @throws T exception thrown if directory specified does not exist or is not readable
   */
  public static <T extends Exception> File getReadableDirectoryOrThrow(String directoryName, Supplier<T> supplier) throws T {
    File f = new File(directoryName);
    if (!f.isDirectory() || !f.canWrite()) {
      throw supplier.get();
    }
    return f;
  }

  /**
   * Checks if the passed directory exists and is writable and calls
   * System.exit() with the default exit error code if not
   *
   * @param directoryName directory to check
   * @return File object for found, writable directory
   */
  public static File getWritableDirectoryOrDie(String directoryName) {
    return getWriteableDirectoryOrThrow(directoryName, () -> {
      File f = new File(directoryName);
      System.err.println("ERROR: " + f.getAbsolutePath() + " is not a writable directory.");
      System.exit(DEFAULT_ERROR_EXIT_CODE);
      return new RuntimeException(); // unreachable code
    });
  }

  /**
   * Checks if the passed directory exists and is writable and throws an exception supplied by
   * the passed supplier if not
   *
   * @param directoryName directory to check
   * @return File object for found, writable directory
   * @throws T exception thrown if directory specified does not exist or is not writeable
   */
  public static <T extends Exception> File getWriteableDirectoryOrThrow(String directoryName, Supplier<T> supplier) throws T {
    File f = new File(directoryName);
    if (!f.isDirectory() || !f.canWrite()) {
      throw supplier.get();
    }
    return f;
  }

  /**
   * Checks if the passed file exists and is readable and calls
   * System.exit() with the default exit error code if not
   *
   * @param fileName directory to check
   * @return File object for found, writable directory
   */
  public static File getReadableFileOrDie(String fileName) {
    return getReadableFileOrThrow(fileName, () -> {
      File f = new File(fileName);
      System.err.println("ERROR: " + f.getAbsolutePath() + " is not a readable file.");
      System.exit(DEFAULT_ERROR_EXIT_CODE);
      return new RuntimeException(); // unreachable code
    });
  }

  /**
   * Checks if the passed file exists and is readable and throws an exception supplied by
   * the passed supplier if not
   *
   * @param fileName directory to check
   * @return File object for found, writable directory
   * @throws T exception thrown if file specified does not exist or is not readable
   */
  public static <T extends Exception> File getReadableFileOrThrow(String fileName, Supplier<T> supplier) throws T {
    File f = new File(fileName);
    if (!f.isFile() || !f.canRead()) {
      throw supplier.get();
    }
    return f;
  }

  /**
   * Tries to close each of the passed Closeables, but does not throw error if
   * the close does not succeed.  Also ignores nulls.
   *
   * @param closeable array of closable objects
   */
  public static void closeQuietly(Closeable... closeable) {
    for (Closeable each : closeable) {
      try { if (each != null) each.close(); } catch (Exception ex) { /* do nothing */ }
    }
  }

  /**
   * Transfers data from input stream to the output stream until no more data
   * is available, then closes input stream (but not output stream).
   *
   * @param outputStream output stream data is written to
   * @param inputStream input stream data is read from
   * @throws IOException if problem reading/writing data occurs
   */
  public static void transferStream(OutputStream outputStream, InputStream inputStream)
      throws IOException {
    try {
      byte[] buffer = new byte[10240]; // send 10kb at a time
      int bytesRead = inputStream.read(buffer);
      //if (LOG.isDebugEnabled()) logBuffer(buffer, bytesRead);
      while (bytesRead != -1) {
        outputStream.write(buffer, 0, bytesRead);
        bytesRead = inputStream.read(buffer);
        //if (LOG.isDebugEnabled()) logBuffer(buffer, bytesRead);
      }
    }
    finally {
      // only close input stream; container will close output stream
      IoUtil.closeQuietly(inputStream);
      // flush the output stream in both success and failure cases to avoid
      //   bytes being stuck in the buffer after exception is thrown
      outputStream.flush();
    }
  }

  @SuppressWarnings("unused") // used for debug when necessary; removed for efficiency
  private static void logBuffer(byte[] buffer, int bytesRead) {
    if (bytesRead == -1) {
      LOG.debug("End of stream");
    }
    else {
      LOG.debug("Loaded " + bytesRead + " into buffer.");
      LOG.trace("Buffer contents: " + new String(Arrays.copyOf(buffer, bytesRead)));
    }
  }

  /**
   * Transfers character data from the reader to the writer until no more data
   * is available, then closes reader (but not the writer).
   *
   * @param writer writer data is written to
   * @param reader reader data is read from
   * @throws IOException if problem reading/writing data occurs
   */
  public static void transferStream(Writer writer, Reader reader) throws IOException {
    try {
      char[] buffer = new char[10240]; // send 10kb at a time (depending on encoding)
      int bytesRead;
      while ((bytesRead = reader.read(buffer)) != -1) {
        writer.write(buffer, 0, bytesRead);
      }
    }
    finally {
      // only close reader; container will close writer
      reader.close();
    }
  }

  /**
   * Transfers character data from the reader to the writer until no more data
   * is available, then closes reader (but not the writer).  This version also
   * allows arbitrary escaping of characters depending on your use case; each
   * character will be translated by the escaper before being written to the
   * writer.
   *
   * @param writer writer data is written to
   * @param reader reader data is read from
   * @param characterEscaper translation function for escaping character values
   * @throws IOException if problem reading/writing data occurs
   */
  public static void transferStream(BufferedWriter writer, BufferedReader reader,
      Function<Character, String> characterEscaper) throws IOException {
    try {
      char[] buffer = new char[10240]; // send 10kb at a time (depending on encoding)
      int bytesRead;
      while ((bytesRead = reader.read(buffer)) != -1) {
        for (int i = 0; i < bytesRead; i++) {
          writer.write(characterEscaper.apply(buffer[i]));
        }
      }
    }
    finally {
      // only close reader; container will close writer
      reader.close();
    }
  }

  /**
   * Serializes a serializable object into a byte array and returns it
   *
   * @param obj object to be serialized
   * @return serialized object
   * @throws IOException if unable to serialize
   */
  public static byte[] serialize(Serializable obj) throws IOException {
    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
         ObjectOutputStream objStream = new ObjectOutputStream(byteStream)) {
      objStream.writeUnshared(obj);
      return byteStream.toByteArray();
    }
  }

  /**
   * Deserializes a byte array into a Java object.
   *
   * @param bytes serialized object
   * @return serializable object built from the passed bytes
   * @throws IOException if unable to convert bytes to object
   * @throws ClassNotFoundException if serialized object's class cannot be
   * found in the current classpath
   */
  public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
         ObjectInputStream objStream = new ObjectInputStream(byteStream)) {
      return objStream.readUnshared();
    }
  }

  /**
   * Read all available characters from the passed Reader and return them as a
   * String.
   *
   * @param charReader Reader from which to read chars
   * @return String containing chars read from reader
   * @throws IOException if unable to read chars
   */
  public static String readAllChars(Reader charReader) throws IOException {
    if (charReader == null) return null;
    StringBuilder buffer = new StringBuilder();
    int c;
    while ((c = charReader.read()) > -1) {
      buffer.append((char)c);
    }
    return buffer.toString();
  }

  /**
   * Reads all available bytes from the passed input stream and returns them as
   * a byte array.
   *
   * @param inputStream stream from which to read bytes
   * @return byte array containing bytes read from stream
   * @throws IOException if unable to read bytes
   */
  public static byte[] readAllBytes(InputStream inputStream) throws IOException {
    if (inputStream == null) return null;
    ByteArrayOutputStream byteCollector = new ByteArrayOutputStream();
    transferStream(byteCollector, inputStream);
    return byteCollector.toByteArray();
  }

  /**
   * Opens a series of files and places readers of them into an AutoCloseableList.  If
   * any of the files are unopenable for read, any already opened readers are closed and
   * an exception is thrown.
   *
   * @param files list of paths of files to be opened for reading
   * @return list of readers that can be closed together
   * @throws FileNotFoundException if unable to open any of the files for read
   */
  public static AutoCloseableList<BufferedReader> openFilesForRead(List<Path> files) throws FileNotFoundException {
    AutoCloseableList<BufferedReader> list = new AutoCloseableList<>();
    try {
      for (Path p : files) {
        list.add(new BufferedReader(new FileReader(p.toFile())));
      }
      return list;
    }
    catch (FileNotFoundException e) {
      list.close();
      throw e;
    }
  }

  /**
   * Opens a series of files and places writers to them into an AutoCloseableList.  If
   * any of the files are unopenable for write, any already opened writers are closed and
   * an exception is thrown.
   *
   * @param files list of paths of files to be opened for write
   * @return list of writers that can be closed together
   * @throws IOException if unable to open any of the files for write
   */
  public static AutoCloseableList<BufferedWriter> openFilesForWrite(List<Path> files) throws IOException {
    AutoCloseableList<BufferedWriter> list = new AutoCloseableList<>();
    try {
      for (Path p : files) {
        list.add(new BufferedWriter(new FileWriter(p.toFile())));
      }
      return list;
    }
    catch (IOException e) {
      list.close();
      throw e;
    }
  }

  /**
   * Create a directory at the given path and open rwx perms to all.
   *
   * @param directory path to directory
   * @throws IOException if unable to create directory or apply permissions
   */
  public static void createOpenPermsDirectory(Path directory) throws IOException {
    createOpenPermsDirectory(directory, false);
  }

  /**
   * Create a directory at the given path and open rwx perms to all.  Second argument allows caller to use
   * an existing directory by that name.  If directory exists and useExisting is true, an attempt will be
   * made to open permissions on the existing directory.  If useExisting is false, an exception will be
   * thrown if the directory already exists.
   *
   * @param directory path to directory
   * @param useExisting if false and directory exists, an exception will be thrown
   * @return true if this method created the directory, false if useExisting is true and directory already existed
   * @throws FileAlreadyExistsException if useExisting is false and directory already exists
   * @throws IOException if unable to create directory
   */
  public static boolean createOpenPermsDirectory(Path directory, boolean useExisting) throws IOException {
    boolean alreadyCreated = false;
    try {
      directory = Files.createDirectory(directory);
    }
    catch (FileAlreadyExistsException e) {
      alreadyCreated = true;
      if (!useExisting) {
        throw e;
      }
    }
    openPosixPermissions(directory);
    return !alreadyCreated;
  }

  /**
   * Creates a directory by creating all nonexistent parent directories first. Unlike the createDirectory method,
   * an exception is not thrown if the directory could not be created because it already exists.  This method
   * assumes you are on a POSIX system and applies open permissions (rwxrwxrwx) to any directories created during
   * its call.  This is done atomically when creating the nonexistent directories.  If this method fails, then it may
   * do so after creating some, but not all, of the parent directories.
   *
   * @param directory the directory to create
   * @return the directory
   */
  public static Path createOpenPermsDirectories(Path directory) throws IOException {
    return Files.createDirectories(directory, getOpenPosixPermsAsFileAttribute());
  }

/**
   * Generate an empty, open-permissions temporary directory in the default tmp location.
   *
   * @param dirPrefix prefix applied to generated directory name
   * @return path to generated directory
   * @throws IOException if unable to create directory or apply open permissions
   */
  public static Path createOpenPermsTempDir(String dirPrefix) throws IOException {
    return openPosixPermissions(Files.createTempDirectory(dirPrefix));
  }

  /**
   * Generate an empty, open-permissions temporary directory under the passed path.
   *
   * @param parentDir parent directory in which new directory will be located
   * @param dirPrefix prefix applied to generated directory name
   * @return path to generated directory
   * @throws IOException if unable to create directory or apply open permissions
   */
  public static Path createOpenPermsTempDir(Path parentDir, String dirPrefix) throws IOException {
    return openPosixPermissions(Files.createTempDirectory(parentDir, dirPrefix));
  }

  /**
   * Opens all POSIX permission (i.e. rwxrwxrwx) for the passed path, ignoring UnsupportedOperationException
   * in case this method is called from a non-POSIX-like OS.
   *
   * @param path path to apply permissions to
   * @return the passed path
   * @throws IOException if I/O error occurs
   */
  public static Path openPosixPermissions(Path path) throws IOException {
    // apply file permissions after the fact in case umask restrictions prevent it during creation
    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxrwx");
    try {
      Files.setPosixFilePermissions(path, perms);
    }
    catch (UnsupportedOperationException ex) {
      // log but ignore it since it's not supported on Windows
      LOG.warn("Cannot set permissions to " + path);
    }
    return path;
  }

  /**
   * Create a file attribute representing open file permissions on a POSIX system (rwx on owner, group, all)
   *
   * @return open posix permissions as a file attribute
   */
  public static FileAttribute<Set<PosixFilePermission>> getOpenPosixPermsAsFileAttribute() {
    return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"));
  }

  /**
   * Given an absolute path to a directory with optional wildcards, returns all matching directories. Note that multiple
   * directories will only be returned if wildcards are specified. Note that this API does not support full glob syntax.
   * Asterisks will only be treated as wild if they are the only character in a path segment (e.g. '/var/log/&#42;/archive').
   * @param absolutePath Path with optional wildcards to search
   * @return All matching paths.
   * @throws IOException If unable to traverse directory due to I/O issues.
   */
  public static List<Path> findDirsFromAbsoluteDirWithWildcards(String absolutePath) throws IOException {
    List<Path> allPaths = new ArrayList<>();
    addPaths("/", absolutePath.split("/"), 0, allPaths);
    return allPaths;
  }

  private static void addPaths(String partialPath, String[] segments, int segmentIndex, List<Path> pathsList) throws IOException {
    Path path = Paths.get(partialPath);
    // only interested in directories at any level
    if (Files.isDirectory(path)) {
      if (segmentIndex == segments.length) {
        // done with this branch; add and return
        pathsList.add(path);
      }
      else if (segments[segmentIndex].equals("*")) {
        // this segment is a wildcard; find all dirs in the partial path and test each
        for (Path foundPath : Files.newDirectoryStream(path)) {
          addPaths(partialPath + "/" + foundPath.getFileName() , segments, segmentIndex + 1, pathsList);
        }
      }
      else {
        // this segment is a regular dir; append to partial path and move on
        addPaths(partialPath + "/" + segments[segmentIndex], segments, segmentIndex + 1, pathsList);
      }
    }
  }

}
