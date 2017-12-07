package org.greeley;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Asynchronous, multi-threaded download of a list of URLs to corresponding
 * individual files.
 *
 * <p>
 *     Downloads are initiated through a call to {@code downloadURLs}.
 * Client receives an object which implements the
 * {@code URLDownloader.Job} interface in return.  This object may be used
 * to cancel jobs in progress.
 *</p>
 *
 * <p>
 *     Upon completing a job (or upon cancellation), results of the job are
 * returned through a callback supplied by the client.
 *</p>
 *
 * <p>
 *     Successfully downloaded content is stored in individual files, using the SHA1
 * hash of the URL as the filename.  See {@code interface
 * URLDownloader.OnDowndloadCompleteListener} for details.
 *</p>
 */

public class URLDownloader {
    public static final String RESULT_FAILED = "failed";
    public static final String RESULT_CANCELLED = "cancelled";

    /**
     * Main method to initiate download processing.
     *
     * @param urls {@code List<String>} with individual URLs.
     * @param timeout time allotted for completion.  0 indicates no timeout.
     * @param listener object implementing the
     *                 {@code OnDownloadsCompleteListener}.
     * @return Object representing the download job, implementing the
     * {@code URLDownloader.Job} interface.
     */
    public Job downloadURLs(List<String> urls, long timeout, OnDownloadsCompleteListener listener) {
        Objects.requireNonNull(urls);

        ExecutorService executorService = Executors.newCachedThreadPool();
        DownloadJob job = new DownloadJob(urls, timeout, executorService, listener);

        job.start();

        return(job);
    }

    /**
     * Interface to control job processing.
     */
    interface Job {
        void cancel();
    }

    /**
     * Results callback interface, to be implemented by client.
     */
    interface OnDownloadsCompleteListener {
        void onComplete(Map<String, String> resultMap);
    }
}
