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

/**
 * @author Kohsuke Kawaguchi
 */
public class WindowsSlaveInstaller extends SlaveInstaller {
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


    @Override @SuppressFBWarnings("DM_EXIT")
    public void install(LaunchConfiguration params, Prompter prompter) throws InstallationException, IOException, InterruptedException {
        if(!DotNet.isInstalled(2,0))
            throw new InstallationException(Messages.WindowsSlaveInstaller_DotNetRequired());

        final File dir = params.getStorage().getAbsoluteFile();
        if (!dir.exists())
            if (!dir.mkdirs()){
                throw new InstallationException(Messages.WindowsSlaveInstaller_RootFsCreationFailed(dir));
            }

        final File agentExe = new File(dir, "jenkins-slave.exe");
        FileUtils.copyURLToFile(WindowsSlaveInstaller.class.getResource("jenkins-slave.exe"), agentExe);

        FileUtils.copyURLToFile(WindowsSlaveInstaller.class.getResource("jenkins-slave.exe.config"),
                new File(dir,"jenkins-slave.exe.config"));

        // write out the descriptor
        String xml = generateSlaveXml(
                generateServiceId(dir.getPath()),
                System.getProperty("java.home")+"\\bin\\java.exe", null, params.buildRunnerArguments().toStringWithQuote());
        FileUtils.writeStringToFile(new File(dir, "jenkins-slave.xml"),xml,"UTF-8");

        // copy slave.jar
        File dstAgentJar = new File(dir,"slave.jar").getCanonicalFile();
        if(!dstAgentJar.exists()) // perhaps slave.jar is already there?
            FileUtils.copyFile(params.getJarFile(), dstAgentJar);

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
        System.exit(0);
    }

    public static String generateServiceId(String slaveRoot) throws IOException {
        return "jenkinsslave-"+slaveRoot.replace(':','_').replace('\\','_').replace('/','_');
    }

    public static String generateSlaveXml(String id, String java, String vmargs, String args) throws IOException {
        String xml = IOUtils.toString(WindowsSlaveInstaller.class.getResourceAsStream("jenkins-slave.xml"), "UTF-8");
        xml = xml.replace("@ID@", id);
        xml = xml.replace("@JAVA@", java);
        xml = xml.replace("@VMARGS@", StringUtils.defaultString(vmargs));
        xml = xml.replace("@ARGS@", args);
        xml = xml.replace("\n","\r\n");
        return xml;
    }

    private static final long serialVersionUID = 1L;
}
