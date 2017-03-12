Changelog
====

Below you can view changelogs for the trunk version of the Windows Agent Installer module.

This file also provides links to Jenkins versions, which bundle the released versions.
See [Jenkins changelog](https://jenkins.io/changelog/) for more details.

## 1.7

Release date: (Mar 03, 2017) => Jenkins `TODO`

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
