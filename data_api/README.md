# kooaba Data API
Data API Version 1.1 (2010-12-20)


## Introduction
The kooaba data API provides the ability to manage content in kooaba's image recognition system.
It is implemented as a [RESTful](http://en.wikipedia.org/wiki/Representational_State_Transfer) web service using XML data over HTTP and allows to add and remove
images, as well as their associated meta data. Images that have been uploaded via the Data API are then available for recognition via the Query API.

## Data Model

The recognition system is organized into groups, items, images and item-resources. API users are member
of one or more groups, which they can add data to. 

Each item can have one or more reference images associated with it. Each of these images can "trigger" the recognition of the item, when a query image matches the respective database image.

Each item can also have meta-data associated with it. We call this meta-data item-resources. (Not to be confused with the resources in the REST terminology.)

## Basic Workflow

The workflow follows the data structure (and is reflected accordingly in the REST URLs).

Before adding any data to the system, you need write permission for a _group_. If you don't have write access to any group yet, please contact kooaba sales to get access.

When adding data to the system, you start with an _item_. An item represents the physical object you 
want to make recognizable. It is a container for images which identify the object. Besides the images, the item contains also associated meta-data, among other things the mandatory title. A particularly important meta-data field for an item is the reference-id. This is an id you can set, and which is later returned when making requests. Typically you would use this to associate the items in our system with your internal id scheme.

After an item has been created, you can add associated _images_. To that end, you first upload a binary file to our service, and then associate it with an image resource.

Each uploaded images then goes through an indexing process, until it is live. This process may take minutes up to a couple of hours currently depending on system load and some internal settings for your group. During this process the current status is reflected in a status variable, which can be checked via the API at any time.

## Authentication

All requests to the kooaba API's have to be authenticated. Authentication is managed using our custom KWS HTTP authentication scheme. 

Authentication is [described on the Query API Documentation](../query_api/README.md).

## Making API Requests

There are two basic types of requests, read requests and write requests. Read requests are HTTP GET
requests which return XML-encoded data representations of the requested resources. Write requests
allow to create new resources (POST), update existing resources (PUT) or delete resources (DELETE).

### Access Control

When trying to access resources which do not belong to the user, an empty response with status code
`403 Forbidden` is returned.

When trying to perform write operations on read-only resources, an empty response with status code
`405 Method Not Allowed` is returned.

## Overview of REST Resources

All data can be accessed and as REST resources. Below follows a description of all resources and their actions.

### Groups

Groups are read-only.

#### Show

Returns an existing group.

**Request**

    GET /api/groups/#{id}.xml

**Response**

    Status: 200 OK

    <group>
      <id type="integer">6</id>
      <title>API Group</title>
      <items-count type="integer">2</items-count>
    </group>

#### Index

Returns a list of groups the API user is a member of.

**Request**

    `GET /api/groups.xml`

**Response**
    
    Status: 200 OK

    <groups type="array">
      <group>
        ...
      </group>
    </groups>

--- 

### Items

Item have read and write permissions.

#### Show

Returns an existing item.

**Request**

    GET /api/items/#{id}.xml

**Response**

    Status: 200 OK

    <item>
      <id type="integer">70</id>
      <title>Item Title from API</title>
      <group-id type="integer">6</group-id>
      <created-at type="datetime">2010-02-11T09:45:23+01:00</created-at>
      <updated-at type="datetime">2010-02-11T09:45:23+01:00</updated-at>
      <images type="array">
        <image>
          <id type="integer">163</id>
          <file-name>upload.jpeg</file-name>
          <file-size type="integer">134422</file-size>
          <file-type>image/jpeg</file-type>
          <status>INACTIVE</status>
          <item-id type="integer">71</item-id>
          <created-at type="datetime">2010-02-11T09:47:47+01:00</created-at>
          <updated-at type="datetime">2010-02-11T09:47:47+01:00</updated-at>
        </image>
      </images>
    </item>

#### Index

Returns a list of items belonging to a given group

**Request**

    GET /api/groups/#{group_id}/items.xml

**Response**

    Status: 200 OK

    <items type="array">
      <item>
        ...
      </item>
      <item>
         ...
      </item>
    </items>


Returns a list of items belonging to a given group and having the attribute values specified in xml

**Request**

    GET /api/groups/#{group_id}/items.xml?item[title]=MyTitle
    params can be item[title] or item[reference_id]
 
**Response**

    Status: 200 OK

    <items type="array">
      <item>
        <title>MyTitle</title>
        ...
      </item>
      <item>
        <title>MyTitle</title>
        ...
      </item>
    </items>

---

#### New

Returns a blank XML template that may be used as a guide for populating the fields of a new Item record.

**Request**

    GET /api/groups/#{group_id}/items/new.xml

**Response**

    Status: 200 OK

    <item>
      ...
    </item>


---

#### Create

Creates a new item belonging to a given group. 

The `title` field is mandatory and has to be set to a human-readable and -understandable string.

An optional `reference-id` can be specified, which is returned as part of a response when querying via the Query API.

**Request**

    POST /api/groups/#{group_id}/items.xml

    Content-Type: application/xml

    <item>
      <title>Item Title from API</title>
      <reference-id>776777</reference-id>
    </item>
    
**Response**

    Status: 201 Created
    Location: http://my.kooaba.com/api/items/#{new-item-id}.xml

    <item>
      <id type="integer">78</id>
      <title>Item Title from API</title>
      <reference-id>776777</reference-id>
      <group-id type="integer">6</group-id>
      <created-at type="datetime">2010-02-11T15:17:50+01:00</created-at>
      <updated-at type="datetime">2010-02-11T15:17:50+01:00</updated-at>
      <images type="array"/>
    </item>

#### Update

Updates information about the given item.

**Request**

    PUT /api/items/#{id}.xml
    
    Content-Type: application/xml

    <item>
      <title>Updated Title from API</title>
    </item>

**Response**

    Status: 200 OK

#### Destroy

Destroys the given item and all of its associated images.

**Request**

    DELETE /api/items/#{id}.xml

**Response**

    Status: 200 OK

---

### Images

Image resources have read and write permissions. In order to create images, image files must be uploaded first
in order to be attached to an image resource.

#### Show

Returns an existing image.

**Request**

    GET /api/images/#{id}.xml

**Response**

    Status: 200 OK

    <image>
      <id type="integer">2592</id>
      <item-id type="integer">4067</item-id>
      <external-id type="integer" nil="true"></external-id>
      <file-name>upload.jpeg</file-name>
      <file-sha1>94cc5d8169302127fe8b058d1108cee29df3bebb</file-sha1>
      <file-size type="integer">134422</file-size>
      <file-type>image/jpeg</file-type>
      <status>INACTIVE</status>
    </image>

#### Index

Returns a list of images belonging to a given item.

**Request**

    GET /api/items/#{item_id}/images.xml

**Response**

    Status: 200 OK

    <images type="array">
      <image>
        ...
      </image>
      <image>
        ...
      </image>
    </images>

#### Create

Creates a new image belonging to a given item using a previously uploaded file. (See below for file uploads.)

An image is associate with an item via the request URL.

**Request**

    POST /api/items/#{item_id}/images.xml

    Content-Type: application/xml

    <image>
      <file>
        <upload-id>94cc5d8.96</upload-id>
      </file>
    </image>

**Response**

    Status: 201 Created
    Location: http://my.kooaba.com/api/images/#{new-image-id}.xml

    <image>
      <id type="integer">2600</id>
      <item-id type="integer">4076</item-id>
      <external-id type="integer" nil="true"></external-id>
      <file-name>upload.jpeg</file-name>
      <file-sha1>94cc5d8169302127fe8b058d1108cee29df3bebb</file-sha1>
      <file-size type="integer">134422</file-size>
      <file-type>image/jpeg</file-type>
      <status>INACTIVE</status>
      <created-at type="datetime">2010-02-12T15:57:20+01:00</created-at>
      <updated-at type="datetime">2010-02-12T15:57:20+01:00</updated-at>
    </image>

#### Destroy

Destroys the given image.

**Request**

    DELETE /api/images/#{id}.xml

**Response**

    Status: 200 OK

#### Status Update

Changes the status of the given image. The value of the status name must be ‘ACTIVE’ or ‘INACTIVE’. Note that changing the status of an image might fail if the image is in the process of being activated.

After uploading an image and setting the status to ‘ACTIVE’, the image will go through the following stati until is live:

    ACTIVATION_QUEUED   Request for activation has been received and image is ready for indexing
    ACTIVATING          Image is being indexed
    ACTIVE              Image is live
  
Similarly, when setting an image to inactive, the sequence is as follows:

    DEACTIVATION_QUEUED Request for deactivation received
    DEACTIVATING        Image is being removed from index
    INACTIVE            Image is deactivated
  

The status can be retrieved with a Show request for the image as described above at anytime.



**Request**

    PUT /api/images/#{id}/status.xml

    Content-Type: application/xml

    <status>
      <name>ACTIVE</name>
    </status>

**Response** (when the image could be activated right away)

    Status: 200 OK

    <image>
      <id type="integer">2592</id>
      ...
      <status>ACTIVE</status>
    </image>

**Response** (when the image the activation has not been completed)

    Status: 202 Accepted

    <image>
      <id type="integer">2592</id>
      ...
      <status>ACTIVATION_QUEUED</status>
    </image>

**Response** (when the image cannot be activated due to errors)

    Status: 422 Unprocessable Entity
    
    <errors>
      <error>Status cannot be set to ACTIVE because no recognition server is configured.</error>
    </errors>
    
---

### Uploads

Some operations, like creating images, allow or require you to attach a file to the record. To do this via the API, you need to send a POST request to the "/api/uploads.xml" URI. The body of the request should be the content of the file you want to attach:

**Request**

    POST /api/uploads.xml

    Content-Type: image/jpeg
    
    [BINARY DATA]

If the upload succeeds, you'll get an XML response back, telling you the ID of your upload. (Your upload is not yet associated with any record, and if you do not do anything with it, it will be deleted within the next hour.)

**Response**

    Status: 200 OK

    <upload>
      <id>94cc5d8.2</id>
    </upload>

---

### Item Resources

For each item you can add generic URIs (links, URNs etc) or create file attachments. We call these information attached to an item "resources". These are then displayed in the response for your search requests matching the item. 

For file attachments, just as with image uploads documented above, you first have to create an upload and then use this API and set resource/file/upload_id to the upload id (same as when adding an image to an item).

**Request**
    
    POST /api/items/:id/item_resources.xml

    <resource>
      <title>Wikipedia</title>
      <section>Links</section>
      <uri>http://wikipedia.org</uri>
    </resource>
        
Required Parameters:

    title    The title of the resource
    section  The section in which it should appear

One of the two following is also required:

    uri      A generic uri (such as http://domain.tld or scheme://path etc)
        
or

    file/upload_id  An upload id (see the public data API).
        
**Response**

    Status: 201 Created

---

### Medium Types

Items can have a Medium object assigned to them. This defines the kind of item. For instance, a Wine, a Book Cover, a Newspaper page, etc. Specifying the Medium helps the recognition engine to optimize certain parameters which are optimized for the kind of item. Furthermore, it allows you to set Medium specific meta-data fields. For example the author of a book, or the vintage of a wine. We need to configure the available Medium Types for your group, so please contact us if you want to make use of this feature. (The specific attributes available for a certain Medium Type [can be seen here](Medium_Types.md) or when adding a new item using the Web upload in your account under my.kooaba.com/admin). The available Medium Types are:  

    Book                Book Cover
    DVD                 DVD Cover
    Game                Game Cover
    Movie               Movie Poster
    Album               CD Cover
    Ad                  Print Advertising
    BillboardPoster     Billboard    
    ConferencePoster    Scientific Poster
    Art                 Piece of Art (painting etc.)
    PeriodicalPage      Page in a Newspaper or Magazine
    CataloguePage       Page in a printed Catalogue     
    Wine                A wine label
    Postcard            A Postcard

#### Create Medium

Parameters are

    type    The medium type, one of PeriodicalPage, Movie, DVD etc.
    title   Can be the same as item[title]. Must be present here as well.
    *       Specific attributes for the medium (example: periodical_page, periodical_issue for a PeriodicalPage)

Pass an empty medium object (`<medium></medium>`) to remove the medium type from the item.

**Request**
  
    PUT /api/items/:id/medium.xml

    <medium>
      <type>Medium::Art</type>
      <title>Mona Lisa</title>
      <artist>Leonardo da Vinci</artist>
    </medium>
    
**Response**

    Status: 204 No Content


