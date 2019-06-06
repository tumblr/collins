#!/usr/bin/env python
import base64
import logging
import urllib.request, urllib.parse, urllib.error

import grequests
try:
    import simplejson as json
except ImportError:
    import json

log = logging.getLogger(__name__)


class CollinsClient:
    """
    This client will help you interface with Collins in a meaningful way
    giving access to all the different apis that Collins allows.
    """
    def __init__(self, username, passwd, host):
        self.username = username
        self.passwd = passwd
        self.host = host

    def async_update_asset(self, tag, params=None):
        if params is None:
            params = {}
        url = "/api/asset/%s" % tag
        return grequests.post(self.host+url, auth=(self.username, self.passwd), data=params)

    def async_asset_finder(self, params=None):
        if params is None:
            params = {}
        url = "/api/assets"
        return grequests.get(self.host+url, auth=(self.username, self.passwd), params=params)

    def assets(self, params=None):
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
        if params is None:
            params = {}
        url = "/api/assets"
        response = self._query("get", url, params)
        return response

    def create_asset(self, tag, params=None):
        """
        """
        if params is None:
            params = {}
        url = "/api/asset/%s" % tag
        response = self._query("put", url, params)
        return response

    def update_asset(self, tag, params=None):
        """
        """
        if params is None:
            params = {}
        url = "/api/asset/%s" % tag
        response = self._query("post", url, params)
        return response

    def delete_asset(self, tag, params=None):
        """
        """
        if params is None:
            params = {}
        url = "/api/asset/%s" % tag
        response = self._query("delete", url, params)
        return response

    def delete_asset_attribute(self, tag, attribute):
        """
        """
        url = "/api/asset/%s/attribute/%s" % (tag, attribute)
        response = self._query("delete", url, {})
        return response

    def asset_finder(self, params=None):
        if params is None:
            params = {}
        url = "/api/assets"
        response = self._query("get", url, params)
        return response

    def asset_info(self, tag, params=None):
        """
        """
        if params is None:
            params = {}
        url = "/api/asset/%s" % tag
        response = self._query("get", url, params)
        return response

    def assets_logs(self, tag, params=None):
        """
        """
        if params is None:
            params = {}
        url = "/api/asset/%s/logs" % tag
        response = self._query("get", url, params)
        return response

    def create_assets_log(self, tag, params=None):
        """
        """
        if params is None:
            params = {}
        url = "/api/asset/%s/log" % tag
        response = self._query("put", url, params)
        return response

    def ping(self):
        url = "/api/ping"
        response = self._query("get", url, {})
        return response

    def create_assettype(self, name, label):
        """
        Description	Create a new asset type
        Request	PUT /api/assettype/:name
        Permission	controllers.AssetTypeApi.createAssetType
        Parameters
            Name	Type	Description
            name 	String 	Must be alphanumeric (but can include underscores and dashes) and unique
            label 	String 	Human readable label for the asset type. 2-32 characters.
        Response Codes
            Code	Reason
            201 	Asset type was successfully created
            409 	Asset type with specified name already exists
        """
        url = "/api/assettype/{0}".format(name)
        params = {'label': label}
        response = self._query("put", url, params)
        return response

    def update_assettype(self, name, label=None, newname=None):
        """
        Description	Update an asset type
        Request	POST /api/assettype/:tag
        Permission	controllers.AssetTypeApi.updateAssetType
        Parameters
            Name	Type	Description
            name 	String 	New name of the asset type (i.e. SERVICE). All uppercase, 2-32 chars.
            label 	String 	New label of the asset type. 2-32 chars.
        Response Codes
            Code	Reason
            200 	Asset type updated successfully
            404 	The specified asset type was not found
        """
        url = "/api/assettype/{0}".format(name)
        params = {}
        if label:
            params['label'] = label
        if newname:
            params['name'] = newname

        response = self._query("post", url, params)
        return response

    def get_assettype(self, name):
        """
        Description	Get an asset type by name
        Request	GET /api/assettype/:name
        Permission	controllers.AssetTypeApi.getAssetType
        Parameters
            Name	Type	Description
            name 	String 	Must be alphanumeric (but can include underscores and dashes) and unique
        Response Codes
            Code	Reason
            200 	Asset type was found
            404 	Asset type could not be found
        """
        url = "/api/assettype/{0}".format(name)
        response = self._query("get", url)
        return response

    def delete_assettype(self, name):
        """ Delete the specified asset type
        :param name: Asset unique name
        :return dict

        Request	DELETE /api/assettype/:name
        Permission	controllers.AssetTypeApi.deleteAssetType
        Parameters
            Name	Type	Description
            name 	String 	Must be alphanumeric (but can include underscores and dashes) and unique
        Response Codes
            Code	Reason
            200 	Asset type has been deleted
            404 	Asset type not found
            409 	System asset types cannot be deleted
            500 	Asset type unable to be deleted (Assets of this type still exist?)
        """
        url = "/api/assettype/{0}".format(name)
        response = self._query("delete", url)
        return response

    def ensure_assettype(self, name, label):
        """ Ensure assettype exists.
        :param name:  Asset type name
        :param label:   Asset type descriptive label
        :return: dict
        """
        try:
            response = self.create_assettype(name, label)
        except urllib.error.HTTPError as e:
            if e.code == 409:
                response = {'status': 'success:exists',
                            'data': {'SUCCESS': True}}

        return response

    def ensure_asset(self, tag, params=None):
        """ Ensure asset exists
        :param tag: Unique asset tag
        :param params: dict
        :return: dict
        """
        if params is None:
            params = {}
        try:
            response = self.create_asset(tag, params)
        except urllib.error.HTTPError as e:
            if e.code == 409:
                response = dict(status='success:exists',
                                data={'SUCCESS': True})
            else:
                response = dict(status='failure:{0}'.format(e.code),
                                data={'SUCCESS': False})

        if not response['status'].startswith('success'):
            log.warning(response['status'])

        return response

    def soft_update(self, tag, key, value):

        old_record = self.asset_info(tag)

        # everything from the API is a string
        value = str(value)

        update = True
        old = None
        if 'ATTRIBS' in list(old_record['data'].keys()):

            # If no attributes have ever been stored, [u'0'] doesn't
            # exist.
            log.debug(len(old_record['data']['ATTRIBS']))

            if len(old_record['data']['ATTRIBS']) > 0:
                attribs = old_record['data']['ATTRIBS']['0']
            else:
                attribs = {}

            if key.upper() in list(attribs.keys()):
                old = attribs[key.upper()]
                if old == value:
                    update = False

        # Never erase
        if value == '' or value == 'None':
            update = False

        if update:
            log.debug('{0}: Will update {1} from {2} to {3}'.format(tag, key, old, value))
            self.update_asset(tag, {'attribute': '{0};{1}'.format(key, value)})
        else:
            log.debug('{0}: No change to {1}, no update needed'.format(tag, key))

    def _query(self, method, url, params=None):
        """
        """
        if params is None:
            params = {}
        handle = urllib.request.build_opener(urllib.request.HTTPHandler)
        # Eventually making this a little more robust
        if method in ['post', 'put']:
            request = urllib.request.Request(self.host+url, data=urllib.parse.urlencode(params, doseq=True))
        else:
            if params:
               url += "?" + urllib.parse.urlencode(params, doseq=True)
            request = urllib.request.Request(self.host+url)
        authstring = base64.encodebytes(('%s:%s' % (self.username, self.passwd)).encode('ascii')).strip()
        request.add_header("Authorization", "Basic %s" % authstring.decode('ascii'))

        # Python does not support case statements
        # This will override the request method, defaulting to get
        request.get_method = {
            "get"  : lambda: "GET",
            "post" : lambda: "POST",
            "put"  : lambda: "PUT",
            "delete" : lambda: "DELETE"
        }.get(method, "get")

        # TODO little more robust
        response = handle.open(request).read()
        response = json.loads(response)
        return response
