package org.caltaylor.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.WatchEvent.Kind;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DirWatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DirWatcher.class);
    private static final String configFileName = "arcticwolfscannerclient.properties";
    File dirToWatch;
    Pattern keyPattern;
    String scannerServerURL;

    public static void main(String[] args) {
        //do all error checking here for config file path
        String errMsg = "Expected path to config file as the argument to the main method.";
        if (args == null) {
            throw new NullPointerException(errMsg);
        }
        String configFilePath = args[0];
        if (configFilePath == null) {
            throw new NullPointerException(errMsg);
        }
        log.debug("initializing DirWatcher with args: " + configFilePath);

        DirWatcher dirWatcher = new DirWatcher(getConfigFileProperties(configFilePath, errMsg));
        Thread thread = new Thread(dirWatcher);
        thread.start();
    }

    public static String getConfigFileName() {
        return configFileName;
    }

    private static Properties getConfigFileProperties(String configFilePath, String errMsg) {

        if (configFilePath == null) {
            throw new NullPointerException(errMsg);
        }

        if (configFilePath.isEmpty()) {
            throw new IllegalArgumentException(errMsg);
        }
        File configDirFile = new File(configFilePath);
        if (!configDirFile.exists()) {
            throw new IllegalArgumentException("Configuration Directory " + configFilePath + ", does not exist.");
        }
        File configFile = new File(configFilePath + File.separator + configFileName);
        if (!configFile.exists()) {
            throw new IllegalArgumentException("Configuration file " + configFile.getPath() + ", does not exist.");
        }

        Properties prop = new Properties();
        try {
            FileInputStream input = new FileInputStream(configFile.getPath());
            prop.load(input);
            log.info("Loaded properties from " + configFile.getPath());
        } catch (IOException ioe) {
            throw new IllegalArgumentException("Could not load properties from file: " + configFile.getPath());
        }
        return prop;
    }

    public DirWatcher(Properties props) {

        dirToWatch = new File(getPropertyValue(props,"watchDirectory"));
        if (!dirToWatch.exists() && !dirToWatch.mkdirs()) {
            log.error("Could not create directory that we will watch: " + dirToWatch);
        }
        keyPattern = Pattern.compile(getPropertyValue(props,"watchDirectoryFilterPattern"));
        scannerServerURL = getPropertyValue(props,"scannerServerURL");

        log.info("Watching " + dirToWatch);
    }

    private String getPropertyValue(Properties props, String key){
        String val = props.getProperty(key);
        if (val == null){
            throw new IllegalArgumentException("Property not set for key: "+ key);
        }
        return val;
    }

    @Override
    public void run() {
        log.debug("run - dirToWatch=" + dirToWatch.getAbsolutePath());
        Path path = FileSystems.getDefault().getPath(dirToWatch.getAbsolutePath());
        FileSystem fs = path.getFileSystem();
        try (WatchService service = fs.newWatchService()) {
            path.register(service, ENTRY_CREATE);
            WatchKey key = null;

            while (true) {
                key = service.take();

                Kind<?> kind = null;
                for (WatchEvent<?> watchEvent : key.pollEvents()) {
                    // Get the type of the event
                    kind = watchEvent.kind();
                    if (OVERFLOW == kind) {
                        log.warn("DirWatcher overflow, we may have seen too many files created to handle, and will want to reread dir");
                    } else if (ENTRY_CREATE == kind) {
                        Object o = watchEvent.context();
                        if (o != null) {
                            log.info("Found new file: " + watchEvent.context());
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> we = (WatchEvent<Path>) watchEvent;
                            Path newPath = we.context();

                            processFile(newPath);
                        } else {
                            log.warn("null context");
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            log.info("Stopping current thread");
        } catch (Exception e) {
            log.error("Problem watching dir: " + dirToWatch.getAbsolutePath() + " " + e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * processFile takes a Path argument pointing to the newly detected file name,
     * reads the contents of the file into a map,
     * filters the keys,
     * forwards the filtered map to a server and
     * deletes the source file.
     */
    private void processFile(Path newPath) {
        File contextFile = newPath.toFile();
        log.debug("Processing  context " + contextFile.getAbsolutePath());
        //the context file is referenced to the current project and not the watched directory
        File file = new File(dirToWatch.getAbsolutePath() + File.separator + contextFile.getPath());
        log.debug("adjusted location " + file.getAbsolutePath());

        if (file.exists()) {
            log.debug("file exists: " + file.getAbsolutePath());
        } else {
            log.error("File not found: " + file.getAbsolutePath());
        }

        //read file into a map,
        Properties props = new Properties();
        try {
            props.load(new FileInputStream(file.getAbsolutePath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //filter keys
        Map<String, String> filteredMap = new HashMap<>();
        filteredMap.put("sourceFile", file.getName());

        Enumeration<?> keys = props.propertyNames();

        // Iterate over the keys using Iterator<String>
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement(); // Cast to String

            if (key.matches(keyPattern.pattern())) {
                log.debug("key " + key + " matched pattern " + keyPattern.pattern());
                filteredMap.put(key, props.getProperty(key));
            } else {
                log.debug("key " + key + " did not match pattern " + keyPattern.pattern());
            }
        }

        //send filtered map to server
        sendMapToServer(filteredMap);

        if (!file.delete()) {
            log.error("Couldn't delete: " + file.getAbsolutePath());
        } else {
            log.info("Deleted: " + file.getAbsolutePath());
        }
    }

    /** Sends the Map of parameters to the server defined via json text.
     * This option isn't the simplest but is language agnostic and easy to expand as needs arise.
     * For this case I could have easily used form data key/values or serialized objects.
     * */
    private void sendMapToServer(Map<String, String> filteredMap) {
        log.debug("Sending map to server");
        // Serialize map to JSON
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(filteredMap);

            log.debug("opening connection to "+ scannerServerURL);

            // Open connection
            URL url = new URI(scannerServerURL).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Write JSON payload to request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                log.debug("sent json "+ json);
            }

            // Send the request and read the response
            int responseCode = connection.getResponseCode();
            log.debug("Response Code: " + responseCode);

            // not going overboard with response code handling, but this is where it would go

            // Close connection
            connection.disconnect();

        } catch (JsonProcessingException e){
            log.error("Error encoding json from map: "+e.getLocalizedMessage(),e);
            throw new RuntimeException(e);
        } catch (IOException e){
            log.error("Error sending http request: "+e.getLocalizedMessage(),e);
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            log.error("Error building url: "+e.getLocalizedMessage(),e);
            throw new RuntimeException(e);
        }
        log.debug("data sent");
    }
}
