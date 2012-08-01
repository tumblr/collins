---
permalink: configuration.html
title: Configuration
<!--- change this layout type if necessary --->
layout: post 
desc: Documentation for permissions schema 
---

This page provides the nitty gritty details of configurable assets in collins.
In some cases these are better detailed in other parts of the documentation
(such as callbacks) and when that is the case it is pointed out.

# General Configuration

## <a name="overview">Overview</a>

All configuration is done via the `application.conf` or `production.conf`
config file. The name of the file is not important as you can specify which
configuration to use at runtime. The format of this file is similar to a
standard java properties file with the exception that bools (true, false) and
numbers must not be quoted.



## <a name="general">General Configuration</a>
There are several general configuration options that are not neccesarily
specific to any feature but are none the less required for operation.

 * `application.secret` - string, required. A long random secret key to be used for cookie verification. If running multiple collins instances this must be the same for all of them
 * `parsers.text.maxLength` - long, required. Maximum size file upload. Must be large enough to accept LSHW/LLDP inputs.
 * `crypto.key` - string, required. A crypto key used for encrypting passwords in the database. Once you initialize a collins instance and begin using this value, it must not change.

### Examples

    application.secret="SjRlgmfF/0aD3sVf0K,_W[sytUTZ!L^V5)!5BV>=`S3DV3/KEWV3w=3mBYIGoF%f"
    parsers.text.maxLength=1048576
    crypto.key="9e8J@U1+98_\pr*@HOi+]'FIL)5mHM[p5xQPm*N!^b1w`W7@|vV*RTOu=FBxksO"



## <a name="auth">Authentication Configuration</a>

Collins support a small in memory authentication mechanism for testing, file
based authentication, LDAP (RFC2307) based authentication, and LDAP (Linux
IPA, varient of RFC2307) based authentication.

### File Based Authentication

Just what it sounds like. Note that changing user settings currently requires
a restart of collins.

#### Examples

    # Set the type
    authentication.type=file
    authentication.file="/usr/local/collins/users.conf"

The format of the `users.conf` file is a varient on the standard SHA1 based
htpassword file, but includes an additional attribute specifying the groups.
An example file might look like:

    foo:{SHA}WzpwX7X3WRcDhto9feDVwX67jHE=:infra,eng
    bar:{SHA}cZ1dzi02IPtmJg78pePKdrqrwc8=:consultants
    fizz:{SHA}LiyQM4/CuT7RXVyzkLkvqZtynKk=:infra
    buzz:{SHA}2mzKeQUxZngHY6UuM+//VUmN4ug=:eng

A username/password can be generated like:

    htpasswd -n -b -s $username $password

Note that you will need to append the colon followed by a comma separated list
of groups. 

### LDAP (RFC2307) Authentication

This authentication provider is configured to leverage an RFC2307 compatible
LDAP schema to provide authentication and group membership information.

#### Examples

    # Set the type
    authentication.type=ldap
    authentication.host="ldap.example.org"
    authentication.searchbase="dc=corp,dc=example,dc=org"
    authentication.usersub="cn=users"

For authentication the bind will be against:

    uid=username,usersub,searchbase
    or
    uid=jdoe,cn=users,dc=corp,dc=example,dc=org

In the above example the group query will be a subtree scoped search (from the
searchbase) such as:

    (&(cn=*)(memberUid=jdoe))

This search is RFC2307 compatible but not IPA compatible which brings us to
our next authentication provider.

### LDAP (Linux IPA) Authentication

Very similar to the ldap provider but handles group search a bit differently.

#### Examples

    # Set the type
    authentication.type=ipa
    authentication.host="ldap.example.org"
    authentication.searchbase="dc=corp,dc=example,dc=org"
    authentication.usersub="cn=users"

The bind works the same but the group query is of the form:

    (&(cn=*)(member=jdoe))

The internal implementation is not very different between the two providers.






## <a name="cache">Cache Configuration</a>

Specifies default configuration for in memory cache. This is essential for
reasonable collins performance. Caches mostly database queries.

 * `cache.default` - string, required, must be set to disabled. This disables
 the default cache implementation.
 * `cache.class` - string, required, must be "com.tumblr.play.CachePlugin"
 * `cache.timeout` - time string, optional, defaults to "10 minutes" (which is
 reasonable). This dictates how long something can exist in cache before it
 falls out. Good to keep somewhat low in case of any possible cache
 inconsistencies.

### Examples

    # Disable default cache plugin
    cache.default=disabled
    # Enable the tumblr cache plugin
    cache.class="com.tumblr.play.CachePlugin"
    # Set cache time to 20 minutes
    cache.timeout="20 minutes"



## <a name="db">Database Configuration</a>

The database configuration is fairly typical for a JDBC driven application.
Frequently used configuration parameters are:

 * `db.collins.driver` - string, required. The JDBC driver to use.
 * `db.collins.url` - string, required. The JDBC connection parameters to use.
 * `db.collins.user` - string, optional. The username for authentication.
 * `db.collins.password` - string, optional. The password for authentication.

### MySQL Example

Useful for production systems.

    db.collins.driver="com.mysql.jdbc.Driver"
    db.collins.url="jdbc:mysql://localhost/collins?autoReconnect=true&interactiveClient=true"
    db.collins.user="master_collins_user"
    db.collins.password="R8alLn91_alKjK9"

Note in this example the `autoReconnect` and and `interactiveClient` settings.
If you see database disconnect errors in the logs, these are good settings to
use.

### H2 Example

Useful for testing/development.

    db.collins.driver=org.h2.Driver
    db.collins.url="jdbc:h2:mem:play;IGNORECASE=TRUE"
    db.collins.user=sa
    db.collins.password=""
    db.collins.logStatements=true

The `IGNORECASE` setting is important here, otherwise text search is case
sensitive which isn't usually desired.





## <a name="features">Feature Configuration</a>

There are a number of parts of collins that can be customized based on
environment. These are referred to in the distributed configuration as feature
flags. Available feature flags are documented below.

 * `features.intakeSupported` - Boolean, defaults to true. When enabled, if in
 the UI you are in the infra group and search by asset tag for a server that
 is new, you will be dropped into the intake workflow. The intake workflow
 requires that IPMI be setup correctly.
 * `features.hideMeta` - A comma separated list of tags to hide. Defaults to empty. These tags will be hidden in all views (API, UI, etc) but are still searchable. Useful for rollup attributes such as `DISK_STORAGE_TOTAL`.
 * `features.encryptedTags` - A comma separated list of tags to
 encrypt/decrypt. Defaults to empty. This can be enabled at any time and data
 will be lazily encrypted.
 * `features.noLogPurges` - A comma separated list of tags to not log
 deletes/updates on. Defaults to empty. By default every tag change is logged.
 However, some automated systems generate many changes that may not be useful.
 You can use this to not log certain changes. Note that it is possible to
 expose sensitive information so use this wisely.
 * `features.sloppyStatus` - Boolean, defaults to false. By default you can't
 set the status explicitly of an asset, it can only be managed via workflows.
 Because the workflows are basically undefined for some scenarios, this is
 often needed.
 * `features.ignoreDangerousCommands` - A comma separated list of asset tags
 to ignore dangerous commands for. Defaults to empty. Any asset tag listed in
 here can not be powered off, reprovisioned, etc. They are somewhat
 untouchable. This is useful for 'key' assets.
 * `features.deleteMetaOnDecommission` - Boolean, defaults to true. Whether
 all tags should be deleted when an asset is decommissioned. Although this
 defaults to true, it is probably better to set this to false otherwise you
 won't know what an asset used to be. Log data is never purged.
 * `features.deleteSomeMetaOnRepurpose` - A comma separated list of tags to
 delete when an asset is reprovisioned or decommissioned. This list is used if
 `deleteMetaOnDecommission` is false or when a machine is reprovisioned. Most
 useful for tags that should not carry over between provisions such as
 `nodeclass` or `PRIMARY_ROLE`.
 * `features.deleteIpmiOnDecommission` - Boolean, defaults to true. Whether to
 delete IPMI data for an asset once it is decommissioned. True is probably the
 right value but may not be in all environments.
 * `features.deleteIpAddressOnDecommission` - Boolean, defaults to true.
 Whether to delete IP address information when an asset is decommissioned. It
 is most useful to set this to false if you are not using the IP address
 management and allocation features, as the addresses go back to the available
 pool.
 * `features.allowTagUpdates` - Comma separated list of tags that can be
 updated for non server assets. Only applies to managed tags. Allows you to
 for instance, update the `POWER_PORT` of a switch.

### Examples

    # Intake flow not supported in environment
    features.intakeSupported=false
    features.hideMeta="RACK_POSITION, POWER_PORT, DISK_STORAGE_TOTAL"
    # Will not log deletes/updates for the following tags
    features.noLogPurges="CONFIG_SHA, SHA, TUMBLR_SHA, LAST_PUPPET_RUN, SYSTEM_PASSWORD"
    # Encrypt our system passwords
    features.encryptedTags="SYSTEM_PASSWORD"
    # Allow the status of an asset to be set via the API
    features.sloppyStatus=true
    # Don't fuck up collins or puppet
    features.ignoreDangerousCommands="sl-124276,sl-124274,sl-73872"
    # Don't purge all tags on decommission
    features.deleteMetaOnDecommission=false
    # Do however delete the following on reprovision
    features.deleteSomeMetaOnRepurpose="CONFIG_SHA, TUMBLR_SHA, NODECLASS"
    # Do purge IPMI info on decommission
    features.deleteIpmiOnDecommission=true
    # Do not purge IP address information on decommission
    features.deleteIpmiOnDecommission=false


## <a name="ipmi">IPMI Configuration</a>

The IPMI configuration is used to drive generation of IPMI addresses,
usernames and passwords. It is also used for lighting up a server during the
intake process. The configured PowerManagement plugin may use this information
as well.

 * `ipmi.gateway` - string, optional. Used to override the default network gateway. Must be a fully specified IP address.
 * `ipmi.network` - string, required. This value is required whether you are using IPMI generation or not. This should be in CIDR notation (e.g.  `10.0.0.0/24`). Addresses will be incremented sequentially out of this pool. If no `ipmi.gateway` is specified, the network gateway will be used.
 * `ipmi.passwordLength` - int, optional, defaults to 12. The length of any generated IPMI passwords.
 * `ipmi.randomUsername` - boolean, optional, defaults to false. Whether a random username should be generated or not. If this is false, and `ipmi.username` is not set, the generated username will be the asset tag followed by "-ipmi", e.g. `sometag-ipmi`.
 * `ipmi.startAddress` - string, optional. If specified, this is the first address in the IPMI address pool that will be allocated. This is useful for skipping some addresses at the beginning of a range. If unspecified, addresses will be allocated starting one after the appropriate gateway.
 * `ipmi.timeout` - string, optional, defaults to 2 seconds. The amount of time to wait for the command to succeed.
 * `ipmi.username` - string, optional. If `ipmi.randomUsername` is false, and you don't wish to have different usernames, set this. For instance, setting this value to "root" will cause the IPMI username to always be set to root.

### Examples

The below configuration creates IPMI information with the username root,
passwords of length 16, a gateway of 10.0.0.1 on the 10.0.1.1 network but
starts allocating addresses at 10.0.1.10.

    ipmi.randomUsername=false
    ipmi.username="root"
    ipmi.passwordLength=16
    ipmi.gateway="10.0.0.1"
    ipmi.network="10.0.1.1/24"
    ipmi.startAddress="10.0.1.10"
    ipmi.timeout="5 seconds"


## <a name="ipAddress">IP Management Configuration</a>

You can also use collins as a basic IPAM solution. It is possible to create
multiple allocation pools (private, public, etc). The configuration attributes
are similar to the ones found in the IPMI section because there is a large
portion of shared code between the implementations. Configuration at least one
pool is required to do any kind of IP address allocation.


 * `ipAddresses.<pool>.gateway` - string, optional. Gateway to override the
 default for the network.
 * `ipAddresses.<pool>.network` - string, required. CIDR notation for network
 to allocate out of.
 * `ipAddresses.<pool>.startAddress` - string, optional. IP address in network
 to start allocating from. Must be a full IP address.

Each `<pool>` must have a different name. This pool name is used when
allocating addresses using the IP Management API. It is also possible to
specify no pool name (e.g. `ipAddresses.gateway`) in the case where you only
have a single pool to allocate from. In this case the pool name does not need to
be specified when using the API.

### Examples

The below example sets up 3 address allocation pools. One named public, one
named backend, and one named management. The public pool specifies a /24 that
will start allocating at `4.4.4.5`. The backend pool specifies a /21 that
will begin allocating at `172.16.20.10`. The management pool specifies a /21
that will begin allocating at `172.16.24.5`.

    # Public Network Allocation
    ipAddresses.public.network="4.4.4.0/24"
    ipAddresses.public.startAddress="4.4.4.5"
    # Backend Network
    ipAddresses.backend.network="172.16.20.0/21"
    ipAddresses.backend.startAddress="172.16.20.10"
    # Management Network
    ipAddresses.management.network="172.16.24.0/21"
    ipAddresses.management.startAddress="172.16.24.5"

You can then allocate an address out of, for instance the backend network, by
doing a `PUT /api/asset/:tag/address?pool=backend`.




## <a name="lshw">LSHW Parsing Configuration</a>

There are a couple of options specific to how the LSHW XML is processed. Both
are related to how PCI flash devices are recognized and sized. This is only
used during the hardware intake process but is important to ensure that flash
devices are properly detected and recognized as storage devices.

 * `lshw.flashProduct` - string, optional. The product description of the flash
 device in use. Be as specific as possible without being so specific that the
 device can't be recognized (e.g. leave out version info, years, etc).
 * `lshw.flashSize` - string (in bytes), optional. The size of the device
 detected by the `flashProduct` string. This is needed because LSHW does not
 correctly size these devices.

### Examples

The following will detect a Virident PCIe flash card and size it at 1.4TB.

    lshw.flashProduct="flashmax"
    lshw.flashSize="1400000000000"

In future versions this will likely need to be expanded to cover additional
models or generations of flash devices.

## <a name ="multicollis">Multi Collins Configuration</a>

Collins can use a multi-master topology for multiple data centers. Each
Collins instance maintains its own local database of assets and fans out
searches to remote instances, aggregating the results. In the webapp search
results list page, assets from remote data-centers will link to the collins
instance for that data-center and not the asset itself.

In order to set up collins for multiple datacenters, the following configuration
settings will need to be set:

 * `multicollins.enabled` - boolean. Set to "true" to enable multi-datacenter 
    searching
 * `multicollins.instanceAssetType` - string, required. Name of the asset type
    that represents a remote datacenter. Default "DATA_CENTER".
 * `multicollins.locationAttribute` - string, required. The name of asset_meta
    tag that holds the connection data for the remote datacenter. Default
    "LOCATION". Format "http://\<hostname\>[:port];\<username\>:\<password\>".
 * `multicollins.thisInstance` - string, required. Tag the data-center asset
    that represents this instance of collins. Default "localhost".

### Examples

The following example sets up an instance of Collins to interact with other Collins

    multicollins.enabled=true
    multicollins.instanceAssetType = "DATA_CENTER"
    multicollins.locationAttribute = "LOCATION"
    multicollins.thisInstance = "localhost"

## <a name="tags">Tag Decorators</a>

As is specified in other parts of the documentation (see the section in the
functions page), it is possible to specify tag decorators. Tag decorators do
what they sound like, they decorate (or mark up) tags. This is useful for for
instance displaying an image or graph instead of just the URL, or linking to a
github page, or linking to searches or other resources.

Tag decorators have a format similar to the IP Address Management
configuration. They have a common key (`tagdecorators`), a tag (e.g.
`DATACENTER`), and then arguments specific to the decorator. Available
arguments include:

 * `decorator` - string, required. A string with the text representation of
 how the tag value should be decorated. Substituions available in the string
 include `{name}` (the name of the tag, e.g. `DATACENTER`), `{value}`
 (the value associated with the tag), `{i.label}` (the label associated with
 the iteration).
 * `valueParser` - string, optional. Defaults to `IdentityParser`, which just
 returns the value unchanged. The other available alternative is
 `util.views.DelimiterParser` which will parse the value using a specified
 delimiter. Using the `DelimiterParser` makes the `delimiter` argument
 available.
 * `delimiter` - string, required if `valueParser` is `DelimiterParser`. The
 token to use for parsing a value into multiple values.
 * `between` - string, optional, defaults to an empty string. This is
 available when `valueParser` is `DelimiterParser` or if the tag is
 multi-valued (such as `IP_ADDRESS`). The value is used between each of the
 formatted results. So for instance setting `between` to ` - ` would create a
 hyphen between each value.
 * `0.label` - string, optional. Only useful when dealing with a multi-valued
 tag or are using the `DelimiterParser`. This allows you to set a label for a
 particular iteration of a value which will be available for substitution in
 the decorator (`{i.label}`).

None of that probably makes much sense, examples are much more useful here.

### Basic Examples

Creates links to a specific github commit. The name of the tag that will match is `CONFIG_SHA` and `{value}` will be replaced with the value of the SHA associatied with that tag/asset.

    tagdecorators.CONFIG_SHA.decorator="<a target=\"_blank\" href=\"https://github.com/tumblr/config/commit/{value}\">{value}</a>"

Create a collins search for other assets in the same datacenter. In this case the name of
the tag that will match is `DATACENTER`, and `{value}` will be replaced
with the text value of the datacenter and `{name}` will be replaced with
the tag name (which is `DATACENTER`).

    tagdecorators.DATACENTER_NAME.decorator="<a href=\"/resources?attribute={name}%3B{value}\">{value}</a>"


### Delimiter Parser Example

Create image HTML from the values associated with the GRAPHS tag. In the
following example, assume some asset has a GRAPHS tag with a value like

    http://monitor.example.com/server.png;http://monitor.example.com/server2.png

To convert this value to a br separated list of image tags you would use the
following configuration:

    tagdecorators.GRAPHS.decorator="<img src=\"{value}\">"
    tagdecorators.GRAPHS.valueParser="util.views.DelimiterParser"
    tagdecorators.GRAPHS.delimiter=";"
    tagdecorators.GRAPHS.between="<br>"

The previous configuration would convert that in the asset details view into
something like

    <img src="http://monitor.example.com/server.png"><br><img src="http://monitor.example.com/server2.png">

### Delimiter Parser with Labels

Say you have some data associated with a `STATS_LINKS` tag such as

    http://ip:8080/stats.txt http://ip:8081/stats.txt

and want to convert that to a hyphen separated list of links with labels. The
following config will parse the value:

    tagdecorators.STATS_LINKS.decorator="<a target=\"_blank\" href=\"{value}\">{i.label}</a>"
    tagdecorators.STATS_LINKS.valueParser="util.views.DelimiterParser"
    tagdecorators.STATS_LINKS.delimiter=" "
    tagdecorators.STATS_LINKS.between=" - "
    tagdecorators.STATS_LINKS.0.label="Thrift"
    tagdecorators.STATS_LINKS.1.label="HTTP"

and produce a new value that looks something like:

    <a target="_blank" href="http://ip:8080/stats.txt">Thrift</a> - <a target="_blank" href="http://ip:8081/stats.txt">HTTP</a>

How this works is the delimiter parser splits the value on a space (specified
by the delimiter argument), and then iterates over each of the values after
the split (2 in this case). On each iteration the decorator is applied using
the label specified for that particular iteration (0 indexed, this isn't nam).
The decorator uses this `{i.label}` substitution, and the configuration
specifies that when `i` is 0, the label should be Thrift and when `i` is 1,
the label should be HTTP.

### Multi Valued Examples

Some tags are multi valued. Only managed tags, and some 'special' ones (such
as `IP_ADDRESS`) are multi valued. A multi-valued tag is a tag that has more
than one value. While most tags are simple key/value associations, some tags
can actually be key/List(values).

Say we wanted to provide an SSH link for each IP address allocated to a host.
This is fairly easy to accomplish with the following configuration.

    tagdecorators.IP_ADDRESS.decorator="<a href=\"ssh://{value}\">{value}</a>"
    tagdecorators.IP_ADDRESS.between=", "

The above produces a list of links that are separated by a comma and space.
Note that we can use `between` here, even though we're not using the
`DelimiterParser`, because we have multiple values.

# Plugin Configuration

Note that each plugin must be enabled before it can be configured. To enable a
plugin, simply add `<plugin name>.enabled` to the configuration.

## <a name="callbacks">Callback Plugin</a>

The callback plugin is adequately documented <a
href="callbacks.html">here</a>.

### Example

Say you wanted to be emailed when an asset goes into Maintenance mode. This is
fairly easy.

First, we enable the plugin as follows:

    callbacks.enabled=true

Next, we create a new callback, named `intoMaintenance`, that watches for
asset updates:

    callbacks.callback.intoMaintenance.event="asset_update"

Next, we create matchMethod's on the current and previous values to indicate
that we are interested in assets that were previously not in maintenance but
now are in maintenance

    callbacks.callback.intoMaintenance.previous.matchMethod="!isMaintenance"
    callbacks.callback.intoMaintenance.current.matchMethod="isMaintenance"

Now, we specify what asset attribute to retrieve if the previous and current
values match. In this case we want the asset tag so we use the following
config:

    callbacks.callback.intoMaintenace.matchMethod="tag"

Last, we specify the action to take in the event of a match. Note that what
ever method was specified as our matchMethod is available for substitution in
the match action.

    callbacks.callback.intoMaintenance.matchAction="exec /tmp/email_me --tag=<tag>"

And that's it. Your complete example:

    callbacks.enabled=true
    callbacks.callback.intoMaintenance.event="asset_update"
    callbacks.callback.intoMaintenance.previous.matchMethod="!isMaintenance"
    callbacks.callback.intoMaintenance.current.matchMethod="isMaintenance"
    callbacks.callback.intoMaintenace.matchMethod="tag"
    callbacks.callback.intoMaintenance.matchAction="exec /tmp/email_me --tag=<tag>"




## <a name="power">Power Management Plugin</a>

The power management plugin allows you to perform power related actions on an asset such as power off and on, power cycle, check power status, and so on.

 * `powermanagement.enabled` - must be true if you wish to use powermanagement functions
 * `powermanagement.class` - use `util.plugins.IpmiPowerManagement` or a custom plugin
 * `powermanagement.disallowStatus` - optional comma separated list of status types that can not be power managed. Defaults to all (which means you have to specifically specify 1 or more here). Using Allocated is a good call.
 * `powermanagement.allowAssetTypes` - optional comma separated list of asset types that can be power managed. Defaults to `SERVER_NODE`.
 * `powermanagement.disallowWhenAllocated` - optional comma separated list of power actions to disallow if the asset is allocated. Possible values are `powerOff`, `powerOn`, `powerSoft`, `powerState`, `rebootSoft`, `rebootHard`, `identify` and `verify`.

Depending on the power management plugin that's enabled, additional
configuration arguments may be required. The SoftLayer plugin is also a power
management provider, and one will need to be created for doing strictly IPMI
power management.

Verify is intended to be used to verify that IPMI is available, to distinguish between connectivity and IPMI errors.

If you are using the `util.plugins.IpmiPowerManagement` plugin, you must also configure how to run each of the power actions. The exit code dictates success (exit code 0) or failure (non-zero).

### Examples

    powermanagement.enabled=true
    powermanagement.class="util.plugins.IpmiPowerManagement"
    powermanagement.disallowStatus="Allocated" # Disallow allocated machines to be power cycled via UI
    powermanagement.disallowWhenAllocated="powerOff"
    powermanagement.powerOff="ipmitool -H <host> -U <username> -P <password> -I lan -L OPERATOR chassis power off"
    powermanagement.powerOn="ipmitool -H <host> -U <username> -P <password> -I lan -L OPERATOR chassis power on"
    powermanagement.powerSoft="ipmitool -H <host> -U <username> -P <password> -I lan -L OPERATOR chassis power soft"
    powermanagement.powerState="ipmitool -H <host> -U <username> -P <password> -I lan -L USER chassis power status"
    powermanagement.rebootHard="ipmitool -H <host> -U <username> -P <password> -I lan -L OPERATOR chassis power cycle"
    powermanagement.rebootSoft="ipmitool -H <host> -U <username> -P <password> -I lan -L OPERATOR chassis power reset"
    powermanagement.identify="ipmitool -H <host> -U <username> -P <password> -I lan -L OPERATOR chassis identify <interval>"
    powermanagement.verify="ping -c 3 <host>"

## <a name="provisioner">Provisioner Plugin</a>

The provisioner plugin adds asset provisioning capabilities to collins. There
is currently only one plugin implemented which has the following required
arguments:

 * `provisioner.enabled` - boolean, must be true to enable.
 * `provisioner.profiles` - string, required. File location with all provisioning profiles.
 * `provisioner.command` - string, required. The command to execute assuming the checkCommand succeeds.
 * `provisioner.checkCommand` - string, required. A command that should exit 0 if the asset is sane to provision. Any failure data can be handed back to the end user by outputting data to stdout or stderr.
 * `provisioner.allowedStatus` - string, required. A comma separated list of status values that allow an asset to be provisioned. Using `2, 5, 9` (unallocated, maintenance, provisioned) usually makes sense.
 * `provisioner.rate` - string, optional. The number of successful requests per time-period that a user can make. Specified as `#/duration` where `#` is an integer and `duration` is the duration. Examples: `1/30 seconds` means once per 30 seconds, `5/1 minute` means 5 per minute (the same as `1/20 seconds`). Defaults to `1/0 seconds`, (no rate limit). `0/0 seconds` means disallowed.

The provisioner plugin re-reads the profiles file every 10 seconds to reflect
any changes. The profiles list dictates both what is displayed to the end user
doing the provisioning as well as what options are available to the end user
or required by the end user. An example provisioner profile is listed below.

### Substitutions in Command and Check Command

The following substitutions are available in the command/checkCommand arguments:

 * `<logfile>` - Defaults to `/tmp/<tag>-<profile-id>.log`
 * `<notify>` - The contact information supplied by the user. Probably their logged in username.
 * `<profile-id>` - The unique identifier of the profile to use (the key in the profiles array in the provisioner profiles file)
 * `<suffix>` - Any suffix to append to the hostname, specified by the user
 * `<tag>` - The asset tag

### Example Configuration

    # Provisioner Plugin
    provisioner.enabled=true
    provisioner.profiles="/usr/local/collins/provisioner.yaml"
    provisioner.command="/opt/ruby-1.9.2/bin/provisioner --debug --config=/usr/local/collins/provisioner.yaml --tag=<tag> --profile-id=<profile-id> --notify=<notify> --suffix=<suffix> --logfile=<logfile>"
    provisioner.checkCommand="/opt/ruby-1.9.2/bin/provisioner --debug --config=/usr/local/collins/provisioner.yaml --tag=<tag> --profile-id=<profile-id> --dry-run"
    provisioner.allowedStatus="2,5,9" # unallocated, maintenance, provisioned

In the above example note the use of the `--dry-run` option in the `checkCommand`.

### Example Provisioner Profile File

The provisioner profiles file contains a list of available profiles for
provisioning. The keys (databasenode, devnode, webnode) are automatically set
to be the nodeclass tag of the asset in collins. This key is also referenced
above as `<profile-id>` and is how the provisioner process knows what
configuration to use.

    :profiles:
        databasenode:
            label: "Database Server"
            prefix: "db"
            primary_role: "DATABASE"
            requires_pool: false
        devnode:
            label: "Dev Machine"
            prefix: "dev"
            allow_suffix: true
            primary_role: "DEVEL"
            pool: "DEVEL"
        webnode:
            label: "Web Server"
            prefix: "web"
            requires_secondary_role: true

The keys/values associated with each profile id are:

 * `label` - string - The label to show in the UI. Should be unique.
 * `prefix` - string - The prefix to use in the hostname. This (semi-readable hostnames) helps with viewing error logs and such.
 * `primary_role` - string - The primary role to set on the asset in collins. The primary role drives automation and other tasks. If this value is set, the user can not select it.
 * `secondary_role` - string - The secondary role to set on the asset in collins. If this value is set, the end user can not select it.
 * `pool` - string - The pool to set on the asset in collins. If this value is set, the end user can not select it.
 * `requires_primary_role` - boolean - Whether a primary role is required or not, defaults to true. If `primary_role` is not specified, the user must select one. If this is set to false, the user does not have to select one.
 * `requires_secondary_role` - boolean - Whether a secondary role is required or not, defaults to false. If `secondary_role` is not specified and this value is true, the user must specify one. If this value is false, the user is not required to specify the value.
 * `requires_pool` - boolean - Whether a pool is required or not, defaults to true. If `pool` is not specified and this value is true the user must specify one.
 * `allow_suffix` - boolean - Whether a user may specify an additional suffix for the hostname, defaults to false. If this is set to true, and the user for instance specifies a value of "foo" and the prefix was set to "dev", the generated hostname will be something like "dev-foo-hash".

## <a name="softlayer">SoftLayer Plugin</a>

The SoftLayer plugin provides support for cancelling assets, activating assets
from the spare pool, and a few other decoration related features (links to SL
portal for assets, etc).

Required plugin arguments:

 * `softlayer.username` - string, required - SoftLayer API username
 * `softlayer.password` - string, required - SoftLayer API password
 * `softlayer.allowedCancelStatus` - string, optional, defaults to all status
 values. Dictates what status an asset can be in in order to be cancelled. A
 reasonable default is `Unallocated,Allocated,Maintenance`.

When an asset is cancelled a cancel ticket is filed with SL and the asset is
moved into a cancelled status.

When an asset is activated the activation request is made to the SL API and
the asset is moved from Incomplete to New (and then again moved from New to
Unallocated once available - this is driven by the reconciler).

### Examples

Again, pretty straight forward.

    softlayer.enabled=true
    softlayer.username="jimbob123"
    softlayer.password="lkajsd09123lkajsd09123lkajsdlk123098asdlkj123098asdlkjasd098123s"
    softlayer.allowedCancelStatus="Unallocated,Allocated,Maintenance"

This plugin also is currently the only power management plugin (it serves
double duty).
