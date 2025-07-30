/**
 * Copyright 2022 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.sample.cast.atvreceiver.data

import android.content.AsyncTaskLoader
import android.content.Context
import android.util.Log
import java.lang.Exception

/**
 * AsyncTaskLoader to load channel list and convert to movie list for backward compatibility
 */
class ChannelToMovieListLoader(context: Context?, private val mUrl: String) : AsyncTaskLoader<List<Movie>?>(context) {
    
    override fun loadInBackground(): List<Movie>? {
        return try {
            Log.d(TAG, "Loading channels from URL: $mUrl and converting to movies")
            // Clear existing list first
            ChannelList.clearList()
            
            val movies = if (mUrl.startsWith("file:///android_asset/")) {
                val assetPath = mUrl.substring("file:///android_asset/".length)
                ChannelToMovieAdapter.setupMoviesFromAssets(context, assetPath)
            } else {
                ChannelToMovieAdapter.setupMoviesFromChannels(mUrl)
            }
            Log.d(TAG, "Loaded and converted ${movies?.size ?: 0} movies from channels")
            Log.d(TAG, "Channel list size: ${ChannelList.getList()?.size ?: 0}")
            movies
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch and convert channel data to movies", e)
            null
        }
    }

    override fun onStartLoading() {
        super.onStartLoading()
        Log.d(TAG, "Starting to load channels and convert to movies")
        forceLoad()
    }

    override fun onStopLoading() {
        Log.d(TAG, "Stopping channel to movie load")
        // Attempt to cancel the current load task if possible.
        cancelLoad()
    }

    companion object {
        private const val TAG = "ChannelToMovieListLoader"
    }
}
