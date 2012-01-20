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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import com.kooaba.demo.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

public class ResultsActivity extends Activity {
	protected static final Logger logger = Logger.getLogger("com.kooaba.demo.ResultsActivity");
	
	public static final int REQUEST_FROM_CAMERA = 1;
	public static final String FILE_NAME = "kooaba-demo.jpg";
	public static final String FILE_NAME_COMPRESSED = "kooaba-demo-compressed.jpg";
	
	private Thread queryThread = null;
	private Thread storeThread = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		SharedPreferences settings = getSharedPreferences(Utils.extractAppName(getApplicationContext())+"Prefs", MODE_PRIVATE);
		// This activity might be terminated due to RAM pressure while the image is obtained from the camera app.
		// Make sure that when this happens we restart in the correct state. 
		boolean inProgress = settings.getBoolean("camera_intent_in_progress", false);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.results);

		if (inProgress == false) {
			inProgress = true;
			getPhotoFromCamera();
		} else {
			// the flag should be cleared from the WelcomeActivity because this activity can be started and stopped (e.g. as a result of camera app or user action)
		}
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean("camera_intent_in_progress", inProgress);
		editor.commit();
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		long startTime = System.currentTimeMillis();

		if (requestCode == REQUEST_FROM_CAMERA && resultCode == RESULT_OK) {
			InputStream is = null;
			try {
				File file = getTempFile();
				try {
					is = new FileInputStream(file);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}

				//On HTC Hero the requested file will not be created. Because HTC Hero has custom camera
				//app implementation and it works another way. It doesn't write to a file but instead
				//it writes to media gallery and returns uri in intent. More info can be found here:
				//http://stackoverflow.com/questions/1910608/android-actionimagecapture-intent
				//http://code.google.com/p/android/issues/detail?id=1480
				//So here's the workaround:
				if (is == null){
					logger.info("Using camera workaround");
					try {
						Uri u = data.getData();
						is = getContentResolver().openInputStream(u);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}

				if (is != null) {
					compressAndReturn(is);
					SharedPreferences settings = getSharedPreferences(Utils.extractAppName(getApplicationContext())+"Prefs", MODE_PRIVATE);
					String destinations = settings.getString("destinations", Utils.getConfig().getProperty("default_destinations"));
					queryThread = new Thread(new Query(this, new Handler(), getCompressedFile(), destinations));
					queryThread.start();
				} else {
					queryFailure("Camera problem", -1);
				}
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch(IOException e) {
					}
				}
			}

			// Make request to server here
			// ----------------------------------------------------------------------

			// ----------------------------------------------------------------------
		}
		logger.fine("Resizing and recompression took "+(System.currentTimeMillis()-startTime)+"ms");
		setContentView(R.layout.results);
	}

	//decodes image and scales it to reduce memory consumption
	private Bitmap decodeStream(InputStream is, int pixelSize) throws IOException{
		try {
			long startTime = System.currentTimeMillis();
			byte[] data = IOUtils.toByteArray(is);
			int scale = 1;
			//Decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(new ByteArrayInputStream(data), null, o);

			//The new size we want to scale to
			final int REQUIRED_SIZE = pixelSize;

			//Find the correct scale value. It should be the power of 2.
			int width_tmp = o.outWidth, height_tmp = o.outHeight;
			while (true) {
				if (width_tmp / 2 < REQUIRED_SIZE && height_tmp / 2 <REQUIRED_SIZE)
					break;
				width_tmp /= 2;
				height_tmp /= 2;
				scale *= 2;
			}

			// Decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = scale;
			Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, o2);
			logger.fine("decoding (scale "+scale+") took "+(System.currentTimeMillis()-startTime)+"ms");
			return bm;
		} catch (FileNotFoundException e) {}
		return null;
	}

	private void compressAndReturn(InputStream is) {
		FileOutputStream out = null;
		try {
			File originalFile = getCompressedFile();
			out = new FileOutputStream(originalFile);
			Properties config = Utils.getConfig();
			SharedPreferences settings = getSharedPreferences(Utils.extractAppName(getApplicationContext())+"Prefs", MODE_PRIVATE);
			
			String value = settings.getString("query_settings", config.getProperty("default_query_settings"));
			String[] split = value.split("@");
			final int querySize = Integer.parseInt(split[0]);
			final int compression = Integer.parseInt(split[1]);
			
			String formattedStoreValues = settings.getString("store_settings", config.getProperty("default_store_settings"));
			formattedStoreValues = formattedStoreValues.trim().replaceAll("\\s+", " ");
			String[] storeValues = formattedStoreValues.split(" ");
			Arrays.sort(storeValues);
			final int maxStoreSize = Integer.parseInt(storeValues[storeValues.length-1].split("@")[0]);

			Bitmap bm = decodeStream(is, Math.max(querySize, maxStoreSize));
			storeThread = new Thread(new Store(this, new Handler(), bm, storeValues));
			storeThread.setPriority(Thread.MIN_PRIORITY);
			storeThread.start();

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

			// recreate the new Bitmap
			Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
			logger.finer("Before: "+width+"x"+height+"; scaling "+scale+"; after: "+resizedBitmap.getWidth()+"x"+resizedBitmap.getHeight());
			resizedBitmap.compress(Bitmap.CompressFormat.JPEG, compression, out);
			out.flush();
			out.close();
			logger.fine("Saved picture file: "+originalFile.getAbsolutePath());
			resizedBitmap.recycle();
		} catch(FileNotFoundException ex) {
			Log.e("FILE_COMPRESS", ex.getMessage());
		} catch(IOException ex) {
			Log.e("FILE_COMPRESS", ex.getMessage());
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
		}
	}

	@Override 
	protected void onStop() {
		logger.finer("stopping");
		super.onStop();
		deleteTestFiles();
		if (queryThread != null) {
			try {
				queryThread.join();
			} catch (InterruptedException e) {
			}
		}
		if (storeThread != null) {
			try {
				storeThread.join();
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	protected void onResume() {
		logger.finer("resuming");
		super.onResume();

		setStatsAndImage(getCompressedFile().getAbsolutePath());
	}

	private void deleteTestFiles() {
		logger.finer("deleting");
		getTempFile().delete();
		getCompressedFile().delete();
	}

	private void getPhotoFromCamera() {
		logger.finer("getPhotoFromCamera starting");
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(getTempFile())); // image will be saved in this file by the camera
		startActivityForResult(intent, REQUEST_FROM_CAMERA);
	}

	private File getTempFile() {
		return new File(Environment.getExternalStorageDirectory(), FILE_NAME);
	}

	private File getCompressedFile() {
		return new File(Environment.getExternalStorageDirectory(), FILE_NAME_COMPRESSED);
	}

	private void setStatsAndImage(String absoluteFilePath) {
		ImageView imgResultView = (ImageView)findViewById(R.id.image_result);
		Bitmap bm = BitmapFactory.decodeFile(absoluteFilePath);
		if (bm != null) {
			imgResultView.setImageBitmap(bm);
		}
		String pictureResolution = "unknown";
		if (bm != null) {
			pictureResolution = bm.getWidth() + " x " + bm.getHeight();
		}

		TextView imgStats = (TextView)findViewById(R.id.image_size);
		String fileSize = Utils.formatDataSize(getCompressedFile().length());
		imgStats.setText(fileSize+", "+pictureResolution);
	}
	
	public void queryStart() {
		setTime(-1);
		setStatus("Querying...");
		setResponse("");
		setBulkText("");
	}
	
	public void querySuccess(String message, String data, long time) {
		setTime(time);
		setStatus("Done");
		setResponse(message);
		setBulkText(data);
		ScrollView view = (ScrollView)findViewById(R.id.root);
		view.invalidate();
	}
	
	public void queryFailure(String error, long time) {
		setTime(time);
		setStatus("Error");
		setResponse("");
		setBulkText(error);
	}
	
	private void setTime(long time) {
		TextView timeText = (TextView)findViewById(R.id.recognition_time);
		if (time >= 0) {
			timeText.setText(time+"ms");
		} else {
			timeText.setText("");
		}
	}
	
	private void setStatus(String status) {
		TextView statusText = (TextView)findViewById(R.id.status);
		statusText.setText(status);
	}
	
	public void setStatus2(String status) {
		TextView statusText = (TextView)findViewById(R.id.status2);
		statusText.setText(status);
	}
	
	private void setResponse(String response) {
		TextView responseText = (TextView)findViewById(R.id.response);
		responseText.setText(response);
	}
	
	private void setBulkText(String text) {
		TextView bulkText = (TextView)findViewById(R.id.bulk_text);
		bulkText.setText(text);
	}
}
