An asset may have an associated power configuration.
A power configuration consists of one or more power units.
A power unit consists of one or more power components (e.g. power strip, power outlet, etc)
A set of power units (PowerUnits) represents a complete power configuration.

TODO - The order that units are listed via unitComponents should be the order they show up in for iteration

Asset
 * PowerConfiguration
   * PowerUnit 1
     * PowerComponent 1
     * PowerComponent 2
     * PowerComponent n
   * PowerUnit 2
   * PowerUnit n

The requirements of the PowerConfiguration are specified by:

 * `conf/application.conf`
   * `powerconfiguration.unitsRequired=INT` - e.g. 2
   * `powerconfiguration.unitComponents=LIST` - e.g. "STRIP,OUTLET"
   * `powerconfiguration.useAlphabeticNames=BOOL` - e.g. true
 * `conf/messages`
   * `powerconfiguration.unit.strip.label` - e.g. Plug Strip {0}
   * `powerconfiguration.unit.outlet.label` - e.g. Outlet {0}
   * `powerconfiguration.missingData` - e.g. Did not find value for ''{0}'', required for ''{1}''
   * `powerconfiguration.unitsRequired.range` - e.g. unitsRequired must be between 0 and 18
   * `powerconfiguration.unitComponents.unspecified` - e.g. unitComponents must be specified when unitsRequired is specified
   * `powerconfiguration.unitComponents.invalid` - e.g. Specified unitComponent ''{0}'' is invalid.
 
The above `application.conf` specifies that upon intake 2 power units with two power components are
required for successful intake. That config will cause the following text fields to show up in the
UI

    Plug Strip A
    Outlet A
    Plug Strip B
    Outlet B

`unitsRequired=2` dictates that two power units (with the configured components STRIP and
OUTLET) are required. Specifying `useAlphabeticNames=true` causes the labels to be A/B
instead of 0/1. The HTTP parameters required or used in the above configuration would be:

    POWER_STRIP_A, POWER_OUTLET_A, POWER_STRIP_B, POWER_OUTLET_B


