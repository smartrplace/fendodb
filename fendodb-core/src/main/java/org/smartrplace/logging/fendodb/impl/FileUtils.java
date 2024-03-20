package org.smartrplace.logging.fendodb.impl;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.LoggerFactory;

public class FileUtils {
	
	private static final String TEMP_FILE_SUFFIX = ".tmp";
	
	private FileUtils() {}
	
	/**
	 * Attempt to store an object in the specified file. First, a backup file is written, then an attempt is
	 * made to copy the latter atomically to the actual target file
	 * @param directory must exist as a directory
	 * @param filename
	 * @param object
	 * @throws IOException
	 */
	public static Path writeJavaBytes(Path directory, String filename, Object object) throws IOException {
		final Path tempFile = directory.resolve(getTempFileName(filename));
		final Path targetFile = directory.resolve(filename);
		try (OutputStream fos = Files.newOutputStream(tempFile, 
					StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				ObjectOutputStream oos = new ObjectOutputStream(bos)) {
			oos.writeObject(object);
		}
		try {
			Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			LoggerFactory.getLogger(FileUtils.class).debug("The atomic move operation failed for {}", tempFile, e);
			Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return targetFile;
	}
	
	/**
	 * Attempt to read object from the specified file in the specified directory, falling back to 
	 * a backup file if the read fails.
	 * @param <T>
	 * @param directory
	 * @param filename
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws ClassCastException
	 */
	public static <T> T readJavaBytes(Path directory, String filename) throws IOException, ClassNotFoundException, ClassCastException {
		final Path targetFile = directory.resolve(filename);
		try {
			return readInternal(targetFile);
		} catch (IOException | ClassNotFoundException | ClassCastException e) {
			final Path backupFile = directory.resolve(getTempFileName(filename));
			return readInternal(backupFile);
		} 
	}
	
	@SuppressWarnings("unchecked")
	private static <T> T readInternal(Path file) throws IOException, ClassNotFoundException, ClassCastException {
		try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(file))) {
			return (T) ois.readObject();
		}
	}
	
	
	private static String getTempFileName(String filename) {
		final int idx = filename.lastIndexOf('.');
		if (idx <= 0)
			return filename + TEMP_FILE_SUFFIX;
		return filename.substring(0, idx) + TEMP_FILE_SUFFIX + filename.substring(idx);
	}
	
	static Optional<Instant> getZipFileTime(Path zip) {
		String fname = zip.getFileName().toString();
		if (!fname.endsWith(".zip")) {
			return Optional.empty();
		}
		try {
			long ts = Long.parseLong(fname, 0, fname.indexOf(".zip"), 10);
			return Optional.of(Instant.ofEpochMilli(ts));
		} catch (NumberFormatException nfe) {
			return Optional.empty();
		}
	}

}
