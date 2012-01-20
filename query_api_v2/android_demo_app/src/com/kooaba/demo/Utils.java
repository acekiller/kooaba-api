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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import android.content.Context;
import android.content.res.Resources;

public class Utils {
	protected static final Logger logger = Logger.getLogger("com.kooaba.demo.Utils");
	
	protected static Properties config;
	protected static final Object configLock = new Object();
	
	public static String extractAppName(Context context) {
		Resources resources = context.getResources();
		CharSequence name = resources.getText(resources.getIdentifier("app_name", "string", context.getPackageName())).toString();
		return name.toString();
	}
	
	/** Format data size for UI display.
	 * 
	 * @param size  Size to format.
	 * @return Size in kiB formatted to one decimal place.
	 */
	public static String formatDataSize(long size) {
		final int decimalsFactor = 10;
		final int unit = 1024;
		final String unitName = "kiB";
		long fixedPointSize = (decimalsFactor*size+unit/2)/unit;
		return (fixedPointSize/decimalsFactor)+"."+(fixedPointSize % decimalsFactor)+unitName;
	}
	
	/** Return config.
	 * 
	 * Requires loadConfig(...) to be called first.
	 * 
	 * @return Configuration properties.
	 */
	public static Properties getConfig() {
		if (config == null) {
			synchronized (configLock) {
				if (config == null) {
					InputStream is = null;
					try {
						is = Thread.currentThread().getContextClassLoader().getResourceAsStream("res/raw/config.properties");
						Properties p = new Properties();
						p.load(is);
						config = p;
					} catch (IOException e) {
						logger.severe("Failed to load config properties file");
					} finally {
						IOUtils.closeQuietly(is);
					}
				}
			}
		}
		return config;
	}
	
	/** Load configuration.
	 * 
	 * @param context App context.
	 * @return True if the configuration is loaded, false otherwise.
	 */
	public static boolean loadConfig(Context context) {
		if (config == null) {
			synchronized (configLock) {
				if (config == null) {  // avoid double loading when called from two threads at once
					InputStream is = null;
					try {
						is = context.getResources().openRawResource(R.raw.config);
						Properties p = new Properties();
						p.load(is);
						config = p;
					} catch (IOException e) {
						logger.severe("Failed to load config properties file");
						return false;
					} finally {
						IOUtils.closeQuietly(is);
					}
				}
			}
		}
		return true;
	}
}
