"""
Client for kooaba Query API v2.

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
from urlparse import urlparse
import email.utils
import httplib
import mimetools
import mimetypes
import os
try:
    import json
except ImportError:
    import simplejson as json

VERSION = '1.0.0'


# Configuration defaults
QUERY_ENDPOINT = 'http://query-beta.kooaba.com'
RESULTS_LIMIT  = 1


class QueryClient:
    """ Client for kooaba Query API v2. """

    def __init__(self, access_key, secret_key, endpoint = None):
        """ Initialize client for given query endpoint.
        None as endpoint means default endpoint.
        """
        self.access_key = access_key
        self.kws = KWSSigner(secret_key)
        query_path = '/v2/query'
        if endpoint is not None:
            self.query_url = endpoint+query_path
        else:
            self.query_url = QUERY_ENDPOINT+query_path

    # TODO allow to reuse the HTTP connection
    def execute(self, request):
        """ Send the query request and return instance of QueryResponse.
        Raises exception on error:
            RuntimeError - Invalid endpoint syntax or unknown protocol (only
                    http is supported at the moment)
            IOError - Error during request.
            ValueError - Wrong format of returned data.
        """
        (boundary, body) = request.serialize()
        if '://' not in self.query_url:
            # endpoint as a host or host:port
            parsed_url = urlparse('http://'+self.query_url)
        else:
            parsed_url = urlparse(self.query_url)
        if parsed_url.scheme != 'http':
            raise RuntimeError("URL scheme '%s' not supported" % parsed_url.scheme)
        port = parsed_url.port
        if port is None:
            port = 80
        host = parsed_url.hostname
        http = httplib.HTTPConnection(host, port)
        try:
            method = 'POST'
            date = email.utils.formatdate(None, localtime=False, usegmt=True)
            signature = self.kws.sign_with_content(method, body, 'multipart/form-data', date, parsed_url.path)
            headers = {
                    'Content-Type': 'multipart/form-data; boundary=%s' % boundary,
                    'Content-Length': str(len(body)),
                    'Accept': 'application/json',
                    'Authorization': 'KWS %s:%s' % (self.access_key, signature),
                    'Date': date
                    }
            try:
                http.request(method, parsed_url.path, headers=headers, body=body)
            except Exception, e:
                raise IOError("Error during request: %s: %s" % (type(e), e))
            response = http.getresponse()
            if (response.status < 200) or (response.status > 299):
                raise IOError("Response indicates error by status %s %s. Response content: %s" % (response.status, response.reason, response.read()))
            return QueryResponse(response.read())
        finally:
            http.close()


class SimpleMultipartRequest:
    """ Multipart request class. """

    def __init__(self, **kwargs):
        """ Create new multipart request.
        The kwargs can contain parts to insert. Each part is specified by a
        tuple (content, content_type, filename). See set_part for details.
        """
        self.data = dict()
        for k in kwargs:
            (content, content_type, filename) = kwargs[k]
            self.set_part(k, content, content_type, filename)

    def set_part(self, name, content, content_type = None, filename = None):
        """ Set or replace the content of the part with given name.
        If content_type or filename are None, they will not be used in the part
        headers. The content can be a file-like object in which case the actual
        content of the file/stream is used (as retrieved by read()) and the
        object will be closed. Using file object or setting filename without
        setting content type will force the content type to
        application/octet-stream.
        """
        def ensure_content_type(content_type):
            if (content_type is None):
                return 'application/octet-stream'
            return content_type

        if hasattr(content, 'read') and hasattr(content, 'close'):
            file_content = content.read()
            content.close()
            content = file_content
            content_type = ensure_content_type(content_type)
        if filename is not None:
            content_type = ensure_content_type(content_type)
        self.data[name] = (content, content_type, filename)

    def remove_part(self, name):
        """ Remove part with given name.
        Returns True if the part was removed, False if there was no such part.
        """
        try:
            del self.data[name]
            return True
        except KeyError:
            return False

    def serialize(self):
        """ Return request data as tuple (boundary, body_stream). """
        # FIXME use hash of choose_boundary return value (the string leaks information about origin of the request)
        boundary = mimetools.choose_boundary()
        body_buffer = StringIO()
        for name in self.data:
            (content, content_type, filename) = self.data[name]
            body_buffer.write('--%s\r\n' % boundary)
            body_buffer.write('Content-Disposition: form-data; name="%s"' % name)
            if filename is not None:
                body_buffer.write('; filename="%s"' % filename)
            body_buffer.write('\r\n')
            if content_type is not None:
                body_buffer.write('Content-Type: %s\r\n' % content_type)
            body_buffer.write('\r\n%s\r\n' % content)
        body_buffer.write('--%s--\r\n' % boundary)
        return (boundary, body_buffer.getvalue())


class QueryRequest(SimpleMultipartRequest):
    """ Query request object for kooaba Query API v2.
    Setters accept None as a value in which case the part is removed from the
    request (API defaults will be used for the query).
    """

    def __init__(self, **kwargs):
        """ Construct new request.
        See constructor of class SimpleMultipartRequest for description of
        expected kwargs format.
        """
        SimpleMultipartRequest.__init__(self, **kwargs)

    def set_image(self, image, content_type = None):
        """ Set image either as data or as file-like object.
        File-like object will be closed as a part of this call.
        """
        if image is None:
            self.remove_part('image')
        else:
            self.set_part('image', image, content_type)

    def set_destinations(self, destinations):
        """ Set query destinations.
        None or empty string are equivalent to listing all user's destinations.
        """
        if (destinations is None) or (destinations == ''):
            self.remove_part('destinations')
        else:
            self.set_part('destinations', destinations)

    def set_early_return(self, early_return_flag):
        """ Set early return flag on the query.
        Passing None removes the flag from the request.
        """
        if early_return_flag is None:
            self.remove_part('early-return')
        else:
            self.set_part('early-return', '%s' % early_return_flag)

    def set_results_limit(self, limit):
        """ Set limit on number of returned results.
        Value of 0 means no limit. Raises ValueError if limit is negative. If
        the limit is None, the results limit part will be removed from the
        request.
        """
        name = 'results-limit'
        if limit is None:
            self.remove_part(name)
        elif limit >= 0:
            self.set_part(name, '%s' % limit)
        else:
            raise ValueError("Results limit cannot be negative.")

    def set_return_low_confidence_results(self, specification):
        """ Set return low confidence results specification on the query.
        Allowed values are 'always', 'as-needed' and 'never'. Passing None or
        empty string removes the specification. Any other value of
        specification will cause ValueError exception.
        """
        name = 'return-low-confidence-results'
        if (specification is None) or (specification == ''):
            self.remove_part(name)
        elif (specification == 'always') or (specification == 'as-needed') or (specification == 'never'):
            self.set_part(name, specification)
        else:
            raise ValueError("Unknown value for return-low-confidence-results: '%s'" % specification)

    def set_returned_metadata(self, specification):
        """ Set which metadata should be returned.
        The value is a comma separated list of values
        (refer to the API documentation for details):
            recognition-location: bounding box and reference projection
            external-references: basic external references
            minimal: minimal human readable description
            extended: extended metadata
            resources: resources metadata
        """
        name = 'returned-metadata'
        if (specification is None) or (specification == ''):
            self.remove_part(name)
        else:
            self.set_part(name, specification)


class QueryResponse:
    """ Query response object for kooaba Query API v2.
    Before calling parse_data() method, all get_* methods (except
    get_raw_data()) will return None. The methods can throw KeyError if the
    response data structure is invalid.
    """

    def __init__(self, data):
        """ Create object with the data, but do not parse them. """
        self.raw_data = data
        self.parsed_data = None

    def get_raw_data(self):
        """ Return raw response data. """
        return self.raw_data

    def get_parsed_data(self):
        """ Return parsed response data. """
        return self.parsed_data

    def parse_data(self, forget_raw_data = False):
        """ Parse the raw data.
        When forget_raw_data is True, it destroys the raw data to free the
        memory. Raises ValueError if parsing fails.
        """
        try:
            self.parsed_data = json.loads(self.raw_data)
            if forget_raw_data:
                self.raw_data = None
        except Exception, e:
            raise ValueError("Cannot parse the raw response: %s: %s" % (type(e), e))

    def get_results(self):
        """ Return an array of results. """
        return self._get_node('results')

    def get_uuid(self):
        """ Return UUID of the query. """
        return self._get_node('uuid')

    def get_early_return_flag(self):
        """ Return whether or not the query returned early. """
        return self._get_node('early-return')

    def _get_node(self, name):
        """ Return node with given name from parsed results.
        If the results are not parsed yet, it returns None. Raises KeyError if
        no such node exists.
        """
        if self.parsed_data is None:
            return None
        return self.parsed_data[name]


def parse_inputs():
    """ Parse and check command line and environment variables. """
    import optparse

    usage = """%prog [options] image
    The user credentials (access and secret keys) should be passed via
    environment variables KWS_ACCESS_KEY and KWS_SECRET_KEY."""

    version = "%%prog %s" % VERSION

    parser = optparse.OptionParser(usage = usage, version = version, add_help_option = True)
    parser.add_option('-c', '--check-top-id', type = 'string', default = '', metavar = 'ID',
            help = "check that the top result id is ID, fail with error code 100 otherwise")
    parser.add_option('-C', '--check-contains-id', type = 'string', default = '', metavar = 'ID',
            help = "check that the top result id is ID, fail with error code 101 otherwise")
    parser.add_option('-d', '--destinations', type = 'string', default = '',
            help = "comma separated list of destinations (empty string means all available destinations) [%default]")
    parser.add_option('-e', '--early-return', action = 'store_true', default = False,
            help = "enable early return query option [%default]")
    parser.add_option('-E', '--endpoint', type = 'string', default = QUERY_ENDPOINT,
            help = "endpoint for query requests (0 means unlimited) [%default]")
    parser.add_option('-l', '--low-confidence-results', type = 'string', default = '',
            help = "set return 'low confidence results' query option, should be one of 'always', 'as-needed' (or empty string), 'never' [%default]")
    parser.add_option('-m', '--returned-metadata', type = 'string', default = 'recognition-location,external-references,minimal,extended,resources,reference-image',
            help = "set what metadata should be returned (consult API documentation for details) [%default]")
    parser.add_option('-r', '--results-limit', type = 'int', default = RESULTS_LIMIT, metavar = 'LIMIT',
            help = "set limit on number of returned results [%default]")
    #parser.add_option('-v', '--verbose', action = 'store_true', default = False,
    #        help = "verbose logging - DEBUG logging level [%default]")

    (options, arguments) = parser.parse_args()

    if len(arguments) != 1:
        parser.error("There must be exactly one image specified on the command line.")
    if options.results_limit < 0:
        parser.error("Results limit must be a non-negative integer.")

    # inputs from environment variables
    if 'KWS_ACCESS_KEY' not in os.environ:
        parser.error("Environment variable KWS_ACCESS_KEY not set.")
    options.access_key = os.environ['KWS_ACCESS_KEY']
    if 'KWS_SECRET_KEY' not in os.environ:
        parser.error("Environment variable KWS_SECRET_KEY not set.")
    options.secret_key = os.environ['KWS_SECRET_KEY']

    return (options, arguments)


def check_top_result_id(results, expected_id):
    """ Check that the top result has the expected ID.
    Returns True if the check passes, returns False otherwise.
    """
    try:
        return (results[0]['id'] == expected_id)
    except IndexError:
        return False
    except KeyError:
        return False
    except AttributeError:
        # results not a list (malformed response)
        return False


def check_contains_result_id(results, expected_id):
    """ Check that one of the results has the expected ID.
    Returns True if the check passes, returns False otherwise.
    """
    try:
        for result in results:
            try:
                if result['id'] == expected_id:
                    return True
            except IndexError:
                return False
            except KeyError:
                pass
            except AttributeError:
                # malformed results structure
                pass
    except Exception:
        # results not a list (malformed response)
        return False
    return False


def main():
    """ CLI client. """
    (options, arguments) = parse_inputs()
    image_filename = arguments[0]
    client = QueryClient(options.access_key, options.secret_key, options.endpoint)

    # construct request
    request = QueryRequest()
    try:
        content_type = mimetypes.guess_type(image_filename)[0]
        if content_type is None:
            content_type = 'application/octet-stream'
        f = open(image_filename, 'rb')
        request.set_image(f, content_type)  # f will be closed by the call
    except IOError, e:
        print "Error opening/reading from file '%s': %s: %s" % (arguments[0], type(e), e)
        return 1
    request.set_destinations(options.destinations)
    request.set_early_return(options.early_return)
    request.set_return_low_confidence_results(options.low_confidence_results)
    request.set_results_limit(options.results_limit)
    request.set_returned_metadata(options.returned_metadata)

    response = client.execute(request)
    retval = 0
    if (options.check_top_id != '') or (options.check_contains_id != ''):
        response.parse_data()
        results = response.get_results()
        if (options.check_top_id != ''):
            if not check_top_result_id(results, options.check_top_id):
                print "Top result ID is not '%s'. Results follow:" % options.check_top_id
                print results
                retval = 100
        else:
            if not check_contains_result_id(results, options.check_contains_id):
                print "Results do not contain any with ID '%s'. Results follow:" % options.check_contains_id
                print results
                retval = 101
    else:
        print response.get_raw_data()

    return retval


if __name__ == '__main__':
    import sys
    sys.exit(main())

# vim: ai:si:sw=4:ts=4:et:sts=4:
