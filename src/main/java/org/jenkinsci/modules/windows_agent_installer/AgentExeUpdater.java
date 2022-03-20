package org.jenkinsci.modules.windows_agent_installer;

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
 * Overwrite <tt>jenkins-agent.exe</tt> by new copy.
 * 
 * @author Kohsuke Kawaguchi
 */
@Extension
@Restricted(NoExternalUse.class)
@RestrictedSince("1.9")
public class AgentExeUpdater extends ComputerListener {
    /**
     * MD5 checksum of jenkins-agent.exe in our resource
     */
    private volatile String ourCopy;

    public static final String AGENT_EXE_NAME = "jenkins-agent.exe";

    /**
     * Disables automatic update of Windows Service Wrapper on agents.
     * This option may be useful if this executable is being managed outside Jenkins.
     * The system property needs to be set up for master only.
     * @since 1.9
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Should be accessible to System Groovy Scripts")
    static boolean DISABLE_AUTOMATIC_UPDATE = Boolean.getBoolean("org.jenkinsci.modules.windows_agent_installer.disableAutoUpdate");
    
    @Override @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public void onOnline(Computer c, final TaskListener listener) throws IOException, InterruptedException {
        if (DISABLE_AUTOMATIC_UPDATE) return;
        if (!(c instanceof SlaveComputer))  return;

        final SlaveComputer sc = (SlaveComputer) c;

        final Boolean isUnix = sc.isUnix();
        if (isUnix == null || isUnix) { // Do not try installing on disconnected or Unix machines
            return;
        }

        // do this asynchronously so as not to block Jenkins from using the agent right away
        MasterComputer.threadPoolForRemoting.submit(new Callable<Void>() {
            public Void call() throws Exception {
                try {

                    Channel ch = sc.getChannel();
                    Slave n = sc.getNode();
                    if (n==null || ch==null)   return null;    // defensive check

                    FilePath root = new FilePath(ch, n.getRemoteFS());
                    FilePath agentExe = root.child(AGENT_EXE_NAME);
                    if (!agentExe.exists())     return null;    // nothing to update

                    String current = agentExe.digest();

                    URL ourExe = WindowsAgentInstaller.class.getResource(AGENT_EXE_NAME);
                    if (ourCopy==null) {
                        ourCopy = Util.getDigestOf(ourExe.openStream());
                    }

                    if(ourCopy.equals(current))     return null; // identical

                    // at this point we want to overwrite jenkins-agent.exe on agent with our copy.
                    // This is tricky because the process is running. The trick is to rename the current
                    // file and place a new file in the correct name.

                    FilePath tmp = new FilePath(agentExe.getChannel(), agentExe.getRemote()+".new");
                    FilePath backup = new FilePath(agentExe.getChannel(), agentExe.getRemote()+".bak");

                    if (backup.exists()) {
                        try {
                            backup.delete();
                        } catch (IOException e) {
                            listener.getLogger().printf("Looks like %s.bak is currently running. aborting overwrite", AGENT_EXE_NAME);
                            return null;
                        }
                    }

                    tmp.copyFrom(ourExe);
                    agentExe.renameTo(backup);
                    tmp.renameTo(agentExe);
                    listener.getLogger().printf("Scheduled overwrite of %s on the next service startup", AGENT_EXE_NAME);
                } catch (Throwable e) {
                    e.printStackTrace(listener.error("Failed to update " + AGENT_EXE_NAME));
                }

                return null;
            }
        });
    }
}
