---
permalink: configuration_old.html
title: Configuration
layout: config 
desc: Configuration Options in Collins
---

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


