package org.jenkinsci.modules.windows_slave_installer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.remoting.Channel;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jenkinsci.modules.slave_installer.SlaveInstaller;
import org.jenkinsci.modules.slave_installer.SlaveInstallerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlaveInstallerFactoryImpl extends SlaveInstallerFactory {
    @Inject
    InstanceIdentity id;

    @Override
    public SlaveInstaller createIfApplicable(Channel c) throws IOException, InterruptedException {
        if (c.call(new IsWindows()))
            return new WindowsSlaveInstaller();
        return null;
    }

    @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "Remoting does not need it")
    private static final class IsWindows extends MasterToSlaveCallable<Boolean,IOException> {
        @Override
        public Boolean call() throws IOException {
            return File.pathSeparatorChar==';';
        }
    }
}
