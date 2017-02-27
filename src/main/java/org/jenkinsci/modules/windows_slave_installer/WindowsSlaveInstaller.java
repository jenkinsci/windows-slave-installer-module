package org.jenkinsci.modules.windows_slave_installer;

import com.sun.jna.Native;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Launcher.LocalLauncher;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import hudson.util.jna.DotNet;
import hudson.util.jna.Kernel32Utils;
import hudson.util.jna.SHELLEXECUTEINFO;
import hudson.util.jna.Shell32;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.modules.slave_installer.InstallationException;
import org.jenkinsci.modules.slave_installer.LaunchConfiguration;
import org.jenkinsci.modules.slave_installer.Prompter;
import org.jenkinsci.modules.slave_installer.SlaveInstaller;
import org.jvnet.localizer.Localizable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static hudson.util.jna.SHELLEXECUTEINFO.*;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Installs agent as a Windows service.
 * The installer uses <a href="https://github.com/kohsuke/winsw">WinSW</a> as a service wrapper.
 * @author Kohsuke Kawaguchi
 */
public class WindowsSlaveInstaller extends SlaveInstaller {
    
    private final static Logger LOGGER = Logger.getLogger(WindowsSlaveInstaller.class.getName());
    
    public WindowsSlaveInstaller() {
    }

    @Override
    public Localizable getConfirmationText() {
        return Messages._WindowsSlaveInstaller_ConfirmInstallation();
    }

    /**
     * Invokes slave.exe with a SCM management command.
     *
     * <p>
     * If it fails in a way that indicates the presence of UAC, retry in an UAC compatible manner.
     */
    static int runElevated(File agentExe, String command, TaskListener out, File pwd) throws IOException, InterruptedException {
        try {
            return new LocalLauncher(out).launch().cmds(agentExe, command).stdout(out).pwd(pwd).join();
        } catch (IOException e) {
            if (e.getMessage().contains("CreateProcess") && e.getMessage().contains("=740")) {
                // fall through
            } else {
                throw e;
            }
        }

        // error code 740 is ERROR_ELEVATION_REQUIRED, indicating that
        // we run in UAC-enabled Windows and we need to run this in an elevated privilege
        SHELLEXECUTEINFO sei = new SHELLEXECUTEINFO();
        sei.fMask = SEE_MASK_NOCLOSEPROCESS;
        sei.lpVerb = "runas";
        sei.lpFile = agentExe.getAbsolutePath();
        sei.lpParameters = "/redirect redirect.log "+command;
        sei.lpDirectory = pwd.getAbsolutePath();
        sei.nShow = SW_HIDE;
        if (!Shell32.INSTANCE.ShellExecuteEx(sei))
            throw new IOException("Failed to shellExecute: "+ Native.getLastError());

        try {
            return Kernel32Utils.waitForExitProcess(sei.hProcess);
        } finally {
            FileInputStream fin = new FileInputStream(new File(pwd,"redirect.log"));
            IOUtils.copy(fin,out.getLogger());
            fin.close();
        }
    }

    @Override
    public void install(LaunchConfiguration params, Prompter prompter) throws InstallationException, IOException, InterruptedException {
        install(params, prompter, false);
    }
    
    @SuppressFBWarnings(value = "DM_EXIT", justification = "Legacy design, but as designed")
    /*package*/ void install(LaunchConfiguration params, Prompter prompter, boolean mock) throws InstallationException, IOException, InterruptedException {
        if(!mock && !DotNet.isInstalled(2,0)) {
            throw new InstallationException(Messages.WindowsSlaveInstaller_DotNetRequired());
        }
        
        final File dir = params.getStorage().getAbsoluteFile();
        if (!dir.exists())
            if (!dir.mkdirs()){
                throw new InstallationException(Messages.WindowsSlaveInstaller_RootFsCreationFailed(dir));
            }
        params.getLatestJarURL();

        final File agentExe = new File(dir, "jenkins-slave.exe");
        FileUtils.copyURLToFile(WindowsSlaveInstaller.class.getResource("jenkins-slave.exe"), agentExe);

        FileUtils.copyURLToFile(WindowsSlaveInstaller.class.getResource("jenkins-slave.exe.config"),
                new File(dir,"jenkins-slave.exe.config"));

        // write out the descriptor
        String xml = generateSlaveXml(
                generateServiceId(dir.getPath()),
                System.getProperty("java.home")+"\\bin\\java.exe", null, 
                params.buildRunnerArguments().toStringWithQuote(), 
                Arrays.asList(new MacroValueProvider[] {new AgentURLMacroProvider(params)}));
        FileUtils.writeStringToFile(new File(dir, "jenkins-slave.xml"),xml,"UTF-8");

        // copy slave.jar
        File dstAgentJar = new File(dir,"slave.jar").getCanonicalFile();
        if(!dstAgentJar.exists()) // perhaps slave.jar is already there?
            FileUtils.copyFile(params.getJarFile(), dstAgentJar);

        if (mock) {
            // If the installation is mocked, do not really try to install it
            return;
        }
        
        // install as a service
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamTaskListener task = new StreamTaskListener(baos);
        int r = runElevated(agentExe,"install",task,dir);
        if(r!=0)
            throw new InstallationException(baos.toString());

        // no mechanism to do confirmation
//        r = JOptionPane.showConfirmDialog(dialog,
//                Messages.WindowsSlaveInstaller_InstallationSuccessful(),
//                Messages.WindowsInstallerLink_DisplayName(), OK_CANCEL_OPTION);
//        if(r!=JOptionPane.OK_OPTION)    return;

        // let the service start after we close our connection, to avoid conflicts
        Runtime.getRuntime().addShutdownHook(new Thread("service starter") {
            public void run() {
                try {
                    StreamTaskListener task = StreamTaskListener.fromStdout();
                    int r = runElevated(agentExe,"start",task,dir);
                    task.getLogger().println(r==0?"Successfully started":"start service failed. Exit code="+r);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        
        // TODO: FindBugs: Move to the outer installation logic?
        System.exit(0);
    }

    public static String generateServiceId(String slaveRoot) throws IOException {
        return "jenkinsslave-"+slaveRoot.replace(':','_').replace('\\','_').replace('/','_');
    }

    /**
     * @deprecated Use {@link #generateSlaveXml(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.util.Map)}
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static String generateSlaveXml(String id, String java, String vmargs, String args) throws IOException {
        return generateSlaveXml(id, java, vmargs, args, Collections.<String, String>emptyMap());
    }
    
    /**
     * Generates WinSW configuration for the agent.
     * @param id Service Id
     * @param java Path to Java
     * @param vmargs JVM args arguments to be passed
     * @param args slave.jar arguments to be passed
     * @param extraMacroValues Additional macro values to be injected.
     * @return Generated WinSW configuration file.
     * @throws IOException The file cannot be generated
     * @since TODO
     */
    @Restricted(NoExternalUse.class)
    public static String generateSlaveXml(String id, String java, String vmargs, String args, @Nonnull Map<String, String> extraMacroValues) throws IOException {
        // Just a legacy behavior for the obsolete installer
        String xml = IOUtils.toString(WindowsSlaveInstaller.class.getResourceAsStream("jenkins-slave.xml"), "UTF-8");
        xml = xml.replace("@ID@", id);
        xml = xml.replace("@JAVA@", java);
        xml = xml.replace("@VMARGS@", StringUtils.defaultString(vmargs));
        xml = xml.replace("@ARGS@", args);
        xml = xml.replace("\n","\r\n");
        
        for (Map.Entry<String, String> entry : extraMacroValues.entrySet()) {
            xml = xml.replace("@" + entry.getKey() + "@", entry.getValue());
        }
        return xml;
    }
      
    /*package*/ static String generateSlaveXml(String id, String java, @CheckForNull String vmargs, 
                @Nonnull String args, @Nonnull Iterable<MacroValueProvider> providers
            ) throws IOException {
        Map<String, String> macroValues = new TreeMap<>();
        for (MacroValueProvider provider : providers) {
            //TODO: fail in the case of duplicated entries?
            macroValues.putAll(provider.getMacroValues());
        }
        
        return generateSlaveXml(id, java, vmargs, args, macroValues);
    }

    private static final long serialVersionUID = 1L;
    
    /**
     * Macro provider implementation for the internal use.
     */
    @Restricted(NoExternalUse.class)
    /*package*/ static abstract class MacroValueProvider {
       
        @Nonnull
        public abstract Map<String, String> getMacroValues();
    }
    
    /*package*/ static class AgentURLMacroProvider extends MacroValueProvider {

        @Nonnull
        private final LaunchConfiguration launchConfiguration;
        
        public AgentURLMacroProvider(@Nonnull LaunchConfiguration launchConfig) {
            this.launchConfiguration = launchConfig;
        }

        @Override
        public Map<String, String> getMacroValues() {
            Map<String, String> res = new TreeMap<>();
            URL remotingURL = null;
            try {
                remotingURL = launchConfiguration.getLatestJarURL();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to retrieve the latest Remoting JAR URL. Auto-download will be disabled", ex);
            }
            
            final String macroValue;
            if (remotingURL != null) {
                macroValue = "<download from=\"" + remotingURL.toString() + "\" to=\"%BASE%\\slave.jar\"/>";
            } else {
                macroValue = "<!-- <download from=\"TODO:jarFile\" to=\"%BASE%\\slave.jar\"/> -->";
            }
            
            res.put("AGENT_DOWNLOAD_URL", macroValue);
            return res;
        }
    }
}
