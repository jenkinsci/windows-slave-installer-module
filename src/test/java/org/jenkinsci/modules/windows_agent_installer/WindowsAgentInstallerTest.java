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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import org.jenkinsci.modules.slave_installer.InstallationException;
import org.jenkinsci.modules.slave_installer.LaunchConfiguration;
import org.jenkinsci.modules.slave_installer.Prompter;
import org.jenkinsci.modules.windows_agent_installer.WindowsAgentInstaller.AgentURLMacroProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

/**
 * Tests of {@link WindowsAgentInstaller}.
 * @author Oleg Nenashev
 */
public class WindowsAgentInstallerTest {
    
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
    @Issue("JENKINS-42745")
    public void shouldGenerateValidConfigWithOldAPI() throws Exception {
        @SuppressWarnings("deprecation")
        String xml = WindowsAgentInstaller.generateAgentXml("serviceid", "myjava", "", "");
        assertThat("There is unresolved macro", xml, not(containsString("@")));
    }
    
    @Test
    @Issue("JENKINS-42745")
    public void shouldThrowUnresolveMacro() throws Exception {
        // Create a nested macro definition, which won't be resolved
        Map<String,String> macroValues = new HashMap<>();
        macroValues.put(AgentURLMacroProvider.MACRO_NAME, "Depends on @" + AgentURLMacroProvider.MACRO_NAME + "@");
        
        // Try to resolve
        try {
            WindowsAgentInstaller.generateAgentXml("serviceid", "myjava", "", "", macroValues);
        } catch (IOException ex) {
            assertThat("Exception message does not mention unresolved macros", 
                    ex.getMessage(), containsString("Unresolved macros in the XML file: "));
            assertThat("Exception message does not reference the macro name", 
                    ex.getMessage(), containsString(AgentURLMacroProvider.MACRO_NAME));
            return;
        }
        Assert.fail("Expected the Unresolved macro exception");
    }
    
    @Test
    @Issue("JENKINS-39237")
    public void shouldGenerateConfigWithValidDownloadLink() throws InstallationException, IOException, InterruptedException {
        
        WindowsAgentInstaller installer = new WindowsAgentInstaller();
        installer.install(launchConfig, prompter, true);
        
        final String xml;
        try (FileInputStream istream = new FileInputStream(new File(tmpDir.getRoot(), "agentDir/jenkins-agent.xml"))) {
            xml = IOUtils.toString(istream, Charset.defaultCharset());
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
        return WindowsAgentInstaller.AgentURLMacroProvider.generateDownloadMacroValue
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
        assertAgentFile(dir, "jenkins-agent.exe");
        assertAgentFile(dir, "jenkins-agent.xml");
        assertAgentFile(dir, "agent.jar");
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
