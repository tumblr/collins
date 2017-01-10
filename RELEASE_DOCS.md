# How to release collins

- [ ] Go to https://github.com/tumblr/collins/releases and click "Draft a new release"
- [ ] The tag version should be `v<next version number>` according to [semantic versioning](http://semver.org/).  The release title should be the same version number but without the leading `v`.  For example, the tag for version 2.1.0 is [`v2.1.0`](https://github.com/tumblr/collins/tree/v2.1.0) and the release is titled [`2.1.0`](https://github.com/tumblr/collins/releases/tag/v2.1.0).
- [ ] Make sure the release is set to target master.
- [ ] Type a few paragraphs highlighting major features or changes in this release.
- [ ] Run `scripts/gen_changelog.rb` to generate a changelog of PRs from the last release.  Copy what it prints and paste it at the bottom of the release draft.
- [ ] Check out the collins code in an environment running jdk 7 with the unlimited strength JCE configured correctly, and play activator checked out in the correct location (`$HOME/src/activator-1.3.6-minimal/activator`).  Run `scripts/package.sh` and attach the resulting zip to the release, and name it `collins-v<version number>.zip`.  
- [ ] Do the previous step but in an environment with jdk 8.  Attach the zip to the release, and title it `collins-v<version number>-jdk8.zip`.
- [ ] Save, but do not publish, the draft.  If you want to get proofreading for your release text, this is the right time to do it.
- [ ] Cut a new branch off of master.  Copy the text of the release into the [CHANGELOG.md](https://github.com/tumblr/collins/blob/master/CHANGELOG.md) file.  Open a PR and get thumbs.  See https://github.com/tumblr/collins/pull/490 for an example PR.
- [ ] Once the changelog PR has been approved and merged into master, publish your release, pointed at the merge commit of your changelog PR.
- [ ] Make sure http://tumblr.github.io/collins/downloads.html is up to date, with correct links to the release zips on github.

# TODO
- https://github.com/tumblr/collins/issues/502 Do we need a separate CHANGELOG.md file and downloads.html github pages site? We should probably just use the github releases page to track our releases - we have the same info in too many places.
