"""
Client for kooaba Data Upload API.

Copyright (c) 2011, kooaba AG

All rights reserved. Redistribution and use in source and binary forms,
with or without modification, are permitted provided that the following
conditions are met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.
  * Neither the name of the kooaba AG nor the names of its contributors may be
    used to endorse or promote products derived from this software without
    specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
"""

from KWS import KWSSigner
from StringIO import StringIO
from textwrap import dedent
from urlparse import urlparse
import email.utils
import httplib
import mimetypes
import os
import xml.etree.ElementTree as ET

VERSION = '1.1.0'


# Configuration defaults
QUERY_ENDPOINT = 'http://my.kooaba.com'
RESULTS_LIMIT  = 1


class BasicDataUploadClient:
    """ Client for kooaba Data Upload API. """

    def __init__(self, access_key, secret_key, endpoint = None):
        """ Initialize client for given query endpoint.
        None as endpoint means default endpoint.
        """
        self.access_key = access_key
        self.kws = KWSSigner(secret_key)
        api_path = '/api'
        if endpoint is not None:
            self.api_url = endpoint+api_path
        else:
            self.api_url = QUERY_ENDPOINT+api_path
        self.debugging = False

    def activate_image(self, image_id):
        """ Activate the image.
        Returns current status of the image.
        """
        return self._set_image_status(image_id, 'ACTIVE')

    def add_resource_file(self, item_id, title, section, filename):
        """ Add a file resource to an item.
        Raises exception on error.
        """
        upload_id = self.upload_from_file(filename)
        xml = self._generate_basic_resource_xml(title, section)
        file_elem = ET.SubElement(xml, "file")
        self._add_xml_subelement(file_elem, "upload_id", upload_id)
        (_response, _body) = self._post_data("/items/%s/item_resources.xml" % item_id, self._serialize_xml(xml), 'application/xml')

    def add_resource_uri(self, item_id, title, section, uri):
        """ Add a URI resource to an item.
        Raises exception on error.
        """
        xml = self._generate_basic_resource_xml(title, section)
        self._add_xml_subelement(xml, "uri", uri)
        (_response, _body) = self._post_data("/items/%s/item_resources.xml" % item_id, self._serialize_xml(xml), 'application/xml')

    def change_item_medium_type(self, item_id, medium_type, title, metadata):
        """ Change the medium type of the item and upload new metadata.
        The title is a required metadata title, in general identical to the
        item title. The metadata is a dictionary with the metadata entries.
        When medium_type is None, the curent medium type is removed from the
        item.
        Raises exception on error.
        """
        xml = ET.Element("medium")
        if (medium_type is not None) and (medium_type != ""):
            self._add_xml_subelement(xml, "type", 'Medium::'+medium_type)
            self._add_xml_subelement(xml, 'title', title)
            for (key, value) in metadata.items():
                self._add_xml_subelement(xml, key, value)
        (_response, _body) = self._put_data("/items/%s/medium.xml" % item_id, self._serialize_xml(xml), 'application/xml')

    def create_image_from_upload(self, item_id, upload_id):
        """ Associate uploaded image with an item.
        Returns image ID.
        """
        xml = ET.Element("image")
        file_elem = ET.SubElement(xml, "file")
        self._add_xml_subelement(file_elem, "upload-id", upload_id)
        (_response, body) = self._post_data("/items/%s/images.xml" % item_id, self._serialize_xml(xml), 'application/xml')
        return self._id_from_xml_string(body)

    def create_item(self, group_id, title, extras = dict()):
        """ Create a new item with title in the group with group_id.
        Optional metadata entries (external_id, reference_id, locale) are
        specified as dictionary in extras (any entries with value None are
        ignored).
        Returns ID of the created item.
        Raises exception:
            - RuntimeError: API call failed with an error.
        """
        xml = ET.Element("item")
        self._add_xml_subelement(xml, "title", title)
        for (key, value) in extras.items():
            if value is not None:
                self._add_xml_subelement(xml, key, value)
        (_response, body) = self._post_data("/groups/%s/items.xml" % group_id, self._serialize_xml(xml), 'application/xml')
        return self._id_from_xml_string(body)

    def deactivate_image(self, image_id):
        """ Deactivate the image.
        Returns current status of the image.
        """
        return self._set_image_status(image_id, 'INACTIVE')

    def delete_image(self, image_id):
        """ Delete the image.
        Raises exception on error.
        """
        (_response, _body) = self._send_request('DELETE', "/images/%s.xml" % image_id)

    def delete_item(self, item_id):
        """ Delete the item.
        Raises exception on error.
        """
        (_response, _body) = self._send_request('DELETE', "/items/%s.xml" % item_id)

    def get_group(self, group_id):
        """ Return description of the group. """
        (_response, body) = self._send_request('GET', "/groups/%s.xml" % group_id)
        return body

    def get_group_items(self, group_id):
        """ Return items from the group. """
        (_response, body) = self._send_request('GET', "/groups/%s/items.xml" % group_id)
        return body

    def get_image(self, image_id):
        """ Return description of the image. """
        (_response, body) = self._send_request('GET', "/images/%s.xml" % image_id)
        return body

    def get_image_status(self, image_id):
        """ Return current image status. """
        (_response, body) = self._send_request('GET', "/images/%s.xml" % image_id)
        return self._status_from_xml_string(body)

    def get_item(self, item_id):
        """ Return description of the item. """
        (_response, body) = self._send_request('GET', "/items/%s.xml" % item_id)
        return body

    def get_item_resources(self, item_id):
        """ Return resources of the item. """
        (_response, body) = self._send_request('GET', "/items/%s/item_resources.xml" % item_id)
        return body

    def set_debug(self, flag):
        """ Enable/disable debugging printouts according to the flag. """
        self.debugging = flag

    def update_item(self, item_id, extras = dict()):
        """ Update the item according to metadata in extras.
        The metadata entries (title, external_id, reference_id, locale) are
        specified as dictionary (any entries with value None are ignored).
        Raises exception:
            - RuntimeError: API call failed with an error.
        """
        xml = ET.Element("item")
        for (key, value) in extras.items():
            if value is not None:
                self._add_xml_subelement(xml, key, value)
        (_response, _body) = self._put_data("/items/%s.xml" % item_id, self._serialize_xml(xml), 'application/xml')

    def upload_data(self, data, content_type):
        """ Upload data from memory.
        Returns upload ID.
        """
        (_response, body) = self._post_data('/uploads.xml', data, content_type)
        return self._id_from_xml_string(body)

    def upload_from_file(self, filename, content_type = None):
        """ Upload a file.
        Returns upload ID.
        """
        if content_type is None:
            (content_type, _encoding) = mimetypes.guess_type(filename)
        with open(filename, 'rb') as f:
            return self.upload_data(f.read(), content_type)

    def _add_xml_subelement(self, root, name, text):
        """ Add a text sub-element to root. """
        elem = ET.SubElement(root, name)
        elem.text = unicode(text, "UTF-8")

    def _element_from_xml(self, xml, name):
        """ Extract element name from supplied XML string.
        Returns the element text content as string.
        Raises KeyError if there is no such element.
        """
        elem = xml.find(name)
        if elem is None:
            raise KeyError("No '"+name+"' element in the supplied XML: "+ET.tostring(xml))
        return elem.text

    def _generate_basic_resource_xml(self, title, section):
        """ Return resource upload XML fragment with title and section. """
        xml = ET.Element("resource")
        self._add_xml_subelement(xml, "title", title)
        self._add_xml_subelement(xml, "section", section)
        return xml

    def _id_from_xml(self, xml):
        """ Extract id element from supplied XML string.
        Returns the ID as string.
        Raises KeyError if there is no ID element.
        """
        return self._element_from_xml(xml, "id")

    def _id_from_xml_string(self, xml_string):
        """ Extract id element from supplied XML string.
        Returns the ID as string.
        Raises KeyError if there is no ID element.
        """
        xml = ET.fromstring(xml_string)
        return self._id_from_xml(xml)

    def _post_data(self, api_path, data, content_type):
        """ Post data to an API node specified by api_path.
        See _send_request() for further details.
        """
        return self._send_request('POST', api_path, data, content_type)

    def _put_data(self, api_path, data, content_type):
        """ Put data to an API node specified by api_path.
        See _send_request() for further details.
        """
        return self._send_request('PUT', api_path, data, content_type)

    def _send_request(self, method, api_path, data=None, content_type=None):
        """ Send (POST/PUT/GET/DELETE according to the method) data to an API
        node specified by api_path.

        Returns tuple (response, body) as returned by the API call. The
        response is a HttpResponse object describint HTTP headers and status
        line.

        Raises exception on error:
            - IOError: Failure performing HTTP call
            - RuntimeError: Unsupported transport scheme.
            - RuntimeError: API call returned an error.
        """
        if self.debugging:
            if data is None:
                print "%s ...%s" % (method, api_path)
            elif len(data) < 4096:
                print "%s ...%s:\n%s" % (method, api_path, data)
            else:
                print "%s ...%s: %sB" % (method, api_path, len(data))
        if '://' not in self.api_url:
            # endpoint as a host or host:port
            parsed_url = urlparse('http://'+self.api_url+api_path)
        else:
            parsed_url = urlparse(self.api_url+api_path)
        if (parsed_url.scheme != 'http') and (parsed_url.scheme != 'https'):
            raise RuntimeError("URL scheme '%s' not supported" % parsed_url.scheme)
        port = parsed_url.port
        if port is None:
            port = 80
        host = parsed_url.hostname
        http = httplib.HTTPConnection(host, port)
        try:
            date = email.utils.formatdate(None, localtime=False, usegmt=True)
            if data is not None:
                signature = self.kws.sign_with_content(method, data, content_type, date, parsed_url.path)
            else:
                signature = self.kws.sign_with_no_content(method, content_type, date, parsed_url.path)
            headers = {
                    'Authorization': 'KWS %s:%s' % (self.access_key, signature),
                    'Date': date
                    }
            if content_type is not None:
                headers['Content-Type'] = content_type
            if data is not None:
                headers['Content-Length'] = str(len(data))
            try:
                http.request(method, parsed_url.path, headers=headers, body=data)
            except Exception, e:
                raise IOError("Error during request: %s: %s" % (type(e), e))
            response = http.getresponse()
            # we have to read the response before the http connection is closed
            body = response.read()
            if self.debugging:
                print "HTTP response status:", response.status, response.reason
                print "Body:"
                print body
            if (response.status < 200) or (response.status > 299):
                raise RuntimeError("API call returned status %s %s. Message: %s" % (response.status, response.reason, body))
            return (response, body)
        finally:
            http.close()

    def _serialize_xml(self, xml):
        """ Serialize ElementTree document. """
        tree = ET.ElementTree(xml)
        buf = StringIO()
        tree.write(buf, encoding="UTF-8", xml_declaration=True, method="xml")
        serialized = buf.getvalue()
        buf.close()
        return serialized

    def _set_image_status(self, image_id, new_status):
        """ Set image status to a new value.
        Returns image status after change.
        Raises exception on error.
        """
        xml = ET.Element("status")
        self._add_xml_subelement(xml, "name", new_status)
        (_response, body) = self._put_data('/images/%s/status.xml' % image_id, self._serialize_xml(xml), 'application/xml')
        response_xml = ET.fromstring(body)
        status_elem = response_xml.find("status")
        if status_elem is None:
            raise KeyError("No status element in the returned XML: "+body)
        return status_elem.text

    def _status_from_xml(self, xml):
        """ Extract status element from supplied XML string.
        Returns the status as string.
        Raises KeyError if there is no status element.
        """
        return self._element_from_xml(xml, "status")

    def _status_from_xml_string(self, xml_string):
        """ Extract id element from supplied XML string.
        Returns the ID as string.
        Raises KeyError if there is no ID element.
        """
        xml = ET.fromstring(xml_string)
        return self._status_from_xml(xml)


def parse_inputs():
    """ Parse and check command line and environment variables. """
    import optparse

    usage = dedent("""
    Create a new item (prints progress):
      %prog [options] --create-item-in GROUP_ID -t TITLE [image [image2] ...]

    Create a new resource (prints nothing):
      %prog [options] --create-resource-for ITEM_ID -t TITLE -s SECTION [file]

    Update metadata of an existing item (prints progress):
      %prog [options] --update-item ITEM_ID [image [image2] ...]

    Delete item (prints nothing):
      %prog [options] --delete-item ITEM_ID

    Delete image (prints nothing):
      %prog [options] --delete-image IMAGE_ID

    Activate image (prints updated image status):
      %prog [options] --activate-image IMAGE_ID

    Deactivate image (prints updated image status):
      %prog [options] --deactivate-image IMAGE_ID

    Get image (prints image description):
      %prog [options] --get-image IMAGE_ID

    Get image status (prints image status):
      %prog [options] --get-image-status IMAGE_ID

    The user credentials (access and secret keys) should be passed via
    environment variables KWS_ACCESS_KEY and KWS_SECRET_KEY.
    """)

    version = "%%prog %s" % VERSION

    parser = optparse.OptionParser(usage = usage, version = version, add_help_option = True)
    parser.add_option('--activate-image', type='int', default=None, metavar='IMAGE_ID',
            help="activate given image {activate image}")
    parser.add_option('--create-item-in', type='int', default=None, metavar='GROUP_ID',
            help="create item in given group {create item}")
    parser.add_option('--create-resource-for', type='int', default=None, metavar='ITEM_ID',
            help="create resource for given item {create resource}")
    parser.add_option('--deactivate-image', type='int', default=None, metavar='IMAGE_ID',
            help="deactivate given image {deactivate image}")
    parser.add_option('--debug', action='store_true', default=False,
            help="enable debugging printouts of communication with the server")
    parser.add_option('--delete-image', type='int', default=None, metavar='IMAGE_ID',
            help="delete given image {delete image}")
    parser.add_option('--delete-item', type='int', default=None, metavar='ITEM_ID',
            help="delete given item {delete item}")
    parser.add_option('--external-id', type='int', default=None,
            help="external ID of the item (integer) {create/update item}")
    parser.add_option('-E', '--endpoint', type='string', default=QUERY_ENDPOINT,
            help="endpoint for the request [%default]")
    parser.add_option('--get-group', type='int', default=None, metavar='GROUP_ID',
            help="retrieve description of given group {get group}")
    parser.add_option('--get-group-items', type='int', default=None, metavar='GROUP_ID',
            help="retrieve items from given group {get group items}")
    parser.add_option('--get-image', type='int', default=None, metavar='IMAGE_ID',
            help="retrieve description of given image {get image}")
    parser.add_option('--get-image-status', type='int', default=None, metavar='IMAGE_ID',
            help="retrieve status of given image {get image status}")
    parser.add_option('--get-item', type='int', default=None, metavar='ITEM_ID',
            help="retrieve description of given item {get item}")
    parser.add_option('--get-item-resources', type='int', default=None, metavar='ITEM_ID',
            help="retrieve resources of given item {get item resources}")
    parser.add_option('--locale', type='string', default=None,
            help="item locale (string) {create/update item}")
    parser.add_option('--medium-type', type='string', default=None,
            help="medium type of the item (use empty string to delete medium type) {create/update item}")
    parser.add_option('-M', '--metadata', type='string', action='append', default=list(),
            help="medium specific metadata as 'name:value' pair (can be repeated multiple times) {create/update item}")
    parser.add_option('--reference-id', type='string', default=None,
            help="reference ID of the item (string) {create/update item}")
    parser.add_option('--skip-image-activation', action='store_true', default=False,
            help="do not activate image(s) after upload {create/update item}")
    parser.add_option('--section', type='string', default=None,
            help="item resource section (string) {create resource}")
    parser.add_option('-t', '--title', type='string', default=None,
            help="item or resource title {create/update item, create resource}")
    parser.add_option('--update-item', type='int', default=None, metavar='ITEM_ID',
            help="update given item {update item}")
    parser.add_option('--uri', type='string', default=None,
            help="item resource URI {create resource}")
    #parser.add_option('-v', '--verbose', action='store_true', default=False,
    #        help="verbose logging - DEBUG logging level [%default]")

    (options, arguments) = parser.parse_args()

    action_names = ['activate-image', 'create-item-in', 'create-resource-for',
            'deactivate-image', 'delete-image', 'delete-item', 'get-group',
            'get-group-items', 'get-image', 'get-image-status', 'get-item',
            'get-item-resources', 'update-item']
    action_count = 0
    for action_name in action_names:
        action = getattr(options, action_name.replace('-', '_'))
        if action is not None:
            selected_action = action_name
            action_count += 1
    if action_count != 1:
        action_list = ""
        for action_name in action_names:
            action_list += "\n  --%s" % action_name
        parser.error("Exactly one of the actions must be specified:"+action_list)

    # validate mandatory options
    if (selected_action == 'create-item-in') and (options.title is None):
        parser.error("Please specify item title.")
    if selected_action == 'create-resource-for':
        if options.title is None:
            parser.error("Please specify resource title.")
        if options.section is None:
            parser.error("Please specify resource section.")
        if len(arguments) > 1:
            parser.error("Item resource can contain at most one file.")
        if (options.uri is None) and (len(arguments) < 1):
            parser.error("Resource requires either an URI or a file.")
        if (options.uri is not None) and (len(arguments) > 0):
            parser.error("Resource requires either an URI or a file, but not both.")

    # validate metadata array
    for md in options.metadata:
        if not ':' in md:
            parser.error("Metadata pair '%s' is invalid (it must be specified as 'name:value')." % md)

    # inputs from environment variables
    if 'KWS_ACCESS_KEY' not in os.environ:
        parser.error("Environment variable KWS_ACCESS_KEY not set.")
    options.access_key = os.environ['KWS_ACCESS_KEY']
    if 'KWS_SECRET_KEY' not in os.environ:
        parser.error("Environment variable KWS_SECRET_KEY not set.")
    options.secret_key = os.environ['KWS_SECRET_KEY']

    return (options, arguments, selected_action)


def create_new_item(client, options, arguments):
    """ Create new item. Prints progress. """
    extras = {'external_id': options.external_id,
            'reference_id': options.reference_id,
            'locale': options.locale}
    item_id = client.create_item(options.create_item_in, options.title, extras)
    print "Created item", item_id
    return _update_item_common(client, item_id, options, arguments)


def update_item(client, options, arguments):
    """ Update existing item. Prints progress. """
    item_id = options.update_item
    extras = dict()
    for entry in ['title', 'external_id', 'reference_id', 'locale']:
        value = getattr(options, entry)
        if value is not None:
            extras[entry] = value
    if len(extras.keys()) > 0:
        client.update_item(item_id, extras)
        print "Updated basic item metadata"
    else:
        print "No update to basic item metadata"
    return _update_item_common(client, item_id, options, arguments)


def _update_item_common(client, item_id, options, arguments):
    """ Update media metadata of the existing item and add any requested images. Prints progress. """
    if options.medium_type is not None:
        metadata = dict()
        for md in options.metadata:
            split = md.split(':', 1)
            metadata[split[0].strip()] = split[1]
        client.change_item_medium_type(item_id, options.medium_type, options.title, metadata)
        print "  changed medium type to", options.medium_type, "and uploaded metadata"
    upload_ids = list()
    for filename in arguments:
        upload_ids.append(client.upload_from_file(filename))
    image_ids = list()
    for upload_id in upload_ids:
        image_id = client.create_image_from_upload(item_id, upload_id)
        image_ids.append(image_id)
        print "  added image", image_id
        if not options.skip_image_activation:
            client.activate_image(image_id)
            print "  activated image", image_id
    return 0


def add_resource(client, options, arguments):
    """ Create new item. Prints nothing. """
    if len(arguments) > 0:
        client.add_resource_file(options.create_resource_for, options.title, options.section, arguments[0])
    else:
        client.add_resource_uri(options.create_resource_for, options.title, options.section, options.uri)
    return 0


def delete_image(client, options):
    """ Delete image. Prints nothing. """
    client.delete_image(options.delete_image)
    return 0


def delete_item(client, options):
    """ Delete item. Prints nothing. """
    client.delete_item(options.delete_item)
    return 0


def activate_image(client, options):
    """ Activate image. Prints new status of the image. """
    print client.activate_image(options.activate_image)
    return 0


def deactivate_image(client, options):
    """ Deactivate image. Prints new status of the image. """
    print client.deactivate_image(options.deactivate_image)
    return 0


def get_group(client, options):
    """ Retrieve description of the group. Prints the description. """
    print client.get_group(options.get_group)
    return 0


def get_group_items(client, options):
    """ Retrieve items of given group. Prints the items. """
    print client.get_group_items(options.get_group_items)
    return 0


def get_image(client, options):
    """ Retrieve description of the image. Prints the description. """
    print client.get_image(options.get_image)
    return 0


def get_image_status(client, options):
    """ Retrieve status of the image. Prints the status. """
    print client.get_image_status(options.get_image_status)
    return 0


def get_item(client, options):
    """ Retrieve description of the item. Prints the description. """
    print client.get_item(options.get_item)
    return 0


def get_item_resources(client, options):
    """ Retrieve resources of the item. Prints the resources. """
    print client.get_item_resources(options.get_item_resources)
    return 0


def main():
    """ CLI client. """
    (options, arguments, selected_action) = parse_inputs()
    client = BasicDataUploadClient(options.access_key, options.secret_key, options.endpoint)
    client.set_debug(options.debug)
    if selected_action == 'create-item-in':
        return create_new_item(client, options, arguments)
    elif selected_action == 'create-resource-for':
        return add_resource(client, options, arguments)
    elif selected_action == 'update-item':
        return update_item(client, options, arguments)
    elif selected_action == 'delete-image':
        return delete_image(client, options)
    elif selected_action == 'delete-item':
        return delete_item(client, options)
    elif selected_action == 'activate-image':
        return activate_image(client, options)
    elif selected_action == 'deactivate-image':
        return deactivate_image(client, options)
    elif selected_action == 'get-group':
        return get_group(client, options)
    elif selected_action == 'get-group-items':
        return get_group_items(client, options)
    elif selected_action == 'get-image':
        return get_image(client, options)
    elif selected_action == 'get-image-status':
        return get_image_status(client, options)
    elif selected_action == 'get-item':
        return get_item(client, options)
    elif selected_action == 'get-item-resources':
        return get_item_resources(client, options)
    else:
        raise NotImplementedError("Unimplemented action: --"+selected_action)


if __name__ == '__main__':
    import sys
    sys.exit(main())

# vim: ai:si:sw=4:ts=4:et:sts=4:
