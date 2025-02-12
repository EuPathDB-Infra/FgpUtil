package org.gusdb.fgputil.cache.disk;

import static org.gusdb.fgputil.IoUtil.createOpenPermsDirectory;
import static org.gusdb.fgputil.functional.Functions.mapException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.gusdb.fgputil.IoUtil;
import org.gusdb.fgputil.functional.FunctionalInterfaces.ConsumerWithException;
import org.gusdb.fgputil.functional.FunctionalInterfaces.FunctionWithException;

/**
 * Manages a filesystem-based cache of entries, with data located specified directory
 *
 * The layout of the cache is as follows:
 * <pre>
 *   {top-level-dir}/
 *   {top-level-dir}/{cache-key}/           // directory where entry content is placed
 *   {top-level-dir}/{cache-key}/.lock      // indicates the entry is locked for read/write
 *   {top-level-dir}/{cache-key}/.complete  // population of this entry completed without exception
 *   {top-level-dir}/{cache-key}/.failed    // population of this entry failed via an exception
 * </pre>
 *
 * Concurrency is managed by .lock files in each entry's directory.  File system writes
 * of these files are atomic, so no software locks or synchronization are used.  The lock
 * file exists for the duration of an entry's population and access; for simplicity,
 * there is no separation between read/write access and these happen (conditionally) in
 * the same operation.
 *
 * @author rdoherty
 */
public class OnDiskCache {

  /**
   * Defines an overwrite strategy when an entry already exists
   */
  public enum Overwrite {

    /** Will always use existing entry content */
    NO        (path -> false),

    /** Will overwrite existing entry if previous attempt to populate this entry failed */
    IF_FAILED (OnDiskCache::isEntryFailed),

    /** Will overwrite existing entry regardles of previous attempt status */
    YES       (path -> true);

    private final Predicate<Path> _pred;

    private Overwrite(Predicate<Path> pred) {
      _pred = pred;
    }

    public Predicate<Path> getPredicate() {
      return _pred;
    }

    public Predicate<Path> or(Predicate<Path> additionalCondition) {
      return _pred.or(additionalCondition);
    }
  }

  // name and methods to manage completion flag file
  private static final String SUCCESS_FILE = ".complete";
  private static void setEntryComplete(Path entryDir) throws IOException {
    Files.createFile(Paths.get(entryDir.toString(), SUCCESS_FILE));
  }
  public static boolean isEntryComplete(Path entryDir) {
    return Files.exists(Paths.get(entryDir.toString(), SUCCESS_FILE));
  }

  // name and methods to manage failure flag file
  private static final String FAILED_FILE = ".failed";
  private static void setEntryFailed(Path entryDir) throws IOException {
    Files.createFile(Paths.get(entryDir.toString(), FAILED_FILE));
  }
  public static boolean isEntryFailed(Path entryDir) {
    return Files.exists(Paths.get(entryDir.toString(), FAILED_FILE));
  }

  private final Path _parentDirectory;
  private final long _populationTimeoutMillis;
  private final long _lockPollFrequencyMillis;

  /**
   * Creates a new on-disk cache
   *
   * @param parentDirectory parent directory for the cache's storage
   * @param populationTimeoutMillis maximum time to wait for an entry to be unlocked before timing out (-1 for no limit)
   * @param lockPollFrequencyMillis duration between attempts to procure an entry lock for write/read
   * @throws IOException if parent directory does not exist or cannot be read or written to (rwx on unix systems)
   */
  public OnDiskCache(Path parentDirectory, long populationTimeoutMillis, long lockPollFrequencyMillis) throws IOException {
    if (!Files.isDirectory(parentDirectory) ||
        !Files.isReadable(parentDirectory) ||
        !Files.isWritable(parentDirectory) ||
        !Files.isExecutable(parentDirectory)) {
      throw new IOException("Path " + parentDirectory.toAbsolutePath() + " must be a readable, writeable directory.");
    }
    _parentDirectory = parentDirectory;
    _populationTimeoutMillis = populationTimeoutMillis;
    _lockPollFrequencyMillis = lockPollFrequencyMillis;
  }

  /**
   * Visits cached content in a keyed directory, populating that content if necessary
   * or requested.
   *
   * @param cacheKey key for this cache entry; must be a valid directory name on your OS
   * @param cachePopulator populates the contents of the passed directory with files to be cached
   * @param cacheVisitor visits the cached files and returns a value generated from the content
   * @param overwriteFlag indicates conditions under which cache entry content should be overwritten
   * @throws IllegalArgumentException if cache key is an illegal value
   * @throws DirectoryLockTimeoutException if unable to procure an entry lock before timeout
   * @throws Exception typically forwarded exceptions thrown by the consumer or predicate arguments
   */
  public <T> T populateAndProcessContent(
      String cacheKey,
      ConsumerWithException<Path> cachePopulator,
      FunctionWithException<Path, T> cacheVisitor,
      Overwrite overwriteFlag) throws Exception {
    return populateAndProcessContent(cacheKey, cachePopulator, cacheVisitor, overwriteFlag.getPredicate());
  }

  /**
   * Visits cached content in a keyed directory, populating that content if necessary
   * or requested.
   *
   * @param cacheKey key for this cache entry; must be a valid directory name on your OS
   * @param cachePopulator populates the contents of the passed directory with files to be cached
   * @param cacheVisitor visits the cached files and returns a value generated from the content
   * @param conditionalOverwritePredicate returns true if the entry's content should be overwritten, else false
   * @throws IllegalArgumentException if cache key is an illegal value
   * @throws DirectoryLockTimeoutException if unable to procure an entry lock before timeout
   * @throws Exception typically forwarded exceptions thrown by the consumer or predicate arguments
   */
  public <T> T populateAndProcessContent(
      String cacheKey,
      ConsumerWithException<Path> cachePopulator,
      FunctionWithException<Path,T> cacheVisitor,
      Predicate<Path> conditionalOverwritePredicate) throws Exception {

    // determine path to entry directory and ensure existence (atomic)
    Path path = getEntryPath(cacheKey);
    mapException(() -> createOpenPermsDirectory(path, true),
        e -> new RuntimeException("Could not create disk cache entry directory " + path, e));

    // get a lock on this entry
    try (DirectoryLock lock = new DirectoryLock(path, _populationTimeoutMillis, _lockPollFrequencyMillis)) {

      // decide whether to overwrite this entry
      if ((!isEntryComplete(path) && !isEntryFailed(path)) // either brand new entry or something has gone wrong
          || conditionalOverwritePredicate.test(path)) {   // check if caller wants to overwrite

        // clear out the directory, except the lock file and the directory itself
        IoUtil.deleteDirectoryTree(path, path, lock.getLockFile());

        // populate the entry directory
        try {
          cachePopulator.accept(path);
          setEntryComplete(path);
        }
        catch (Exception e) {
          // populator errored; write error file
          setEntryFailed(path);
          throw e;
        }
      }

      // entry population complete (for better or worse); visit the produced files
      return cacheVisitor.apply(path);
    }
  }

  private Path getEntryPath(String cacheKey) {
    return mapException(() -> Paths.get(_parentDirectory.toString(), Objects.requireNonNull(cacheKey)),
        e -> new IllegalArgumentException("Illegal cache key; " + e.getMessage()));
  }

  public static class EntryNotCreatedException extends NoSuchFileException {
    public EntryNotCreatedException(String file) {
      super(file);
    }
  }

  /**
   * Visits the content of a cache entry.  If an entry does not yet exist, does NOT
   * create a new one, but a EntryNotCreatedException is thrown and the visitor is not called.
   *
   * @param cacheKey key for this cache entry
   * @param cacheVisitor visits the cached files and returns a value generated from the content
   * @throws EntryNotCreatedException if no entry yet exists for this key
   * @throws Exception if exception is thrown while accessing entry or by the contentVisitor
   */
  public <T> T visitContent(String cacheKey, FunctionWithException<Path,T> contentVisitor) throws Exception {
    Path path = getEntryPath(cacheKey);
    if (!Files.exists(path)) {
      throw new EntryNotCreatedException(path.toAbsolutePath().toString());
    }
    return populateAndProcessContent(cacheKey, d -> {}, contentVisitor, Overwrite.NO);
  }

  /**
   * Waits for the lock on the entry for this key, then deletes the entry.
   *
   * @param cacheKey key for this cache entry
   */
  public void removeEntry(String cacheKey) {

    // determine path; if not present, nothing to do
    Path path = getEntryPath(cacheKey);
    if (!Files.exists(path)) return;

    // get a lock on this entry
    try (DirectoryLock lock = new DirectoryLock(path, _populationTimeoutMillis, _lockPollFrequencyMillis)) {
      // to minimize likelihood of a collision, delete everything but the lock in step one
      IoUtil.deleteDirectoryTree(path, path, lock.getLockFile());
      // then delete both the lock file and parent directory
      IoUtil.deleteDirectoryTree(path);
    }
    catch (Exception e) {
      throw (e instanceof RuntimeException) ? (RuntimeException)e :
        new RuntimeException("Unable to delete entry at " + path.toAbsolutePath());
    }
  }

  /**
   * Removes all entries in the cache store's parent directory.  Note it will delete ALL directories
   * in the parent dir, assuming they are meant to be entries.  It will not delete plain files.
   */
  public void removeAllEntries() {
    try {
      List<String> entries = Files.list(_parentDirectory).map(p -> p.getFileName().toString()).collect(Collectors.toList());
      for (String cacheKey : entries) {
        if (Files.isDirectory(Paths.get(_parentDirectory.toString(), cacheKey))) {
          removeEntry(cacheKey);
        }
      }
    }
    catch (Exception e) {
      throw (e instanceof RuntimeException) ? (RuntimeException)e :
        new RuntimeException("Unable to delete all entries in " + _parentDirectory.toAbsolutePath());
    }
  }
}
