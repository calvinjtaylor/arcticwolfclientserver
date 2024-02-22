package org.caltaylor;

import org.caltaylor.client.DirWatcher;
import org.caltaylor.client.DirWatcherTest;
import org.caltaylor.server.JsonServer;
import org.caltaylor.server.JsonServerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FunctionalTest {
    private static final String CONFIG_DIR = "/tmp/aw-config";
    @BeforeEach
    public void beforeEach() throws Exception {
        JsonServer.stopServer();
//        Thread.sleep(500);
    }

    /**
     * 1. start server watching
     * 2. start dirwatcher client
     * 3. create test files in watched dir
     * 4. verify files get created in server output dir.
    */
    @Test
    public void testEverythingTogether() throws IOException, InterruptedException {
        //1. start server watching
        String serverConfigDir = CONFIG_DIR+"/serverConfig";
        String serverOutputDir = "build/functionalTest";
        cleanDir(serverOutputDir);  //clean the output dir first

        JsonServerTest.generateServerConfigFileInDir(serverConfigDir, "1337", serverOutputDir);
        System.out.println("testEverythingTogether starting server");
        JsonServer.stopServer();
        Thread jsonServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonServer.main(new String[]{serverConfigDir});
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        jsonServerThread.start();

        //2. start dir watcher, might need to background this process
        String clientConfigDir = CONFIG_DIR+"/clientConfig";
        Properties props = DirWatcherTest.generateDirWatcherConfigFileInDir(clientConfigDir); //consider dynamically creating config file
        String watchDirectory = props.getProperty("watchDirectory");
        System.out.println("testEverythingTogether Starting DirWatcher client.");
//        DirWatcher.main(new String[]{clientConfigDir});
        Thread dirWatcherThread = new Thread(() -> DirWatcher.main(new String[]{clientConfigDir}));
        dirWatcherThread.start();

        try {
            Thread.sleep(1000);  // give some time for the server to boot up and start watching dir
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        //3. create some files in a test dir, then move them to the configured DirWatcher watch directory
        System.out.println("testEverythingTogether Creating test files.");
        List<File> createdFiles = new ArrayList<>();
        for (int i=0;i<1;i++){
            File testFile = DirWatcherTest.createTestFile(watchDirectory,"testPrefix", "properties");
            if (testFile == null) {
                System.err.println("testEverythingTogether Failed to create file.");
                break;
            }
            createdFiles.add(testFile);
            System.out.println("testEverythingTogether created testfile: "+testFile.getPath());
        }

        //wait a few moments for processing to occur and remove the created files.
        System.out.println("testEverythingTogether Waiting for processing inside DirWatcher to send map and delete file.");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        //4. verify files were deleted by the DirWatchClient
        for (File createdFile: createdFiles){
            System.out.println("testEverythingTogether Testing created file has been removed: "+ createdFile.getAbsolutePath());
            assertFalse(createdFile.exists(), "Created file should not exist anymore: "+createdFile.getName());
        }

        //4. verify files were written to the server's configured output dir.
        for (File createdFile: createdFiles){
            File createdServerFile = new File("build/functionalTest/"+createdFile.getName());
            System.out.println("testEverythingTogether Testing server wrote copy of original file to configured output dir: "+ createdServerFile.getPath());
            assertTrue(createdServerFile.exists(), "File should have been created by the server: "+createdServerFile.getPath());
        }
        // Wait for both threads to finish
        jsonServerThread.join();
        dirWatcherThread.join();
        JsonServer.stopServer();
        System.out.println("testEverythingTogether complete");
    }

    private void cleanDir(String directoryPath) {
        File directory = new File(directoryPath);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        boolean deleted = file.delete();
                        if (deleted) {
                            System.out.println("cleanDir deleted file: " + file.getName());
                        } else {
                            System.out.println("cleanDir failed to delete file: " + file.getName());
                        }
                    }
                }
            }
        } else {
            System.out.println("cleanDir directory does not exist or is not a directory; "+directoryPath);
        }
    }
}