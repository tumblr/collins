# Power Configuration

## Overview

Depending on the environment, each asset may have a different power configuration.
A power configuration specifies the number of power units required per asset,
the number of power components required per power unit, and various
requirements for each power component.

## Definitions

An asset may have an associated power configuration.
A power configuration consists of one or more power units.
A power unit consists of one or more power components (e.g. power strip, power outlet, etc)
A set of power units (PowerUnits in the code) represents a complete power configuration.

You can think of an asset power configuration as looking like:

Asset
 * PowerConfiguration
   * PowerUnit 1
     * PowerComponent 1
     * PowerComponent 2
     * PowerComponent n
   * PowerUnit 2
   * PowerUnit n

## Configuration

The requirements of the PowerConfiguration are specified by:

 * `conf/application.conf`
   * `powerconfiguration.unitsRequired=INT` - e.g. 2
   * `powerconfiguration.unitComponents=LIST` - e.g. "STRIP,OUTLET"
   * `powerconfiguration.useAlphabeticNames=BOOL` - e.g. true
   * `powerconfiguration.uniqueComponents=LIST` - e.g. "STRIP"
 * `conf/messages`
   * `powerconfiguration.unit.strip.label` - e.g. Plug Strip {0}
   * `powerconfiguration.unit.outlet.label` - e.g. Outlet {0}
   * `powerconfiguration.missingData` - e.g. Did not find value for ''{0}'', required for ''{1}''
   * `powerconfiguration.unitsRequired.range` - e.g. unitsRequired must be between 0 and 18
   * `powerconfiguration.unitComponents.unspecified` - e.g. unitComponents must be specified when unitsRequired is specified
   * `powerconfiguration.unitComponents.invalid` - e.g. Specified unitComponent ''{0}'' is invalid.
   * `powerconfiguration.uniqueComponents.invalid` - e.g. Specified uniqueComponent ''{0}'' not found in unitComponents
 
The above `application.conf` specifies that upon intake 2 power units with two power components are
required for successful intake. It also specifies that a unit must consist of unique power strips.
That is, during intake/etc all of the specified power strips must be different. That config will
cause the following text fields to show up in the UI

    Plug Strip A
    Outlet A
    Plug Strip B
    Outlet B

`unitsRequired=2` dictates that two power units (with the configured components STRIP and
OUTLET) are required. Specifying `useAlphabeticNames=true` causes the keys to be A/B
instead of 0/1. The HTTP parameters required or used in the above configuration would be:

    POWER_STRIP_A, POWER_OUTLET_A, POWER_STRIP_B, POWER_OUTLET_B

`uniqueComponents=STRIP` would indicate that the values associated with
`POWER_STRIP_A` and `POWER_STRIP_B` must be different. In reality this
configuration probably dictates that an asset must be connected to two
different power strip's (for redundant power connections).

## API Interaction

The `application.conf` configuration will dictate what parameters are required
to complete an asset intake. The required HTTP parameters are dynamic, with
respect to the configuration. These parameters can be supplied for the asset
update API, or for finder opreations. The HTTP parameters for the above
configuration are:


    POWER_STRIP_A, POWER_OUTLET_A, POWER_STRIP_B, POWER_OUTLET_B

Note that the current collins ruby client assumes that unitsRequired has been
set to 2, unitComponents is STRIP, useAlphabeticNames is true, and
uniqueComponents is STRIP.

## API Details

An asset that has gone through an intake with the above configuration will
have a POWER key in the asset JSON that looks something like:

    "POWER": [
      {
        "UNIT_ID": 0,
        "UNITS": [
          {
            "KEY": "POWER_PORT_A",
            "VALUE": "plug a",
            "TYPE": "POWER_PORT",
            "LABEL": "Plug Strip A",
            "POSITION": 0,
            "IS_REQUIRED": true,
            "UNIQUE": true
          },
          {
            "KEY": "POWER_OUTLET_A",
            "VALUE": "outlet a",
            "TYPE": "POWER_OUTLET",
            "LABEL": "Outlet A",
            "POSITION": 1,
            "IS_REQUIRED": true,
            "UNIQUE": false
          }
        ]
      },
      {
        "UNIT_ID": 1,
        "UNITS": [
          {
            "KEY": "POWER_PORT_B",
            "VALUE": "plug b",
            "TYPE": "POWER_PORT",
            "LABEL": "Plug Strip B",
            "POSITION": 0,
            "IS_REQUIRED": true,
            "UNIQUE": true
          },
          {
            "KEY": "POWER_OUTLET_B",
            "VALUE": "outlet b",
            "TYPE": "POWER_OUTLET",
            "LABEL": "Outlet B",
            "POSITION": 1,
            "IS_REQUIRED": true,
            "UNIQUE": false
          }
        ]
      }
    ]

This provides most of the useful information that can be found for each of the
power components.
