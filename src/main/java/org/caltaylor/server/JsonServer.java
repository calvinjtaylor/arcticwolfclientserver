package org.caltaylor.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.json.JSONException;
public class JsonServer {

    private static final Logger log = LoggerFactory.getLogger(JsonServer.class);
    private static HttpServer server;
    private static ExecutorService executorService;
    private static final String configFileName = "arcticwolfscannerserver.properties";
    private static String outputPath = "";
    private static final int numberOfThreads = 10; //arbitrary, but 1 is sufficient at this point.
    private static final String contextPath = "/json";

    public static void main(String[] args) throws IOException {
        if(args == null){
            throw new NullPointerException("Parameter to JsonServer must be a directory that can be created.");
        }
        String configFilePath = args[0];
        log.info("Looking in configFilePath="+configFilePath + " for config file.");

        int port = 8080;
        String configPath;
        if(configFilePath == null){
            throw new NullPointerException("Parameter to JsonServer must be a directory that can be created.");
        }
        if(configFilePath.isEmpty()){
            throw new IllegalArgumentException("Parameter to JsonServer must be a directory that can be created.");
        }
        configPath = configFilePath + File.separator + configFileName;
        File configFile = new File(configPath);
        if (!configFile.exists()){
            throw new IllegalArgumentException("Parameter to JsonServer must be a directory that can be created. Could not find "+ configPath);
        } else {
            Properties prop = new Properties();
            try {
                log.info("Loading properties from config file: "+configFile.getPath());
                FileInputStream input = new FileInputStream(configFile.getPath());
                prop.load(input);

                //get the property values and override default
                port = Integer.parseInt(prop.getProperty("port"));
                String tempOutputPath = prop.getProperty("outputPath");
                if (tempOutputPath != null && !tempOutputPath.isEmpty())
                    outputPath = tempOutputPath;
            }
            catch (IOException ioe) {
                log.error("Error reading properties from config file "+ configFile.getAbsolutePath(), ioe);
                throw new IllegalArgumentException("Parameter to JsonServer must be a directory that can be created. Error reading properties from config file "+ configPath +" message");
            }
        }

        log.debug("outputPath = "+outputPath);

        if (!outputPath.isEmpty()) {
            File outputPathDir = new File(outputPath);
            if (!outputPathDir.exists()) {
                if (!outputPathDir.mkdirs()) {
                    log.error("Properties file "+ configPath +" referenced an output path that generated an error when creating: outputPath="+outputPath);
                    throw new IllegalArgumentException("Properties file "+ configPath +" referenced an output path that generated an error when creating: outputPath="+outputPath);
                }
            }
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(contextPath, new JsonHandler());
        executorService = Executors.newFixedThreadPool(numberOfThreads);
        server.setExecutor(executorService);
        server.start();

        log.info("Server is running on port "+ port + ", writing uploaded files to " + outputPath);
    }

    public static void stopServer(){
        if(server != null) {
            server.stop(0);
            executorService.shutdown(); // Disable new tasks from being submitted
            try {
                // Wait for existing tasks to terminate
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow(); // tired of waiting, make it so!
                    // Wait a while for tasks to respond to being cancelled
                    if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                        log.error("JsonServer thread pool did not terminate");
                }
                log.info("Server stopped");
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                executorService.shutdownNow();
                // Preserve interrupt status
                log.warn("Server stopped, throwing interrupted exception");
                Thread.currentThread().interrupt();
            }
            server = null;
        } else {
            log.info("Server wasn't running");
        }
    }

    static class JsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            InputStream requestBody = exchange.getRequestBody();

            // Read the JSON data from the input stream
            StringBuilder requestStringBuilder = new StringBuilder();
            int nextByte;
            while ((nextByte = requestBody.read()) != -1) {
                requestStringBuilder.append((char) nextByte);
            }

            // Convert the JSON data to a String
            String jsonRequest = requestStringBuilder.toString();
            log.info("Received JSON request: " + jsonRequest);

            try {
                JSONObject jsonObject = new JSONObject(requestStringBuilder.toString());
                processJson(jsonObject);
            }catch (JSONException err){
                log.error("Error building JSON from string '"+requestStringBuilder.toString()+"'");
            }

            // Send a response back to the client
            String response = "JSON received successfully";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(response.getBytes());
            responseBody.close();
            log.debug("Sent JSON successfully received message.");
        }

        void processJson(JSONObject jsonObject) throws IOException {
            log.debug("Processing JSON: "+ jsonObject);
            String sourceFile = jsonObject.getString("sourceFile");

            File sourceProperties = new File(outputPath + File.separator + sourceFile);
            File outputPathFile = new File(outputPath);
            if (!outputPathFile.exists() && !outputPathFile.mkdirs()){
                log.error("Could not create output directory: "+ outputPath);
            }
            if (outputPath.isEmpty()){
                sourceProperties = new File(sourceFile);
            }
            PrintWriter writer = new PrintWriter(sourceProperties.getPath(), StandardCharsets.UTF_8);
            Iterator<String> keys = jsonObject.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                if (!key.equals("sourceFile")) { //skip sourceFile key as it's used to set the name of the output file.
                    writer.println("key = " + jsonObject.getString(key));
                }
            }

            writer.close();
            log.debug("Processing JSON complete.  Wrote :"+ sourceProperties.getPath());
        }
    }
}