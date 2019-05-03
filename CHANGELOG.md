Changelog
====

Below you can view changelogs for the trunk version of the Windows Agent Installer module.

This file also provides links to Jenkins versions, which bundle the released versions.
See [Jenkins changelog](https://jenkins.io/changelog/) for more details.

## 1.11

Release date: May 03, 2019

* [JENKINS-51577](https://issues.jenkins-ci.org/browse/JENKINS-51577) -
Enable TLS 1.2 by default when running with .NET 4.6 or above.
It will enable downloads from recent HTTPS enpoints with disabled TLS 1.0.

## 1.10.0

Release date: Jan 17, 2019

* [PR #22](https://github.com/jenkinsci/windows-slave-installer-module/pull/22) - 
Update WinSW from 2.1.2 to 2.2.0 to support disabling, renaming and archiving service logs
  * [Full changelog](https://github.com/kohsuke/winsw/blob/master/CHANGELOG.md#220)

## 1.9.3

Release date: Nov 07, 2018

* [PR #21](https://github.com/jenkinsci/windows-slave-installer-module/pull/21) -
Change the platform discovery approach to reduce the classloading traffic between master and agents

## 1.9.2

Release date: (Nov 03, 2017)

* [JENKINS-47015](https://issues.jenkins-ci.org/browse/JENKINS-47015) -
Performance: Do not try to update `jenkins-slave.exe` on Unix agents when they connect.

## 1.9.1

Release date: (Aug 18, 2017)

This release updates [Windows Service Wrapper](https://github.com/kohsuke/winsw/) from `2.1.0` to `2.1.2`.
Full changelog can be found [here](https://github.com/kohsuke/winsw/blob/master/CHANGELOG.md).

Fixed issues:

- [JENKINS-46282](https://issues.jenkins-ci.org/browse/JENKINS-46282) - Runaway Process Killer extension was not using the stopTimeoutMs parameter
- [WinSW Issue #206](https://github.com/kohsuke/winsw/issues/206) - Prevent printing of log entries in the `status` command
- [WinSW Issue #218](https://github.com/kohsuke/winsw/issues/218) - Prevent hanging of the stop executable when its logs are not being drained do the parent process

## 1.9

Release date: (May 03, 2017) => Jenkins 2.60

* [JENKINS-43737](https://issues.jenkins-ci.org/browse/JENKINS-43737) -
Update to [Windows Service Wrapper 2.1.0](https://github.com/kohsuke/winsw/blob/master/CHANGELOG.md#210) to support new features: `<download>` command with authentication, flag for startup failure on `<download>` error, _Delayed Automatic Start_ mode.
* [JENKINS-43603](https://issues.jenkins-ci.org/browse/JENKINS-43603) -
Add System Property, which allows disabling WinSW automatic upgrade on agents.
Property name - `org.jenkinsci.modules.windows_slave_installer.disableAutoUpdate`, type - boolean. 
[More info](README.md#disabling-automatic-upgrade).
* [JENKINS-42745](https://issues.jenkins-ci.org/browse/JENKINS-42745) -
Restore compatibility of the `WindowsSlaveInstaller#generateSlaveXml()` method.
Formally it is a regression in `1.7`, but there is no known usages of this API.
* [JENKINS-43930](https://issues.jenkins-ci.org/browse/JENKINS-43930) -
Prevent fatal file descriptor leak when agent service installer fails to read data from the service `startup.log`.
* [PR #14](https://github.com/jenkinsci/windows-slave-installer-module/pull/14) -
Improve logging for restart to the service after the installation completion.

The new features will not be enabled by default in service configuration files, but they can be configured manually.

## 1.8 

Release date: (Apr 01, 2017) => Jenkins 2.53

* [JENKINS-42744](https://issues.jenkins-ci.org/browse/JENKINS-42744) -
Update to [Windows Service Wrapper 2.0.3](https://github.com/kohsuke/winsw/blob/master/CHANGELOG.md#203)
to prevent conversion of environment variables to lowercase in the agent executable. 
(regression in `1.7` and Jenkins `2.50`)

## 1.7

Release date: (Mar 03, 2017) => Jenkins 2.50

This is a major release, which integrates support of the new Windows Service Wrapper (WinSW), which includes many improvements and bugfixes.
See the upgrade guidelines below.

### Improvements

* Upgrade Windows Service Wrapper from `1.18` to `2.0.2`.
[Full changelog](https://github.com/kohsuke/winsw/blob/master/CHANGELOG.md).
* [JENKINS-39231](https://issues.jenkins-ci.org/browse/JENKINS-39231) - 
Enable [Runaway Process Killer](https://github.com/kohsuke/winsw/blob/master/doc/extensions/runawayProcessKiller.md) by default in new Agent installations. 
* [JENKINS-39237](https://issues.jenkins-ci.org/browse/JENKINS-39237) - 
Enable auto-upgrade of newly installed agents if they are connected by HTTPS.
* [JENKINS-23487](https://issues.jenkins-ci.org/browse/JENKINS-23487) - 
Add support of shared directories mapping in Windows agent services.
See [Shared Directory Mapper](https://github.com/kohsuke/winsw/blob/master/doc/extensions/sharedDirectoryMapper.md) for more info.
* [JENKINS-42468](https://issues.jenkins-ci.org/browse/JENKINS-42468) - 
Modify the default Agent service display name prefix to make names human-readable.
* [PR #4](https://github.com/jenkinsci/windows-slave-installer-module/pull/4) - 
Rename `slave` to `agent` in Javadoc and WebUI in order to be compliant with Jenkins 2 terminology.
 - Download endpoints (`/jnlpJars/slave.jar`) and filesystem names (e.g. `jenkins-slave.exe`) have not been modified due to the compatibility reasons.

### Fixed issues
* [JENKINS-22692](https://issues.jenkins-ci.org/browse/JENKINS-22692) - 
Agent connection reset issues when WinSW gets terminated due to the system shutdown.
* Other stability and performance fixes integrated from `1.18` to `2.0.2`.
There are many fixes around configuration options and process termination.
[Full changelog](https://github.com/kohsuke/winsw/blob/master/CHANGELOG.md).


### Upgrade notes (1.7)
* See the upgade guidelines in [Readme](./README.md)
* [Runaway Process Killer](https://github.com/kohsuke/winsw/blob/master/doc/extensions/runawayProcessKiller.md) needs to be manually enabled on old agent installations, which have been created before the upgrade.
* Agent JAR file (`slave.jar` for default installations) needs to be manually enabled on old agent installations.

## 1.6

There is no changelogs for this release and previous ones.
See the commit history to get the info.
