package com.astralstream.player.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

/**
 * DLNA/UPnP service for device discovery and media streaming
 * Implements SSDP (Simple Service Discovery Protocol) for device discovery
 */
@Singleton
class DlnaService @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "DlnaService"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_SEARCH_MESSAGE = 
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 3\r\n" +
            "ST: upnp:rootdevice\r\n" +
            "\r\n"
        
        private const val DLNA_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
        private const val DLNA_MEDIA_SERVER = "urn:schemas-upnp-org:device:MediaServer:1"
        private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val CONTENT_DIRECTORY_SERVICE = "urn:schemas-upnp-org:service:ContentDirectory:1"
    }
    
    data class DlnaDevice(
        val uuid: String,
        val friendlyName: String,
        val manufacturer: String? = null,
        val modelName: String? = null,
        val type: DeviceType,
        val location: String,
        val ipAddress: String,
        val services: Map<String, String> = emptyMap(),
        val iconUrl: String? = null,
        val lastSeen: Long = System.currentTimeMillis()
    )
    
    enum class DeviceType {
        MEDIA_SERVER,
        MEDIA_RENDERER,
        OTHER
    }
    
    data class DlnaContent(
        val id: String,
        val parentId: String,
        val title: String,
        val type: ContentType,
        val url: String? = null,
        val duration: String? = null,
        val size: Long = 0,
        val mimeType: String? = null,
        val albumArt: String? = null
    )
    
    enum class ContentType {
        CONTAINER,
        VIDEO,
        AUDIO,
        IMAGE
    }
    
    sealed class DlnaResult<out T> {
        data class Success<T>(val data: T) : DlnaResult<T>()
        data class Error(val exception: Exception) : DlnaResult<Nothing>()
        object Loading : DlnaResult<Nothing>()
    }
    
    private val discoveredDevices = ConcurrentHashMap<String, DlnaDevice>()
    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    
    private var discoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    
    /**
     * Start DLNA device discovery
     */
    fun startDiscovery() {
        stopDiscovery()
        
        discoveryJob = CoroutineScope(Dispatchers.IO).launch {
            // Acquire multicast lock for UDP multicast
            acquireMulticastLock()
            
            try {
                while (isActive) {
                    discoverDevices()
                    delay(30000) // Refresh every 30 seconds
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error", e)
            }
        }
    }
    
    /**
     * Stop DLNA device discovery
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        releaseMulticastLock()
    }
    
    /**
     * Discover DLNA devices on the network
     */
    private suspend fun discoverDevices() = withContext(Dispatchers.IO) {
        try {
            val socket = MulticastSocket(SSDP_PORT)
            socket.soTimeout = 5000
            
            val group = InetAddress.getByName(SSDP_ADDRESS)
            socket.joinGroup(group)
            
            // Send M-SEARCH message
            val searchMessage = SSDP_SEARCH_MESSAGE.toByteArray()
            val packet = DatagramPacket(
                searchMessage,
                searchMessage.size,
                group,
                SSDP_PORT
            )
            socket.send(packet)
            
            // Listen for responses
            val buffer = ByteArray(8192)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            
            val endTime = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < endTime) {
                try {
                    socket.receive(responsePacket)
                    val response = String(responsePacket.data, 0, responsePacket.length)
                    parseSsdpResponse(response, responsePacket.address.hostAddress ?: "")
                } catch (e: SocketTimeoutException) {
                    // Timeout is expected, continue
                }
            }
            
            socket.leaveGroup(group)
            socket.close()
            
            // Update device list
            _devices.value = discoveredDevices.values.toList()
            
        } catch (e: Exception) {
            Log.e(TAG, "Device discovery failed", e)
        }
    }
    
    /**
     * Parse SSDP response and extract device information
     */
    private suspend fun parseSsdpResponse(response: String, ipAddress: String) {
        val lines = response.split("\r\n")
        var location: String? = null
        var uuid: String? = null
        
        for (line in lines) {
            when {
                line.startsWith("LOCATION:", ignoreCase = true) -> {
                    location = line.substring(9).trim()
                }
                line.startsWith("USN:", ignoreCase = true) -> {
                    val usn = line.substring(4).trim()
                    uuid = extractUuid(usn)
                }
            }
        }
        
        if (location != null && uuid != null) {
            fetchDeviceDescription(uuid, location, ipAddress)
        }
    }
    
    /**
     * Extract UUID from USN string
     */
    private fun extractUuid(usn: String): String? {
        val regex = "uuid:([^:]+)".toRegex()
        return regex.find(usn)?.groupValues?.get(1)
    }
    
    /**
     * Fetch and parse device description XML
     */
    private suspend fun fetchDeviceDescription(uuid: String, location: String, ipAddress: String) {
        try {
            val url = URL(location)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val xmlContent = reader.use { it.readText() }
            
            parseDeviceXml(uuid, xmlContent, location, ipAddress)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch device description from $location", e)
        }
    }
    
    /**
     * Parse device description XML
     */
    private fun parseDeviceXml(uuid: String, xml: String, location: String, ipAddress: String) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())
            
            val device = document.getElementsByTagName("device").item(0) as? Element ?: return
            
            val friendlyName = device.getElementsByTagName("friendlyName").item(0)?.textContent ?: "Unknown Device"
            val manufacturer = device.getElementsByTagName("manufacturer").item(0)?.textContent
            val modelName = device.getElementsByTagName("modelName").item(0)?.textContent
            val deviceType = device.getElementsByTagName("deviceType").item(0)?.textContent
            
            val type = when {
                deviceType?.contains(DLNA_MEDIA_SERVER) == true -> DeviceType.MEDIA_SERVER
                deviceType?.contains(DLNA_MEDIA_RENDERER) == true -> DeviceType.MEDIA_RENDERER
                else -> DeviceType.OTHER
            }
            
            // Parse services
            val services = mutableMapOf<String, String>()
            val serviceList = device.getElementsByTagName("service")
            for (i in 0 until serviceList.length) {
                val service = serviceList.item(i) as? Element ?: continue
                val serviceType = service.getElementsByTagName("serviceType").item(0)?.textContent
                val controlUrl = service.getElementsByTagName("controlURL").item(0)?.textContent
                
                if (serviceType != null && controlUrl != null) {
                    services[serviceType] = controlUrl
                }
            }
            
            // Parse icon URL
            val iconUrl = parseIconUrl(device, location)
            
            val dlnaDevice = DlnaDevice(
                uuid = uuid,
                friendlyName = friendlyName,
                manufacturer = manufacturer,
                modelName = modelName,
                type = type,
                location = location,
                ipAddress = ipAddress,
                services = services,
                iconUrl = iconUrl
            )
            
            discoveredDevices[uuid] = dlnaDevice
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse device XML", e)
        }
    }
    
    /**
     * Parse icon URL from device description
     */
    private fun parseIconUrl(device: Element, baseUrl: String): String? {
        val iconList = device.getElementsByTagName("icon")
        if (iconList.length > 0) {
            val icon = iconList.item(0) as? Element
            val url = icon?.getElementsByTagName("url")?.item(0)?.textContent
            if (url != null) {
                return if (url.startsWith("http")) {
                    url
                } else {
                    val base = URL(baseUrl)
                    URL(base, url).toString()
                }
            }
        }
        return null
    }
    
    /**
     * Browse content on a DLNA media server
     */
    suspend fun browseContent(
        device: DlnaDevice,
        objectId: String = "0"
    ): Flow<DlnaResult<List<DlnaContent>>> = flow {
        emit(DlnaResult.Loading)
        
        try {
            val contentDirService = device.services[CONTENT_DIRECTORY_SERVICE]
            if (contentDirService == null) {
                emit(DlnaResult.Error(Exception("Device doesn't support content browsing")))
                return@flow
            }
            
            val content = browseContentDirectory(device, contentDirService, objectId)
            emit(DlnaResult.Success(content))
            
        } catch (e: Exception) {
            emit(DlnaResult.Error(e))
        }
    }
    
    /**
     * Browse content directory using SOAP action
     */
    private suspend fun browseContentDirectory(
        device: DlnaDevice,
        serviceUrl: String,
        objectId: String
    ): List<DlnaContent> = withContext(Dispatchers.IO) {
        val soapAction = "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\""
        val soapBody = buildBrowseSoapRequest(objectId)
        
        val fullUrl = if (serviceUrl.startsWith("http")) {
            serviceUrl
        } else {
            val base = URL(device.location)
            URL(base, serviceUrl).toString()
        }
        
        val url = URL(fullUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
        connection.setRequestProperty("SOAPAction", soapAction)
        connection.doOutput = true
        
        connection.outputStream.use { it.write(soapBody.toByteArray()) }
        
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        parseContentDirectoryResponse(response)
    }
    
    /**
     * Build SOAP request for browsing content
     */
    private fun buildBrowseSoapRequest(objectId: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" 
                        s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Browse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                        <ObjectID>$objectId</ObjectID>
                        <BrowseFlag>BrowseDirectChildren</BrowseFlag>
                        <Filter>*</Filter>
                        <StartingIndex>0</StartingIndex>
                        <RequestedCount>1000</RequestedCount>
                        <SortCriteria></SortCriteria>
                    </u:Browse>
                </s:Body>
            </s:Envelope>""".trimIndent()
    }
    
    /**
     * Parse content directory response
     */
    private fun parseContentDirectoryResponse(response: String): List<DlnaContent> {
        val contents = mutableListOf<DlnaContent>()
        
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(response.byteInputStream())
            
            val result = document.getElementsByTagName("Result").item(0)?.textContent ?: return contents
            val resultDoc = builder.parse(result.byteInputStream())
            
            // Parse containers
            val containers = resultDoc.getElementsByTagName("container")
            for (i in 0 until containers.length) {
                val container = containers.item(i) as? Element ?: continue
                contents.add(parseContainer(container))
            }
            
            // Parse items
            val items = resultDoc.getElementsByTagName("item")
            for (i in 0 until items.length) {
                val item = items.item(i) as? Element ?: continue
                contents.add(parseItem(item))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse content directory response", e)
        }
        
        return contents
    }
    
    /**
     * Parse container element
     */
    private fun parseContainer(element: Element): DlnaContent {
        return DlnaContent(
            id = element.getAttribute("id") ?: "",
            parentId = element.getAttribute("parentID") ?: "",
            title = element.getElementsByTagName("dc:title").item(0)?.textContent ?: "Unknown",
            type = ContentType.CONTAINER
        )
    }
    
    /**
     * Parse item element
     */
    private fun parseItem(element: Element): DlnaContent {
        val upnpClass = element.getElementsByTagName("upnp:class").item(0)?.textContent ?: ""
        val type = when {
            upnpClass.contains("video") -> ContentType.VIDEO
            upnpClass.contains("audio") -> ContentType.AUDIO
            upnpClass.contains("image") -> ContentType.IMAGE
            else -> ContentType.VIDEO
        }
        
        val res = element.getElementsByTagName("res").item(0)
        val url = res?.textContent
        val size = (res as? Element)?.getAttribute("size")?.toLongOrNull() ?: 0
        val duration = (res as? Element)?.getAttribute("duration")
        val mimeType = (res as? Element)?.getAttribute("protocolInfo")?.split(":")?.getOrNull(2)
        
        return DlnaContent(
            id = element.getAttribute("id") ?: "",
            parentId = element.getAttribute("parentID") ?: "",
            title = element.getElementsByTagName("dc:title").item(0)?.textContent ?: "Unknown",
            type = type,
            url = url,
            duration = duration,
            size = size,
            mimeType = mimeType,
            albumArt = element.getElementsByTagName("upnp:albumArtURI").item(0)?.textContent
        )
    }
    
    /**
     * Play media on a DLNA renderer
     */
    suspend fun playOnRenderer(
        device: DlnaDevice,
        mediaUrl: String,
        mediaTitle: String = "Media"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val avTransportService = device.services[AV_TRANSPORT_SERVICE]
                ?: return@withContext false
            
            // Set AV Transport URI
            setAvTransportUri(device, avTransportService, mediaUrl, mediaTitle)
            
            // Play
            play(device, avTransportService)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play on renderer", e)
            false
        }
    }
    
    /**
     * Set AV Transport URI
     */
    private suspend fun setAvTransportUri(
        device: DlnaDevice,
        serviceUrl: String,
        mediaUrl: String,
        mediaTitle: String
    ) = withContext(Dispatchers.IO) {
        val soapAction = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\""
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                        s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>$mediaUrl</CurrentURI>
                        <CurrentURIMetaData>
                            &lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"&gt;
                                &lt;item&gt;
                                    &lt;dc:title xmlns:dc="http://purl.org/dc/elements/1.1/"&gt;$mediaTitle&lt;/dc:title&gt;
                                &lt;/item&gt;
                            &lt;/DIDL-Lite&gt;
                        </CurrentURIMetaData>
                    </u:SetAVTransportURI>
                </s:Body>
            </s:Envelope>""".trimIndent()
        
        sendSoapAction(device, serviceUrl, soapAction, soapBody)
    }
    
    /**
     * Play command
     */
    private suspend fun play(
        device: DlnaDevice,
        serviceUrl: String
    ) = withContext(Dispatchers.IO) {
        val soapAction = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\""
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                        s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <Speed>1</Speed>
                    </u:Play>
                </s:Body>
            </s:Envelope>""".trimIndent()
        
        sendSoapAction(device, serviceUrl, soapAction, soapBody)
    }
    
    /**
     * Send SOAP action
     */
    private suspend fun sendSoapAction(
        device: DlnaDevice,
        serviceUrl: String,
        soapAction: String,
        soapBody: String
    ) = withContext(Dispatchers.IO) {
        val fullUrl = if (serviceUrl.startsWith("http")) {
            serviceUrl
        } else {
            val base = URL(device.location)
            URL(base, serviceUrl).toString()
        }
        
        val url = URL(fullUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
        connection.setRequestProperty("SOAPAction", soapAction)
        connection.doOutput = true
        
        connection.outputStream.use { it.write(soapBody.toByteArray()) }
        
        // Read response
        connection.inputStream.bufferedReader().use { it.readText() }
    }
    
    /**
     * Acquire multicast lock for UDP multicast
     */
    private fun acquireMulticastLock() {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("AstralStreamDLNA").apply {
            acquire()
        }
    }
    
    /**
     * Release multicast lock
     */
    private fun releaseMulticastLock() {
        multicastLock?.release()
        multicastLock = null
    }
    
    /**
     * Clear discovered devices
     */
    fun clearDevices() {
        discoveredDevices.clear()
        _devices.value = emptyList()
    }
}