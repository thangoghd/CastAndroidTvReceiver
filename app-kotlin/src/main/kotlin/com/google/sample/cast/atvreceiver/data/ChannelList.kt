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

import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import java.io.*
import java.lang.Exception
import java.lang.StringBuilder
import java.net.URL
import java.util.ArrayList
import android.content.Context

class ChannelList {
    
    protected fun parseUrl(urlString: String?): JSONObject? {
        var inputStream: InputStream? = null
        return try {
            inputStream = if (urlString?.startsWith("file:///android_asset/") == true) {
                // Handle assets file
                val assetPath = urlString.substring("file:///android_asset/".length)
                // Note: This requires context, will be handled in setupChannels
                null
            } else {
                // Handle regular URL
                val url = URL(urlString)
                val urlConnection = url.openConnection()
                BufferedInputStream(urlConnection.getInputStream())
            }
            
            val reader = BufferedReader(InputStreamReader(inputStream, "utf-8"), 1024)
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            val json = sb.toString()
            JSONObject(json)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse the json for channel list", e)
            null
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close()
                } catch (e: Exception) {
                    Log.d(TAG, "JSON InputStream could not be closed", e)
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "ChannelList"
        
        // JSON field names
        private const val TAG_CHANNELS = "channels"
        private const val TAG_ID = "id"
        private const val TAG_NAME = "name"
        private const val TAG_SUBTITLE = "subtitle"
        private const val TAG_LABELS = "labels"
        private const val TAG_IMAGE = "image"
        private const val TAG_TYPE = "type"
        private const val TAG_DISPLAY = "display"
        private const val TAG_SOURCES = "sources"
        private const val TAG_CONTENTS = "contents"
        private const val TAG_STREAMS = "streams"
        private const val TAG_STREAM_LINKS = "stream_links"
        private const val TAG_URL = "url"
        private const val TAG_DEFAULT = "default"
        private const val TAG_REQUEST_HEADERS = "request_headers"
        private const val TAG_KEY = "key"
        private const val TAG_VALUE = "value"
        private const val TAG_POSITION = "position"
        private const val TAG_TEXT = "text"
        private const val TAG_COLOR = "color"
        private const val TAG_TEXT_COLOR = "text_color"
        private const val TAG_HEIGHT = "height"
        private const val TAG_WIDTH = "width"
        private const val TAG_SHAPE = "shape"
        
        private var list: MutableList<Channel>? = null
        
        fun getList(): List<Channel>? {
            return list
        }
        
        fun clearList() {
            list = null
        }
        
        @Throws(JSONException::class)
        fun setupChannels(url: String?): List<Channel>? {
            if (null != list) {
                return list
            }
            list = ArrayList()
            
            val jsonObj = ChannelList().parseUrl(url)
            val channels = jsonObj?.getJSONArray(TAG_CHANNELS)
            
            return parseChannelsFromJson(channels)
        }
        
        @Throws(JSONException::class)
        fun setupChannelsFromAssets(context: Context, assetPath: String): List<Channel>? {
            if (null != list) {
                return list
            }
            list = ArrayList()
            
            val jsonObj = parseAssetFile(context, assetPath)
            val channels = jsonObj?.getJSONArray(TAG_CHANNELS)
            
            return parseChannelsFromJson(channels)
        }
        
        private fun parseAssetFile(context: Context, assetPath: String): JSONObject? {
            var inputStream: InputStream? = null
            return try {
                inputStream = context.assets.open(assetPath)
                val reader = BufferedReader(InputStreamReader(inputStream, "utf-8"), 1024)
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                val json = sb.toString()
                JSONObject(json)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to parse the json from assets", e)
                null
            } finally {
                if (null != inputStream) {
                    try {
                        inputStream.close()
                    } catch (e: Exception) {
                        Log.d(TAG, "Asset InputStream could not be closed", e)
                    }
                }
            }
        }
        
        private fun parseChannelsFromJson(channels: JSONArray?): List<Channel>? {
            if (null != channels) {
                for (i in 0 until channels.length()) {
                    val channelJson = channels.getJSONObject(i)
                    val channel = parseChannel(channelJson)
                    if (channel.hasValidSources()) {
                        list?.add(channel)
                    }
                }
            }
            
            return list
        }
        
        private fun parseChannel(channelJson: JSONObject): Channel {
            val channel = Channel()
            
            // Basic channel info
            channel.id = channelJson.optString(TAG_ID)
            channel.name = channelJson.optString(TAG_NAME)
            channel.subtitle = channelJson.optString(TAG_SUBTITLE)
            channel.type = channelJson.optString(TAG_TYPE)
            channel.display = channelJson.optString(TAG_DISPLAY)
            
            // Parse labels
            val labelsArray = channelJson.optJSONArray(TAG_LABELS)
            if (labelsArray != null) {
                for (j in 0 until labelsArray.length()) {
                    val labelJson = labelsArray.getJSONObject(j)
                    val label = parseLabel(labelJson)
                    channel.labels.add(label)
                }
            }
            
            // Parse image
            val imageJson = channelJson.optJSONObject(TAG_IMAGE)
            if (imageJson != null) {
                channel.image = parseImage(imageJson)
            }
            
            // Parse sources
            val sourcesArray = channelJson.optJSONArray(TAG_SOURCES)
            if (sourcesArray != null) {
                for (k in 0 until sourcesArray.length()) {
                    val sourceJson = sourcesArray.getJSONObject(k)
                    val source = parseSource(sourceJson)
                    channel.sources.add(source)
                }
            }
            
            return channel
        }
        
        private fun parseLabel(labelJson: JSONObject): Label {
            val label = Label()
            label.position = labelJson.optString(TAG_POSITION)
            label.text = labelJson.optString(TAG_TEXT)
            label.color = labelJson.optString(TAG_COLOR)
            label.textColor = labelJson.optString(TAG_TEXT_COLOR)
            return label
        }
        
        private fun parseImage(imageJson: JSONObject): Image {
            val image = Image()
            image.url = imageJson.optString(TAG_URL)
            image.height = imageJson.optInt(TAG_HEIGHT)
            image.width = imageJson.optInt(TAG_WIDTH)
            image.display = imageJson.optString(TAG_DISPLAY)
            image.shape = imageJson.optString(TAG_SHAPE)
            return image
        }
        
        private fun parseSource(sourceJson: JSONObject): Source {
            val source = Source()
            source.id = sourceJson.optString(TAG_ID)
            source.name = sourceJson.optString(TAG_NAME)
            
            val contentsArray = sourceJson.optJSONArray(TAG_CONTENTS)
            if (contentsArray != null) {
                for (l in 0 until contentsArray.length()) {
                    val contentJson = contentsArray.getJSONObject(l)
                    val content = parseContent(contentJson)
                    source.contents.add(content)
                }
            }
            
            return source
        }
        
        private fun parseContent(contentJson: JSONObject): Content {
            val content = Content()
            content.id = contentJson.optString(TAG_ID)
            content.name = contentJson.optString(TAG_NAME)
            
            val streamsArray = contentJson.optJSONArray(TAG_STREAMS)
            if (streamsArray != null) {
                for (m in 0 until streamsArray.length()) {
                    val streamJson = streamsArray.getJSONObject(m)
                    val stream = parseStream(streamJson)
                    content.streams.add(stream)
                }
            }
            
            return content
        }
        
        private fun parseStream(streamJson: JSONObject): Stream {
            val stream = Stream()
            stream.id = streamJson.optString(TAG_ID)
            stream.name = streamJson.optString(TAG_NAME)
            
            val streamLinksArray = streamJson.optJSONArray(TAG_STREAM_LINKS)
            if (streamLinksArray != null) {
                for (n in 0 until streamLinksArray.length()) {
                    val streamLinkJson = streamLinksArray.getJSONObject(n)
                    val streamLink = parseStreamLink(streamLinkJson)
                    stream.streamLinks.add(streamLink)
                }
            }
            
            return stream
        }
        
        private fun parseStreamLink(streamLinkJson: JSONObject): StreamLink {
            val streamLink = StreamLink()
            streamLink.id = streamLinkJson.optString(TAG_ID)
            streamLink.name = streamLinkJson.optString(TAG_NAME)
            streamLink.type = streamLinkJson.optString(TAG_TYPE)
            streamLink.isDefault = streamLinkJson.optBoolean(TAG_DEFAULT, false)
            streamLink.url = streamLinkJson.optString(TAG_URL)
            
            val headersArray = streamLinkJson.optJSONArray(TAG_REQUEST_HEADERS)
            if (headersArray != null) {
                for (o in 0 until headersArray.length()) {
                    val headerJson = headersArray.getJSONObject(o)
                    val header = RequestHeader()
                    header.key = headerJson.optString(TAG_KEY)
                    header.value = headerJson.optString(TAG_VALUE)
                    streamLink.requestHeaders.add(header)
                }
            }
            
            return streamLink
        }
    }
}
