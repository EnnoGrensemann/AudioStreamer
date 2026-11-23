package com.vinylcast.sonos

import com.vinylcast.model.TrackInfo

object SonosXmlBuilder {

    fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun buildDidlLiteMetadata(
        streamUrl: String,
        track: TrackInfo?
    ): String {
        val title = escapeXml(track?.title?.takeIf { it.isNotBlank() } ?: "Vinyl Line-In")
        val artist = escapeXml(track?.artist?.takeIf { it.isNotBlank() } ?: "Plattenspieler")
        val album = escapeXml(track?.album?.takeIf { it.isNotBlank() } ?: "VinylCast")
        val albumArtTag = track?.coverUrl?.takeIf { it.isNotBlank() }?.let {
            "<upnp:albumArtURI>${escapeXml(it)}</upnp:albumArtURI>"
        } ?: ""

        return """
            <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                <item id="-1" parentID="-1" restricted="true">
                    <res protocolInfo="http-get:*:audio/wav:*">${escapeXml(streamUrl)}</res>
                    <dc:title>$title</dc:title>
                    <dc:creator>$artist</dc:creator>
                    <upnp:album>$album</upnp:album>
                    $albumArtTag
                    <upnp:class>object.item.audioItem.audioBroadcast</upnp:class>
                </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    fun buildSetAvTransportUriSoap(streamUrl: String, didlMetadata: String): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>${escapeXml(streamUrl)}</CurrentURI>
                        <CurrentURIMetaData>${escapeXml(didlMetadata)}</CurrentURIMetaData>
                    </u:SetAVTransportURI>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun buildPlaySoap(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <Speed>1</Speed>
                    </u:Play>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun buildStopSoap(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Stop>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun buildGetVolumeSoap(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:GetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                        <InstanceID>0</InstanceID>
                        <Channel>Master</Channel>
                    </u:GetVolume>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun buildSetVolumeSoap(volume: Int): String {
        val clamped = volume.coerceIn(0, 100)
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                        <InstanceID>0</InstanceID>
                        <Channel>Master</Channel>
                        <DesiredVolume>$clamped</DesiredVolume>
                    </u:SetVolume>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun parseSsdpLocation(ssdpResponse: String): String? {
        return ssdpResponse.lineSequence()
            .firstOrNull { it.trim().startsWith("LOCATION:", ignoreCase = true) }
            ?.substringAfter(":", "")
            ?.trim()
            ?.takeIf { it.startsWith("http") }
    }

    fun parseXmlTag(xml: String, tagName: String): String? {
        val regex = Regex("<$tagName>(.*?)</$tagName>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }
}
