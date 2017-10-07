package org.jenkinsci.modules.windows_slave_installer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins.MasterComputer;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Overwrite <tt>jenkins-slave.exe</tt> by new copy.
 * 
 * @author Kohsuke Kawaguchi
 */
@Extension
@Restricted(NoExternalUse.class)
@RestrictedSince("1.9")
public class SlaveExeUpdater extends ComputerListener {
    /**
     * MD5 checksum of jenkins-slave.exe in our resource
     */
    private volatile String ourCopy;

    /**
     * Disables automatic update of Windows Service Wrapper on agents.
     * This option may be useful if this executable is being managed outside Jenkins.
     * The system property needs to be set up for master only.
     * @since 1.9
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Should be accessible to System Grrovy Scripts")
    static boolean DISABLE_AUTOMATIC_UPDATE = Boolean.getBoolean("org.jenkinsci.modules.windows_slave_installer.disableAutoUpdate");
    
    @Override @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public void onOnline(Computer c, final TaskListener listener) throws IOException, InterruptedException {
        if (DISABLE_AUTOMATIC_UPDATE) return;
        if (!(c instanceof SlaveComputer))  return;


        final SlaveComputer sc = (SlaveComputer) c;
        final Boolean isUnix = sc.isUnix();

        if(isUnix) return;


        // do this asynchronously so as not to block Jenkins from using the slave right away
        MasterComputer.threadPoolForRemoting.submit(new Callable<Void>() {
            public Void call() throws Exception {
                try {
                    Channel ch = sc.getChannel();
                    Slave n = sc.getNode();
                    if (n==null || ch==null)   return null;    // defensive check

                    FilePath root = new FilePath(ch, n.getRemoteFS());
                    FilePath agentExe = root.child("jenkins-slave.exe");
                    if (!agentExe.exists())     return null;    // nothing to update

                    String current = agentExe.digest();

                    URL ourExe = WindowsSlaveInstaller.class.getResource("jenkins-slave.exe");
                    if (ourCopy==null) {
                        ourCopy = Util.getDigestOf(ourExe.openStream());
                    }

                    if(ourCopy.equals(current))     return null; // identical

                    // at this point we want to overwrite jenkins-slave.exe on slave with our copy.
                    // This is tricky because the process is running. The trick is to rename the current
                    // file and place a new file in the correct name.

                    FilePath tmp = new FilePath(agentExe.getChannel(), agentExe.getRemote()+".new");
                    FilePath backup = new FilePath(agentExe.getChannel(), agentExe.getRemote()+".bak");

                    if (backup.exists()) {
                        try {
                            backup.delete();
                        } catch (IOException e) {
                            listener.getLogger().println("Looks like jenkins-slave.exe.bak is currently running. aborting overwrite");
                            return null;
                        }
                    }

                    tmp.copyFrom(ourExe);
                    agentExe.renameTo(backup);
                    tmp.renameTo(agentExe);
                    listener.getLogger().println("Scheduled overwrite of jenkins-slave.exe on the next service startup");
                } catch (Throwable e) {
                    e.printStackTrace(listener.error("Failed to update jenkins-slave.exe"));
                }

                return null;
            }
        });
    }
}
