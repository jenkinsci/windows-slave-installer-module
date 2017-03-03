Windows Agent Installer module for Jenkins
---

This module is a part of the Jenkins core, which provides features for agent management as Windows services.

Provided features:

* Installation agents as Windows services powered by the [Windows Service Wrapper (WinSW)](https://github.com/kohsuke/winsw). 
* GUI option for the JNLP agents, which adds the `Install as a service` menu option.
* Automatic upgrade of Windows service wrapper versions to keep them aligned with the version from this module.

## Changelog

See the changelog [here](./CHANGELOG.md).

## Installation 

### Automatic

Once you start a JNLP agents on Windows in the GUI mode (e.g. via Java WebStart), 
It will display the `Install as a service` menu option once Jenkins master finishes the slave configuration.

* The agent will be installed if your Windows account has enough permissions to install Windows Services and to access the System and Application event logs.

### Manual

You can setup the agent service manually by following the [WinSW Installation Guide](https://github.com/kohsuke/winsw/blob/master/doc/installation.md#winsw-installation-guide).

### More info

See also the old installation guide on Jenkins Wiki
([link](https://wiki.jenkins-ci.org/display/JENKINS/Installing+Jenkins+as+a+Windows+service)).


## Upgrading old agents

<!-- TODO: It s a temporary location of the guidelines. They will be moved to Jenkins.io soon -->

This section provides information about upgrading Jenkins agents installed as Windows services.
In order to apply the new features, you may need to...

* Upgrade the WinSW executable (manually or automatically)
* Upgrade the WinSW configuration (manually)

### Upgrading Windows Service Wrapper

Windows Agent Installer module may be able to automatically upgrade the agent in particular cases.

#### Automatic upgrade

0. Upgrade Jenkins to the version, which provides this module
0. Jenkins is expected to automatically upgrade `jenkins-slave.exe` executables if... 
  * The agent is **connected** to the Jenkins master
    - If the agent is not connected, the update will be postponed till the agent connects to the master
  * WinSW executable is located in `REMOTE_ROOT_DIR/jenkins-slave.exe` and writable by the 
  * WinSW executable is writable as well as `REMOTE_ROOT_DIR/jenkins-slave.exe.new` and `REMOTE_ROOT_DIR/jenkins-slave.exe.bak` files
0. If the upgrade happens, you should be able to see the message in the `Agent log` in the Jenkins Web UI
0. Once upgrade is done, the changes will be applied on the next Windows service restart

#### Manual upgrade

Manual upgrade may be required if the agent does not comply with the requirements mentioned above.

Please note that Jenkins master **may override** the WinSW executable 
if it is located in `REMOTE_ROOT_DIR/jenkins-slave.exe`.
It possible to disable the automatic upgrade only by using another path or by making the executable non-writable by the Windows agent.

0. Download the new WinSW release from [GitHub Releases](https://github.com/kohsuke/winsw/releases) or [NuGet](https://www.nuget.org/packages/WinSW/).
  * Depending on the .NET Framework version in your system, 
  you can use `WinSW.NET2.exe` or `WinSW.NET4.exe` executable.
  * If you need to run the `WinSW.NET2.exe` executable on .NET 4 or above, 
  see [this guide](https://github.com/kohsuke/winsw/blob/master/doc/installation.md#making-winsw-1x-compatible-with-net-runtime-40).
0. Replace the WinSW executable on your agent machines by the new version.

#### Upgrading agent configuration

Windows Agent Installer module **never** updates WinSW configuration files, but these files enable particular features on new agent installations.
For example `1.7` introduces [Runaway Process Killer](https://github.com/kohsuke/winsw/blob/master/doc/extensions/runawayProcessKiller.md) and [Automatic JNLP agent upgrade](https://issues.jenkins-ci.org/browse/JENKINS-39237).
It is advised to keep configurations up to date on all agents.

In order to update the configurations you, need to edit the XML configuration files (e.g. `jenkins-slave.xml`).

* The default configuration of agents can be found [here](https://github.com/jenkinsci/windows-slave-installer-module/blob/master/src/main/resources/org/jenkinsci/modules/windows_slave_installer/jenkins-slave.xml).
* All available options are described in the [WinSW XML config file specification](https://github.com/kohsuke/winsw/blob/master/doc/xmlConfigFile.md).
