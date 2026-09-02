package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Android mapping for SVG icons observed in the current fnOS Music Web build.
 * The source SVG paths and build hash are recorded in docs/web-assets/manifest.json.
 */
object FnIcons {
    /** Dedicated roam glyph used by the current fnOS Music Web home card. */
    val Roam: ImageVector by lazy { webIcon("WebRoam", "M3.882 3.317a1 1 0 0 1 1.462 1.365C3.526 6.63 2.5 9.255 2.5 12c0 2.744 1.026 5.37 2.844 7.317a1 1 0 0 1-1.462 1.366C1.709 18.355.5 15.238.5 12c0-3.238 1.21-6.357 3.382-8.683zm14.83-.048a1 1 0 0 1 1.414.048c2.172 2.326 3.382 5.445 3.382 8.683 0 3.238-1.21 6.356-3.382 8.683a1 1 0 0 1-1.462-1.366c1.818-1.946 2.844-4.573 2.844-7.317 0-2.745-1.026-5.371-2.844-7.318a1 1 0 0 1 .049-1.413zM6.838 7.317A1 1 0 0 1 8.3 8.682 4.865 4.865 0 0 0 7.012 12c0 1.249.467 2.44 1.287 3.317a1 1 0 0 1-1.462 1.366A6.865 6.865 0 0 1 5.012 12c0-1.743.65-3.425 1.825-4.683zm8.932-.048a1 1 0 0 1 1.414.048A6.865 6.865 0 0 1 19.008 12c0 1.742-.65 3.424-1.825 4.683a1.001 1.001 0 0 1-1.462-1.366A4.865 4.865 0 0 0 17.008 12c0-1.249-.467-2.44-1.287-3.318a1 1 0 0 1 .048-1.413zM12 10.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z") }
    val Home: ImageVector by lazy { webIcon("WebHome", "M11.999,15.883a3.3,3.3 0,0 1,3.28 2.935L15.411,20H19l0.107,-0.005c0.213,-0.02 0.407,-0.096 0.558,-0.21l0.072,-0.06A0.771,0.771 0,0 0,20 19.159V8.701a0.89,0.89 0,0 0,-0.451 -0.773L12,3.649 4.451,7.928A0.89,0.89 0,0 0,4 8.7V19.16c0,0.195 0.083,0.4 0.262,0.566 0.183,0.167 0.447,0.275 0.738,0.275h3.586l0.132,-1.182a3.301,3.301 0,0 1,3.281 -2.935zM22,19.159a2.77,2.77 0,0 1,-0.91 2.037v0.001a3.076,3.076 0,0 1,-1.802 0.79L19,22h-4.484a1,1 0,0 1,-0.995 -0.89l-0.23,-2.071a1.3,1.3 0,0 0,-1.169 -1.15L12,17.883a1.3,1.3 0,0 0,-1.293 1.156l-0.23,2.071a1,1 0,0 1,-0.995 0.89H5c-0.77,0 -1.523,-0.28 -2.09,-0.803A2.77,2.77 0,0 1,2 19.158V8.701c0,-1.04 0.56,-2 1.465,-2.514l8.042,-4.557 0.118,-0.057a1,1 0,0 1,0.868 0.057l8.042,4.557A2.89,2.89 0,0 1,22 8.701V19.16z") }
    val Favorite: ImageVector by lazy { webIcon("WebFavorite", "M20.662,8.996a4.316,4.316 0,0 0,-7.847 -2.482l-0.818,1.161 -0.817,-1.16a4.316,4.316 0,0 0,-7.848 2.481c0,2.273 1.36,4.57 3.274,6.505 1.777,1.794 3.886,3.124 5.391,3.704 1.505,-0.58 3.614,-1.91 5.39,-3.704 1.916,-1.934 3.275,-4.232 3.275,-6.505zM22.662,8.996c0,3.042 -1.781,5.82 -3.853,7.912 -2.082,2.102 -4.628,3.688 -6.497,4.31l-0.315,0.104 -0.315,-0.105c-1.868,-0.62 -4.415,-2.207 -6.496,-4.31C3.114,14.815 1.332,12.037 1.332,8.996A6.316,6.316 0,0 1,7.647 2.68c1.687,0 3.217,0.664 4.35,1.74a6.316,6.316 0,0 1,10.666 4.576z") }
    val Recent: ImageVector by lazy { webIcon("WebRecent", "M12,1c6.075,0 11,4.925 11,11s-4.925,11 -11,11S1,18.075 1,12 5.925,1 12,1zM12,3a9,9 0,1 0,0 18,9 9,0 0,0 0,-18zM12,6a1,1 0,0 1,1 1v4.382l3.447,1.723a1,1 0,0 1,-0.894 1.79l-4,-2A1,1 0,0 1,11 12V7a1,1 0,0 1,1 -1z") }
    val Album: ImageVector by lazy { webIcon("WebAlbum", "M21,12a9,9 0,1 0,-18 0,9 9,0 0,0 18,0zM17,12a1,1 0,1 1,2 0c0,1.977 -0.815,3.747 -2.127,4.94a1,1 0,0 1,-1.346 -1.48C16.415,14.653 17,13.423 17,12zM5,12c0,-1.977 0.815,-3.747 2.127,-4.94a1,1 0,0 1,1.346 1.48C7.585,9.347 7,10.577 7,12a1,1 0,1 1,-2 0zM23,12c0,6.075 -4.925,11 -11,11S1,18.075 1,12 5.925,1 12,1s11,4.925 11,11z") }
    val Artist: ImageVector = Icons.Rounded.Person
    val Library: ImageVector by lazy { webIcon("WebLibrary", "M11.25,3.5a1,1 0,1 1,0 2H5.1A1.1,1.1 0,0 0,4 6.6v12.3A1.1,1.1 0,0 0,5.1 20H18a2,2 0,0 0,2 -2v-7.5a1,1 0,1 1,2 0V18a4,4 0,0 1,-4 4H5.1A3.1,3.1 0,0 1,2 18.9V6.6a3.1,3.1 0,0 1,3.1 -3.1h6.15zM20.784,3.145a1,1 0,0 1,0.121 1.996l-1.81,0.11a0.501,0.501 0,0 0,-0.461 0.407l-0.98,5.253 -0.005,0.019 -0.396,2.123a3.896,3.896 0,0 1,-4.544 3.114l-0.133,-0.025a3.761,3.761 0,0 1,0.554 -7.454l2.924,-0.105 0.614,-3.292a2.5,2.5 0,0 1,2.307 -2.036l1.81,-0.11zM13.202,10.687a1.76,1.76 0,0 0,-0.26 3.49l0.134,0.024a1.896,1.896 0,0 0,2.211 -1.516l0.39,-2.088 -2.475,0.09z") }
    val Search: ImageVector = Icons.Rounded.Search
    val More: ImageVector = Icons.Rounded.MoreHoriz
    val Settings: ImageVector = Icons.Rounded.Settings

    private fun webIcon(name: String, path: String): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}
