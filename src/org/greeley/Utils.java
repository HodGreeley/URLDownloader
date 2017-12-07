package org.greeley;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {
    private static final int BUFFER_SIZE = 8192;

    /**
     * Copy data from an input stream to a file, while checking for thread interrupts.
     *
     * Note the IO calls are not interruptible, so this only does best effort.  Other
     * techniques should be used in addition, such as separately closing a underlying
     * connection, to help ensure a prompt return if needed.
     *
     * @param is source input stream
     * @param file destination file
     * @throws IOException
     * @throws InterruptedException
     */

    public static void interruptibleWriteStreamToFile(InputStream is, File file) throws IOException, InterruptedException {
        byte[] buffer = new byte[BUFFER_SIZE];

        try (BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE);
             FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);
        ) {
            int count;

            if (Thread.interrupted()) {
                throw(new InterruptedException());
            }

            while ((count = bis.read(buffer)) != -1) {
                if (Thread.interrupted()) {
                    throw(new InterruptedException());
                }

                bos.write(buffer, 0, count);
            }
        } catch (FileNotFoundException ex) {
            System.err.println("Failed creating output stream for temp file " + file.getAbsolutePath());

            throw(new RuntimeException());
        }
    }

    private static final Object renameLock = new Object();

    /**
     * Synchronized rename of a file, which overwrites any previous content.
     *
     * Routine to guarantee only one thread at a time attempts to rename
     * a file, potentially overwriting an existing file.
     * Use when two or more threads might collide in moving files, the order
     * of renames/overwrites is unimportant.
     *
     * @param from source file (must exist)
     * @param to destination file (may exist)
     */

    public static void syncedFileForcedRename(File from, File to) {
        synchronized (renameLock) {
            if (to.exists()) {
                to.delete();
            }

            if (!from.renameTo(to)) {
                System.err.println("File rename failed (?) " + from.getName() + " " + to.getName());
            }
        }
    }

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * Converts binary data to a hex digit representation.
     *
     * @param data binary hash data to convert to a hex string.
     * @return a string of hex nibbles encoding the input data.
     */

    public static String bytesToHexString(byte[] data) {
        if (null == data) {
            return(null);
        }

        char[] chars = new char[data.length * 2];

        for (int nn = 0; nn < data.length; ++nn) {
            chars[nn * 2] = HEX_DIGITS[(data[nn] >> 4) & 0xf];
            chars[nn * 2 + 1] = HEX_DIGITS[data[nn] & 0xf];
        }

        return(new String(chars));
    }

    /**
     * Creates a SHA-1 hash of an input string, encoded as a hex nibble string.
     *
     * @param string a UTF8-encoded string to hash.
     * @return a hex-encoded SHA-1 hash of string.
     */

    public static String sha1AsHexString(String string) {
        if (null == string) {
            return(null);
        }

        MessageDigest digest = null;

        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            System.err.println("SHA-1 not supported");
            System.exit(1);
        }

        digest.reset();

        try {
            digest.update(string.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            System.err.println("UTF8 encoding not supported");
            System.exit(1);
        }

        return(bytesToHexString(digest.digest()));
    }

    /**
     * Creates a new HTTP connection object and attempts to connect.
     *
     * Sets caching, redirection, user interaction, and do output options to false.
     *
     * @param url URL of page to download.
     * @return a new HTTP connection.
     * @throws IOException if unable to create a new HTTP connection.
     */

    public static HttpURLConnection createHttpConnection(String url) throws IOException {
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setAllowUserInteraction(false);
        connection.setDoOutput(false);

        return(connection);
    }

    /**
     * Pause (sleep) for a fixed time, exit if interrupted.
     *
     * This is a convenience routine meant for use in situations
     * where an interruption indicates abnormal behavior.
     *
     * @param seconds is the length of time to pause.
     */

    public static void pause(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException ex) {
            System.err.println("Unexpected interrupt during pause");

            System.exit(1);
        }
    }
}
