package org.jenkinsci.modules.windows_slave_installer;

import hudson.Extension;
import hudson.Util;
import hudson.remoting.Callable;
import hudson.slaves.SlaveComputer;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jenkinsci.modules.slave_installer.SlaveInstaller;
import org.jenkinsci.modules.slave_installer.SlaveInstallerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlaveInstallerFactoryImpl extends SlaveInstallerFactory {
    @Inject
    InstanceIdentity id;

    @Override
    public SlaveInstaller createIfApplicable(SlaveComputer c) throws IOException, InterruptedException {
        if (Boolean.FALSE.equals(c.isUnix())) {
            return new WindowsSlaveInstaller(c.getNode().getRemoteFS());
        }
        return null;
    }
}
