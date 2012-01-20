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

import com.kooaba.demo.R;

import java.util.Properties;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class WelcomeActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.welcome);

		// handling buttons
		final Button cameraButton = (Button) findViewById(R.id.preview_btn);
		cameraButton.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View view) {
				// clear camera intent flag (e.g. when the other activity was killed prematurely)
				// we need to do it here as the ResultActivity cannot know when it should do it
				SharedPreferences settings = getSharedPreferences(Utils.extractAppName(getApplicationContext())+"Prefs", MODE_PRIVATE);
				SharedPreferences.Editor editor = settings.edit();
				editor.putBoolean("camera_intent_in_progress", false);
				editor.commit();

				Intent camera = new Intent(view.getContext(), ResultsActivity.class);
				startActivity(camera);
			}
		});
		
		Utils.loadConfig(getApplicationContext());
	}

	@Override
	public void onResume() {
		Properties config = Utils.getConfig();
		super.onResume();
		SharedPreferences settings = getSharedPreferences(Utils.extractAppName(getApplicationContext())+"Prefs", MODE_PRIVATE);
		
		final EditText destinations = (EditText) findViewById(R.id.destinations);
		String destinationsValue = settings.getString("destinations", config.getProperty("default_destinations"));
		destinations.setText(destinationsValue);
		
		final EditText querySettings = (EditText) findViewById(R.id.query_settings);
		String queryValue = settings.getString("query_settings", config.getProperty("default_query_settings"));
		querySettings.setText(queryValue);
	}

	@Override
	public void onStop() {
		super.onStop();
		SharedPreferences settings = getSharedPreferences(Utils.extractAppName(getApplicationContext())+"Prefs", MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		
		final EditText destinations = (EditText) findViewById(R.id.destinations);
		String destinationsValue = destinations.getText().toString().trim();
		editor.putString("destinations", destinationsValue);
		
		final EditText querySettings = (EditText) findViewById(R.id.query_settings);
		String queryValue = querySettings.getText().toString().trim();
		if (queryValue.matches("[1-9][0-9]*@([1-9][0-9]?|100)")) {
			editor.putString("query_settings", queryValue);
		}
		
		editor.commit();
	}
}
