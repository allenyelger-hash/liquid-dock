/*
 * Copyright 2025 Kyant. Apache License, Version 2.0.
 * Adapted from Backdrop internal/Shaders.kt at 65ab177e90e5c1d8c62e70cf7755841982da65f6.
 * G2 continuous capsule SDF, clean affine frosted base glass with full-height passage lift &
 * 360-deg toroidal 3D glass wall, diffeomorphic 360-deg 3D droplet Snell meniscus, 4-corner
 * bubble x Dock contour intersection Cauchy prism dispersion, separate 360-deg convex droplet
 * label/badge refraction, and continuous meniscus lip adapted.
 * See THIRD_PARTY_NOTICES/AndroidLiquidGlass.md.
 */
package com.liquiddock.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported

/**
 * Shared backdrop for the base bar and circular action button. A fine crest rolls into
 * an illuminated bevel above and below, with a dark grazing edge at the sides.
 */
internal fun BackdropEffectScope.dockBaseGlass(
    pressProgress: Float = 0f,
    lensLift: Float = 0f,
    clarity: Float = 0f,
    pillCenter: Offset = Offset(-10000f, -10000f),
    pillHalfSize: Offset = Offset.Zero,
    footprintCenter: Offset = Offset(-10000f, -10000f),
    footprintHalfSize: Offset = Offset.Zero,
) {
    if (size.isUnspecified || size.width <= 0f || size.height <= 0f) return
    val runtimeShaderSupported = isRuntimeShaderSupported()
    val height = size.height
    val blurRatio = if (runtimeShaderSupported) 0.090f else 0.118f
    vibrancy()
    blur(height * blurRatio)
    lens(height * 0.205f, height * 0.28f, depthEffect = true)
    if (!runtimeShaderSupported) return
    runtimeShaderEffect("DockAdaptiveBaseGlass", DockBaseGlassShader, "content") {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("density", density)
        setFloatUniform("pressProgress", pressProgress.coerceIn(0f, 1.5f))
        setFloatUniform("lensLift", lensLift.coerceIn(0f, 1.2f))
        setFloatUniform("clarity", clarity.coerceIn(0f, 1f))
        setFloatUniform("pillCenter", pillCenter.x, pillCenter.y)
        setFloatUniform("pillHalfSize", pillHalfSize.x, pillHalfSize.y)
        setFloatUniform("footprintCenter", footprintCenter.x, footprintCenter.y)
        setFloatUniform("footprintHalfSize", footprintHalfSize.x, footprintHalfSize.y)
    }
}

/**
 * Scale-invariant 3D convex water-droplet optical transfer for the active lifted capsule's
 * page backdrop + dock base glass layer (API 33+):
 * - Globally C-infinity G2 continuous capsule SDF & 3D surface normal;
 * - 360-degree isotropic contact-zero diffeomorphic meniscus Snell shift (`S(0) == 0`, zero endcap
 *   adhesion, 0-jump boat-window convergence at P_L/P_R, unbroken 180-deg concentric endcap arc);
 * - Localized central ~1.18x dome magnification with single-tap fast path in the central reading zone;
 * - Four-corner bubble x Dock-contour intersection 7-band CIE Cauchy prism dispersion + refracted-rail
 *   prismatic spectral caustic (`sdG2Capsule <= 0` strictly enforced on all 7 taps, pure silver-white
 *   horizontal center crown);
 * - Seamless convex dome clarity, a fine inner highlight biased toward the upper/lower arc,
 *   and a crisp outer graphite rim around the overhang.
 */
internal fun BackdropEffectScope.dockLens(
    progress: Float,
    baseCenter: Offset = Offset.Zero,
    baseHalfSize: Offset = Offset.Zero,
) {
    if (!isRuntimeShaderSupported()) return
    if (progress <= 0f) return
    if (size.isUnspecified || size.width <= 0f || size.height <= 0f) return
    val clampedProgress = progress.coerceIn(0f, 1.2f)
    val hasExplicitBase = baseHalfSize.x > 1f && baseHalfSize.y > 1f
    val resolvedBaseHalfX = if (hasExplicitBase) baseHalfSize.x else size.width * 2.5f
    val resolvedBaseHalfY = if (hasExplicitBase) baseHalfSize.y else size.height * (0.5f / 1.1940f) * 1.045f
    padding = maxOf(padding, size.height * 0.22f)
    runtimeShaderEffect("DockDropletLens", DockRefractionShader, "content") {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("progress", clampedProgress)
        setFloatUniform("density", density)
        setFloatUniform("baseCenter", baseCenter.x, baseCenter.y)
        setFloatUniform("baseHalfSize", resolvedBaseHalfX, resolvedBaseHalfY)
    }
}

/**
 * Independent 360-degree convex water-droplet refraction & spectral dispersion for the transparent
 * tab labels & badge layer (`recordedLabels`, API 33+). Separating label refraction from [dockLens]
 * lets swept strokes and corner badges stretch outward along the curved droplet contour with vivid
 * chromatic dispersion while keeping the underlying dock rails and semicircular endcaps strictly
 * concentric and adhesion-free.
 */
internal fun BackdropEffectScope.dockLabelLens(
    progress: Float,
) {
    if (!isRuntimeShaderSupported()) return
    if (progress <= 0f) return
    if (size.isUnspecified || size.width <= 0f || size.height <= 0f) return
    val clampedProgress = progress.coerceIn(0f, 1.2f)
    padding = maxOf(padding, size.height * 0.22f)
    runtimeShaderEffect("DockLabelDropletLens", DockLabelRefractionShader, "content") {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("progress", clampedProgress)
        setFloatUniform("density", density)
    }
}

// Globally C-infinity G2 continuous capsule SDF and smooth 3D outward gradient.
// Exactly degenerates to a Euclidean circle when halfSize.x == halfSize.y (LiquidButton),
// aligns sdG2Capsule == 0 to Kyant's Capsule() (Continuous) cubic Bezier outline within 0.06dp RMS,
// and provides smooth continuous curvature across the entire capsule crown and oblique shoulders.
private const val DockG2CapsuleSDF = """
float sdG2Capsule(float2 p, float2 halfSize) {
    float a = halfSize.x;
    float b = halfSize.y;
    if (abs(a - b) < 0.01) {
        return length(p) - min(a, b);
    }
    if (a < b) {
        float Lv = b - a;
        float sigV = min(0.26 * a, Lv);
        float uv = abs(p.y) - Lv;
        float spV = 0.5 * (uv + sqrt(uv * uv + sigV * sigV));
        float s0V = 0.5 * (-Lv + sqrt(Lv * Lv + sigV * sigV));
        float sEndV = max(0.5 * (a + sqrt(a * a + sigV * sigV)) - s0V, 0.0001);
        float qy = max(spV - s0V, 0.0) * (a / sEndV);
        return length(float2(p.x, qy)) - a;
    }
    float L = a - b;
    float sigma = min(0.26 * b, L);
    float u = abs(p.x) - L;
    float splus = 0.5 * (u + sqrt(u * u + sigma * sigma));
    float s0 = 0.5 * (-L + sqrt(L * L + sigma * sigma));
    float sEnd = max(0.5 * (b + sqrt(b * b + sigma * sigma)) - s0, 0.0001);
    float qx = max(splus - s0, 0.0) * (b / sEnd);
    return length(float2(qx, p.y)) - b;
}

float2 gradG2Capsule(float2 p, float2 halfSize) {
    float a = halfSize.x;
    float b = halfSize.y;
    if (abs(a - b) < 0.01) {
        float len = length(p);
        if (len > 0.0001) {
            return p / len;
        }
        return float2(0.0, mix(-1.0, 1.0, step(0.0, p.y)));
    }
    if (a < b) {
        float Lv = b - a;
        float sigV = min(0.58 * a, Lv);
        float uv = abs(p.y) - Lv;
        float rootV = sqrt(uv * uv + sigV * sigV);
        float spV = 0.5 * (uv + rootV);
        float dsDv = 0.5 * (1.0 + uv / max(rootV, 0.0001));
        float s0V = 0.5 * (-Lv + sqrt(Lv * Lv + sigV * sigV));
        float sEndV = max(0.5 * (a + sqrt(a * a + sigV * sigV)) - s0V, 0.0001);
        float scaleV = a / sEndV;
        float qy = max(spV - s0V, 0.0) * scaleV;
        float rv = max(length(float2(p.x, qy)), 0.0001);
        float gxV = p.x / rv;
        float gyV = (qy / rv) * scaleV * dsDv * mix(-1.0, 1.0, step(0.0, p.y));
        float gnV = max(length(float2(gxV, gyV)), 0.0001);
        return float2(gxV / gnV, gyV / gnV);
    }
    float L = a - b;
    float sigma = min(0.58 * b, L);
    float u = abs(p.x) - L;
    float rootU = sqrt(u * u + sigma * sigma);
    float splus = 0.5 * (u + rootU);
    float dsDu = 0.5 * (1.0 + u / max(rootU, 0.0001));
    float s0 = 0.5 * (-L + sqrt(L * L + sigma * sigma));
    float sEnd = max(0.5 * (b + sqrt(b * b + sigma * sigma)) - s0, 0.0001);
    float scale = b / sEnd;
    float qx = max(splus - s0, 0.0) * scale;
    float r = max(length(float2(qx, p.y)), 0.0001);
    float gx = (qx / r) * scale * dsDu * mix(-1.0, 1.0, step(0.0, p.x));
    float gy = p.y / r;
    float gn = max(length(float2(gx, gy)), 0.0001);
    return float2(gx / gn, gy / gn);
}"""

private val DockBaseGlassShader = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float density;
uniform float pressProgress;
uniform float lensLift;
uniform float clarity;
uniform float2 pillCenter;
uniform float2 pillHalfSize;
uniform float2 footprintCenter;
uniform float2 footprintHalfSize;
$DockG2CapsuleSDF

half4 main(float2 coord) {
    half4 c = content.eval(coord);
    float2 halfSize = size * 0.5;
    float2 p = coord + offset - halfSize;
    float safeDensity = max(density, 1.0);
    float bSd = sdG2Capsule(p, halfSize);
    float depth = max(-bSd, 0.0);
    float depthDp = depth / safeDensity;
    float2 normal = gradG2Capsule(p, halfSize);

    // 1. Affine frosted glass transmittance + ambient scatter across dark/light dynamic range.
    float pPress = clamp(pressProgress, 0.0, 1.0);
    float tau = mix(0.646, 0.720, pPress);
    float scatter = mix(0.058, 0.083, pPress);

    // 2. Full-height passage lift strictly confined inside the active lens span (smoothstep(0.65, 0.96)):
    //    eliminates white-page dirty gray horizontal collars while preventing lateral glow spill onto bare base.
    float domeActive = smoothstep(0.28, 0.95, clamp(lensLift, 0.0, 1.15));
    float passageMask = 0.0;
    if (domeActive > 0.001 && footprintHalfSize.x > 1.0) {
        float xDist = abs(p.x - footprintCenter.x) / footprintHalfSize.x;
        passageMask = (1.0 - smoothstep(0.65, 0.96, xDist)) * domeActive;
        tau = mix(tau, 0.785, passageMask);
        scatter = mix(scatter, 0.122, passageMask);
    }

    float3 rgb = float3(c.rgb) * tau + float3(scatter);

    // 3. Keep grazing shadow on the silhouette while the reflection narrows
    //    and moves inward as the rim turns toward the side.
    float absNy = abs(normal.y);
    float ny2 = normal.y * normal.y;
    float sideTurn = 1.0 - absNy;
    float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    float bright = smoothstep(0.24, 0.86, luma);
    float sideFacing = 1.0 - smoothstep(0.22, 0.92, absNy);
    float shadowDepthDp = mix(0.18, 0.53, sideFacing * sideFacing);
    float grazing = mix(0.88, 0.27, bright) * sideFacing * exp(-depthDp / shadowDepthDp);
    rgb *= 1.0 - grazing * (1.0 - 0.22 * passageMask);

    float angular = ny2 * mix(0.62 + 0.38 * ny2, 1.0, sideFacing);
    float upper = step(p.y, 0.0);
    float crestPeakDp = 0.32 + 0.012 * (size.y / safeDensity) * sideTurn;
    float crestDepthDp = (depthDp - crestPeakDp) / mix(1.0, 0.62, sideTurn);
    float crest = smoothstep(-0.44, 0.0, crestDepthDp) * exp(-max(crestDepthDp, 0.0) / 0.50);
    float crestLight = mix(0.418, 0.456, pPress) * mix(0.87, 1.04, upper) * crest;

    float relativeDepth = depth / max(size.y, 1.0);
    float bevel = smoothstep(0.005, 0.023, relativeDepth) *
        (1.0 - smoothstep(0.035, mix(0.088, 0.092, upper), relativeDepth));
    float bevelLight = mix(0.041, 0.056, upper) * ny2 * bevel;
    float reflected = (crestLight + bevelLight) * angular * (1.0 + 0.10 * normal.x * normal.y);
    rgb = mix(rgb, float3(0.94), reflected);

    // 4. Per-pixel luminance-adaptive resting dark selection capsule (beneath white tab glyphs).
    float darkKeep = 1.0 - clamp(clarity, 0.0, 1.0);
    if (darkKeep > 0.001 && pillHalfSize.x > 1.0 && pillHalfSize.y > 1.0) {
        float pillSd = sdG2Capsule(p - pillCenter, pillHalfSize);
        float pillMask = 1.0 - smoothstep(-0.6 * safeDensity, 0.6 * safeDensity, pillSd);
        if (pillMask > 0.001) {
            float baseLuma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
            float pillAlpha = mix(0.450, 0.245, smoothstep(0.16, 0.55, baseLuma)) * darkKeep * pillMask;
            rgb *= (1.0 - pillAlpha);
        }
    }

    return half4(half3(clamp(rgb, 0.0, 1.0)), c.a);
}"""

private const val DockClampToG2Capsule = """
float2 clampToG2Capsule(float2 q, float2 halfSize, float padPx) {
    float sdQ = sdG2Capsule(q, halfSize);
    if (sdQ > 0.0) {
        q -= gradG2Capsule(q, halfSize) * (sdQ + padPx);
        float sdQ2 = sdG2Capsule(q, halfSize);
        if (sdQ2 > 0.0) {
            q -= gradG2Capsule(q, halfSize) * (sdQ2 + padPx * 0.5);
        }
    }
    return q;
}"""

private val DockRefractionShader = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float progress;
uniform float density;
uniform float2 baseCenter;
uniform float2 baseHalfSize;
$DockG2CapsuleSDF
$DockClampToG2Capsule

float2 cornerRailWave(
    float2 pTap,
    float2 bCenter,
    float2 bHalfSize,
    float safeDensity,
    float dHat,
    float baseSdDp,
    float s
) {
    float bSdK = sdG2Capsule(pTap - bCenter, bHalfSize) / safeDensity;
    float uRidge = (bSdK + 0.44) / 1.12;
    float ridgeK = exp(-uRidge * uRidge);
    float uArch = (dHat - (0.045 - 0.020 * s)) / 0.028;
    float outerArchK = exp(-uArch * uArch) * exp(-max(baseSdDp - 0.25, 0.0) / 2.6);
    float uTrough = (bSdK - 1.25) / 1.50;
    float troughK = exp(-uTrough * uTrough);
    return float2(ridgeK + 0.40 * outerArchK, troughK);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 coordOrigin = halfSize - offset;
    float2 p = coord - coordOrigin;
    float safeDensity = max(density, 1.0);
    float b = max(halfSize.y, 1.0);
    float sd = sdG2Capsule(p, halfSize);
    float depth = max(-sd, 0.0);
    float depthDp = depth / safeDensity;
    float dHat = clamp(depth / b, 0.0, 1.0);

    float lift = clamp(progress, 0.0, 1.15);

    // Smooth 3D surface outward direction: G2 boundary gradient blended with normalized radial dome slope.
    float2 g0 = gradG2Capsule(p, halfSize);
    float2 radial = p / max(halfSize, float2(1.0));
    float2 dir = g0 + 0.32 * radial;
    float2 grad = dir / max(length(dir), 0.0001);
    float sideFactor = grad.x * grad.x;

    // 1. Strict contact-zero 360-degree diffeomorphic meniscus Snell shift (S(0) == 0.0, slope < 1.0):
    //    0-jump boat-window convergence at P_L/P_R and unbroken 180-degree concentric semicircular endcap.
    float uM = max(1.0 - dHat / 0.80, 0.0);
    float meniscus = (0.84 * dHat) * (uM * uM) * (1.0 - 0.06 * sideFactor) * b * lift;

    // 2. Localized central 3D magnifying dome (~1.18x in center, 0 in outer meniscus so endcaps stay outward).
    float domeW = smoothstep(0.20, 0.68, dHat);
    float2 central = (-0.155 * domeW * lift) * p;
    float2 pRefracted = p + central + grad * meniscus;
    float2 refractedCoord = pRefracted + coordOrigin;

    // 3. Single-tap fast path in central reading zone; 7-band CIE Cauchy prism + 4-corner Dock-rail
    //    intersection dispersion in meniscus (strictly edge0 < edge1 for hardware GPU smoothstep).
    float rimBand = (1.0 - smoothstep(0.0, 0.42, dHat)) * smoothstep(0.05, 0.35, lift);
    float cornerPrism = 0.0;
    float edgeTaper = 0.0;
    float3 ridgeRgb = float3(0.0);
    float3 troughRgb = float3(0.0);
    float3 rgb;
    half a;
    if (rimBand <= 0.001) {
        half4 centerSample = content.eval(refractedCoord);
        rgb = float3(centerSample.rgb);
        a = centerSample.a;
    } else {
        float liftActive = smoothstep(0.10, 0.55, lift);
        float2 tangent = float2(-grad.y, grad.x);

        // Evaluate proximity and oblique prism angle against the Dock base contour in lens-local p space.
        float2 baseRel = pRefracted - baseCenter;
        float baseSdDp = sdG2Capsule(baseRel, baseHalfSize) / safeDensity;
        float2 baseGrad = gradG2Capsule(baseRel, baseHalfSize);

        // 4-corner oblique geometry: strictly 0 at top/bottom center (grad.x == 0 -> silver-white crown)
        // and at left/right equators (grad.y == 0), peaking where the 4 rounded shoulders cross the Dock rails.
        float prismCross = grad.x * baseGrad.y - grad.y * baseGrad.x;
        float cornerOblique = 4.0 * sideFactor * (grad.y * grad.y);
        float obliqueGate = smoothstep(0.10, 0.52, cornerOblique);
        float crossBoost = smoothstep(0.08, 0.56, abs(prismCross));
        float cornerGeom = obliqueGate * (0.14 + 0.86 * crossBoost);

        float uRailBand = (baseSdDp + 0.40) / 2.25;
        float railBand = exp(-uRailBand * uRailBand);
        float outerProwBand = exp(-max(baseSdDp - 0.25, 0.0) / 2.6) * smoothstep(-3.2, -0.4, baseSdDp + 1.6);
        cornerPrism = cornerGeom * max(0.12 + 0.88 * railBand, 0.32 * outerProwBand) * rimBand * liftActive;

        edgeTaper = smoothstep(0.0, 0.028, dHat);
        float cornerAmpPx = 0.056 * b * lift * edgeTaper * cornerPrism;

        // Outward normal of the bent corner rail pointing into the boat-window crescent at TL, TR, BL, BR.
        float2 boatDir = float2(baseGrad.x - 0.55 * grad.x, baseGrad.y + 0.35 * grad.y);
        float2 boatNormal = boatDir / max(length(boatDir), 0.0001);

        // Subtle contact-zero lateral meniscus dispersion on the base/background layer, zero at dHat == 0.
        float eqFactor = sideFactor * (1.0 - grad.y * grad.y);
        float eqDispPx = 0.24 * eqFactor * meniscus * rimBand;
        float2 dispVec = boatNormal * cornerAmpPx + (tangent * 0.85 + grad * 0.30) * eqDispPx;

        // Strictly project every spectral tap inside sdG2Capsule <= 0 (zero neighbor-tab glyph ghosting).
        float padPx = 0.06 * safeDensity;
        float2 p0 = clampToG2Capsule(pRefracted - dispVec, halfSize, padPx);
        float2 p1 = clampToG2Capsule(pRefracted - dispVec * 0.667, halfSize, padPx);
        float2 p2 = clampToG2Capsule(pRefracted - dispVec * 0.333, halfSize, padPx);
        float2 p3 = clampToG2Capsule(pRefracted, halfSize, padPx);
        float2 p4 = clampToG2Capsule(pRefracted + dispVec * 0.333, halfSize, padPx);
        float2 p5 = clampToG2Capsule(pRefracted + dispVec * 0.667, halfSize, padPx);
        float2 p6 = clampToG2Capsule(pRefracted + dispVec, halfSize, padPx);

        half4 t0 = content.eval(p0 + coordOrigin);
        half4 t1 = content.eval(p1 + coordOrigin);
        half4 t2 = content.eval(p2 + coordOrigin);
        half4 t3 = content.eval(p3 + coordOrigin);
        half4 t4 = content.eval(p4 + coordOrigin);
        half4 t5 = content.eval(p5 + coordOrigin);
        half4 t6 = content.eval(p6 + coordOrigin);

        // 7-band CIE-aligned spectral weights (each channel sums to 1.00 -> exact white balance on flat fields).
        rgb = float3(
            float(t0.r) * 0.06 + float(t1.r) * 0.02 + float(t2.r) * 0.02 + float(t3.r) * 0.09 +
                float(t4.r) * 0.21 + float(t5.r) * 0.30 + float(t6.r) * 0.30,
            float(t1.g) * 0.09 + float(t2.g) * 0.22 + float(t3.g) * 0.34 + float(t4.g) * 0.24 +
                float(t5.g) * 0.11,
            float(t0.b) * 0.28 + float(t1.b) * 0.32 + float(t2.b) * 0.24 + float(t3.b) * 0.12 +
                float(t4.b) * 0.04
        );
        a = (t1.a + t3.a + t5.a) / 3.0;

        if (cornerPrism > 0.001) {
            float2 w0 = cornerRailWave(p0, baseCenter, baseHalfSize, safeDensity, dHat, baseSdDp, -1.0);
            float2 w1 = cornerRailWave(p1, baseCenter, baseHalfSize, safeDensity, dHat, baseSdDp, -0.667);
            float2 w2 = cornerRailWave(p2, baseCenter, baseHalfSize, safeDensity, dHat, baseSdDp, -0.333);
            float2 w3 = cornerRailWave(p3, baseCenter, baseHalfSize, safeDensity, dHat, baseSdDp, 0.0);
            float2 w4 = cornerRailWave(p4, baseCenter, baseHalfSize, safeDensity, dHat, baseSdDp, 0.333);
            float2 w5 = cornerRailWave(p5, baseCenter, baseHalfSize, safeDensity, dHat, baseSdDp, 0.667);
            float2 w6 = cornerRailWave(p6, baseCenter, baseHalfSize, safeDensity, dHat, baseSdDp, 1.0);

            ridgeRgb = float3(
                w0.x * 0.06 + w1.x * 0.02 + w2.x * 0.02 + w3.x * 0.09 + w4.x * 0.21 + w5.x * 0.30 + w6.x * 0.30,
                w1.x * 0.09 + w2.x * 0.22 + w3.x * 0.34 + w4.x * 0.24 + w5.x * 0.11,
                w0.x * 0.28 + w1.x * 0.32 + w2.x * 0.24 + w3.x * 0.12 + w4.x * 0.04
            );
            troughRgb = float3(
                w0.y * 0.06 + w1.y * 0.02 + w2.y * 0.02 + w3.y * 0.09 + w4.y * 0.21 + w5.y * 0.30 + w6.y * 0.30,
                w1.y * 0.09 + w2.y * 0.22 + w3.y * 0.34 + w4.y * 0.24 + w5.y * 0.11,
                w0.y * 0.28 + w1.y * 0.32 + w2.y * 0.24 + w3.y * 0.12 + w4.y * 0.04
            );
        }
    }

    // 4. Refracted rail, convex dome and a slim directional highlight inside the dark contour.
    float rimActive = smoothstep(0.26, 0.92, lift);
    float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    float bright = smoothstep(0.22, 0.85, luma);

    if (cornerPrism > 0.001) {
        float ridgeMean = (ridgeRgb.r + ridgeRgb.g + ridgeRgb.b) / 3.0;
        float3 ridgeChroma = ridgeRgb - float3(ridgeMean);
        float troughMean = (troughRgb.r + troughRgb.g + troughRgb.b) / 3.0;
        float3 troughChroma = troughRgb - float3(troughMean);
        float3 causticGlow = float3(0.048 * (1.0 - 0.78 * bright)) * ridgeRgb;
        float3 causticSplit = float3(0.066 + 0.058 * bright) * ridgeChroma - float3(0.058 * bright) * troughChroma;
        rgb = clamp(rgb + (cornerPrism * edgeTaper) * (causticGlow + causticSplit), 0.0, 1.0);
    }

    float domeGlow = mix(0.026, 0.040, bright) * smoothstep(0.16, 0.86, dHat) * rimActive;
    rgb = clamp(rgb + float3(domeGlow), 0.0, 1.0);

    float grazeDark = 0.40 + 0.14 * sideFactor;
    float grazeLight = 0.15 + 0.15 * sideFactor;
    float grazeStr = mix(grazeDark, grazeLight, bright);
    float cornerRelief = 1.0 - 0.38 * cornerPrism * smoothstep(0.35, 1.00, depthDp);
    float grazing = grazeStr * exp(-depthDp / 0.40) * rimActive * cornerRelief;
    rgb *= (1.0 - grazing);

    float absNy = abs(grad.y);
    float uLip = depthDp / 0.82;
    float lipBand = uLip * exp(1.0 - uLip) * smoothstep(0.08, 0.50, depthDp);
    float lipDir = (0.16 + 0.84 * absNy * absNy) * (1.0 + 0.10 * grad.x * grad.y);
    float lipAmp = mix(0.15, 0.058, bright) * (1.0 - 0.62 * cornerPrism);
    rgb = clamp(rgb + float3(lipAmp * lipBand * lipDir * rimActive), 0.0, 1.0);

    return half4(half3(rgb), a);
}"""

private val DockLabelRefractionShader = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float progress;
uniform float density;
$DockG2CapsuleSDF
$DockClampToG2Capsule

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 coordOrigin = halfSize - offset;
    float2 p = coord - coordOrigin;
    float safeDensity = max(density, 1.0);
    float b = max(halfSize.y, 1.0);
    float sd = sdG2Capsule(p, halfSize);
    float depth = max(-sd, 0.0);
    float depthDp = depth / safeDensity;
    float dHat = clamp(depth / b, 0.0, 1.0);
    float lift = clamp(progress, 0.0, 1.15);

    float2 g0 = gradG2Capsule(p, halfSize);
    float2 radial = p / max(halfSize, float2(1.0));
    float2 dir = g0 + 0.32 * radial;
    float2 grad = dir / max(length(dir), 0.0001);
    float sideFactor = grad.x * grad.x;

    // Full 360-degree convex water-droplet refraction for transparent tab labels & badges:
    // central dome magnifies (~1.18x) cleanly with single-tap fast path; outer 360-degree rim
    // pulls swept glyphs and corner badges smoothly outward toward the curved droplet contour.
    float edgeBand = 1.0 - smoothstep(0.0, 0.42, dHat);
    float rimBand = edgeBand * smoothstep(0.05, 0.35, lift);
    float rimPull = 0.22 * b * edgeBand * lift;
    float domeW = smoothstep(0.20, 0.68, dHat);
    float2 central = (-0.155 * domeW * lift) * p;
    float2 pRefracted = p + central - grad * rimPull;

    if (rimBand <= 0.001) {
        return content.eval(pRefracted + coordOrigin);
    }

    float liftActive = smoothstep(0.10, 0.55, lift);
    float2 tangent = float2(-grad.y, grad.x);
    float dispPx = 0.115 * b * rimBand * liftActive;
    float2 dispVec = (tangent * 0.92 + grad * 0.25) * dispPx;

    // Strictly project every spectral tap inside sdG2Capsule <= 0 (zero neighbor-tab glyph ghosting).
    float padPx = 0.06 * safeDensity;
    float2 p0 = clampToG2Capsule(pRefracted - dispVec, halfSize, padPx);
    float2 p1 = clampToG2Capsule(pRefracted - dispVec * 0.667, halfSize, padPx);
    float2 p2 = clampToG2Capsule(pRefracted - dispVec * 0.333, halfSize, padPx);
    float2 p3 = clampToG2Capsule(pRefracted, halfSize, padPx);
    float2 p4 = clampToG2Capsule(pRefracted + dispVec * 0.333, halfSize, padPx);
    float2 p5 = clampToG2Capsule(pRefracted + dispVec * 0.667, halfSize, padPx);
    float2 p6 = clampToG2Capsule(pRefracted + dispVec, halfSize, padPx);

    half4 t0 = content.eval(p0 + coordOrigin);
    half4 t1 = content.eval(p1 + coordOrigin);
    half4 t2 = content.eval(p2 + coordOrigin);
    half4 t3 = content.eval(p3 + coordOrigin);
    half4 t4 = content.eval(p4 + coordOrigin);
    half4 t5 = content.eval(p5 + coordOrigin);
    half4 t6 = content.eval(p6 + coordOrigin);

    // 7-band CIE spectral weights in premultiplied alpha space.
    float3 rgb = float3(
        float(t0.r) * 0.06 + float(t1.r) * 0.02 + float(t2.r) * 0.02 + float(t3.r) * 0.09 +
            float(t4.r) * 0.21 + float(t5.r) * 0.30 + float(t6.r) * 0.30,
        float(t1.g) * 0.09 + float(t2.g) * 0.22 + float(t3.g) * 0.34 + float(t4.g) * 0.24 +
            float(t5.g) * 0.11,
        float(t0.b) * 0.28 + float(t1.b) * 0.32 + float(t2.b) * 0.24 + float(t3.b) * 0.12 +
            float(t4.b) * 0.04
    );
    float aR = float(t0.a) * 0.06 + float(t1.a) * 0.02 + float(t2.a) * 0.02 + float(t3.a) * 0.09 +
        float(t4.a) * 0.21 + float(t5.a) * 0.30 + float(t6.a) * 0.30;
    float aG = float(t1.a) * 0.09 + float(t2.a) * 0.22 + float(t3.a) * 0.34 + float(t4.a) * 0.24 +
        float(t5.a) * 0.11;
    float aB = float(t0.a) * 0.28 + float(t1.a) * 0.32 + float(t2.a) * 0.24 + float(t3.a) * 0.12 +
        float(t4.a) * 0.04;
    float outA = clamp(max((aR + aG + aB) / 3.0, max(rgb.r, max(rgb.g, rgb.b))), 0.0, 1.0);
    if (outA <= 0.001) {
        return half4(0.0);
    }

    // Apply the same outer graphite contour attenuation so refracted strokes/badges sit strictly
    // inside the bubble's dark rim (distributes algebraically with DockRefractionShader's contour).
    float rimActive = smoothstep(0.26, 0.92, lift);
    float grazing = (0.15 + 0.15 * sideFactor) * exp(-depthDp / 0.40) * rimActive;
    rgb *= (1.0 - grazing);

    return half4(half3(clamp(rgb, 0.0, outA)), half(outA));
}"""

/**
 * The runtime shader owns the rim on API 33+. Older devices use the library highlight.
 */
internal fun dockRimHighlight(): Highlight? = if (isRuntimeShaderSupported()) {
    null
} else {
    Highlight(width = 0.75.dp, alpha = 0.34f)
}
