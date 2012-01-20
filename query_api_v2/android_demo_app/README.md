# Android Demo App for kooaba Image Recognition Service

The android application is a fully functional client for the Query API v2. The application requires Android 2.1 or higher.


## How to Start

### Compiling the app

You will need the following to compile the application from sources:

* API keys that you received when registering for the API access (the keys are valid for all kooaba services). If you did not register yet, you can do that in developer section of the [http://kooaba.com](kooaba homepage).
* Eclipse IDE for Java Developers with ADT plugin and Android SDK. See the [http://eclipse.org](Eclipse Foundation open source community website) and [http://developer.android.com/sdk/](Android SDK website) for installation instructions.
* A phone or tablet with Android version 2.1 or higher. The app will work in an emulator, but the default emulator only provides a predefined static picture as the camera image.

Import the source code to the Eclipse IDE (File > Import, then General > Existing Projects to Workspace) and replace the placeholders in the file `res/raw/config.properties` with your API keys. You'll have to enable installation of non-market apps on your device (Settings > Applications > Unknown sources). To install the app:

* Enable USB debugging on your device (Settings > Applications > Development > USB debugging)
* Connect the device with the computer running the Eclipse IDE via a USB cable
* Run the application from the Eclipse IDE (Run > Run, then choose Android Application)

This will install the app on your device and starts it. You can also prepare a signed APK file (context menu of the KooabaDemo project > Android Tools > Export Signed Application Package) and install it as any other non-Market application.


## Using the App

The basic usage is pretty straightforward. Pressing the "Take Picture" button on the welcome screen will start a camera app where you can take picture of an object you want to recognize. The picture is then sent to our recognition service, displaying the result and few information about the performed request:

* Query Image Size: Data size and image resolution of the image sent for recognition (the resolution is configurable, see below)
* Status: Querying, Done or Error. The rightmost part shows progress of background image saving (described below)
* Recognition Time: Duration of the recognition request (end-to-end, i.e. including establishing of a data connection and response parsing)
* Response: If the request succeeded, it displays size of the response data (without HTTP headers) and number of results.
* The middle part of the screen contains details of the response. It's the UUID of the request and metadata for the topmost recognition (if any).
* The bottom part of the screen displays the query image as sent with the request.

You can hit the back button to return to the welcome screen and start another recognition. Please not that the information on this page do not persist. When returning to this screen from another app, all data will be reset.

The welcome screen offers two customizations. The Destinations is a list of destinations to search. If you have access to more than one group, you can enter a group name (or comma separated list of groups) to narrow your search only to those specific groups. When left empty, all your groups will be searched. Please consult documentation of the kooaba Data API for more information about groups.

The Resolution@Quality setting describes the parameters of the query image. It consists of two numbers separated by an at symbol @. The first number is maximum size of the image in pixels (longer edge of the image counts), the second is a JPEG compression setting (integer values from 1 to 100). The default value of 320@30 is an aggressive setting for mobile networks. Some content is more challenging than other, therefore we suggest you to experiment a bit with this setting and choose values appropriate for your application.

### Advanced configuration

Advanced configuration options are stored in the `config.properties` file and require to recompilation of the app. The are:

* `default_query_settings`: Default query image parameters. See above for the format. They are used at the first start of the application, but any changes on the welcome screen take precedence.
* `default_store_settings`: Parameters for saving images. The format is as with the query image settings, but one can specify a whitespace separated list of values. Each value results in an image saved on the SD card of the device (or equivalent location for devices with big internal storage). You can leave it empty to disable the image saving.
* `store_images_directory`: Directory where to store the above images.
* `query_endpoint`: The recognition requests are sent to this address. Useful for switching between Beta and production version of the Query API v2.
* `requested_metadata`: List of metadata that are returned for successful recognition. See the Query API v2 documentation for more information.


## Modifying the App

The app is released under BSD license (with a small exception, see the License and Legal Information for complete details), so feel free to use it (whole or the interesting parts) as a basis for your own app.


## License and Legal Information

The source code contained in the directories `src/com/kooaba/demo` and `res` is covered by the BSD 3-clause license:

````
Copyright (c) 2012, kooaba AG
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.
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
````

The source code in the file `src/base64/Base64.java` is in public domain (see [http://iharder.net/base64](the homepage of the BASE64 code) for more information).

The `kooaba Image Recognition` and the corresponding logo (file `res/drawable-hdpi/kooaba_logo.png`) are trademarks of kooaba AG.
