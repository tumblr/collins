#!/usr/bin/env python

from setuptools import setup, find_packages

setup(name="collins_client",
      version="0.1.0",
      description="The python interface to the collins api.",
      author="John Bunting",
      author_email="johnb@tumblr.com",
      url="https://github.com/tumblr/collins/tree/master/support/python/collins-client",
      packages=find_packages(),
      keywords='collins infastructure managment',
      classifiers= [
          'Development Status :: 5 - Production/Stable',
          'Intended Audience :: Developers',
          'Intended Audience :: System Administrators',
          'License :: OSI Approved :: Apache Software License',
          'Programming Language :: Python :: 2 :: Only',
          'Programming Language :: Python :: 2.6',
          'Programming Language :: Python :: 2.7',
          'Topic :: System :: Systems Administration'
      ],
      install_requires= [
          'grequests==0.2.0',
      ]
)
