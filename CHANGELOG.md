# 2.2.0 2017/10/11

This version of Collins includes an important security patch, as well as several new features and bug fixes.

The security patch is adding CSRF protection to the various forms of the Collins web UI. Currently, if an attacker can guess (or bruteforce) the asset tags of nodes he or she would be able to create assets, decommission assets, put assets in maintenance, etc. by getting a logged in user to visit a webpage. More information can be found in the [pull request (#570)](https://github.com/tumblr/collins/pull/570).

Here is the full list of merged pull request since the last release. Many thanks to everyone who contributed!

- Trying to revive the tests #491 @discordianfish
- Fix "respond_to? is old fashion" warning #501 @william-richard
- Only query allocated remote assets #498 @william-richard
- Added docs explaining how to cut a collins release #503 @william-richard
- Add ipmitool to container #506 @michaeljs1990
- Fix relative path to collins-client in collins-shell bin #504 @ssgelm
- Add EXTRA_OPTS for extra java options which do not fit other types #507 @vhp
- pin rake for collins-state gem #516 @byxorna
- Make base_serial optional in LSHW parsing #517 @byxorna
- make default docker permissions.yaml synchronized with latest changes #494 @byxorna
- make vlan-id attribute optional in lldp #523 @byxorna
- Fix unit tests for CI travis #531 @byxorna
- AddressPool name conversion should always handle defaultPoolName correctly #527 @byxorna
- IPMI network allocations API with pool support #513 @byxorna
- Add IPMI pools to /api/address/pools endpoint #521 @michaeljs1990
- Add Classification to asset API #538 @michaeljs1990
- Show overview broken out by interface capacities #548 @byxorna
- Tuning for Solr to improve indexing latency #529 @byxorna
- Add gpu support #537 @jyundt
- Adding methods to python client. #456 @ytjohn
- Fix ipmi pool api #554 @michaeljs1990
- Dynamic Enum Fix #547 @michaeljs1990
- Fix duplicate getLshwValues definition #560 @byxorna
- Add logo and favicon #563 @defect
- Add support for NVMe disks in LSHW #565 @defect
- Don't show Graphs tabs if plugin is disabled #556 @defect
- CSRF protection for web forms #570 @defect

# 2.1.0 2016/11/17

Collins 2.1.0 has a very important security patch.

Collins has a feature that allows you to [encrypt certain attributes](http://tumblr.github.io/collins/configuration.html#features) on every asset.  It also had a permission that restricted which users could read those encrypted tags.  It did NOT have a permission that restricted which users could modify encrypted tags.

*It is strongly recommended that you upgrade to collins 2.1.0 if you are using the encrypted tags feature, as well as rotate any values stored in encrypted tags.*

The severity of this vulnerability depends heavily upon how you use collins in your infrastructure.  If you do not use the encrypted tags feature, you are not vulnerable to this problem.  If you do use the encrypted tags feature, you will need to explore your automation and consider how vulnerable you are.

If, for example, your infrastructure has automation that regularly sets the root password on servers to match a value that is in collins, an attacker without the ability to read the current password could set it to a value that they know, wait for the automation to change the password, and then gain root on a server.

This change is backwards compatible with collins v2.0.0, though once you upgrade it will stop any writes to encrypted tags by users that have not been granted `feature.canWriteEncryptedTags` permission.  We have also renamed `feature.canSeePasswords` to `feature.canSeeEncryptedTags`, but collins will continue to respect the value of `feature.canSeePasswords` if `feature.canSeeEncryptedTags` is not set.  Once `feature.canSeeEncryptedTags` is set, collins will ignore the value of `feature.canSeePasswords`.

Full set of changes:

- Ensure that we build only with java 1.7 #473 @Primer42
- Write encrypted tags permission #486 @Primer42

# 2.0.0 2016/09/19

Collins 2.0.0 is finally released!  As of this release, we will start following semantic versioning (http://semver.org/).  There
have been some non-backwards compatible changes to collins' functionality and configuration settings, but nothing that will
be too difficult to upgrade.

Here are some highlights of what has changed since the last release:

- Event firehose
- Refactor of collins' caching logic, to safely support HA
- Improved LDAP authentication configuration
- Python collins client
- Consolr gem, for executing IPMI commands on collins assets
- Upgraded to play 2.3.9

Thanks to @MaximeDevalland, @Primer42, @andrewjkerr, @baloo, @byxorna, @davidblum, @defect, @funzoneq,
@gtorre, @maddalab, @schallert, @sushruta and @unclejack for their contributions!

And here are all the pull requests included in this release, in the order they were merged to master

- Ipmi validation #309 @maddalab
- Fix collins-notify for ruby 2.2.0 #310 @byxorna
- my bad, i didnt build the gem after adopting feedback #311 @byxorna
- collins_client clean up #307 @defect
- Fix the error message displayed when login fails #314 @maddalab
- Fix issue with invoking authentication twice #316 @maddalab
- Remove use of async result during asset cancel request #319 @maddalab
- Minor: A Two-Tuple of Options where only one of the tuple elements is Some at a time is an either #320 @maddalab
- Update play to 2.3.9 #322 @maddalab
- Minor: Upgrade solr and httpcomponents version #321 @maddalab
- Set IPMI Password minLength to 4 #327 @MaximeDevalland
- Use a hostname deploy (useful for standbys) #328 @maddalab
- Minor: Styling using bootswatch themes #325 @maddalab
- Use the tryAuthCache method to avoid making auth queries continously. #332 @maddalab
- Fix the color scheme #333 @Primer42
- cleanup the dockerfile some #334 @byxorna
- Bug: Avoid recursive (stack overflowing) error with ldap auth #335 @maddalab
- Update README.md to use correct GitHub Pages URL #336 @andrewjkerr
- Rethink use of Guava cache using play's plugin architecture #337 @maddalab
- added input search field in top bar #330 @MaximeDevalland
- fixed input search, was returning all results #343 @MaximeDevalland
- fix asset_log.created_by to be varchar(255) #342 @byxorna
- Changes to search bar #344 @maddalab
- Added a new gem - consolr wrapping on top on IPMI Tool #346 @sushruta
- Ensure only 1 instance of auth provider is ever created #348 @maddalab
- api delete endpoint #349 @gtorre
- Fixing relative paths and missing bracket #351 @funzoneq
- Reintroduce caching into models #350 @maddalab
- Handle leading/trailing white space in top search bar field. #352 @maddalab
- Only fetch the required fields from solr. #353 @maddalab
- Adding additional stats. #354 @maddalab
- Instrumenting with more stats #355 @maddalab
- Instrumenting with stats around the serialization of json. #356 @maddalab
- Minor cleanups #357 @maddalab
- Fix collins-client gem unit tests #361 @gtorre
- travis improvments #360 @Primer42
- Consolr dangerous asset behavior and unit tests #359 @Primer42
- Will fix use whitelist on repurpose de base #365 @Primer42
- Introducing hazel cast for clustered operation of collins #367 @maddalab
- Fix some specs that were not using the right scope #368 @maddalab
- Addressing issue with specification scope for a couple of tests. #369 @maddalab
- Running the cache spec for both In-memory and Distributed  #370 @maddalab
- adding unit tests for delete (nuke) action #372 @gtorre
- Added scoverage based coverage reports.  #373 @maddalab
- bump versions in build.sbt, h2, solr, mysql-connector, snakeyaml, jsoup, bootstrap #374 @maddalab
- Address the setting of attributes and handling of whitelisted attribs when provisioning #376 @maddalab
- Minor tweaks from changes to support useWhitelistOnRepurpose. #377 @maddalab
- Implement a firehose for events #379 @maddalab
- Enable asset distance test #381 @maddalab
- Upgrade activator to 1.3.6 from 1.3.4 #382 @maddalab
- [ipmi] allow templating of asset tag in config #386 @schallert
- Gabe optional ipmi power restrictions #388 @byxorna
- update dockerfile to jdk8 and cleanup build #390 @byxorna
- Refactor/scalaish #392 @baloo
- Include solr query string in cache key #396 @defect
- Revert "Include solr query string in cache key" #400 @defect
- fix timeout deprecation warning in collins client request #406 @byxorna
- add volume for solr cores #402 @byxorna
- Make remote query cache timeout configurable #401 @defect
- Fix multi-collins queries #407 @defect
- Collins-shell fix dependencies #408 @Primer42
- format provisioning errors less shittily #405 @byxorna
- Consolr updates #412 @defect
- Travis runs are having trouble with the http -> https redirect #420 @Primer42
- Remove toplevel parameter from solr #397 @defect
- change all instances of 2015 to 2016 #413 @sushruta
- Add support for sensor reading #423 @defect
- add a flag for consolor to print SOL info #424 @sushruta
- Gracefully handle when HOME is not set by making consolr bypass those config files #425 @Primer42
- Fix minor typo in my last consolr PR #426 @Primer42
- Dockerfile: use the JRE image, not JDK #419 @unclejack
- [consolr] readme: update reference config #431 @schallert
- Updated minAddress to respect startAt #432 @davidblum
- add /usr array of java_home locations #440 @davidblum
- Add a parameter to disable the multicollins cache #437 @Primer42

# 1.3.0 2014/09/10

- Moved to Play 2.0.8
- Tumblr supported Docker image
- Reworked and greatly improved init script
- Monitoring plugin
- Open sourced collins-auth ruby gem
- Unit test improvements
- Customizable intake page fields
- Provisioning profile contact and contact_notes fields, and ability to set or remove arbitrary attributes based on provisioning profile
- IP allocation improvements
- Removed IP allocation caching layer
- Mixed authentication modes
- Added new API for asset type
- Improved solr integration for external solr instances
- Restrict provisioning based on hardware configuration

Special thanks to @discordianfish @matthiasr @dallasmarlow @rednuopxivrec @skottler and @asheepapart for their contributions!

And here are all the pull requests in this release, in no particular order

* Gabe dockerfile #208 @byxorna
    * Gabe portable init #209 @byxorna
* remove daemonize from build, isnt necessary #210 @byxorna
* Remove logging if the config is missing #218 @Primer42
* document docker usage #211 @byxorna
* fix race when service doesnt open application.log fast enough #212 @byxorna
* Update quickstart ids #213 @Primer42
* allow multiple product strings to be matched for flash disk detection #132 @byxorna
* add documentation for lshw.flashProducts #133 @byxorna
* Upstart scripts for collins #125 @funzoneq
* populate changelog with 1.2.4 release #137 @byxorna
* Provisioning profiles support for contact and contact_notes fields #134 @byxorna
* Open sourcing collins_auth #141 @funzoneq
* Fix Dockerfile #139 @discordianfish
* fix collins-auth prompt #143 @dallasmarlow
* fix reprovision action to not fail if contact is empty #140 @byxorna
* optional user submitted config file #142 @dallasmarlow
* Show asset classification in overview page #147 @byxorna
* Ignoring some extra files that emacs and eclipse has put in my collins repo #148 @Primer42
* remove Ruby version clamping for collins-shell #150 @matthiasr
* update docs for additional display name nodeclassifier attribute #151 @byxorna
* POSIX formatted attributes are now all caps, fix tests to reflect that #153 @byxorna
* Gabe extra provision attributes #154 @byxorna
* Will update gems #144 @Primer42
* Gabe provision hardware restrictions #155 @byxorna
* docs for profiles.yaml #156 @byxorna
* fix misspelled required attribute #162 @byxorna
* play 2.0.8 #163 @byxorna
* Support play 2.0.8 #160 @byxorna
* gracefully handle missing ENV['HOME'] var #161 @rednuopxivrec
* add a bit of context when meta attributes dont validate #157 @byxorna
* Solr configuration tunables #165 @byxorna
* add documentation for new solr tunables #166 @byxorna
* Gabe tag decorators #167 @byxorna
* fix breakage in solr test spec #170 @byxorna
* fix getNextAvailableAddress to use local maximums instead of just last a... #168 @byxorna
* Bump collins-shell version #169 @Primer42
* Configurable parameters for intake page deux #172 @byxorna
* Configurable parameters for intake page #98 @jmackey
* remove minimum Contact length #201 @matthiasr
* (script/package) set -e to prevent the build from proceeding after a step fails #203 @skottler
* Gabe monitoring plugin #199 @byxorna
* remove AddressPool cache #195 @byxorna
* disable graphs pill when asset is not graphable #200 @byxorna
* Add tests for PowerManagementConfig #92 @asheepapart
* add some fields to asset_meta that are useful #198 @byxorna
* Fix update to 409 when setting IPMI address to conflicting IP #194 @byxorna
* Gabe assettype docs #197 @byxorna
* fix incorrect curl #196 @byxorna
* move collins-shell into support/ruby #193 @byxorna
* add capability to create asset types via API #191 @byxorna
* Mixed authentication modes #101 @asheepapart
* recognise disks with full-disk LVM #192 @matthiasr
* Evolve db flag #159 @asheepapart
* Allow sysconfig to override more defaults #158 @asheepapart
* Remove the youtube link from pages too #189 @Primer42
* Remove youtube screencast, because the link is broken, and I can't find ... #188 @Primer42
* Fix unit tests #185 @maddalab
* Gabe fix ipallocation #181 @byxorna
* Bhaskar cleanups #179 @maddalab
* Avoid querying the database for an asset when adding an asset to the lis... #180 @byxorna
* Gabe more collinsauth fixing #177 @byxorna
* Added note about problems with net-ssh versions > 2.8.0 #171 @Primer42
* fix for collins user with nologin #219 @byxorna

# 1.2.4 2014/03/07

* Docs: Various documentation/labeling fixes ( #95, #94, #99, #103, #104)
* Bug: vlan names can be optional (Chris Burroughs #93)
* Bug: squeryl session cleanup and updated deployment automation (Dallas Marlow #109)
* Build: Upgraded to play 2.0.4, to handle a UTF8 issue (Dallas Marlow #108)
* Build: Upgraded bonecp (Dallas Marlow #110)
* Metrics: Added Metrics support (Chris Burroughs #86)
* Housekeeping: Added dockerfiles, so users can build and run Collins with Docker, if they choose to (Johannes 'fish' Ziemke #111)
* Housekeepin: Minor script fixes (Will Richard and Brent Langston #97 and #113)
* Bug: Trim whitespace from strings before sending them to solr, to get more accurate results (Will Richard #115)
* UI: Update Bootstrap link in docs footer (Chris Rebert #119)
* Bug: Don't parse config yml files if plugins are disabled (Gabe Conradi #122)
* LSHW: Allow LSHW and LLDP updates in more states (Gabe Conradi #123)
* Support: Accept collins asset state when doing a state update or state delete (Dallas Marlow #124)
* Housekeeping: Created a 'contrib' directory for helpful script for running and maintaining collins (Will Richard & Gabe Conradi #126)
* API: Ensured that variables provided when using text/x-shellscript API endpoint are valid POSIX (Will Richard & Gabe Conradi #129)

# 1.2.3 2013/04/24

* LSHW: Allow a default speed to be specified via defaultNicCapacity (Benjamin VanEvery #91)
* Bug: Evolution 11 autoinc should work with MySQL and H2 (Benjamin VanEvery #90)
* Bug: Exact match search when dropdown used in UI (Chris Burroughs #88)
* UI: Bookmarkable tabs and working logs refresh button in asset view (Chris Burroughs #87)
* Docs: Document ganglia graphing config (Chris Burroughs #84 and #85)
* UI: Display dimension of attribute in asset view (Chris Burroughs, Blake Matheny #83 and #79)
* Logging: Better LDAP failure messages (Chris Burroughs #79)
* LSHW: Include server description, vendor, etc during intake (Chris Burroughs #77)
* Graph: Ganglia GraphView support (Chris Burroughs #76)
* Shell: Support for size and threads parameter for batch operations (Blake Matheny #72)
* LSHW: Handle ghost CPUs in LSHW output (Chris Burroughs #70)

# 1.2.2 2013/02/13

* Search: Refactored solr code, added log search API endpoint
* LDAP: Config now supports a 'schema' of rfc2307 or rfc2307bis (default)
* Search: Index decommissioned assets
* Bug: CQL parser should properly handle all quotes
* LSHW: Support for B.02.16 (thanks Johannes Ziemke)
* Bug: IP Address now retrieved for config assets (#47)
* Bug: Don't purge intake data on LSHW update (#55)
* Client: set_multi_attribute allows multiple updates, file upload support via '@'

# 1.2.1 2012/11/01

* Build: Created install tool (scripts/setup) for initial setup
* Build: Made package.sh script a bit more correct
* Build: Starter config for use when packaging a release
* Deps: Remove snakeyaml-SNAPSHOT, code was integrated upstream
* Docs: Added ChangeLog
* Config: Fixed default ipmi configuration. Thanks Chris Graf

# 1.2.0 2012/10/31

* initial release
