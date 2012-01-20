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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.IOUtils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Environment;
import android.os.Handler;

public class Store extends WorkerThread implements Runnable {
	private final Bitmap   bm;
	private final String[] settings;
	
	public Store(ResultsActivity main, Handler handler, Bitmap bm, String[] settings) {
		super(main, handler);
		this.bm = bm;
		this.settings = settings;
	}
	
	@Override
	public void run() {
		int errors = 0;
		int countDone = 0;
		if ((bm != null) && (settings != null)) {
			String path = prepareDirectory();
			if (path == null) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						main.setStatus2("FAIL");
					}
				});
				return;
			}
			handler.post(new Runnable() {
				@Override
				public void run() {
					main.setStatus2("0/"+settings.length);
				}
			});
			
			for (String s : settings) {
				String filePath = path+"/"+s+".jpg";
				OutputStream os = null;
				try {
					String[] split = s.split("@");
					final int querySize = Integer.parseInt(split[0]);
					final int compression = Integer.parseInt(split[1]);
					
					int width = bm.getWidth();
					int height = bm.getHeight();
					float scale;
					if (width > height) {
						scale = ((float)querySize) / width;
					} else {
						scale = ((float)querySize) / height;
					}

					Matrix matrix = new Matrix();
					matrix.postScale(scale, scale);
					
					Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
					if (resizedBitmap != null) {
						os = new FileOutputStream(filePath);
						resizedBitmap.compress(Bitmap.CompressFormat.JPEG, compression, os);
						os.close();
					}
				} catch (NumberFormatException e) {
					logger.warning("Malformed setting: "+s);
					errors += 1;
				} catch (IOException e) {
					logger.warning("Failed to write: "+filePath);
					errors += 1;
				} finally {
					IOUtils.closeQuietly(os);
				}
				
				countDone += 1;
				final String fmessage = countDone+"/"+settings.length;
				handler.post(new Runnable() {
					@Override
					public void run() {
						main.setStatus2(fmessage);
					}
				});
			}
		}
		String message = "";
		if (errors > 0) {
			message = errors+"ERR";
		}
		final String fmessage = message;
		handler.post(new Runnable() {
			@Override
			public void run() {
				main.setStatus2(fmessage);
			}
		});
	}

	private String prepareDirectory() {
		String path = Environment.getExternalStorageDirectory()+Utils.getConfig().getProperty("store_images_directory", "/images")+"/"+(new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()));
		File pathFile = new File(path);
		if (!pathFile.exists() && !pathFile.mkdirs()) {
			logger.warning("Cannot create directories for "+path);
			path = null;
		}
		return path;
	}
}
