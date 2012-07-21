# Collins Query Language
#### Syntax Specification

##Getting started

#### In Github:

- main dev branch: dan-collins-solr
- solr config branch: dan-collins-solr-config

the solr config branch just has a new folder collins/conf/solr that has a ton
of out-of-the-box config files.  I put them in a branch just to avoid massive
diffs when comparing the dev branch to master.  The Solr schema is specified in schema.xml

#### Configuring Solr

Collins can either use an external running instance of Solr or launch its own embedded Server.  Using an external instance is better tested, but both appear to be working.

Here are the config settings

- solr.enabled = true
- solr.repopulateOnStartup = false
- solr.useEmbeddedServer = false
- solr.externalUrl="http://localhost:8983/solr"
- solr.embeddedSolrHome = "/Users/dan/projects/platform_b/collins/conf/solr"

NOTE!!! - When running collins in dev mode within Play/SBT, setting
repopulateOnStartup to true will cause Collins to begin reloading forever.
This appears to be due to some side effect caused by the apache httpclient when
called from within a plugins onStart method.  To populate Solr in dev mode,
instead set repopulateOnStartup to false and after starting collins navigate to
/solr in the web app.  

## Key/values

Any primary value (tag, status, type, etc.) or attribute can be searched on

- key names are case insensitive
- any string values that are one word don't need quotes
- wildcards are supported at the beginning and/or end of string values

    tag = "test1"
    tag = test1
    tag = test*
    tag = *1
    TAG = test1

All keys are typed (although most are still defaulting to string)

    total_disk_size = 12345 //ok
    total_disk_size = foobar //error

## Keys with Enumerated values
TYPE and STATUS only accept certain values (case insensitive)

    TYPE = SERVER_NODE //ok
    TYPE = FOO //not ok

## Ranges

All values support ranges

    CPU_CORES = [4,8]
    CPU_CORES = [*,10]
    TAG = ["foo","foz"]

## Logical Operators

CQL allows for queries that consist of arbirary boolean expressions on asset data.

    IP_ADDRESS = "192.168.*" OR IP_ADDRESS = "10.60.*"
    
    TYPE = SERVER_NODE AND NOT TAG = foo-*
    
    STATUS = UNALLOCATED OR (STATUS = ALLOCATED AND IP_ADDRESS = "192.168.*")

## Other details

- Any key can be used to sort results
- When a new attribute is created on-demand, its type defaults to String

## Known Issues


- Solr is not updated when an asset or value is created/updated/deleted (this is the last remaining functionality that needs to be finished)
- Multi-collins CQL searching not implemented
- ip addresses must be quoted (otherwise it tries to parse it as a decimal)
- single quote marks not supported
- dates must be in ISO 8601 format (support for more formats on the way)

## Using CQL 

### How to make CQL queries in collins_shell

    collin_shell asset find --selector='query:"status = unallocated AND ip_address = [192.168.1.1, 192.168.1.100]"' 

### In the web app

    http://collinsurl/solrsearch?query=urlencodedquery

