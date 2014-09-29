#!/usr/bin/env python
import grequests

import base64
import urllib
import urllib2
try:
    import simplejson as json
except ImportError:
    import json

class CollinsClient:
    """
    This client will help you interface with Collins in a meaningful way
    giving access to all the different apis that Collins allows.
    """
    def __init__(self, username, passwd, host="http://50.97.87.90:8080"):
        self.username = username
        self.passwd = passwd
        self.host = host

    def async_update_asset(self, tag, params={}):
        url = "/api/asset/%s" % tag
        return grequests.post(self.host+url, auth=(self.username, self.passwd), data=params)

    def async_asset_finder(self, params={}):
        url = "/api/assets"
        return grequests.get(self.host+url, auth=(self.username, self.passwd), params=params)

    def assets(self, params={}):
        """
        Finds assets matching the following criteria:
            attribute - string, optional. Specified as keyname;valuename. keyname can be a reserved meta tag such as CPU_COUNT, MEMORY_SIZE_BYTES, etc.
            type - string, optional. One of SERVER_NODE, SERVER_CHASSIS, etc.
            status - string, optional. One of New, Incomplete, etc.
            createdAfter - ISO8601 date, optional.
            createdBefore - ISO8601 date, optional.
            updatedAfter - ISO8601 date, optional.
            updatedBefore - ISO8601 date, optional.

            #TODO add pagination support
        """
        url = "/api/assets"
        response = self._query("get", url, params)
        return response

    def create_asset(self, tag, params={}):
        """
        """
        url = "/api/asset/%s" % tag
        response = self._query("put", url, params)
        return response

    def update_asset(self, tag, params={}):
        """
        """
        url = "/api/asset/%s" % tag
        response = self._query("post", url, params)
        return response



    def delete_asset(self, tag, params={}):
        """
        """
        url = "/api/asset/%s" % tag
        response = self._query("delete", url, params)
        return response

    def delete_asset_attribute(self, tag, attribute):
        """
        """

        url = "/api/asset/%s/attribute/%s" % (tag, attribute)
        response = self._query("delete", url, {})
        return response

    def asset_finder(self, params={}):
        url = "/api/assets"
        response = self._query("get", url, params)
        return response

    def asset_info(self, tag, params={}):
        """
        """
        url = "/api/asset/%s" % tag
        response = self._query("get", url, params)
        return response

    def assets_logs(self, tag, params={}):
        """
        """
        url = "/api/asset/%s/logs" % (tag)
        response = self._query("get", url, params)
        return response

    def create_assets_log(self, tag, params={}):
        """
        """
        url = "/api/asset/%s/log" % (tag)
        response = self._query("put", url, params)

    def _query(self, method, url, params={}):
        """
        """
        handle = urllib2.build_opener(urllib2.HTTPHandler)
        #Eventually making this a little more robust
        if method in ['post', 'put']:
            request = urllib2.Request(self.host+url, data=urllib.urlencode(params, doseq=True))
        else:
            if params:
               url += "?" + urllib.urlencode(params, doseq=True)
            request = urllib2.Request(self.host+url)

        authstring = base64.encodestring("%s:%s" % (self.username, self.passwd)).strip()
        request.add_header("Authorization", "Basic %s" % authstring)

        #Python does not support case statements
        #This will override the request method, defaulting to get
        request.get_method = {
            "get"  : lambda: "GET",
            "post" : lambda: "POST",
            "put"  : lambda: "PUT",
            "delete" : lambda: "DELETE"
        }.get(method, "get")

        #TODO little more robust
        try:
            response = handle.open(request).read()
            response = json.loads(response)
            return response
        except Exception, e:
            pass
