---
permalink: callbacks.html
title: Callbacks
layout: post
desc: Callback Mechanism in Collins
---

Collins has a basic framework for taking actions when certain triggers are
matched. This callback framework supports the following kinds of use cases:

 * Email me when a server moves from provisioning to provisioned
 * Send me an IM when any server has an emergency logged
 * POST to a URL when a configuration asset is modified

This mechanism allows you to subscribe to events of a particular type,
optionally match the event based on matchers applied to the previous or
current value, and take some action using the current value when the event has
a match.

The callbacks are currently maintained in the application configuration
however moving them to the backend data store would be fairly trivial and may
happen in the future.

## Event Types

Events are primarily tied to data models but can easily be added for other
types of events. Current implemented ones include:

 * `asset_create` and `asset_update` - Fired when assets are created or updated
 * `asset_log_create` - Fired when logs are created
 * `asset_value_update` - Fired when tags are changed

These are the known as event types and are specified in your callback
configuration as:

    callbacks.callback.$NAME.event="$EVENT_TYPE"

Where `$NAME` is the unique name of the callback (e.g. emailWhenProvisioned,
imOnEmergency, httpPostIfConfigModified, etc) and `$EVENT_TYPE` is one of the
listed event types.

## Event Matchers

Each event has some implicit data type tied to it. For example, `asset_create`
and `asset_update` are both tied to an asset. Matchers can leverage any of the
methods in the data type that return a boolean value. `asset_log_create` for
example can leverage `isEmergency`, `isAlert`, `isCritical`, etc.

Matchers can be applied to the previous value (in the event of an update), the
current value, neither, or both.

Here is an example matcher that matches if an asset changes from
`Provisioning` to `Provisioned`:

    callbacks.callback.nowProvisioned.event="asset_update"
    callbacks.callback.nowProvisioned.previous.matchMethod="isProvisioning"
    callbacks.callback.nowProvisioned.current.matchMethod="isProvisioned"

The above configuration creates a callback named `nowProvisioned` that is
subscribed to `asset_update` events. The callback only matches if the previous
status of the asset was `Provisioning` (in which case `isProvisioning` on the
old value will return true) and the current status of the asset is
`Provisioned` (in which case `isProvisioned` on the current value will return
true). To match all asset updates, just leave off the previous/current
`matchMethod` parameters. It is also possible to negate the `matchMethod` by
prefixing it with a `!`. See the <a href="configuration.html">configuration</a>
documentation for a concrete example.

## Match Actions and Values

In the event that your specified event matcher has a match, you will want to
take some action. This is specified by a `matchAction` parameter. In the event
of a match it is also possible to specify an additional `matchMethod`, the
return type of which should be a string. This value will be passed to the
specified `matchAction`. Continuing from the above example, if you wanted to
be emailed when a machine was provisioned, your `matchAction` might look like:

    callbacks.callback.nowProvisioned.matchMethod="tag"
    callbacks.callback.nowProvisioned.matchAction="exec /usr/local/bin/notifier \
        --config=/usr/local/collins/notifier.yaml \
        --tag=<tag> \
        --template=provisioned_email.html.erb"

The above specifies that in the event of a match, the `tag` method (which
returns a string) should be called on the current asset. The `matchMethod`, a
command line exec, will then be taken.

You'll notice that the `matchMethod` has a `<tag>` value in the string. The
value of `matchMethod` (tag in this case), can be used as a substitution in
the `matchAction` string. Say for example you wanted that IM when an emergency
was logged on an asset. It would look like:

    callbacks.callback.emergencyLog.event="asset_log_create"
    callbacks.callback.emergencyLog.current.matchMethod="isEmergency"
    callbacks.callback.emergencyLog.matchMethod="getAssetTag"
    callbacks.callback.emergencyLog.matchAction="exec /usr/local/bin/notifier \
        --config=/usr/local/collins/notifier.yaml \
        --tag=<getAssetTag> \
        --type=hipchat \
        --template=/tmp/emergency_im.erb"

In this case because we called `getAssetTag` on the `asset_log` instance,
`<getAssetTag>` is available for substitution in the `matchAction`.

Notice that the `matchAction` is prefixed by `exec`. This is required to tell
the match processor that this should be handled as a command line execution.
In the future, additional types of action types may be handled (e.g. we may
just implement `notify` as an action type instead of a command line tool).
