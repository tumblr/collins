To install this package:

python setup.py install


To use this package:

```
import collins

client = collins.CollinsClient(<username>, <password>)
```

From there you can make any of the calls needed for the functionality of the collins api. 

When dealing with multiple attributes, you should be able to pass a list of values.
Say you want to find assets with a certain hostname and a primary role.

```
client.asset_finder({'attribute': ['HOSTNAME;example.tumblr.net', 'PRIMARY_ROLE;TUMBLR_APP']})
```

