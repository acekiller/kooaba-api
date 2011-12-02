# Python Client for kooaba Data API

The upload\_client.py is a command line script for uploading image data and metadata via the kooaba Data API. The current version of the script covers version 1.1 of the API.

The source code is under BSD license which allows you to modify the sources and incorporate them into your code base.


## Prerequisites

To use the kooaba Data API, you have to register a developer account at http://kooaba.com. After registration, you'll obtain access credentials named _access key_ and _secret key_. The access key identifies your account and the secret key is used for signing request to the API. The credentials are valid across all the kooaba APIs, so you do not need to register if you already using access credentials for another of the kooaba APIs.

The access credentials must be set as environment variables KWS_ACCESS_KEY and KWS_SECRET_KEY. This procedure varies by platform, therefore consult your operating system or command line utility documentation.

The script requires Python version 2.5, 2.6 or 2.7. You can download suitable version from the [Python download page](http://www.python.org/getit/). If you opt for version 2.5, the script also requires simplejson package. The KWS.py (API request signing) supports also Python versions 2.4 and Python3K (3.0 and above). In the rest of the text we will assumed that the python interpreter is invoked by command "python".


## Data Model Explained

This section explains concepts used in the kooaba Data API. While not strictly necessary, familiarity with the concepts will help you when using the Data API either via the script or directly from your source code.

You already encountered _group_, which is a partition in our system. One of them was assigned to your exclusive use and you'll be uploading your data to it[1]. Each group can contain arbitrary number of items. _Item_ is a central object of the metadata model as all the metadata entries are associated with one of the items. The common set of metadata is limited to _title_, _external ID_ (integer reference to your database), _reference ID_ (string reference to your database) and _locale_. Each item must have a title, but the other metadata are optional.

Additional metadata fields can be activated by setting the _medium type_ of the item[2]. Some of the additional metadata fields are mandatory. The list of available medium types and their metadata fields can be found in the [kooaba Data API reference](https://github.com/kooaba/kooaba-api/tree/master/data_api).

You can attach more than one _image_ to your item. Whichever of them gets recognized, the same item metadata are returned. This comes handy for example when you want to recognize a product not only from a front side view, but also from a back side view (small tip for cylindrical packagings: upload product photos taken from multiple angles as you never know when the product faces the customer by its side).

The _image status_ shows if the image is ready for recognition. The image is ready for recognition when it's marked as ACTIVE. Any other state indicates that the recognition of the image is not possible. New image starts in INACTIVE state. When activated (the scripts activates the images by default), the state changes to ACTIVATION\_QUEUED. This indicates to our indexing system that the image should be picked up for processing. The state changes to ACTIVATING when the indexing system starts to process the image and finally to ACTIVE when the image becomes ready for recognition via the Query API. One can deactivate the active image which puts it to DEACTIVATION\_QUEUED state. The image in this state cannot be recognized by our system, but any activation enables the recognition immediately without any further processing. The cleanup process eventually picks up the DEACTIVATION\_QUEUED images (and sets them to INACTIVE state when done), but the image can stay in this state for extended periods of time. You can always _delete_ the image when not needed anymore.

The last component in the metadata model is _item resource_. Each item can have zero or more resources. The resources are intended to point to a related resources, and come in two flavors, _URI_ and _file_. The URI is most commonly used for URLs, but you can use your own schemes as well. The file resource uploads a file to the kooaba system (useful if you do not wish to host the related data yourself). Each resource requires a _title_ and must be assigned to a _section_. The section name can be an arbitrary string. If you do not want to organize the resources into sections, it's recommended to use an empty string as a section.

The last concept to cover is _upload_. The script hides this concept, but it is useful if you want to upload images or resource files from your own code. The upload is a temporary action and the uploaded data are removed unless assigned as an image to an item or as a file to a resource.

Deleting an item deletes any images, item resources and other metadata associated with the item.

  [1] The group ID number is in the welcome e-mail together with the access credentials.

  [2] Medium types have to be enabled for your group. Contact support@kooaba.com to arrange the desired medium types for your group.


## Script features

Typical workflow looks like this:

1. Create a new item (metadata, medium type, one or more images)
2. Add resources (if any)
3. Check that the image is active (repeat if the image is not active yet)

The commands can print progress, retrieved data or nothing depending on what action is being performed. The script indicates success or failure by its return status. API errors are displayed in a raw form and contain description of what went wrong.

To see communication between the client script and the API server, use command line switch --debug.

### Creating a new item

A basic item with an image is created using:

    python upload_client.py --create-item-in <GROUP_ID> --title <TITLE> IMAGE_FILE

For optional metadata entries, add one or more of:

    --external-id EXTERNAL_ID
        Adds integer ID that can refer to your internal database (e.g. ID clumn of your database table)
    
    --reference-id REFERENCE_ID
        Adds string ID that can refer to your internal database (e.g. product identifier)
    
    --locale
        Locale string (e.g. to denote language or target region of the item)

Additional metadata entries are enabled by specifying Media Type:

    python upload_client.py --create-item-in <GROUP_ID> --title <TITLE> --media-type <MEDIA_TYPE> IMAGE_FILE

The media type is for example Art or Book. Each type has different set of additional metadata (listed in [kooaba Data API reference](https://github.com/kooaba/kooaba-api/tree/master/data_api)), some of which might be mandatory. The metadata fields are specified by one or more metadata entries:

    --metadata <NAME:VALUE>

where NAME is name of the field and VALUE is the value of the field. For example:

    --metadata "artist:Leonardo da Vinci"

The script prints its progress and IDs of any manipulated items or images:

    Created item 35552981
     added image 23396181
     activated image 23396181

### Updating an existing item

Updating item is similar to creating an item:

    python upload_client.py --update-item <ITEM_ID> [metadata switches] [image files]

All the command line switches used for creating an item are possible, but none of them is mandatory. It is possible to...

... change any number of metadata entries:


    python upload_client.py --update-item <ITEM_ID> [--title <TITLE>] [--locale <LOCALE>]

... change medium type:

    python upload_client.py --update-item <ITEM_ID> --medium-type <MEDIUM_TYPE> [--metadata <NAME:VALUE>] [...]

... add new images to the item:

    python upload_client.py --update-item <ITEM_ID> IMAGE_FILE [IMAGE_FILE_2] [...]

... or any combination of the above.

A progress report is printed as in the case of creating an item.

### Adding item resources

Title and section are mandatory for each item resource. There are two types of resources...

... URI resource:

    python upload_client.py --create-resource-for <ITEM_ID> --title <TITLE> --section <SECTION> --uri <URI>

e.g.

    python upload_client.py --create-resource-for 5974 --title "Musée du Louvre" --section "Links" --uri "http://www.louvre.fr"

... file resource:

    python upload_client.py --create-resource-for <ITEM_ID> --title <TITLE> --section <SECTION> <FILE>

e.g.

    python upload_client.py --create-resource-for 5974--title "Museum guide" --section "Extras" guide.pdf

Neither type prints any data.

### Getting and setting image status

The image status can be queried like this:

    python upload_client.py --get-image-status <IMAGE_ID>

It will return status of the image, e.g.:

    ACTIVE

To activate an image, issue an activation command:

    python upload_client.py --activate-image <IMAGE_ID>

To deactivate an active image, issue a deactivation command:

    python upload_client.py --deactivate-image <IMAGE_ID>

Neither activation nor deactivation print any data.

### Exploring the data

Besides image status (described in the previous section), one can explore...

... group statistics:

    python upload_client.py --get-group <GROUP_ID>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<group>
  <id type="integer">1</id>
  <title>Testing</title>
  <images>
    <status>
      <inactive type="integer">0</inactive>
      <activation_queued type="integer">0</activation_queued>
      <activating type="integer">0</activating>
      <active type="integer">9</active>
    </status>
  </images>
  <items>
    <count type="integer">8</count>
  </items>
</group>
```

... items in a group:

    python upload_client.py --get-group-items <GROUP_ID>

```xml
<items type="array">
  <item>
    <created-at type="datetime">2008-08-08T19:17:02+02:00</created-at>
    <group-id type="integer">1</group-id>
    <id type="integer">5974</id>
    <title>Mona Lisa</title>
    <updated-at type="datetime">2011-11-25T12:55:49+01:00</updated-at>
    <reference-id nil="true"></reference-id>
    <images type="array">
      <image>
        <created-at type="datetime">2008-08-08T19:17:32+02:00</created-at>
        <creator-id type="integer">4</creator-id>
        <external-id type="integer" nil="true"></external-id>
        <external-url nil="true"></external-url>
        <file-name>f55a0d445e2ecc7ef067622587f9721101cc7c50.jpeg</file-name>
        <file-sha1>f55a0d445e2ecc7ef067622587f9721101cc7c50</file-sha1>
        <file-size type="integer">587473</file-size>
        <file-type>image/jpeg</file-type>
        <id type="integer">15071</id>
        <image-file-id type="integer" nil="true"></image-file-id>
        <import-id type="integer" nil="true"></import-id>
        <item-id type="integer">5974</item-id>
        <quality type="integer">25</quality>
        <server-index-id type="integer">7413</server-index-id>
        <similar-id type="integer" nil="true"></similar-id>
        <status>ACTIVE</status>
        <updated-at type="datetime">2011-11-23T16:46:50+01:00</updated-at>
        <updater-id type="integer">19602</updater-id>
        <uri>s3://kooaba-storage-production/images/f5/5a/f55a0d445e2ecc7ef067622587f9721101cc7c50-15071.jpeg</uri>
      </image>
    </images>
  </item>

  <!-- 7 more items removed for clarity -->

</items>
```

... item:

    python upload_client.py --get-item <ITEM_ID>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<item>
  <created-at type="datetime">2008-08-08T19:17:02+02:00</created-at>
  <group-id type="integer">1</group-id>
  <id type="integer">5974</id>
  <title>Mona Lisa</title>
  <updated-at type="datetime">2011-11-25T12:55:49+01:00</updated-at>
  <reference-id nil="true"></reference-id>
  <images type="array">
    <image>
      <created-at type="datetime">2008-08-08T19:17:32+02:00</created-at>
      <creator-id type="integer">4</creator-id>
      <external-id type="integer" nil="true"></external-id>
      <external-url nil="true"></external-url>
      <file-name>f55a0d445e2ecc7ef067622587f9721101cc7c50.jpeg</file-name>
      <file-sha1>f55a0d445e2ecc7ef067622587f9721101cc7c50</file-sha1>
      <file-size type="integer">587473</file-size>
      <file-type>image/jpeg</file-type>
      <id type="integer">15071</id>
      <image-file-id type="integer" nil="true"></image-file-id>
      <import-id type="integer" nil="true"></import-id>
      <item-id type="integer">5974</item-id>
      <quality type="integer">25</quality>
      <server-index-id type="integer">7413</server-index-id>
      <similar-id type="integer" nil="true"></similar-id>
      <status>ACTIVE</status>
      <updated-at type="datetime">2011-11-23T16:46:50+01:00</updated-at>
      <updater-id type="integer">19602</updater-id>
      <uri>s3://kooaba-storage-production/images/f5/5a/f55a0d445e2ecc7ef067622587f9721101cc7c50-15071.jpeg</uri>
    </image>
  </images>
</item>
```

... image:

    python upload_client.py --get-image <IMAGE_ID>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<image>
  <created-at type="datetime">2008-08-08T19:17:32+02:00</created-at>
  <creator-id type="integer">4</creator-id>
  <external-id type="integer" nil="true"></external-id>
  <external-url nil="true"></external-url>
  <file-name>f55a0d445e2ecc7ef067622587f9721101cc7c50.jpeg</file-name>
  <file-sha1>f55a0d445e2ecc7ef067622587f9721101cc7c50</file-sha1>
  <file-size type="integer">587473</file-size>
  <file-type>image/jpeg</file-type>
  <id type="integer">15071</id>
  <image-file-id type="integer" nil="true"></image-file-id>
  <import-id type="integer" nil="true"></import-id>
  <item-id type="integer">5974</item-id>
  <quality type="integer">25</quality>
  <server-index-id type="integer">7413</server-index-id>
  <similar-id type="integer" nil="true"></similar-id>
  <status>ACTIVE</status>
  <updated-at type="datetime">2011-11-23T16:46:50+01:00</updated-at>
  <updater-id type="integer">19602</updater-id>
  <uri>s3://kooaba-storage-production/images/f5/5a/f55a0d445e2ecc7ef067622587f9721101cc7c50-15071.jpeg</uri>
</image>
```

### Deleting data

You can delete whole item with all the associated data:

    python upload_client.py --delete-item <ITEM_ID>

or only a single image:

    python upload_client.py --delete-image <IMAGE_ID>

The delete operation does not print anything.

## Links

[kooaba Data API reference](https://github.com/kooaba/kooaba-api/tree/master/data_api)
[latest version of the upload script](https://github.com/kooaba/kooaba-api/tree/master/data_api/python)
[kooaba APIs documentation](https://github.com/kooaba/kooaba-api)
