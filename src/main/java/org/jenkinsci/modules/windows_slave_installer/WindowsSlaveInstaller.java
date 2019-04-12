package org.jenkinsci.modules.windows_slave_installer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Launcher.LocalLauncher;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import hudson.util.jna.DotNet;
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
import java.io.IOException;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
    
    /**
     * Lists the new required macros, which has been added to the pattern since 1.6.
     * All of these macros are expected to have a default value.
     */
    private static final Set<String> ADDITIONAL_REQUIRED_MACROS = new TreeSet<>(
        Arrays.asList(AgentURLMacroProvider.MACRO_NAME));
    
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
        return new LocalLauncher(out).launch().cmds(agentExe, command).stdout(out).pwd(pwd).join();
    }

    @Override
    public void install(LaunchConfiguration params, Prompter prompter) throws InstallationException, IOException, InterruptedException {
        install(params, prompter, false);
    }
    
    @SuppressFBWarnings(value = "DM_EXIT", justification = "Legacy design, but as designed")
    /*package*/ void install(LaunchConfiguration params, Prompter prompter, boolean mock) throws InstallationException, IOException, InterruptedException {
        if(!mock && !DotNet.isInstalled(4, 0)) {
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

        // write out the descriptor
        final String serviceId = generateServiceId(dir.getPath());
        String xml = generateSlaveXml(
                serviceId,
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
        Runtime.getRuntime().addShutdownHook(new ServiceStarterThread(agentExe, dir, serviceId));
        
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
    public static String generateSlaveXml(String id, String java, String vmargs, String args) throws IOException {
        return generateSlaveXml(id, java, vmargs, args, Collections.<String, String>emptyMap());
    }
    
    /**
     * Generates WinSW configuration for the agent.
     * This method takes a template from the resources and injects macro values there.
     * Macro values can be contributed by {@code extraMacroValues} or by {@link MacroValueProvider}s.
     * @param id Service Id
     * @param java Path to Java
     * @param vmargs JVM args arguments to be passed
     * @param args slave.jar arguments to be passed
     * @param extraMacroValues Additional macro values to be injected.
     *                         The list of required macros is provided in {@link #ADDITIONAL_REQUIRED_MACROS}.
     *                         If the macro value is not provided, the implementation will look up for the default value in
     *                         available {@link MacroValueProvider}s.
     *                         
     * @return Generated WinSW configuration file.
     * @throws IOException The file cannot be generated or if not all macro variables can be resolved
     * @since TODO
     */
    public static String generateSlaveXml(String id, String java, String vmargs, String args, @Nonnull Map<String, String> extraMacroValues) throws IOException {
        // Just a legacy behavior for the obsolete installer
        String xml = IOUtils.toString(WindowsSlaveInstaller.class.getResourceAsStream("jenkins-slave.xml"), "UTF-8");
        xml = xml.replace("@ID@", id);
        xml = xml.replace("@JAVA@", java);
        xml = xml.replace("@VMARGS@", StringUtils.defaultString(vmargs));
        xml = xml.replace("@ARGS@", args);
        xml = xml.replace("\n","\r\n");
        
        // Resolve missing macros to retain compatibility with old API
        Map <String, String> toResolve = new HashMap<>(extraMacroValues);
        Collection<MacroValueProvider> defaultProviders = MacroValueProvider.allDefaultProviders();
        for (String macroName : ADDITIONAL_REQUIRED_MACROS) {
            if (!extraMacroValues.containsKey(macroName)) {
                for (MacroValueProvider provider : defaultProviders) {
                    String defaultValue = provider.getDefaulValue(macroName);
                    if (defaultValue != null) {
                        toResolve.put(macroName, defaultValue);
                        break;
                    }
                }
            }
        }
        
        for (Map.Entry<String, String> entry : toResolve.entrySet()) {
            xml = xml.replace("@" + entry.getKey() + "@", entry.getValue());
        }
        
        if (xml.contains("@")) {
            Set<String> unresolvedMacros = new HashSet<>();
            for (String macroName : ADDITIONAL_REQUIRED_MACROS) {
                if (xml.contains("@" + macroName + "@")) {
                    unresolvedMacros.add(macroName);
                } 
            }
            // If there is any unknown macro, it will be caught by tests.
            throw new IOException("Unresolved macros in the XML file: " + StringUtils.join(unresolvedMacros, ","));
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
     * Currently the implementation supports only one macro for the provider.
     */
    @Restricted(NoExternalUse.class)
    /*package*/ static abstract class MacroValueProvider {
       
        @Nonnull
        public abstract Map<String, String> getMacroValues();
        
        @Nonnull
        public abstract Set<String> getMacroNames();
        
        @CheckForNull
        public abstract String getDefaulValue(@Nonnull String macroName);
        
        static final Collection<MacroValueProvider> allDefaultProviders() {
            return Arrays.<MacroValueProvider>asList(new AgentURLMacroProvider(null));
        }
    }
    
    /*package*/ static class ServiceStarterThread extends Thread {

        private final File agentExe;
        private final File rootDir;
        private final String serviceId;

        public ServiceStarterThread(@Nonnull File agentExe, @Nonnull File rootDir, @Nonnull String serviceId) {
            super("Service Starter for " + serviceId);
            this.agentExe = agentExe;
            this.rootDir = rootDir;
            this.serviceId = serviceId;
        }

        @Override
        public void run() {
            try {
                StreamTaskListener task = StreamTaskListener.fromStdout();
                int r = runElevated(agentExe, "start", task, rootDir);
                task.getLogger().println(r == 0 ? "Successfully started" : "Start service failed. Exit code=" + r);
            } catch (IOException | InterruptedException ex) {
                // Level is severe, because the process won't be recovered in the service mode
                LOGGER.log(Level.SEVERE, "Failed to start the service with id=" + serviceId, ex);
            }
        }
    }
    
    /*package*/ static class AgentURLMacroProvider extends MacroValueProvider {

        static final String MACRO_NAME = "AGENT_DOWNLOAD_URL";
        static final String DEFAULT_DISABLED_VALUE = "<!-- <download from=\"TODO:jarFile\" to=\"%BASE%\\slave.jar\"/> -->";
        
        private static final Set<String> MACRO_NAMES = new TreeSet<>(Arrays.asList(MACRO_NAME));
        
        @CheckForNull
        private final LaunchConfiguration launchConfiguration;
        
        public AgentURLMacroProvider(@CheckForNull LaunchConfiguration launchConfig) {
            this.launchConfiguration = launchConfig;
        }

        @Override
        public Map<String, String> getMacroValues() {
            Map<String, String> res = new TreeMap<>();
            
            URL remotingURL = null;
            if (launchConfiguration != null) {
                try {
                    remotingURL = launchConfiguration.getLatestJarURL();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to retrieve the latest Remoting JAR URL. Auto-download will be disabled", ex);
                }
            }
            
            res.put(MACRO_NAME, generateDownloadMacroValue(remotingURL));
            return res;
        }
        
        @Nonnull
        /**package*/ static String generateDownloadMacroValue(@CheckForNull URL remotingURL) {
            String macroValue;
            if (remotingURL != null) {
                macroValue = "<download from=\"" + remotingURL.toString() + "\" to=\"%BASE%\\slave.jar\"/>";
                if (!"https".equals(remotingURL.getProtocol())) {
                    macroValue = "<!-- " + macroValue + " -->";
                }         
            } else {
                macroValue = DEFAULT_DISABLED_VALUE;
            }
            return macroValue;
        }

        @Override
        public Set<String> getMacroNames() {
            return Collections.unmodifiableSet(MACRO_NAMES);
        }

        @Override
        public String getDefaulValue(String macroName) {
            if (MACRO_NAMES.contains(macroName)) {
                // Fine since we keep one macro
                return DEFAULT_DISABLED_VALUE;
            }
            return null;
        }
    }
}
