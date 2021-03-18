package com.defold.extender;

import com.defold.extender.client.*;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class AuthenticationTest {

    private static final int EXTENDER_PORT = 9000;
    private static final String SDK_VERSION = "fe2b689302e79b7cf8c0bc7d934f23587b268c8a";
    private static final String PLATFORM_ARMV7_ANDROID = "armv7-android";
    private static final String PLATFORM_LINUX = "x86_64-linux";
    private static final String PLATFORM_WIN32 = "x86_64-win32";

    private long startTime;

    private static final String DM_PACKAGES_URL = System.getenv("DM_PACKAGES_URL");
    private static final String AUTHENTICATION_PLATFORMS = "linux,android";
    private static final String AUTHENTICATION_USERS = "file:test-data/testusers.txt"

    static {
        LoggingSystem.get(ClassLoader.getSystemClassLoader()).setLogLevel(Logger.ROOT_LOGGER_NAME, LogLevel.INFO);
    }

    @Rule
    public TestName name = new TestName();

    public AuthenticationTest() {}

    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        ProcessExecutor processExecutor = new ProcessExecutor();
        processExecutor.putEnv("DM_PACKAGES_URL", AuthenticationTest.DM_PACKAGES_URL);
        processExecutor.putEnv("extender.authentication.platforms", AuthenticationTest.AUTHENTICATION_PLATFORMS);
        processExecutor.putEnv("extender.authentication.users", AuthenticationTest.AUTHENTICATION_USERS);
        processExecutor.execute("scripts/start-test-server.sh");
        System.out.println(processExecutor.getOutput());

        long startTime = System.currentTimeMillis();

        // Wait for server to start in container.
        File cacheDir = new File("build");
        ExtenderClient extenderClient = new ExtenderClient("http://localhost:" + EXTENDER_PORT, cacheDir);

        int count = 100;
        for (int i  = 0; i < count; i++) {
            try {
                if (extenderClient.health()) {
                    System.out.println(String.format("Server started after %f seconds!", (System.currentTimeMillis() - startTime) / 1000.f));
                    break;
                }
            } catch (IOException e) {
                if (i == count-1) {
                    e.printStackTrace();
                }
            }
            System.out.println("Waiting for server to start...");
            Thread.sleep(2000);
        }
    }

    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        ProcessExecutor processExecutor = new ProcessExecutor();
        processExecutor.execute("scripts/stop-test-server.sh");
        System.out.println(processExecutor.getOutput());
    }

    @Before
    public void beforeTest() throws IOException {
        startTime = System.currentTimeMillis();
    }

    @After
    public void afterTest()
    {
        File buildDir = new File("build" + File.separator + SDK_VERSION);
        if (buildDir.exists()) {
            try {
                FileUtils.deleteDirectory(buildDir);
            } catch (IOException e) {
            }
        }

        System.out.println(String.format("Test %s took: %.2f seconds", name.getMethodName(), (System.currentTimeMillis() - startTime) / 1000.f));
    }

    private File doBuild(List<ExtenderResource> sourceFiles, String user, String password, File destination, File log, String platform) throws IOException, ExtenderClientException {
        File cachedBuild = new File(String.format("build/%s/build.zip", platform));
        if (cachedBuild.exists())
            cachedBuild.delete();
        assertFalse(cachedBuild.exists());

        File cacheDir = new File("build");
        String url;
        if (user != null) {
            url = String.format("http://%s:%s@localhost:%d", user, password, EXTENDER_PORT);
        }
        else {
            url = String.format("http://localhost:%d", EXTENDER_PORT);
        }
        ExtenderClient extenderClient = new ExtenderClient(url, cacheDir);
        try {
            extenderClient.build(
                    platform,
                    SDK_VERSION,
                    sourceFiles,
                    destination,
                    log
            );
        } catch (ExtenderClientException e) {
            System.out.println("ERROR LOG:");
            System.out.println(new String(Files.readAllBytes(log.toPath())));
            throw e;
        }
    }

    @Test
    public void buildWithBasicAuth() throws IOException, ExtenderClientException {

        List<ExtenderResource> sourceFiles = Lists.newArrayList(
                new FileExtenderResource("test-data/AndroidManifest.xml", "AndroidManifest.xml"),
                new FileExtenderResource("test-data/ext_basic/ext.manifest"),
                new FileExtenderResource("test-data/ext_basic/src/test_ext.cpp")
        );

        File destination = Files.createTempFile("dmengine", ".zip").toFile();
        File log = Files.createTempFile("dmengine", ".log").toFile();
        doBuild(sourceFiles, "bobuser", "bobpassword", destination, log, PLATFORM_ARMV7_ANDROID);
        assertTrue("Resulting engine should be of a size greater than zero.", destination.length() > 0);
        assertEquals("Log should be of size zero if successful.", 0, log.length());
    }

}
