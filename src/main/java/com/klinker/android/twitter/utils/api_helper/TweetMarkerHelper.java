/*
 * Copyright 2014 Luke Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.klinker.android.twitter.utils.api_helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.klinker.android.twitter.APIKeys;
import com.klinker.android.twitter.data.sq_lite.HomeContentProvider;
import com.klinker.android.twitter.data.sq_lite.HomeDataSource;
import com.klinker.android.twitter.settings.AppSettings;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import twitter4j.Twitter;

public class TweetMarkerHelper extends APIHelper {

    public static final String TWEETMARKER_API_KEY = APIKeys.TWEETMARKER_API_KEY;

    private int currentAccount;
    private String screenname;
    private String postURL;
    private Twitter twitter;
    private SharedPreferences sharedPrefs;

    public TweetMarkerHelper(int currentAccount, String screenname, Twitter twitter, SharedPreferences sharedPrefs) {
        this.currentAccount = currentAccount;
        this.screenname = screenname;
        this.twitter = twitter;
        this.sharedPrefs = sharedPrefs;

        postURL = "http://api.tweetmarker.net/v2/lastread?api_key=" + Uri.encode(TWEETMARKER_API_KEY) +
                "&username=" + Uri.encode(screenname);
    }

    public boolean contentProvider = false;
    public void setUseContentProvider(boolean use) {
        contentProvider = use;
    }

    public boolean sendCurrentId(String collection, long id) {
        try {
            HttpPost post = new HttpPost(postURL);
            post.addHeader("X-Auth-Service-Provider", SERVICE_PROVIDER);
            post.addHeader("X-Verify-Credentials-Authorization", getAuthrityHeader(twitter));

            JSONObject json = new JSONObject();
            json.put("id", id);
            JSONObject base = new JSONObject();
            base.put(collection, json);

            Log.v("talon_tweetmarker", "sending " + id + " to " + screenname);

            post.setEntity(new ByteArrayEntity(base.toString().getBytes("UTF8")));
            DefaultHttpClient client = new DefaultHttpClient();

            HttpResponse response = client.execute(post);
            int responseCode = response.getStatusLine().getStatusCode();
            Log.v("talon_tweetmarker", "sending response code: " + responseCode);

            if (responseCode != 200) { // there was an error, we will retry once
                // wait first
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {

                }

                response = client.execute(post);
                responseCode = response.getStatusLine().getStatusCode();
                Log.v("talon_tweetmarker", "sending response code: " + responseCode);

                if (responseCode == 200) {
                    // success, return true
                    int currentVersion = sharedPrefs.getInt("last_version_account_" + currentAccount, 0);
                    sharedPrefs.edit().putInt("last_version_account_" + currentAccount, currentVersion + 1).commit();
                    return true;
                }

            } else {
                int currentVersion = sharedPrefs.getInt("last_version_account_" + currentAccount, 0);
                sharedPrefs.edit().putInt("last_version_account_" + currentAccount, currentVersion + 1).commit();
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public boolean getLastStatus(String collection, Context context) {

        long currentId = sharedPrefs.getLong("current_position_" + currentAccount, 0l);

        boolean updated = false;

        try {
            HttpGet get = new HttpGet(postURL + "&" + collection);
            get.addHeader("X-Auth-Service-Provider", SERVICE_PROVIDER);
            get.addHeader("X-Verify-Credentials-Authorization", getAuthrityHeader(twitter));

            HttpClient client = new DefaultHttpClient();

            HttpResponse response = client.execute(get);
            Log.v("talon_tweetmarker", "getting id response code: " + response.getStatusLine().getStatusCode() + " for " + screenname);

            StatusLine statusLine = response.getStatusLine();

            if (statusLine.getStatusCode() == 200) { // request ok
                BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

                String line;
                StringBuilder builder = new StringBuilder();
                while ((line = rd.readLine()) != null) {
                    builder.append(line);
                }

                JSONObject jsonObject = new JSONObject(builder.toString());
                JSONObject timeline = jsonObject.getJSONObject(collection);

                if (timeline != null) {
                    currentId = Long.parseLong(timeline.getString("id"));
                    int version = Integer.parseInt(timeline.getString("version"));

                    Log.v("talon_tweetmarker", "getting tweetmarker," +
                            " version: " + version +
                            " id: " + currentId +
                            " screename: " + screenname);

                    int lastVer = sharedPrefs.getInt("last_version_account_" + currentAccount, 0);

                    if (lastVer != version) {
                        updated = true;
                    }

                    sharedPrefs.edit().putInt("last_version_account_" + currentAccount, version).commit();
                }
            } else { // there was an error, we will retry once
                // wait first
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {

                }

                response = client.execute(get);

                statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() == 200) { // request ok
                    BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

                    String line;
                    StringBuilder builder = new StringBuilder();
                    while ((line = rd.readLine()) != null) {
                        builder.append(line);
                    }

                    JSONObject jsonObject = new JSONObject(builder.toString());
                    JSONObject timeline = jsonObject.getJSONObject(collection);

                    if (timeline != null) {

                        currentId = Long.parseLong(timeline.getString("id"));
                        int version = Integer.parseInt(timeline.getString("version"));

                        Log.v("talon_tweetmarker", "getting tweetmarker," +
                                " version: " + version +
                                " id: " + currentId +
                                " screename: " + screenname);

                        int lastVer = sharedPrefs.getInt("last_version_account_" + currentAccount, 0);

                        if (lastVer != version) {
                            updated = true;
                        }

                        sharedPrefs.edit().putInt("last_version_account_" + currentAccount, version).commit();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } catch (OutOfMemoryError e) {

        }

        Log.v("talon_launcher_stuff", "writing " + currentId + " to shared prefs");
        sharedPrefs.edit().putLong("current_position_" + currentAccount, currentId).commit();
        if (contentProvider) {
            HomeContentProvider.updateCurrent(currentAccount, context, currentId);
        } else {
            try {
                HomeDataSource.getInstance(context).markPosition(currentAccount, currentId);
            } catch (Exception e) {

            }
        }


        return updated;
    }
}
