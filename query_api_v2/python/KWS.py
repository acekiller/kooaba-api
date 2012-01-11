"""
Computation of KWS request signatures.

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

import base64
import sys

# Prepare hash functions
try:
    # Python 2.5 and newer
    import hashlib

    def compute_md5_hex(data):
        h = hashlib.md5()
        h.update(data)
        return h.hexdigest()

    def compute_sha1_base64(data):
        h = hashlib.sha1()
        h.update(data)
        return base64.b64encode(h.digest())
except ImportError:
    # Python 2.4
    import md5
    import sha

    def compute_md5_hex(data):
        h = md5.new()
        h.update(data)
        return h.hexdigest()

    def compute_sha1_base64(data):
        h = sha.new()
        h.update(data)
        return base64.b64encode(h.digest())

# Python 2 vs 3
if sys.hexversion > 0x03000000:
    EOL = b"\n"

    def to_hashable(data):
        """ Convert data to a hashable type. """
        if isinstance(data, bytes):
            return data
        else:
            return str(data).encode()

    def ascii_to_hashable(data):
        """ Convert ASCII text data to a hashable type. """
        if isinstance(data, bytes):
            return data
        else:
            return str(data).encode('ascii')
else:
    EOL = "\n"

    def to_hashable(data):
        """ Convert data to a hashable type. """
        return str(data)

    def ascii_to_hashable(data):
        """ Convert ASCII text data to a hashable type. """
        return str(data)


__all__ = ['KWSSigner']


class KWSSigner:
    """ Class generating KWS request signatures. """

    def __init__(self, secret_key):
        self.secret_key = ascii_to_hashable(secret_key)

    def sign_with_content_md5(self, method, content_md5, content_type, date, request_path):
        """
        Sign request using MD5 hash of the request content.

        Arguments:
           * method: HTTP verb (e.g. GET) used in the request
           * content_md5: MD5 hash of the content (body) of the request. FIXME empty request (GET)
           * content_type: Content type specified in the request. FIXME empty request (GET)
           * date: Value of the date header sent with the request (as GMT). The
              signature is only valid if the request is performed within 15
              minutes of the specified date.
           * request_path: Path of the destination resource (e.g. /user.xml).
              It has to be URL encoded if necessary.

        Returns the signature as str (Python 2) or bytes (Python 3).
        """
        toSign = ascii_to_hashable(self.secret_key)+EOL+EOL
        toSign += ascii_to_hashable(method)+EOL
        toSign += ascii_to_hashable(content_md5)+EOL
        if content_type is not None:
            toSign += ascii_to_hashable(content_type)+EOL
        else:
            toSign += EOL
        toSign += ascii_to_hashable(date)+EOL
        toSign += ascii_to_hashable(request_path)
        return compute_sha1_base64(toSign)

    def sign_with_content(self, method, content, content_type, date, request_path):
        """
        Sign request using request content.

        The content argument is body of the request. Under Pyton 3, it is
        strongly recommended to pass it as bytes to avoid problems with
        incorrect encoding. See sign_with_content_md5 for the description of
        the remaining arguments.

        Returns the signature as str (Python 2) or bytes (Python 3).
        """
        content_md5 = compute_md5_hex(to_hashable(content))
        return self.sign_with_content_md5(method, content_md5, content_type, date, request_path)

    def sign_with_no_content(self, method, content_type, date, request_path):
        """
        Sign request that uses no body (e.g. empty GET).

        Returns the signature as str (Python 2) or bytes (Python 3).
        """
        return self.sign_with_content_md5(method, "", content_type, date, request_path)


# vim: ai:si:et:sw=4:ts=4:
