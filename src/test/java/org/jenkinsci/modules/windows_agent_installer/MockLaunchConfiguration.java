/**
The MIT License

Copyright (c) 2017 CloudBees, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.jenkinsci.modules.windows_agent_installer;

import hudson.util.ArgumentListBuilder;
import org.jenkinsci.modules.slave_installer.LaunchConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.annotation.CheckForNull;

//TODO: consider moving the class to agent_installer module
/**
 * Mock Launch Configuration for testing purposes.
 * @author Oleg Nenashev
 */
/*package*/ class MockLaunchConfiguration extends LaunchConfiguration {
    
    private final URL jarUrl;
    private final URL jnlpUrl;
    private final File storage;
    private final File jarFile;
    private final String jnlpMac;

    MockLaunchConfiguration(URL jarUrl, URL jnlpUrl, File storage, File jarFile) {
        this(jarUrl, jnlpUrl, storage, jarFile, null);
    }
    
    MockLaunchConfiguration(URL jarUrl, URL jnlpUrl, File storage, File jarFile, @CheckForNull String jnlpMac) {
        this.jarUrl = jarUrl;
        this.jnlpUrl = jnlpUrl;
        this.storage = storage;
        this.jnlpMac = jnlpMac;
        this.jarFile = jarFile;
    }

    @Override
    public File getStorage() throws IOException {
        return storage;
    }

    @Override
    public File getJarFile() throws IOException {
        return jarFile;
    }

    @Override
    public URL getLatestJarURL() throws IOException {
        return jarUrl;
    }

    @Override
    public ArgumentListBuilder buildRunnerArguments() {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("-jnlpUrl").add(jnlpUrl);
        if (jnlpMac != null) {
            args.add("-secret").add(jnlpMac);
        }
        return args;
    }
}
