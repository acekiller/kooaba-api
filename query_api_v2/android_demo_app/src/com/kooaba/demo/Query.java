/*
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
*/
package com.kooaba.demo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.kooaba.demo.HttpConnection.AuthMethod;

import android.os.Handler;

class Query extends WorkerThread implements Runnable {
	private static final String ENDPOINT     = Utils.config.getProperty("query_endpoint");
	
	private File            file = null;
	private String          destinations;
	
	Query(ResultsActivity main, Handler handler, File file, String destinations) {
		super(main, handler);
		this.file = file;
		this.destinations = destinations;
	}
	
	@Override
	public void run() {
		final long startTime = System.currentTimeMillis();
		InputStream inputStream = null;
		try {
			Properties config = Utils.getConfig();
			MultipartEntity multipartEntity	= new MultipartEntity();
			FileBody imageByteArrayBody = new FileBody(file, "application/octet-stream", "imageFileName");
			multipartEntity.addPart("image", imageByteArrayBody);
			multipartEntity.addPart("returned-metadata", new StringBody(config.getProperty("requested_metadata")));
			if ((destinations != null) && !destinations.equals("")) {
				multipartEntity.addPart("destinations", new StringBody(destinations));
			}
			
			logger.log(Level.FINE, "querying");
			handler.post(new Runnable() {
				@Override
				public void run() {
					main.queryStart();
				}
			});
			HttpConnection httpConnection = new HttpConnection(ENDPOINT, 80, config.getProperty("access_key"), config.getProperty("secret_key"), AuthMethod.KWS);
			long preSendTime = System.currentTimeMillis();
			logger.log(Level.FINE, "Query preparation took "+(preSendTime-startTime)+"ms");
			inputStream = httpConnection.post(multipartEntity, "/v2/query");
			long postSendTime = System.currentTimeMillis();
			logger.log(Level.FINE, "Query execution took "+(postSendTime-preSendTime)+"ms");
			final String response = IOUtils.toString(inputStream);
			long postReadingSendTime = System.currentTimeMillis();
			logger.log(Level.FINE, "reading results took "+(postReadingSendTime-postSendTime)+"ms @ "+(postReadingSendTime-postSendTime)+"ms");
			JSONObject parsed = new JSONObject(response);
			long postParsingTime = System.currentTimeMillis();
			logger.log(Level.FINE, "parsing results took "+(postParsingTime-postReadingSendTime)+"ms @ "+(postParsingTime-postSendTime)+"ms");
			JSONArray results = parsed.getJSONArray("results");
			String firstResult = "uuid: "+parsed.getString("uuid")+"\n";
			if (results.length() >= 1) {
				JSONObject result = results.getJSONObject(0);
				firstResult = firstResult+result.toString(4);
			}
			long postImageDecodingTime = System.currentTimeMillis();
			logger.log(Level.FINE, "image decoding took "+(postImageDecodingTime-postParsingTime)+"ms @ "+(postImageDecodingTime-postSendTime)+"ms");
			final String prettyPrint = firstResult;
			
			logger.log(Level.FINE, "query done; "+response);
			
			String message;
			if (results.length() == 1) {
				message = Utils.formatDataSize(response.length())+", "+results.length()+" result";
			} else {
				message = Utils.formatDataSize(response.length())+", "+results.length()+" results";
			}
			final String fmessage = message;
			handler.post(new Runnable() {
				@Override
				public void run() {
					main.querySuccess(fmessage, prettyPrint, System.currentTimeMillis()-startTime);
				}
			});
			logger.log(Level.FINE, "Response processing took "+(System.currentTimeMillis()-postSendTime)+"ms");
		} catch (final IOException e) {
			logger.log(Level.WARNING, "Query error: "+e);
			
			handler.post(new Runnable() {
				@Override
				public void run() {
					main.queryFailure(e.toString(), System.currentTimeMillis()-startTime);
				}
			});
		} catch (final JSONException e) {
			logger.log(Level.WARNING, "Query response error: "+e);

			handler.post(new Runnable() {
				@Override
				public void run() {
					main.queryFailure(e.toString(), System.currentTimeMillis()-startTime);
				}
			});
		} catch (final NoSuchAlgorithmException e) {
			logger.log(Level.WARNING, "BUG: "+e);

			handler.post(new Runnable() {
				@Override
				public void run() {
					main.queryFailure(e.toString(), System.currentTimeMillis()-startTime);
				}
			});
		} finally {
			IOUtils.closeQuietly(inputStream);
		}
	}
}