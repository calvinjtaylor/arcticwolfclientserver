package org.caltaylor.server;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class JsonServerTest {
    private static final String CONFIG_FILE_NAME = "arcticwolfscannerserver.properties";
    private static final String TEST_PORT = "1337";
    private static final String SERVER_OUTPUT_DIRECTORY = "build/serverOutput";
    @BeforeEach
    public void beforeEach() throws Exception {
        JsonServer.stopServer();
    }
    @AfterEach
    public void afterEach() throws Exception {
        File tempConfigFile = new File(CONFIG_FILE_NAME);
        if (tempConfigFile.exists() && !tempConfigFile.delete()){
            System.err.println("Couldn't delete temp config file");
        }
        JsonServer.stopServer();
        Thread.sleep(5000);
    }
    @AfterAll
    public static void afterAll() throws Exception {
        JsonServer.stopServer();
        Thread.sleep(5000);
    }

    @Test
    public void testWithNullConfigDirParam() throws Exception {

        Exception exception = assertThrows(NullPointerException.class, () -> {
            JsonServer.main(null); // Provide a config file path for testing
            common();
        });

        String expectedMessage = "Parameter to JsonServer must be a directory that can be created.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }
    @Test
    public void testWithEmptyConfigDirParam() throws Exception {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            String configDir = "";  //tests current dir
            generateServerConfigFileInDir(configDir, TEST_PORT, SERVER_OUTPUT_DIRECTORY);
            JsonServer.main(new String[]{configDir});
            common();
        });

        String expectedMessage = "Parameter to JsonServer must be a directory that can be created.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    public void testWithDotConfigDirParam() throws Exception {
        String configDir = ".";  //tests current dir
        generateServerConfigFileInDir(configDir, TEST_PORT, SERVER_OUTPUT_DIRECTORY);
        JsonServer.main(new String[]{configDir});
        common();
     }

    @Test
    public void testWithDefinedConfigDir() throws Exception {
        String configDir = "build";
        generateServerConfigFileInDir(configDir, TEST_PORT, SERVER_OUTPUT_DIRECTORY);
        JsonServer.main(new String[]{configDir}); // Provide a config file path for testing
        common();
    }

    void common() throws IOException {
        HttpExchange exchange = Mockito.mock(HttpExchange.class);

        // Prepare input and output streams
        InputStream inputStream = new ByteArrayInputStream("{\"sourceFile\": \"generated-defined-in-JsonServerTest.properties\", \"key1\": \"important data\",\"key2\": \"ipsum lorum\"}".getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Stub methods of HttpExchange
        when(exchange.getRequestBody()).thenReturn(inputStream);
        when(exchange.getResponseBody()).thenReturn(outputStream);

        // Create JsonServer instance and call handle method
        JsonServer.JsonHandler handler = new JsonServer.JsonHandler();
        handler.handle(exchange);

        // Verify the response sent back
        String response = outputStream.toString();
        assert(response.equals("JSON received successfully"));

    }

    public static void generateServerConfigFileInDir(String dir, String port, String outputPath) throws IOException {

        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }

        File generatedServerConfigFile = new File(CONFIG_FILE_NAME);
        if (!dir.isEmpty()){
            generatedServerConfigFile = new File(dirFile.getPath() + File.separator + CONFIG_FILE_NAME);
        }

        PrintWriter writer = new PrintWriter(generatedServerConfigFile.getPath(), StandardCharsets.UTF_8);

        writer.println("port "+port);
        writer.println("outputPath "+outputPath);

        writer.close();
        System.out.println("Wrote server config at " + generatedServerConfigFile.getPath());
    }
}