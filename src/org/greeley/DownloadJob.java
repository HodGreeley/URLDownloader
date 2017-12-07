package org.greeley;

import java.io.File;
import java.io.IOException;

import java.net.HttpURLConnection;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import java.util.concurrent.*;

/**
 * A concrete implementation of a class to perform URL downloads.
 *
 * <p>
 *     Implements {@code URLDownloader.Job} interface.  Executes downloads
 * in parallel using a thread pool type that allows for an unbounded
 * number of threads.  One thread is started for each URL, and one more
 * to manage the others and run the final callback.
 * </p>
 */

class DownloadJob implements URLDownloader.Job {
    private final long timeout;
    private final List<String> urls;
    private final ExecutorService executorService;
    private final ExecutorCompletionService<String> completionService;
    private final HashMap<Future<String>, String> downloadMap;
    private final URLDownloader.OnDownloadsCompleteListener listener;

    /**
     * C'tor taking list of URLs to download, timeout, threading
     * ExecutorService, and callback object.
     *
     * Does simple initialization only.
     *
     * @param urls {@code List<String>} of urls to retrieve.
     * @param timeout maximum time to allow, or 0 to leave open-ended.
     * @param executorService implementation of {@code ExecutorService} interface.
     * @param listener callback to return results to client.  Can be {@code null}.
     */
    DownloadJob(List<String> urls, long timeout, ExecutorService executorService,
                URLDownloader.OnDownloadsCompleteListener listener) {
        this.urls = urls;
        this.timeout = timeout * 1000;  // Convert from seconds to milliseconds
        this.listener = listener;
        this.executorService = executorService;

        completionService = new ExecutorCompletionService<>(executorService);
        downloadMap = new HashMap<>();
    }

    // Start actual processing.
    void start() {
        // Use Futures to allow easy management.
        for (String url : urls) {
            Future<String> download = completionService.submit(new HTTPDownload(url));

            downloadMap.put(download, url);
        }

        executorService.execute(new JobManager());
    }

    // Class to monitor downloads, fill in completion results, and handle
    // the results callback.
    private class JobManager implements Runnable {
        @Override
        public void run() {
            Map<String, String> resultMap = new HashMap<>();

            try {
                processDownloads(resultMap);
            } catch (TimeoutException ex) {
                stopRemainingDownloads(resultMap, URLDownloader.RESULT_FAILED);
            } catch (InterruptedException ex) {
                // Some internals make it easier to use InterruptedException
                // but this really indicates the job was cancelled.
                stopRemainingDownloads(resultMap, URLDownloader.RESULT_CANCELLED);
            }

            if (null != listener) {
                listener.onComplete(resultMap);
            }

            // If the thread pool isn't explicitly shutdown, the process can
            // hang around for an extended time.
            executorService.shutdown();
        }

        // Monitor download completion, cancellation, or timeout.
        // Cancellation can show as an interrupt or a cancellation exception.
        // Beware to handle both.
        // Calling method is responsible for cleaning up and setting results
        // after either a timeout or a cancellation.
        private void processDownloads(Map<String, String> resultMap) throws TimeoutException, InterruptedException {
            long finishByTime = (0 == timeout ? Long.MAX_VALUE : System.currentTimeMillis() + timeout);

            // Loop by count to make sure we catch all downloads.
            for (int nn = urls.size(); nn > 0; --nn) {
                long wait = (0 == timeout ? Long.MAX_VALUE : finishByTime - System.currentTimeMillis());

                // Check if time is up
                if (0 > wait) {
                    throw(new TimeoutException());
                }

                // Wait for the next download to complete, or for a timeout to occur.
                // Return value of null indicates timeout.  An InterruptedException
                // indicates the future was cancelled (sorry future) by job.cancel call.
                // Allow exception the percolate up.
                Future<String> future = completionService.poll(wait, TimeUnit.MILLISECONDS);

                // If timed out, bail out.  Caller is responsible for cleanup.
                if (null == future) {
                    throw(new TimeoutException());
                }

                String url = downloadMap.get(future);

                // Alternate way to discover job has been cancelled.  Make it
                // act the same to the calling method.
                if (future.isCancelled()) {
                    throw(new InterruptedException());
                }

                try {
                    resultMap.put(url, future.get());
                } catch (CancellationException ex) {
                    // There's a very tiny window where the client
                    // could cancel and cause an exception here.
                    // Propagate so this acts the same as an interrupt.
                    throw(new InterruptedException());
                } catch (ExecutionException ex) {
                    ex.printStackTrace();

                    throw(new RuntimeException());
                }
            }
        }

        // Used after main processing to cleanup any outstanding downloads and
        // fill in any missing completion results.
        private void stopRemainingDownloads(Map<String, String> resultMap, String result) {
            for (Future<String> download : downloadMap.keySet()) {
                if (!download.isDone()) {
                    download.cancel(true);
                }

                String url = downloadMap.get(download);

                resultMap.putIfAbsent(url, result);
            }
        }
    }

    private class HTTPDownload implements Callable<String> {
        private final String url;
        private HttpURLConnection connection = null;
        private File tempFile = null;

        HTTPDownload(String url) {
            this.url = url;
        }

        @Override
        public String call() throws IOException {
            // The code relies on thread interrupts to cancel operations.
            // Requires checking interrupt status manually
            if (Thread.interrupted()) {
                return(URLDownloader.RESULT_CANCELLED);
            }

            // Stream page contents to a temporary file.  On completion, rename the
            // file according to spec.
            // Using a temp file creates an issue.  For efficiency and simplicity in guaranteeing
            // parallel downloads of the same site don't collide, it's easier to rename the temp
            // file.  This potentially leaves files behind if the program crashes.
            // Alternatively, the temp file could be marked for automatic deletion, but this would
            // require copying the contents to the final file.

            try {
                tempFile = File.createTempFile("tdl", ".tmp", new File("."));
            } catch (IOException ex) {
                System.err.println("Failed creating output file");
                cleanup();

                throw (new RuntimeException());
            }

            // Create an HttpUrlConnection to handle the download and connect.
            // Note several methods of HttpUrlConnection are synchronized!
            try {
                connection = Utils.createHttpConnection(url);

                // Simple approach to aid in honoring the requested timeout.
                // Timeout is elsewhere as well by looking for thread interrupts.
                // The network timeouts only take ints, not longs.  Treat a timeout
                // larger than the max int value as infinite.
                int connectionTimeout = (timeout > Integer.MAX_VALUE ? 0 : (int)timeout);

                connection.setConnectTimeout(connectionTimeout);
                connection.setReadTimeout(connectionTimeout);

                // The connection happens automatically upon trying to read a response.
                // Connect explicitly for clarity.
                connection.connect();
            } catch (IOException ex) {
                cleanup();

                return(URLDownloader.RESULT_FAILED);
            }

            // Stream site to temp file
            try {
                Utils.interruptibleWriteStreamToFile(connection.getInputStream(), tempFile);
            } catch (InterruptedException ex) {
                // Operation cancelled by thread interrupt
                cleanup();

                return (URLDownloader.RESULT_CANCELLED);
            } catch (IOException ex) {
                cleanup();

                return (URLDownloader.RESULT_FAILED);
            }

            connection.disconnect();

            // Check the response code _after_ reading the stream.
            // Otherwise processing blocks and unpleasant exceptions can
            // get thrown.
            int responseCode = connection.getResponseCode();

            if (HttpURLConnection.HTTP_OK != responseCode) {
                cleanup();

                return (URLDownloader.RESULT_FAILED);
            }

            String outputFileName = Utils.sha1AsHexString(url);
            File outputFile = new File(outputFileName);

            Utils.syncedFileForcedRename(tempFile, outputFile);

            return (outputFileName);
        }

        private void cleanup() {
            if (null != tempFile) {
                tempFile.delete();
            }

            if (null != connection) {
                connection.disconnect();
            }
        }
    }

    // Cancel a running job.
    @Override
    public void cancel() {
        // Rely on invoking cancel method on futures still in progress.
        // Note calling cancel on a future causes the thread's interrupt status
        // to be set.
        for (Future<String> download : downloadMap.keySet()) {
            if (!download.isDone()) {
                download.cancel(true);
            }
        }
    }
}
