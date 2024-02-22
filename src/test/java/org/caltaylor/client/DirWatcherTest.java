package org.caltaylor.client;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.caltaylor.server.JsonServer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;


/** DirWatcherTest tests the DirWatcher class.
 * DirWatcher is started with a parameter which defines
 * where to look for its configuration file.
 * The config dir defines
 * what directory it will be monitoring,
 * a regex filter pattern
 * server url including port
 * This test also generates the files that will be presented
 * to the DirWatcher to ensure it correctly handles the files presented.
 * */
@WireMockTest(/*httpsEnabled = true, */httpPort = 1337)
public class DirWatcherTest {
    private static final String tmpDir = "/tmp/dirwatcher";

    public DirWatcherTest(){
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        //we can't mock the server if the actual one has the address in use.
        JsonServer.stopServer();
//        Thread.sleep(5000);
    }

//    @AfterAll
//    public static void afterAll() throws Exception {
//        Thread.sleep(5000);
//    }

    @Test
    public void testWiremock(WireMockRuntimeInfo wmRuntimeInfo){
        try {
            stubFor(post("/json").willReturn(ok()));
            int port = wmRuntimeInfo.getHttpPort();
            System.out.println("testWiremock Wiremock running on port "+port);
            String json = "{some=test}";
            URL url = new URI("http://localhost:"+port+"/json").toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Write JSON payload to request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                System.out.println("testWiremock Wrote json " + json);
            }
            // Send the request and read the response
            int responseCode = connection.getResponseCode();
            System.out.println("testWiremock Response Code: " + responseCode);
            System.out.println("testWiremock Response Code: " + responseCode);
        }catch(Exception e){
            System.err.println("testWiremock Failed to complete test successfully: "+e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }
    @Test
    public void testDirWatcherThrowsExceptionWithNoArgs(){
        System.out.println("testDirWatcherThrowsExceptionWithNoArgs started");

        assertThrows(java.lang.NullPointerException.class, () -> {
            launchDirWatcher(null);
        });
    }
    @Test
    public void testDirWatcherThrowsExceptionWithEmptyArgs(){
        System.out.println("testDirWatcherThrowsExceptionWithEmptyArgs started");

        assertThrows(java.lang.IllegalArgumentException.class, () -> {
            launchDirWatcher("");
        });
    }

    @Test
    public void testDirWatcherThrowsExceptionWithMissingDirArgs(){
        System.out.println("testDirWatcherThrowsExceptionWithMissingDirArgs started");

        assertThrows(java.lang.IllegalArgumentException.class, () -> {
            launchDirWatcher("randoMissingDirectory");
        });
    }

    @Test
    public void testDirWatcherThrowsExceptionWithMissingConfigFileArgs(){
        System.out.println("testDirWatcherThrowsExceptionWithMissingConfigFileArgs started");

        File testDirFile = new File (tmpDir + "/testDirWatcherThrowsExceptionWithMissingConfigFileArgs" );
        testDirFile.mkdirs();
        assertThrows(java.lang.IllegalArgumentException.class, () -> {
            launchDirWatcher("randoMissingDirectory");
        });
    }

    @Test
    public void testDirWatcher() throws IOException {
        stubFor(post("/json").willReturn(ok()));
        System.out.println("testDirWatcher started");
        //start dir watcher, might need to background this process
        String configDir = tmpDir+"/testDirWatcher";
        generateDirWatcherConfigFileInDir(configDir); //consider dynamically creating config file
        launchDirWatcher(configDir);

        String dir2Monitor = "/tmp/watch1";
        List<File> createdFiles = new ArrayList<>();
        //create some files in test dir, verify they get processed properly.
        for (int i=0;i<1;i++){
            File testFile = createTestFile(dir2Monitor,"testPrefix", "properties");
            createdFiles.add(testFile);;
        }

        //wait a few moments for processing to occur and remove the created files.
        System.out.println("testDirWatcher Waiting for processing inside DirWatcher to send map and delete file.");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (File createdFile: createdFiles){
            System.out.println("testDirWatcher Testing created file has been removed: "+ createdFile.getAbsolutePath());
            assertFalse(createdFile.exists(), "Created file should not exist anymore: "+createdFile.getName());
        }
        System.out.println("testDirWatcher complete");
    }

    @Test
    public void testDirWatcherThreadSafety() throws InterruptedException {
        // Create multiple threads each running a DirWatcher instance
        int numThreads = 5;
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                DirWatcher dirWatcher = new DirWatcher(getTestProperties());
                dirWatcher.run();
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for a while to let the watchers start
        Thread.sleep(1000); // Adjust this delay as necessary

        // Interrupt all threads to stop the watchers
        for (Thread thread : threads) {
            thread.interrupt();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // If the test reaches this point without exceptions, it means the class is thread-safe
        assertTrue(true);

        //allow for gracefully closing of threads.
        Thread.sleep(1000);
    }

    public static Properties generateDirWatcherConfigFileInDir(String configDir) throws IOException {
        //find the file in src/test/resources
        String configFileName = DirWatcher.getConfigFileName();
        InputStream inputStream = DirWatcherTest.class.getClassLoader().getResourceAsStream(configFileName);
        if (inputStream == null) {
            throw new IOException("Config file not found in resources directory: " + configFileName);
        }

        // create client config dir if needed
        File configDirFile = new File (configDir);
        if (!configDirFile.exists()&&!configDirFile.mkdirs()){
            throw new RuntimeException("Could not create client config directory "+ configDirFile.getPath());
        }
        // Create Path object for the destination directory
        Path destinationPath = Path.of(configDir);

        // Move the file to the destination directory
        Path targetPath = destinationPath.resolve(configFileName);
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

        //test we can load properties from the new file
        Properties props = getProperties(targetPath.toFile().getPath());
        int keyCount = 0;
        for (Object key: props.keySet()){
            Object val = props.get(key);
            System.out.println("generateDirWatcherConfigFileInDir key="+key.toString()+" val="+val.toString());
            keyCount++;
        }
        if (keyCount == 0){
            throw new NullPointerException("Could not load properties from file "+targetPath.toFile().getPath());
        }

        System.out.println("generateDirWatcherConfigFileInDir DirWatcher client config file moved to "+targetPath.toFile().getPath()+" successfully. keyCount="+keyCount);
        return props;
    }

    public static void launchDirWatcher(String configDir){
        DirWatcher.main(new String[]{configDir});
        try {
            Thread.sleep(1000);  // give some time for the server to boot up and start watching dir
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Want to build file in temp location then push into monitored dir,
     * so that the file isn't processed before we are done constructing it. */
    public static File createTestFile(String watchDirName, String filePrefix, String extension) {
        try {
            File tmpDirFile = new File(tmpDir);
            tmpDirFile.mkdirs();
            double random = Math.random();
            String fileName = filePrefix + "-" + random + "." + extension;
            PrintWriter writer = new PrintWriter(tmpDir + File.separator + fileName, "UTF-8");
            writer.println("key1 = val1" + random);
            writer.println("key2 = val2" + random);
            writer.println("key3 = val3" + random);
            writer.println("notNeededKey1 = valNotNeeded1" + random);
            writer.println("notNeededKey2 = valNotNeeded2" + random);
            writer.println("notNeededKey3 = valNotNeeded3" + random);
            writer.println("anotherKey1 = valAnother1" + random);
            writer.println("anotherKey2 = valAnother2" + random);
            writer.println("anotherKey3 = valAnother3" + random);
            writer.println("anotherKey4 = valAnother4" + random);
            writer.close();

            File written = new File(tmpDir + File.separator + fileName);
            if (!written.exists()){
                System.err.println("createTestFile Written file doesn't exist: "+written.getAbsolutePath());
            }

            File watchDir = new File(watchDirName);
            if (!watchDir.exists()){
                watchDir.mkdirs();
            }

            File finalLocation = new File(watchDirName+ File.separator + fileName);
            if (!written.renameTo(finalLocation)){
                System.err.println("createTestFile Could not rename written file: "+written.getAbsolutePath());
            } else {
                System.out.println("createTestFile Renamed file to "+finalLocation.getAbsolutePath());
            }
            if (!finalLocation.exists()){
                System.err.println("createTestFile Written file doesn't exist: "+finalLocation.getAbsolutePath());
            }
            System.out.println ("createTestFile Created "+finalLocation.getAbsolutePath());
            return finalLocation;
        } catch (Exception e){
            System.err.println("createTestFile Error building testFile: "+ e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    // Mock method to provide test properties
    private Properties getTestProperties() {
        Properties props = new Properties();
        try {
            InputStream inputStream = DirWatcherTest.class.getClassLoader().getResourceAsStream(DirWatcher.getConfigFileName());
            props.load(inputStream);
            System.out.println ("getTestProperties Loaded properties from test resource config file.");
        }
        catch (IOException ioe) {
            throw new IllegalArgumentException("getTestProperties Could not load properties from resource test config file.");
        }
        return props;
    }

    private static Properties getProperties(String propertyFile) throws IOException {
        Properties props = new Properties();
        FileInputStream inputStream = new FileInputStream(propertyFile);
        props.load(inputStream);
        System.out.println ("getProperties Loaded properties from test resource config file.");
        return props;
    }
}
