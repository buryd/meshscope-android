package com.meshscope.android.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.meshscope.android.data.AnalyzerNode
import com.meshscope.android.data.LiveTrafficEvent
import com.meshscope.android.data.PathHop
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.max

@Composable
fun TrafficMap(
    nodes: List<AnalyzerNode>,
    events: List<LiveTrafficEvent>,
    selectedEventKey: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidBasePath = context.cacheDir
        Configuration.getInstance().osmdroidTileCache = context.cacheDir.resolve("tiles")
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(32.613, -83.624)) // Warner Robins, GA
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(nodes, events, selectedEventKey) {
        mapView.overlays.clear()

        val nodeById = nodes.associateBy { it.resolvedId() }
        val nodePoints = mutableListOf<GeoPoint>()

        nodes.forEach { node ->
            val lat = node.resolvedLat() ?: return@forEach
            val lon = node.resolvedLon() ?: return@forEach
            val point = GeoPoint(lat, lon)
            nodePoints += point
            val marker = Marker(mapView).apply {
                position = point
                title = node.name ?: node.resolvedId().take(10)
                snippet = node.role ?: "node"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays += marker
        }

        val selected = events.firstOrNull { it.key == selectedEventKey } ?: events.firstOrNull()
        selected?.let { event ->
            val pathPoints = resolvePathPoints(event.hops, nodeById, event.packet.originId, event.packet.destId)
            if (pathPoints.size >= 2) {
                val line = Polyline().apply {
                    setPoints(pathPoints)
                    outlinePaint.color = 0xFF7CFFB2.toInt()
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.isAntiAlias = true
                }
                mapView.overlays += line
                mapView.overlays += PulseOverlay(pathPoints)
                nodePoints += pathPoints
            }
        }

        // Dimmer recent trails.
        events.drop(0).take(8).forEachIndexed { index, event ->
            if (event.key == selected?.key) return@forEachIndexed
            val pts = resolvePathPoints(event.hops, nodeById, event.packet.originId, event.packet.destId)
            if (pts.size < 2) return@forEachIndexed
            val alpha = max(40, 140 - index * 15)
            val trail = Polyline().apply {
                setPoints(pts)
                outlinePaint.color = (alpha shl 24) or 0x0039C9A0
                outlinePaint.strokeWidth = 4f
            }
            mapView.overlays += trail
        }

        if (nodePoints.isNotEmpty() && mapView.zoomLevelDouble < 4.5) {
            val avgLat = nodePoints.map { it.latitude }.average()
            val avgLon = nodePoints.map { it.longitude }.average()
            mapView.controller.setCenter(GeoPoint(avgLat, avgLon))
            mapView.controller.setZoom(estimateZoom(nodePoints))
        }

        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { it.invalidate() },
    )
}

private fun resolvePathPoints(
    hops: List<PathHop>,
    nodes: Map<String, AnalyzerNode>,
    originId: String?,
    destId: String?,
): List<GeoPoint> {
    val fromHops = hops.mapNotNull { hop ->
        val lat = hop.lat
        val lon = hop.lon
        if (lat != null && lon != null) {
            GeoPoint(lat, lon)
        } else {
            val node = nodes[hop.resolvedId()]
                ?: hop.publicKey?.let { nodes[it] }
                ?: hop.name?.let { key -> nodes.values.firstOrNull { it.name == key } }
            val nLat = node?.resolvedLat()
            val nLon = node?.resolvedLon()
            if (nLat != null && nLon != null) GeoPoint(nLat, nLon) else null
        }
    }
    if (fromHops.size >= 2) return fromHops

    val fallback = mutableListOf<GeoPoint>()
    listOfNotNull(originId, destId).forEach { id ->
        val node = nodes[id] ?: return@forEach
        val lat = node.resolvedLat() ?: return@forEach
        val lon = node.resolvedLon() ?: return@forEach
        fallback += GeoPoint(lat, lon)
    }
    return fallback
}

private fun estimateZoom(points: List<GeoPoint>): Double {
    if (points.size == 1) return 12.0
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val span = max(maxLat - minLat, maxLon - minLon)
    return when {
        span > 8 -> 6.0
        span > 3 -> 7.5
        span > 1 -> 9.0
        span > 0.3 -> 10.5
        else -> 12.0
    }.coerceIn(5.0, 14.0)
}

private class PulseOverlay(
    private val points: List<GeoPoint>,
) : Overlay() {
    private var progress = 0f

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || points.size < 2) return
        progress = (progress + 0.02f) % 1f
        val projection = mapView.projection
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xE67CFFB2.toInt()
        }
        val screen = Point()
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val lat = a.latitude + (b.latitude - a.latitude) * progress
            val lon = a.longitude + (b.longitude - a.longitude) * progress
            projection.toPixels(GeoPoint(lat, lon), screen)
            canvas.drawCircle(screen.x.toFloat(), screen.y.toFloat(), 14f, paint)
        }
        mapView.postInvalidateDelayed(40)
    }
}
