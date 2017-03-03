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

package org.jenkinsci.modules.windows_slave_installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import javax.annotation.CheckForNull;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.CoreMatchers.*;
import org.jenkinsci.modules.slave_installer.InstallationException;
import org.jenkinsci.modules.slave_installer.LaunchConfiguration;
import org.jenkinsci.modules.slave_installer.Prompter;
import org.junit.Assert;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

/**
 * Tests of {@link WindowsSlaveInstaller}.
 * @author Oleg Nenashev
 */
public class WindowsSlaveInstallerTest {
    
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    
    private LaunchConfiguration launchConfig;
    private Prompter prompter;
    
    @Before
    public void initAttributes() throws IOException {
        File storage = tmpDir.newFolder("agentDir");
        File remotingJar = tmpDir.newFile("remoting.notjar");
        
        launchConfig = new MockLaunchConfiguration(
                new URL("http://my.jenkins/jnlpJars/slave.jar"),
                new URL("http://my.jenkins/computer/myAgent/connect.jnlp"),
                storage, remotingJar
        );
        
        prompter = new Prompter() {
            @Override
            public String prompt(String question, String defaultValue) throws InterruptedException {
                return "unspecified";
            }

            @Override
            public String promptPassword(String question) throws InterruptedException {
                return "unspecified";
            }
        };
    }
    
    @Test
    @Issue("JENKINS-39237")
    public void shouldGenerateConfigWithValidDownloadLink() throws InstallationException, IOException, InterruptedException {
        
        WindowsSlaveInstaller installer = new WindowsSlaveInstaller();
        installer.install(launchConfig, prompter, true);
        
        final String xml;
        try (FileInputStream istream = new FileInputStream(new File(tmpDir.getRoot(), "agentDir/jenkins-slave.xml"))) {
            xml = IOUtils.toString(istream);
        }
        
        // Verify that the directory has been initialized correctly
        verifyAgentDirectory(new File(tmpDir.getRoot(), "agentDir"));
        
        // Check config.xml entries
        assertThat("There is unresolved macro", xml, not(containsString("@")));
        assertThat("The JAR download URL has not been resolved correctly", not(containsString("TODO:jarFile")));
        assertThat("The JAR download URL contains the invalid value", xml, containsString("<download from=\"" + launchConfig.getLatestJarURL() + "\""));
    }
    
    @Test
    @Issue("JENKINS-39237")
    public void shouldNotDownloadForNonHTTPS() throws Exception {
        assertDownloadSettingDisabled("http://myserver.com/remoting.jar");
        assertDownloadSettingDisabled("ftp://myserver.com/remoting.jar");
        assertDownloadSettingDisabled("file:/my/files/remoting.jar");
    }
    
    @Test
    @Issue("JENKINS-39237")
    public void shouldDownloadForHTTPS() throws Exception {
        String url = "https://myserver.com/remoting.jar";
        String macroValue = downloadMacroFor(url);
        assertXMLNotCommented(macroValue);
        assertThat("The download macro does not reference the link", macroValue, containsString(url));
    }
    
    @Test
    @Issue("JENKINS-39237")
    public void shouldNotFailForNull() throws Exception {
        String macroValue = downloadMacroFor(null);
        assertXMLCommented(macroValue);
        assertThat("The download macro does not contain TODO", macroValue, containsString("TODO"));
    }
    
    private static void assertDownloadSettingDisabled(String url) throws MalformedURLException {
        String macroValue = downloadMacroFor(url);
        assertXMLCommented(macroValue);
        assertThat("The download macro does not reference the link", macroValue, containsString(url));
    }
    
    private static String downloadMacroFor(@CheckForNull String url) throws MalformedURLException  {
        return WindowsSlaveInstaller.AgentURLMacroProvider.generateDownloadMacroValue
            (url != null ? new URL(url) : null);
    }
    
    private static void assertXMLCommented(String str) {
        assertThat("No starting comment definition", str, startsWith("<!--"));
        assertThat("No end comment definition", str, endsWith("-->"));
    }
    
    private static void assertXMLNotCommented(String str) {
        assertThat("Found the comment start definition", str, not(startsWith("<!--")));
        assertThat("Found the comment end definition", str, not(endsWith("-->")));
    }
    
    private static void verifyAgentDirectory(File dir) throws AssertionError {
        assertAgentFile(dir, "jenkins-slave.exe");
        assertAgentFile(dir, "jenkins-slave.exe.config");
        assertAgentFile(dir, "jenkins-slave.xml");
        assertAgentFile(dir, "slave.jar");
    }
    
    private static void assertAgentFile(File parent, String child) throws AssertionError {
        File file = new File(parent, child);
        if (!file.exists()) {
            Assert.fail("File does not exist: " + file);
        }
        if (!file.isFile()) {
            Assert.fail("File is not a normal file: " + file);
        }
    }
    
    // TODO: Add more tests
}
