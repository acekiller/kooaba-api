# Query API v2 (Beta)

Revision 2012-01-12.



## Introduction

This document describes the _Beta_ version of the Query API v2 (abbreviated as _Beta version_ or _Beta API_ in the rest of the document). The Query API v2 aims to be an easily extensible API and addresses some limitations of Query API v1. There are three major differences when compared to the Query API v1:

* **Extensible:** It has a defined procedure for introducing new features, including those that go beyond simple image matching. If you are in a need of a specific feature, don't hesitate to contact us!
* **Flexible verbosity:** The amount of metadata is configurable per-query, ranging in several steps from a bare recognition identifier to all available metadata. This allows to keep the data transfers small and to obtain the result faster in case when no or only basic metadata are needed.
* **Not a copy of v1:** Not all features of Query API made it to the v2. The biggest omission is filtering by metadata (i.e. you can't limit results to a specific locale). On the bright side, some quirks were fixed too (e.g. an arbitrary limitation of only returning item resources with scheme `urn:ean`).

The Beta version serves as a staging environment for new features and other changes. This allows to catch and fix API mistakes before they enter the production version (see next section for details). Another purpose of the Beta version is to give the developers early access to new features. This allows closer cooperation when developing the features.

The Beta version is primarily intended for interfacing with development and testing versions of Query API clients. If you plan to use the Beta version of the API in bigger deployment such as public beta testing, contact us so we can synchronize on any possible API changes or migration of the desired features to the production version of the Query API v2.



## Forward and Backward Compatibility

The Query API v2 is designed to be extensible. The future revisions will bring additional features, but will not remove or break behavior of previous revisions of the API. This allows clients created against older revisions of the API to function normally as long as they follow these basic guidelines:

* Do not include unknown values. Unless explicitly stated in this documentation, unknown values are silently ignored. Future revisions might define meaning for these values which can break the response (this is not an API breaking behavior as the defaults for new options are always backward compatible).
* Do not rely on ordering of the data elements in the response. Unless explicitly stated otherwise, the ordering of the data elements is arbitrary and there are no guarantees to stay the same even between two consecutive but identical requests. This holds both for objects (also known as maps or hashes) and for arrays.

Please note that the compatibility requirements are strictly followed only in the production version of the API. The compatibility rules in the Beta version can and occasionally will be broken. If a problem is discovered in a feature that is only in the Beta version, the preferred course of action is to fix the problem even if it breaks the API. When possible with reasonable effort, both variants (before and after fixing the problem) will be supported to ease transition to the fixed API. 



## API Extension Procedure

New API extensions first enter the Beta API. In this phase the extension is only available through the Beta API endpoint and is documented as a beta-only feature in the [Beta API documentation](https://github.com/kooaba/kooaba-api/tree/beta/query_api_v2/). The relaxed compatibility rules of the Beta API allow changes in case of problems that went undetected during the development. The extension will stay in the beta-only state for a period required to test the quality of the extension. Both implementation quality and usefulness of the extension to the API users is evaluated, therefore don't hesitate to test new features and report any problems, wishes or other observations. The beta period is generally foreseen to be one to three months, but can be shorter if there is demand and sufficient amount of use of the feature (or longer if the feature sees no use).

The extension might be removed from the Beta API if there are severe problems with its implementation or if it is not used. In the later case, the extension will be marked in the documentation as "Removal Pending" and its implementation will be removed no sooner than 30 days after publishing the "Removal Pending" notice.

Successful extensions will be moved to production system. They will enter the strict compatibility mode as soon as the extension is published in the documentation of the production Query API v2.

We would like to see the extension process to be driven by you - our API users. If you have needs that are not sufficiently covered by this API, don't hesitate to let us know! Share your desired use cases, technical problems or any improvement ideas with us, we will work with you and try to deliver the best for the future revisions of this API.


### Discrepancies between documentation and implementation

As they say, to err is human and the API developers are mere humans, so sooner or later there will be a bug or inconsistency between what was meant and what happened. The preferred solution is to follow the documentation if possible, but there are few cases when the documentation will be fixed instead of the implementation:

* Fix would not be compatible and
 * the implementation is in heavy use
 * timely API client software upgrade is not possible or too complex to execute
* The documentation is in error (e.g. incorrect wording)

The discovered bugs will be documented in **Known Bugs** and the fixes will be documented in the **Errata Log** section of the documentation.



## Performing a Query

To perform a query you'll need following:

* Image in a JPEG or PNG format, maximum 3Mpix and no more than 2MB in size
 * JPEG must be either grayscale or in RGB colorspace, 8bit per pixel channel
 * PNG must be a grayscale, palleted or RGB (i.e. no alpha channel), 8bit per color channel
* API access credentials (access key and secret key)
* Software to communicate with the Query API v2 (you can use the example code in this repository)

Although both JPEG and PNG image formats with resolutions up to 3Mpix are accepted, optimizing image parameters for query can speed up recognition response times while keeping high recognition quality. These two settings are recommended for purposes of image matching:

* **WiFi:** JPEG format, compression quality 75 (on libjpeg scale 1..100), longer side of the image is 640 pixels (also known as VGA resolution). Image file size is around 65kiB, depending on the image content.
* **Mobile networks:** JPEG format, compression quality 30 (on libjpeg scale 1..100), longer side of the image is 320 pixels (also known as QVGA resolution). Image file size is around 15kiB, depending on the image content. Recognition rate on a challenging data set is only 4% lower than with the WiFi settings. Increasing the quality to 50 yields image size around 18kB and the recognition rate is less than 2% worse than the mentioned baseline.

Image matching supports grayscale input images without loss of recognition rate which allows further reduction of query file size.

The credentials are used to sign the request set to the API. The signing procedure is described in the [documentation](https://github.com/kooaba/kooaba-api/tree/master/authentication).

The software should generate a signed HTTP query request in accordance to the documentation described in the section "Request Specification" and should be able to deal with response formatted according to the documentation in the section "Response Specification".



## Request Specification

Definitions:

* Destination
 * Identifies a partition of recognizable content. Currently a group number.
* Boolean flag
 * Accepted values on input are 0, 1, true and false (both are case insensitive).
 * The canonical values on output are (case sensitive) true and false, but conforming client must accept values 0 and 1 as well.
* Early return
 * Recognition results (positive or negative recognitions) for different parts of data are known at different time points. By default (early return disabled) the query waits for all the recognition results before sending a response. Enabling early return allows the query to return response as soon as a high confidence recognition is detected. If there is no high confidence recognition, the query returns only after all recognition results are known.
 * One can ensure that results of selected destinations are known before returning early by listing the selected destinations in the mandatory-destinations part of the request.
 * This optimization is especially helpful when only a single result is requested.
* Endpoint
 * URL prefix for sending query requests. The Beta API endpoint is one of
  * http://query-beta.kooaba.com
  * https://query-beta.kooaba.com

The query request is a `POST` request to path `/v2/query` to the endpoint with `multipart/form-data` payload. It is required to set the `Accept` header of the request to `application/json`. Authentication and request signature is performed via a KWS header (the algorithm is common for all kooaba APIs and is detailed in https://github.com/kooaba/kooaba-api/tree/master/authentication). Request size limit is 2MB for the request body. Each field can occur only once. Multiple occurrence of a field in the request leads to undefined behavior. The field names are case sensitive. Any unknown fields are ignored. Standard fields are:

* `image`
 * Mandatory
 * Image in binary encoding. Only JPEG and PNG formats are accepted. The resolution must be 3Mpix or less.
* `destinations`
 * Optional, defaults to empty list
 * Comma separated list of destinations (case sensitive). Any whitespace around any of the destinations is ignored.
 * Empty list is equivalent to specifying all destinations that the user is allowed to query.
* `results-limit`
 * Optional, defaults to `0`
 * A non-negative integer indicating maximum number of results to return or 0 to indicate no limit for number of returned results.
* `early-return`
 * Optional, defaults to `false`
 * A boolean flag indicating if an early return is desired.
* `mandatory-destinations`
 * Optional, only used when early return is enabled, defaults to empty list
 * List of destinations whose results are required before returning early.
* `return-low-confidence-results`
 * Optional, defaults to `as-needed`
 * When should be results of low confidence returned (see Response section for definition of confidence level). Accepted values are
  * `never` - Do not return any results of low confidence.
  * `as-needed` - Return results of low confidence only when there is no high or normal confidence result available.
  * `always` - Always return results with low confidence.
* `returned-metadata`
 * Optional, defaults to an empty list
 * List of metadata types to return. Accepted values are:
  * `recognized-area` – Description of what part of the query image is responsible for the recognition (i.e. the results should contain bounding-box and reference-projection nodes).
  * `external-reference` – External references/identifiers associated with the result (external-id and reference-id).
  * `minimal` – Minimal human readable description of the recognition (e.g. tile and medium type).
  * `extended` – Medium specific metadata.
  * `resources` – Resource metadata associated with the recognition.
 * Recognition ID and recognition quality metadata are always returned.
* `resource-sorting`
 * Optional, defaults to an empty list
 * List of stable sorting passes to perform on the item resources. Supported values are:
  * `creation-time` – Sort to order in which the resources were created.
  * `position` – Sort according to the resource position.
   * At the time of writing, the Data API does not support setting or changing the position.
  * `section` – Group the resources by section. The sections are ordered lexicographically (see the note).
 * **Note:** The server does not support alphabetical sorting by section or title because such sorting is locale specific. Sorting on the client is preferable as the client already supports the required locale, even if it is one of the exotic ones.
* `resource-uploads`
 * Optional, defaults to an empty string
 * Specifies details on how to handle resource uploads. Supported values are:
  * `expires-in=T` – Set the expiration time of the pre-signed URLs to T seconds.

The response can be compressed to reduce amount of transferred data by specifying gzip as preferred encoding. This is done by setting the `Accept-Encoding` header of the request to e.g.:
```
Accept-Encoding: gzip;q=1.0, identity; q=0.5, *;q=0
```



## Response Specification

The response is in a JSON format. There are two top level nodes inside of the anonymous root object:

* `results`
 * Always present
 * Contains an array of results sorted by recognition quality (highest quality first).
* `early-return`
 * A boolean flag indicating that the early return behavior was triggered.

The results are always sorted by recognition quality (lexicographically by pair \[confidence_level, score\]).

The following sections will describe building blocks of the result (some are optional, see `returned-metadata` request option). A full response example will follow. Undefined values (e.g. there is no external_id) are omitted. Empty metadata sections (e.g. there are no item resources associated with the recognized image), the section might be omitted too.


### Basic result entry
The minimal result entry contains:

* `id`
 * ID of the recognized object.
 * The ID is structured as type:value where the type indicates what kind of recognition produced the result and the value is an unique ID for what was recognized. The actual interpretation of the value is type dependent. The type is hierarchical with subtype separated by a dot (e.g. `image.sha1`). Subtypes can be chained.
* `confidence_level`
 * Confidence level of the recognition. There are 3 levels defined:
  * `high` - A high confidence recognition. Chance of the recognition being incorrect is very low.
  * `normal` - A normal confidence recognition. Chance of the recognition being incorrect is low.
  * `low` - A low confidence recognition. Chance of the recognition being incorrect is moderate to high. It is suggested that this recognition is only used when no other recognition is available.
* `score`
 * Relative quality of the recognition within the confidence level (floating point number). Please do not rely on the absolute value of the score, as the scale can change at any time.


### Recognized area specification
The corners are specified by x and y coordinates (floating point numbers).

* `bounding_box`
 * Indicates area on the query image responsible for the result (e.g. which part of the query image matched the reference image)
 * Bounding box is specified by four corners (upper right first, then clockwise). Each corner is specified by x and y coordinates. Values are in pixel coordinates.
* `projection`
 * Approximate projection of the reference to the query image.
 * Corners of recognized reference object projected to the query image. By convention, the reference object is enclosed in a rectangle, whose upper left corner is the first corner of the projection and the next ones are following in clockwise order. Corner values follow the same convention as in the bounding box.


### External references
There are two possible external references:

* `external_id`
 * Numeric (integer) ID.
* reference_id
 * String ID.


### Minimal human readable description
The minimal human readable metadata for image matching (`image.sha1` ID type) are:

* `type`
 * Media type of the recognized object (e.g. book)
* `title`
 * Title or name of the recognized object.


### Extended metadata
Arbitrary amount of metadata entries grouped under `extended` node. The entries vary by type (which is specified in Minimal human readable description). See [Medium Types documentation](https://github.com/kooaba/kooaba-api/blob/master/data_api/Medium_Types.md) for more information.


### Resources metadata
Item resource metadata associated with the recognition. It is an array node named `resources`. Each entry consists of:

* `title`
 * Title for the metadata entry.
* `section`
 * Section this entry belongs to.
* `uri`
 * URI for the resource metadata entry. If the entry describes a file upload, it will be a pre-signed URL. The default validity of the pre-signed URL is 3600 seconds, but can be changed using the `resource-uploads` field of the request.
* `content_type`
 * Content type of the uploaded file (if the entry describes a file upload).

The array is unordered by default, but if the `resource-sorting` request option was specified, the resources are sorted according to the criteria from the option.


### Result ID type prefix
As mentioned above, the result ID value is prefixed with a type identifier. This identifier indicates type of the recognition. It can be sub-typed by appending one or more `.subtype` specifications. Possible values:
* `image.sha1`
 * Recognition of a reference image. The recognition image is identified by its SHA1 hash encoded as a hexadecimal string.


### Full response example
The response was formatted for readability.

```
{
  "results":
    [
      {
        "confidence_level":"high",
        "score":3.253968,
        "bounding_box":
          [
            { "x":115.0, "y":39.0 },
            { "x":115.0, "y":216.0 },
            { "x":615.0, "y":216.0 },
            { "x":615.0, "y":39.0 }
          ],
        "reference_projection":
          [
            { "x":-1.407532, "y":-2.519043 },
            { "x":-2.0, "y":245.0 },
            { "x":627.0, "y":245.0 },
            { "x":623.0, "y":0.0 }
          ],
        "id":"image.sha1:f55a0d445e2ecc7ef067622587f9721101cc7c50",
        "extended":
          {
            "title":"Latest Blockbuster",
            "released_on":"2011/11/11"
          },
        "resources":
          [
            {
              "uri":"http://www.blockbustermovie.com",
              "section":"Links",
              "title":"Movie website"
            },
            {
              "uri":"http://www.blockbustermovie.com/trailer/",
              "section":"Links",
              "title":"Movie Trailer"
            },
            {
              "uri":"https://kooaba-storage-production.s3-eu-west-1.amazonaws.com/item_resource_attachments%2F58%2F6c%2F586c56c98a6fbd1854e7d4c14905cc1e4ccb66d5-956499.zip?Expires=1326285835&AWSAccessKeyId=AKIAJMGBQT6NJULUPQOQ&Signature=59JecEtKEoQJX7DHGEOScu44Lrs%3D",
              "section":"Materials",
              "content_type":"application/zip",
              "title":"Press Kit"
            }
          ],
        "type":"Movie",
        "title":"Latest Blockbuster"
      }
    ],
  "uuid":"a0e039af3e934ad59b16baba6061b156",
  "early-return":false
}
```



## Known Bugs

None so far.



## Errata Log

None so far.



## Changelog

* 2012-01-11 Initial public release
* 2012-01-12 Clarification: The `Accept` HTTP header is mandatory.
* 2012-02-21 Added resource sorting request option. Added option for setting signature expiration for resource uploads.
