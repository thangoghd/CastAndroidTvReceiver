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
package com.google.sample.cast.atvreceiver.ui

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import com.google.android.gms.cast.tv.media.MediaManager
import com.google.android.gms.cast.tv.CastReceiverContext
import androidx.leanback.app.VideoSupportFragment
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.sample.cast.atvreceiver.player.VideoPlayerGlue
import com.google.android.gms.cast.tv.media.MediaManager.MediaStatusInterceptor
import org.json.JSONObject
import org.json.JSONException
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import androidx.leanback.app.VideoSupportFragmentGlueHost
import com.google.android.exoplayer2.SimpleExoPlayer
import androidx.leanback.widget.PlaybackControlsRow
import com.google.sample.cast.atvreceiver.data.ChannelList
import com.google.sample.cast.atvreceiver.data.ChannelToMovieAdapter
import android.widget.Toast
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.gms.cast.tv.media.MediaInfoWriter
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.tv.media.MediaLoadCommandCallback
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.cast.tv.media.MediaException
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaError.DetailedErrorCode
import com.google.android.gms.tasks.Task
import com.google.sample.cast.atvreceiver.data.Movie
import com.google.sample.cast.atvreceiver.data.Channel
import com.google.sample.cast.atvreceiver.data.Source
import com.google.sample.cast.atvreceiver.data.Content
import com.google.sample.cast.atvreceiver.data.Stream
import com.google.sample.cast.atvreceiver.data.StreamLink
import com.google.sample.cast.atvreceiver.data.RequestHeader
import com.google.sample.cast.atvreceiver.data.Image
import java.util.ArrayList
import java.util.function.Consumer

/**
 * Handles video playback with media controls.
 */
class PlaybackVideoFragment : VideoSupportFragment() {
    private var mMediaSession: MediaSessionCompat? = null
    private var mMediaSessionConnector: MediaSessionConnector? = null
    private var mPlayer: SimpleExoPlayer? = null
    private var mPlayerAdapter: LeanbackPlayerAdapter? = null
    private var mPlayerGlue: VideoPlayerGlue? = null
    private var mPlaylistActionListener: PlaylistActionListener? = null
    private var mMediaManager: MediaManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LOG_TAG, "onCreate")
        mMediaSession = MediaSessionCompat(requireContext(), LOG_TAG)
        mMediaSessionConnector = MediaSessionConnector(mMediaSession!!)
        initializePlayer()
    }

    override fun onStart() {
        super.onStart()
        Log.d(LOG_TAG, "onStart")
        mMediaManager = CastReceiverContext.getInstance().mediaManager
        mMediaManager?.setSessionCompatToken(mMediaSession!!.sessionToken)
        mMediaManager?.setMediaLoadCommandCallback(MyMediaLoadCommandCallback())
        mMediaManager?.setMediaStatusInterceptor(MediaStatusInterceptor { mediaStatusWriter ->
            try {
                mediaStatusWriter.setCustomData(JSONObject("{data: 'CustomData'}"))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        })
        initializePlayer()
        mMediaSessionConnector!!.setPlayer(mPlayer)
        val timelineQueueNavigator: TimelineQueueNavigator = object : TimelineQueueNavigator(mMediaSession!!) {
            override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
                val mediaMetadata = player.getMediaItemAt(windowIndex).mediaMetadata
                return MediaDescriptionCompat.Builder()
                        //.setMediaUri(mediaMetadata.mediaUri)
                        .setIconUri(mediaMetadata.artworkUri)
                        .setTitle(mediaMetadata.title)
                        .setSubtitle(mediaMetadata.subtitle)
                        .build()
            }
        }
        mMediaSessionConnector!!.setQueueNavigator(timelineQueueNavigator)
        mMediaSession!!.isActive = true
        if (mMediaManager!!.onNewIntent(requireActivity().intent)) {
            // If the SDK recognizes the intent, you should early return.
            return
        }

        // If the SDK doesn't recognize the intent, you can handle the intent with
        // your own logic.
        processIntent(requireActivity().intent)
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOG_TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(LOG_TAG, "onPause")
        if (mPlayerGlue != null && mPlayerGlue!!.isPlaying) {
            mPlayerGlue!!.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(LOG_TAG, "onStop")
        mMediaSessionConnector!!.setPlayer(null)
        mMediaSession!!.isActive = false
        mMediaSession!!.release()
        mMediaManager!!.setSessionCompatToken(null)
        releasePlayer()
    }

    public override fun onError(errorCode: Int, errorMessage: CharSequence) {
        Log.d(LOG_TAG, "onError")
        logAndDisplay(errorMessage.toString())
        requireActivity().finish()
    }

    fun processIntent(intent: Intent) {
        Log.d(LOG_TAG, "processIntent()")
        if (intent.hasExtra(MainActivity.CHANNEL)) {
            // Intent came from MainActivity (User chose an item inside ATV app).
            val channel = intent.getSerializableExtra(MainActivity.CHANNEL) as Channel?
            startPlaybackFromChannel(channel, 0)
        } else if (intent.hasExtra(MainActivity.MOVIE)) {
            // Fallback to Movie for backward compatibility
            val movie = intent.getSerializableExtra(MainActivity.MOVIE) as Movie?
            startPlayback(movie, 0)
        } else {
            logAndDisplay("Null or unrecognized intent action")
            requireActivity().finish()
        }
    }

    private fun initializePlayer() {
        if (mPlayer == null) {
            Log.d(LOG_TAG, "initializePlayer")
            val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
            mPlayer = SimpleExoPlayer.Builder(requireContext()).build()
            mPlayerAdapter = LeanbackPlayerAdapter(requireContext(), mPlayer!!, UPDATE_DELAY)
            mPlayerAdapter!!.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)
            mPlaylistActionListener = PlaylistActionListener()
            mPlayerGlue = VideoPlayerGlue(context, mPlayerAdapter, mPlaylistActionListener!!)
            mPlayerGlue!!.host = glueHost
            mPlayerGlue!!.isSeekEnabled = true
            mPlayer?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    var title: CharSequence? = ""
                    var subtitle: CharSequence? = ""
                    if (mediaItem != null) {
                        // mediaIem is null if player has been stopped or
                        // all media items have been removed from the playlist
                        title = mediaItem.mediaMetadata.title
                        subtitle = mediaItem.mediaMetadata.subtitle
                    }
                    mMediaManager!!.mediaStatusModifier.clear()
                    mPlayerGlue!!.title = title
                    mPlayerGlue!!.subtitle = subtitle
                }
            })
        }
    }

    private fun releasePlayer() {
        if (mPlayer != null) {
            Log.d(LOG_TAG, "releasePlayer")
            mPlayer!!.release()
            mPlayer = null
            mPlayerAdapter = null
        }
    }

    private fun startPlayback(movie: Movie?, startPosition: Long) {
        val mediaItems: MutableList<MediaItem> = ArrayList()
        val firstMediaItem = MediaItem.Builder()
                .setUri(movie!!.videoUrl)
                .setMediaMetadata(
                        MediaMetadata.Builder()
                                .setArtworkUri(Uri.parse(movie.cardImageUrl))
                                .setTitle(movie.title)
                                .setSubtitle(movie.description)
                                .build()
                ).build()
        mediaItems.add(firstMediaItem)
        // Get movies from channel data
        val movieList = ChannelToMovieAdapter.setupMoviesFromAssets(requireContext(), "channel.json")
        movieList?.forEach(Consumer { movieItem: Movie ->
            mediaItems.add(MediaItem.Builder()
                    .setUri(movieItem.videoUrl)
                    .setMediaMetadata(
                            MediaMetadata.Builder()
                                    .setArtworkUri(Uri.parse(movieItem.cardImageUrl))
                                    .setTitle(movieItem.title)
                                    .setSubtitle(movieItem.description)
                                    .build()
                    ).build()
            )
        })
        mPlayer!!.setMediaItems(mediaItems)
        mPlayer!!.prepare()
        mPlayerGlue!!.playWhenPrepared()
        mPlayerGlue!!.seekTo(startPosition)
        mMediaManager!!.mediaStatusModifier.clear()
    }
    
    private fun startPlaybackFromChannel(channel: Channel?, startPosition: Long) {
        if (channel == null) {
            logAndDisplay("Channel is null")
            return
        }
        
        val streamLink = channel.getPrimaryStreamLink()
        
        if (streamLink?.url == null) {
            logAndDisplay("No valid stream URL found")
            return
        }
        
        // Create DataSource factory with headers
        val dataSourceFactory = createDataSourceFactoryWithHeaders(streamLink)
        
        // Create MediaSource with custom DataSource
        val mediaSource = if (streamLink.type == "hls" || streamLink.url!!.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(streamLink.url!!))
        } else {
            // For other formats, use default
            val mediaItem = MediaItem.Builder()
                .setUri(streamLink.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setArtworkUri(Uri.parse(channel.getImageUrl()))
                        .setTitle(channel.getDisplayTitle())
                        .setSubtitle(channel.getDisplayDescription())
                        .build()
                )
                .build()
            
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }
        
        // Set the media source
        mPlayer!!.setMediaSource(mediaSource)
        mPlayer!!.prepare()
        mPlayerGlue!!.playWhenPrepared()
        mPlayerGlue!!.seekTo(startPosition)
        mMediaManager!!.mediaStatusModifier.clear()
    }
    
    private fun createDataSourceFactoryWithHeaders(streamLink: com.google.sample.cast.atvreceiver.data.StreamLink): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        
        // Add headers if available
        if (streamLink.requestHeaders.isNotEmpty()) {
            val headersMap = mutableMapOf<String, String>()
            streamLink.requestHeaders.forEach { header ->
                header.key?.let { key ->
                    header.value?.let { value ->
                        headersMap[key] = value
                    }
                }
            }
            
            if (headersMap.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(headersMap)
                Log.d(LOG_TAG, "Added headers: $headersMap")
            }
        }
        
        return httpDataSourceFactory
    }

    private fun logAndDisplay(error: String) {
        Log.d(LOG_TAG, error)
        Toast.makeText(activity, error, Toast.LENGTH_SHORT).show()
    }

    internal inner class PlaylistActionListener : VideoPlayerGlue.OnActionClickedListener {
        override fun onPrevious() {
            mPlayer!!.previous()
        }

        override fun onNext() {
            mPlayer!!.next()
        }
    }

    private fun myFillMediaInfo(mediaInfoWriter: MediaInfoWriter) {
        val mediaInfo = mediaInfoWriter.mediaInfo
        Log.d(LOG_TAG, "***Type:" + mediaInfo.contentType)
        if (mediaInfo.contentUrl == null && mediaInfo.entity != null) {
            // Load By Entity
            val entity = mediaInfo.entity
            val movieMetadata = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE)
            val movie = entity?.let { convertEntityToMovie(it) }
            if(movie!=null) {
                movie.title?.let {
                    movieMetadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, it)
                }
                movie.description?.let {
                    movieMetadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, it)
                }
                movie.studio?.let {
                    movieMetadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_STUDIO, it)
                }
                movie.cardImageUrl?.let {
                    movieMetadata.addImage(WebImage(Uri.parse(it)))
                }
                movie.backgroundImageUrl?.let {
                    movieMetadata.addImage(WebImage(Uri.parse(it)))
                }
                movie.videoUrl?.let {
                    mediaInfoWriter.setContentUrl(it).setMetadata(movieMetadata)
                }
            }
        }
    }

    internal inner class MyMediaLoadCommandCallback : MediaLoadCommandCallback() {
        override fun onLoad(senderId: String?, loadRequestData: MediaLoadRequestData): Task<MediaLoadRequestData> {
            Toast.makeText(activity, "onLoad()", Toast.LENGTH_SHORT).show()
            return if (loadRequestData == null) {
                // Throw MediaException to indicate load failure.
                Tasks.forException(MediaException(
                        MediaError.Builder()
                                .setDetailedErrorCode(DetailedErrorCode.LOAD_FAILED)
                                .setReason(MediaError.ERROR_REASON_INVALID_REQUEST)
                                .build()))
            } else Tasks.call {

                // Resolve the entity into your data structure and load media.
                myFillMediaInfo(MediaInfoWriter(loadRequestData!!.mediaInfo!!))
                
                // Check if MediaInfo has custom data with headers
                val mediaInfo = loadRequestData.mediaInfo!!
                val customData = mediaInfo.customData
                
                if (customData != null && customData.has("headers")) {
                    // Create Channel from MediaInfo with headers
                    val channel = convertMediaInfoToChannel(mediaInfo)
                    Log.d(LOG_TAG, "Starting playback from channel with headers: ${customData.optJSONObject("headers")}")
                    startPlaybackFromChannel(channel, 0)
                } else {
                    // Fallback to regular Movie playback
                    Log.d(LOG_TAG, "Starting regular playback without headers")
                    startPlayback(convertLoadRequestToMovie(loadRequestData), 0)
                }

                // Update media metadata and state (this clears all previous status
                // overrides).
                mMediaManager!!.setDataFromLoad(loadRequestData)
                mMediaManager!!.broadcastMediaStatus()
                loadRequestData
            }
        }
    }

    companion object {
        private const val LOG_TAG = "PlaybackVideoFragment"
        private const val UPDATE_DELAY = 16
        private fun convertEntityToMovie(entity: String): Movie {
            // Note: This method needs context, should be refactored to be non-static
            // For now, return a default Movie
            return Movie()
        }

        private fun convertLoadRequestToMovie(loadRequestData: MediaLoadRequestData?): Movie? {
            if (loadRequestData == null) {
                return null
            }
            val mediaInfo = loadRequestData.mediaInfo ?: return null
            var videoUrl = mediaInfo.contentId
            if (mediaInfo.contentUrl != null) {
                videoUrl = mediaInfo.contentUrl!!
            }
            val metadata = mediaInfo.metadata
            val movie = Movie()
            movie.videoUrl = videoUrl
            if (metadata != null) {
                movie.title = metadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)
                movie.description = metadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE)
                movie.cardImageUrl = metadata.images[0].url.toString()
            }
            return movie
        }
        
        /**
         * Convert MediaInfo from Cast to Channel object with headers support
         */
        private fun convertMediaInfoToChannel(mediaInfo: com.google.android.gms.cast.MediaInfo): Channel {
            val channel = Channel()
            
            // Set basic channel info
            val metadata = mediaInfo.metadata
            if (metadata != null) {
                channel.name = metadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE) ?: "Cast Video"
                channel.subtitle = metadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE) ?: ""
                
                // Set image if available
                if (metadata.images.isNotEmpty()) {
                    val image = Image()
                    image.url = metadata.images[0].url.toString()
                    channel.image = image
                }
            }
            
            // Create source with content
            val source = Source()
            source.name = "Cast Source"
            
            val content = Content()
            content.name = channel.name
            content.id = "cast-content"
            
            // Create stream with headers from custom data
            val stream = Stream()
            val streamLink = StreamLink()
            streamLink.url = mediaInfo.contentId
            streamLink.type = when (mediaInfo.contentType) {
                "application/x-mpegURL" -> "hls"
                "application/dash+xml" -> "dash"
                "video/mp4" -> "mp4"
                else -> "hls"
            }
            
            // Extract headers from custom data
            val customData = mediaInfo.customData
            if (customData != null && customData.has("headers")) {
                try {
                    val headersJson = customData.getJSONObject("headers")
                    val headers = mutableListOf<RequestHeader>()
                    
                    val keys = headersJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = headersJson.getString(key)
                        val header = RequestHeader()
                        header.key = key
                        header.value = value
                        headers.add(header)
                        Log.d(LOG_TAG, "Extracted header from Cast: $key = $value")
                    }
                    
                    streamLink.requestHeaders = headers
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Error extracting headers from Cast custom data: ${e.message}")
                }
            }
            
            stream.streamLinks = mutableListOf(streamLink)
            content.streams = mutableListOf(stream)
            source.contents = mutableListOf(content)
            channel.sources = mutableListOf(source)
            
            Log.d(LOG_TAG, "Created channel from MediaInfo: ${channel.name}, headers: ${streamLink.requestHeaders.size}")
            return channel
        }
    }
}