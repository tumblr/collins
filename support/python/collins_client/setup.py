#!/usr/bin/env python

from setuptools import setup, find_packages

long_desc = """To install this package:

> python3 -m pip install collins-client

To use this package:

> import collins_client
> client = collins_client.CollinsClient(<username>, <password>, <host>)

From there you can make any of the calls needed for the functionality of the collins api.

When dealing with multiple attributes, you should be able to pass a list of values.
Say you want to find assets with a certain hostname and a primary role.

> client.asset_finder({'attribute': ['HOSTNAME;example.tumblr.net', 'PRIMARY_ROLE;TUMBLR_APP']})
"""

setup(name="collins_client",
      version="1.0.0",
      description="The python interface to the collins api.",
      long_description=long_desc,
      author="Tumblr Inc.",
      author_email="opensourcesoftware@tumblr.com",
      url="https://github.com/tumblr/collins/tree/master/support/python/collins_client",
      packages=find_packages(),
      keywords='collins infastructure managment',
      classifiers= [
          'Development Status :: 5 - Production/Stable',
          'Intended Audience :: Developers',
          'Intended Audience :: System Administrators',
          'License :: OSI Approved :: Apache Software License',
          'Programming Language :: Python :: 3 :: Only',
          'Programming Language :: Python :: 3.6',
          'Programming Language :: Python :: 3.7',
          'Topic :: System :: Systems Administration'
      ],
      install_requires= [
          'grequests==0.2.0',
      ]
)
