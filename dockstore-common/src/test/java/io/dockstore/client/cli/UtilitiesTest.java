package io.dockstore.client.cli;

import com.google.common.io.ByteStreams;
import io.dockstore.common.Utilities;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class UtilitiesTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Test
    public void testEnvironmentParam() throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final Map<String, String> map = new HashMap<>();
        map.put("foo", "goo");

        // Ensure foo gets substituted with goo
        Utilities.executeCommand("echo ${foo}", os, ByteStreams.nullOutputStream(), new File("."), map);
        Assert.assertEquals("goo\n", os.toString());

        // Make sure that a non-existent variable works
        os.reset();
        Utilities.executeCommand("echo ${foo}", os, ByteStreams.nullOutputStream(), new File("."), null);
        Assert.assertEquals("${foo}\n", os.toString());
    }
}
