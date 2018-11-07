package org.jenkinsci.modules.windows_slave_installer;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jenkinsci.modules.slave_installer.SlaveInstaller;
import org.jenkinsci.modules.slave_installer.SlaveInstallerFactory;

import javax.inject.Inject;
import java.io.IOException;

@Extension
public class SlaveInstallerFactoryImpl extends SlaveInstallerFactory {
    @Inject
    InstanceIdentity id;

    @Override
    public SlaveInstaller createIfApplicable(Channel c) throws IOException, InterruptedException {
        if (new FilePath(c, ".").createLauncher(TaskListener.NULL).isUnix()) {
            return null;
        } else {
            return new WindowsSlaveInstaller();
        }
    }

}
