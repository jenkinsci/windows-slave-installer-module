package org.jenkinsci.modules.windows_slave_installer;

import hudson.Extension;
import hudson.FilePath;
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

/**
 * Overwrite <tt>jenkins-slave.exe</tt> by new copy
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlaveExeUpdater extends ComputerListener {
    /**
     * MD5 checksum of jenkins-slave.exe in our resource
     */
    private volatile String ourCopy;

    @Override
    public void onOnline(Computer c, final TaskListener listener) throws IOException, InterruptedException {
        if (!(c instanceof SlaveComputer))  return;

        final SlaveComputer sc = (SlaveComputer) c;

        // do this asynchronously so as not to block Jenkins from using the slave right away
        MasterComputer.threadPoolForRemoting.submit(new Callable<Void>() {
            public Void call() throws Exception {
                try {
                    Channel ch = sc.getChannel();
                    Slave n = sc.getNode();
                    if (n==null || ch==null)   return null;    // defensive check

                    FilePath root = new FilePath(ch, n.getRemoteFS());
                    FilePath slaveExe = root.child("jenkins-slave.exe");
                    if (!slaveExe.exists())     return null;    // nothing to update

                    String current = slaveExe.digest();

                    URL ourExe = WindowsSlaveInstaller.class.getResource("jenkins-slave.exe");
                    if (ourCopy==null) {
                        ourCopy = Util.getDigestOf(ourExe.openStream());
                    }

                    if(ourCopy.equals(current))     return null; // identical

                    // at this point we want to overwrite jenkins-slave.exe on slave with our copy.
                    // This is tricky because the process is running. The trick is to rename the current
                    // file and place a new file in the correct name.

                    FilePath tmp = new FilePath(slaveExe.getChannel(), slaveExe.getRemote()+".new");
                    FilePath backup = new FilePath(slaveExe.getChannel(), slaveExe.getRemote()+".bak");

                    if (backup.exists()) {
                        try {
                            backup.delete();
                        } catch (IOException e) {
                            listener.getLogger().println("Looks like jenkins-slave.exe.bak is currently running. aborting overwrite");
                            return null;
                        }
                    }

                    tmp.copyFrom(ourExe);
                    slaveExe.renameTo(backup);
                    tmp.renameTo(slaveExe);
                    listener.getLogger().println("Scheduled overwrite of jenkins-slave.exe on the next service startup");
                } catch (Throwable e) {
                    e.printStackTrace(listener.error("Failed to update jenkins-slave.exe"));
                }

                return null;
            }
        });
    }
}
