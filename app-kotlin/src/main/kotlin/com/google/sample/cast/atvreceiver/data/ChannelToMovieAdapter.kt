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

import android.content.Context

/**
 * Adapter to convert Channel objects to Movie objects for backward compatibility
 */
object ChannelToMovieAdapter {
    
    /**
     * Convert a Channel to a Movie object
     */
    fun channelToMovie(channel: Channel, movieId: Int = 0): Movie {
        val movie = Movie()
        
        movie.id = movieId
        movie.title = channel.getDisplayTitle()
        movie.description = channel.getDisplayDescription()
        movie.videoUrl = channel.getPrimaryVideoUrl()
        movie.cardImageUrl = channel.getImageUrl()
        movie.backgroundImageUrl = channel.getImageUrl()
        
        // Extract studio from source name if available
        movie.studio = channel.sources.firstOrNull()?.name ?: "Unknown"
        
        return movie
    }
    
    /**
     * Convert a list of Channels to a list of Movies
     */
    fun channelsToMovies(channels: List<Channel>): List<Movie> {
        return channels.mapIndexed { index, channel ->
            channelToMovie(channel, index)
        }
    }
    
    /**
     * Convert Channel list to Movie list and setup MovieList for backward compatibility
     */
    fun setupMoviesFromChannels(url: String?): List<Movie>? {
        return try {
            val channels = ChannelList.setupChannels(url)
            channels?.let { channelsToMovies(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert Channel list from assets to Movie list
     */
    fun setupMoviesFromAssets(context: Context, assetPath: String): List<Movie>? {
        return try {
            val channels = ChannelList.setupChannelsFromAssets(context, assetPath)
            channels?.let { channelsToMovies(it) }
        } catch (e: Exception) {
            null
        }
    }
}
