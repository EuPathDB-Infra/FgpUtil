package org.gusdb.fgputil.cache.disk;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.gusdb.fgputil.runtime.ThreadUtil;

/**
 * Handles locking a directory for use.  This is not a filesystem lock and
 * only other software using this implementation will respect the lock.  The
 * lock is created by writing a .lock file inside the specified directory;
 * when the lock is released, this file is removed.  Because of this strategy,
 * however, no software locks or synchronization is used so disjoint systems
 * (separate JVMs) using this class can share and respect each other's locks.
 */
public class DirectoryLock implements AutoCloseable {

  private final Path _lockFile;

  /**
   * Creates a lock on the specified directory, waiting if necessary until
   * any existing lock is released.  Lock access is unfair; there is no
   * guarantee of lock award order.
   *
   * @param directory directory to lock
   * @param timeoutMillis maximum time to wait for a lock before timing out (-1 for no limit)
   * @param pollFrequencyMillis duration between attempts to procure a lock
   * @throws DirectoryLockTimeoutException wait time for a lock has expired
   */
  public DirectoryLock(Path directory, long timeoutMillis, long pollFrequencyMillis) {

    _lockFile = Paths.get(directory.toString(), ".lock");

    // keep trying to create the lock file until successful or timeout
    long millisExpended = 0;
    while (timeoutMillis < 0 || millisExpended < timeoutMillis) {
      // try again
      try {
        Files.createFile(_lockFile);
        return; // success; got the lock
      }
      catch (FileAlreadyExistsException exists) {
        // wait, then try again
        if (ThreadUtil.sleep(pollFrequencyMillis)) {
          throw new RuntimeException("Thread performing directory lock procurement was interrupted before it could complete.");
        }
        millisExpended += pollFrequencyMillis;
      }
      catch (IOException e) {
        throw new RuntimeException("Could not create lock file in directory " + directory, e);
      }
    }
    throw new DirectoryLockTimeoutException("Timeout (" + timeoutMillis + "ms) occurred before able to lock directory: " + directory);
  }

  /**
   * @return path to the lock file (use with caution)
   */
  public Path getLockFile() {
    return _lockFile;
  }

  /**
   * Releases this lock (i.e. deletes the lock file)
   *
   * @throws IOException if unable to release the lock
   */
  public void release() throws IOException {
    try {
      Files.delete(_lockFile);
    }
    catch (NoSuchFileException e) {
      // got what we wanted
    }
  }

  /**
   * Calls release() method for AutoCloseable API
   */
  @Override
  public void close() throws Exception {
    release();
  }

  /**
   * Exception thrown if time to acquire lock has expired
   */
  public static class DirectoryLockTimeoutException extends RuntimeException {
    public DirectoryLockTimeoutException(String message) {
      super(message);
    }
  }
}
