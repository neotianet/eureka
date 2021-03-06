/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.client.resolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author Tomasz Bak
 */
public class FileServerResolverTest extends AbstractResolverTest {

    private File configurationFile;
    private FileServerResolver resolver;
    private TestScheduler testScheduler;

    @Before
    public void setUp() throws Exception {
        configurationFile = File.createTempFile("eureka-resolver-test", ".conf");
        updateFile("serverA;port=555", "serverB");

        // We need to force reload, as file last update time resolution is 1sec. Too long to wait.
        testScheduler = Schedulers.test();
        resolver = new FileServerResolver(configurationFile)
                .configureReload(true, 10, 100, TimeUnit.MILLISECONDS)
                .configureReloadScheduler(testScheduler);
    }

    @After
    public void tearDown() throws Exception {
        resolver.close();
        if (configurationFile != null && configurationFile.exists()) {
            configurationFile.delete();
        }
    }

    @Test(timeout = 30000)
    public void testReadingServersFromFile() throws Exception {
        Set<Server> expected = asSet(new Server("serverA", 555),
                new Server("serverB", 0));
        // take 1 extra, should loop back and be de-duped
        Set<Server> actual = asSet(takeNext(resolver), takeNext(resolver), takeNext(resolver));

        assertThat(actual, is(equalTo(expected)));

        // Now update the file, and change one server address
        updateFile("serverA", "serverC");
        testScheduler.advanceTimeBy(10, TimeUnit.MILLISECONDS);

        expected = asSet(new Server("serverA", 0), new Server("serverC", 0));
        // take 1 extra, should loop back and be de-duped
        actual = asSet(takeNext(resolver), takeNext(resolver), takeNext(resolver));

        assertThat(actual, is(equalTo(expected)));
    }

    private void updateFile(String... servers) throws IOException {
        configurationFile.delete();
        try (FileWriter writer = new FileWriter(configurationFile)) {
            for (String server : servers) {
                writer.write(server);
                writer.write('\n');
            }
        }
    }
}