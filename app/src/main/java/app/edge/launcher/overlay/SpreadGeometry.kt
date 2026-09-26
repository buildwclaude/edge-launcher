package app.edge.launcher.overlay

import kotlin.math.max
import kotlin.math.min

/** Where and how a card sits: left edge x, vertical centre, scale, tilt (deg). */
data class CardPose(
    val x: Float,
    val cy: Float,
    val scale: Float,
    val angle: Float,
    val alpha: Float,
    /** Opacity of the icon + title above the card. */
    val tile: Float,
) {
    fun lerp(to: CardPose, f: Float) = CardPose(
        x + (to.x - x) * f,
        cy + (to.cy - cy) * f,
        scale + (to.scale - scale) * f,
        angle + (to.angle - angle) * f,
        alpha + (to.alpha - alpha) * f,
        tile + (to.tile - tile) * f,
    )
}

/**
 * Layout model of the Lomiri phone spread: cards run along an ease-in-out
 * curve between a left and a right stack, tilting away from the viewer
 * (more on the right) and shrinking towards the left.
 */
class SpreadGeometry(val w: Float, val h: Float, density: Float) {
    private val gu = 8f * density
    private val heightGu = h / gu

    val contentMargin = 0.1f * h
    val appInfoH = (0.17f - 0.0014f * (heightGu - 40f)).coerceIn(0.08f, 0.2f) * h
    val cardH = h - contentMargin * 2 - appInfoH
    val cardW = cardH * w / h
    private val leftX = 0.03f * w
    private val rightX = w - 1.5f * leftX
    private val spreadW = rightX - leftX
    private val stackW = min(leftX / 3f, 1.5f * gu)
    private val angleFactor = (1.3f - 0.008f * ((h - 2 * contentMargin) / gu - 28f)).coerceIn(0.6f, 1.5f)
    private val leftAngle = 22f * angleFactor
    private val rightAngle = 32f * angleFactor
    private val overlap = (0.74f - 0.068f * (spreadW / cardH - 1f)).coerceIn(0.55f, 0.82f)
    private val visibleCount = (spreadW / cardH) / (1f - overlap)
    private val cardCy = contentMargin + appInfoH + cardH / 2f

    private fun totalWidth(n: Int) = max(2, n) * spreadW / visibleCount
    fun maxScroll(n: Int) = max(0f, totalWidth(n) - spreadW)
    private fun centering(n: Int) = max(spreadW - totalWidth(n) + leftX * 2f, 0f) / (2f * spreadW)

    fun spread(i: Int, n: Int, scroll: Float): CardPose {
        val pos = i / visibleCount - scroll / spreadW
        val leftP = map(pos, 0f, -STACK / visibleCount, 0f, 1f).coerceIn(0f, 1f)
        val rightP = map(pos, 1f, 1f + STACK / visibleCount, 0f, 1f).coerceIn(0f, 1f)
        val stackingX = (easeOutCubic(rightP) - easeOutCubic(leftP)) * stackW
        val x = leftX + spreadW * curve(pos + centering(n)) + stackingX
        val angle = map(x, leftX, rightX, leftAngle, rightAngle)
            .coerceIn(min(leftAngle, rightAngle), max(leftAngle, rightAngle))
        val scale = map(pos, 0f, 1f, 0.82f, 1f).coerceIn(0.82f, 1f)
        val tile = min(
            map(leftP, 0f, 1f / (STACK * 3f), 1f, 0f).coerceIn(0f, 1f),
            map(pos, 0.9f, 1f, 1f, 0f).coerceIn(0f, 1f),
        )
        val hidden = pos < -(STACK + 1) / visibleCount || (pos > 1f + STACK / visibleCount && i != n - 1)
        return CardPose(x, cardCy, scale, angle, if (hidden) 0f else 1f, tile)
    }

    /** Pose that exactly covers the screen. */
    fun fullscreen() = CardPose(0f, h / 2f, h / cardH, 0f, 1f, 0f)

    companion object {
        private const val STACK = 3f

        private fun map(v: Float, a: Float, b: Float, c: Float, d: Float) = c + (v - a) / (b - a) * (d - c)
        private fun easeOutCubic(t: Float): Float { val u = t - 1f; return u * u * u + 1f }

        /** cubic-bezier(0.19, 0, 0.91, 1) evaluated at x. */
        fun curve(xIn: Float): Float {
            val x = xIn.coerceIn(0f, 1f)
            val x1 = 0.19f; val y1 = 0f; val x2 = 0.91f; val y2 = 1f
            fun bx(t: Float) = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
            fun by(t: Float) = 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
            var lo = 0f
            var hi = 1f
            var t = x
            repeat(20) {
                t = (lo + hi) / 2f
                if (bx(t) < x) lo = t else hi = t
            }
            return by(t)
        }
    }
}
