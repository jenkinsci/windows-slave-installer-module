package org.jenkinsci.modules.windows_slave_installer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.VerRsrc.VS_FIXEDFILEINFO;
import com.sun.jna.platform.win32.Version;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.RestrictedSince;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;
import jenkins.SlaveToMasterFileCallable;
import jenkins.model.Jenkins.MasterComputer;

/**
 * Overwrite <tt>jenkins-slave.exe</tt> by new copy.
 * 
 * @author Kohsuke Kawaguchi
 */
@Extension
@Restricted(NoExternalUse.class)
@RestrictedSince("1.9")
public class SlaveExeUpdater extends ComputerListener {

    private static class GetVersion extends SlaveToMasterFileCallable<List<Integer>> {
        private static final long serialVersionUID = 1L;
        @Override
        public List<Integer> invoke(File f, VirtualChannel channel) {
            String path = f.getPath();
            int size = Version.INSTANCE.GetFileVersionInfoSize(path, new IntByReference());
            if (size == 0)
                return null;

            Memory data = new Memory(size);
            if (!Version.INSTANCE.GetFileVersionInfo(path, 0, size, data))
                return null;

            PointerByReference buffer = new PointerByReference();
            if (!Version.INSTANCE.VerQueryValue(data, "\\", buffer, new IntByReference()))
                return null;

            VS_FIXEDFILEINFO info = new VS_FIXEDFILEINFO(buffer.getValue());
            return Arrays.asList(
                info.dwFileVersionMS.getHigh().intValue(),
                info.dwFileVersionMS.getLow().intValue(),
                info.dwFileVersionLS.getHigh().intValue()
            );
        }
    }

    /**
     * Our versions of jenkins-slave.exe defined in pom.xml
     */
    private List<Integer> ourVersions;

    /**
     * Disables automatic update of Windows Service Wrapper on agents.
     * This option may be useful if this executable is being managed outside Jenkins.
     * The system property needs to be set up for master only.
     * @since 1.9
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Should be accessible to System Groovy Scripts")
    static boolean DISABLE_AUTOMATIC_UPDATE = Boolean.getBoolean("org.jenkinsci.modules.windows_slave_installer.disableAutoUpdate");
    
    @Override @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public void onOnline(Computer c, final TaskListener listener) throws IOException, InterruptedException {
        if (DISABLE_AUTOMATIC_UPDATE) return;
        if (!(c instanceof SlaveComputer))  return;

        final SlaveComputer sc = (SlaveComputer) c;

        final Boolean isUnix = sc.isUnix();
        if (isUnix == null || isUnix) { // Do not try installing on disconnected or Unix machines
            return;
        }

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

                    List<Integer> currentVersions = agentExe.act(new GetVersion());

                    if (currentVersions == null)
                        return null;

                    if (ourVersions == null) {
                        // NOTE: Keep in sync with <winsw.version> in pom.xml.
                        ourVersions = Arrays.asList(2, 9, 0);
                    }

                    boolean oursIsNewer = false;
                    for (int i = 0; i < ourVersions.size(); i++) {
                        if (ourVersions.get(i) > currentVersions.get(i)) {
                            oursIsNewer = true;
                            break;
                        }

                        if (ourVersions.get(i) < currentVersions.get(i)) {
                            oursIsNewer = false;
                            break;
                        }
                    }

                    if (!oursIsNewer)
                        return null;

                    URL ourExe = WindowsSlaveInstaller.class.getResource("jenkins-slave.exe");

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
