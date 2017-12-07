package org.greeley;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TestURLDownloader implements URLDownloader.OnDownloadsCompleteListener {
    private static final List<String> quick = Arrays.asList(
            "http://www.greeley.org",
            "http://www.greeley.org/~hod/FAQ/Basics.html"
    );

    private static final List<String> testSites = Arrays.asList(
            "http://162.222.178.49:8085",       // Returns a 200 OK response
            "http://162.222.178.49:8085/503",   // Returns a 503 Error
            "http://162.222.178.49:8085/slow"   // Delays 15 seconds, returns a 200 OK response
    );

    private static final List<String> sampleSites = Arrays.asList(
            "www.couchbase.com", "www.google.com", "www.youtube.com", "www.yahoo.com",
            "www.msn.com", "www.wikipedia.org", "www.baidu.com", "www.microsoft.com",
            "www.qq.com", "www.bing.com", "www.ask.com", "www.adobe.com", "www.taobao.com",
            "www.youku.com", "www.soso.com", "www.wordpress.com", "www.sohu.com",
            "www.windows.com", "www.163.com", "www.tudou.com", "www.amazon.com"
    );

    public static void main(String[] args) {
        URLDownloader.OnDownloadsCompleteListener listener = new TestURLDownloader();
        URLDownloader testDL = new URLDownloader();
        URLDownloader sampleDL = new URLDownloader();
        URLDownloader.Job job;

        System.out.println("Starting tests...");

        // Load the test sites with a 5 second timeout.
        // The "slow" site should get timed in this case.
        testDL.downloadURLs(testSites, 5, listener);

        //Utils.pause(5);

        // Test job cancellation using the test sites.
        // Cancel after 5 seconds, before the "slow" site responds.
        job = testDL.downloadURLs(testSites, 0, listener);

        Utils.pause(5);
        job.cancel();

        // Load the test sites with no timeout.
        testDL.downloadURLs(testSites, 0, listener);

        // Load a bunch of real sites with a separate downloader
        sampleDL.downloadURLs(sampleSites, 0, listener);
    }

    @Override
    public void onComplete(Map<String, String> resultMap) {
        for (final String key : resultMap.keySet()) {
            System.out.println("Download of " + key + " produced " + resultMap.get(key));
        }
    }
}
