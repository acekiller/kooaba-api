<?php

/**
 * Sample code in PHP for using the kooaba REST API
 *
 * Contact:  support@kooaba.com
 * Created:  2008-11-10
 * Modified: 2009-04-28
 * @author   Herbert Bay & Joachim Fornallaz
 * @version: 1.1.2
 */

# Define user data provided by kooaba
$access_key = "<your access key>";
$secret_key = "<your secret key>";
$group_id = "32";

# Query image
$filename = "../lena.jpg";

# Load image
$img = file_get_contents($filename); 

# Connection data. Hostname, port number, and path
$host = "search.kooaba.com";
$port = "80";
$path = "/queries.xml";
$content_type = "multipart/form-data";

# Define timezone for RFC 2616 standards
date_default_timezone_set('GMT');

# Get current time in RFC 2616 format
$date = date("D, d M Y H:i:s T");

# Define boundary for multipart message
$boundary = uniqid();

# Construct message body first as it is needed for the authentication
$body  = "--" . $boundary . "\r\n";
$body .= 'Content-Disposition: form-data; name="query[file]"; filename="' . $filename . '"' . "\r\n";
$body .= 'Content-Transfer-Encoding: binary' ."\r\n";
$body .= 'Content-Type: image/jpeg' . "\r\n\r\n";
$body .= $img . "\r\n";
# Group part begin
$body .= "--" . $boundary . "\r\n";
$body .= 'Content-Disposition: form-data; name="query[group_ids][]"'."\r\n\r\n";
$body .= $group_id . "\r\n";
# Group part end
# To query multiple groups, append additional group parts (each with a single group ID)
$body .= "--" . $boundary . "--\r\n";

# Create the string to sign
$string_to_sign = $secret_key . "\n\n"
	. "POST" . "\n"
	. md5($body) . "\n"
	. $content_type. "\n" 
	. $date. "\n" 
	. $path;

# Create signature
$signature = base64_encode( sha1($string_to_sign, true) );
$auth = "KWS" . " " . $access_key . ":" . $signature;

# Define HTTP message header
$header  = "Content-Type: " . $content_type . "; boundary=" . $boundary ."\r\n";
$header .= "Host: " . $host . "\r\n";
$header .= "Date: " . $date . "\r\n";
$header .= "Authorization: " . $auth . "\r\n";
$header .= "Content-Length: " . strlen($body) . "\r\n";

# Send request
$answer = send_http_post($host, $port, $path, $header, $body);
print_r($answer);


/**
 * Sends an HTTP POST request
 *
 * @param string $host Host address 
 * @param string $port Port number
 * @param string $path Request path
 * @param string $header Request header
 * @param string $body Request body
 * @return Answer of remote host
 */
function send_http_post($host, $port, $path, $header, $body) {
	# Open the connection using Internet socket connection
	$fp = @fsockopen($host, $port, $errno, $errstr);
	if (!$fp)
		fatal("<p>Unable to establish connection to <tt>$host</tt> ".
			"(port <tt>$port</tt>):</p><pre>$errstr</pre>");

	# Send HTTP message headers
	fwrite($fp, "POST $path HTTP/1.0\r\n");
	fwrite($fp, $header);
	fwrite($fp, "\r\n");
 
	# Send HTTP message body
	fwrite($fp, $body);

	# Read response from the server
	$buf = '';
	$length = false;
	while (!feof($fp)) {
		$buf .= fread($fp, 8192);
		
		# Parse the HTTP answer
		$answer = parse_answer($buf);
		
		# Find out about content length
		if (!$length) {
			if ($answer[0] == 200)
				$length = $answer[1]['content-length'];
		}

		# Was response fully transmitted or not
		if ($length) {
			if (strlen($buf) == strlen($answer[3]) + $length)
			break;
		}
	}
	fclose($fp);
	return $answer;
}


/**
 * 	Parses an HTTP answer to extract response code,	headers, and body
 *
 * @param string $answer Answer of remote host
 * @return Array with response code, header, and body, and raw header
 */
function parse_answer($answer) {
	# Separate server response status, headers, and data contents 
	if (ereg("^(([^\n]+)\r\n(.*)\r\n\r\n)(.*)", $answer, $regs)) {
		$full_headers   = $regs[1];
		$response       = $regs[2];
		$headers_string = $regs[3];
		$body           = $regs[4];
        
		# Parse server response status
		if (ereg("^HTTP/[0-9\.]+ ([0-9]+)", $response, $regs)) {
			$response_code = $regs[1];
		} 
		
		# Parse headers and build a hash with them
		foreach (split("\r\n", $headers_string) as $line) {
			if (ereg("^([^:]+): (.*)", $line, $regs)) {
				$headers[strtolower($regs[1])] = $regs[2];
			}
		}
	} else {
		# Return -1 as response code if parsing was not possible
		return array(-1);
	}
	return array($response_code, $headers, $body, $full_headers);
}


/**
 * Displays an error message in HTML format
 *
 * @param string $msg Error message
 * @return void
 */
function fatal($msg) {
  echo "<h2>Error</h2>\n";
  echo "$msg\n";
  echo "</body></html>";
  exit;
}

?>
